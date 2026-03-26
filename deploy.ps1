<#
.SYNOPSIS
    Build and deploy FitnessExtractor to a Hetzner server via Tailscale.

.DESCRIPTION
    1. Builds the React SPA (ai-ui)
    2. Copies it into src/main/resources/static/ai/
    3. Builds the Spring Boot fat jar (SPA baked in)
    4. SCPs the jar + docker-compose.yml + .env to the server
    5. SSHs in and restarts the containers

.PARAMETER Server
    Tailscale hostname or IP of the Hetzner machine.

.PARAMETER User
    SSH user on the server (default: root).

.PARAMETER DeployDir
    Remote directory to deploy into (default: /opt/fitness-extractor).

.PARAMETER SkipBuild
    Skip the local build step and just deploy whatever jar exists.

.EXAMPLE
    .\deploy.ps1 -Server my-hetzner
    .\deploy.ps1 -Server 100.64.1.5 -User deploy -DeployDir /home/deploy/fitness
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$Server,

    [string]$User = "root",

    [string]$DeployDir = "/opt/fitness-extractor",

    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$JarName = "FitnessExtractor-2.0.0.jar"
$JarPath = Join-Path $ProjectRoot "target" $JarName
$SshTarget = "${User}@${Server}"

function Write-Step($msg) {
    Write-Host "`n==> $msg" -ForegroundColor Cyan
}

# --- 1. Build ---
if (-not $SkipBuild) {
    Write-Step "Building React SPA"
    Push-Location (Join-Path $ProjectRoot "ai-ui")
    npm ci --ignore-scripts
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "SPA build failed" }
    Pop-Location

    Write-Step "Building fat jar (SPA is baked in)"
    Push-Location $ProjectRoot
    mvn -DskipTests package -q
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    Pop-Location
}

if (-not (Test-Path $JarPath)) {
    throw "Jar not found at $JarPath - run without -SkipBuild first"
}

$jarSizeMB = [math]::Round((Get-Item $JarPath).Length / 1MB, 1)
Write-Step "Jar ready: $JarPath ($jarSizeMB MB)"

# --- 2. Upload ---
Write-Step "Creating remote directory $DeployDir"
ssh $SshTarget "mkdir -p $DeployDir"

Write-Step "Uploading jar"
scp $JarPath "${SshTarget}:${DeployDir}/app.jar"

Write-Step "Uploading docker-compose.yml"
scp (Join-Path $ProjectRoot "docker-compose.yml") "${SshTarget}:${DeployDir}/docker-compose.yml"

$envFile = Join-Path $ProjectRoot ".env"
if (Test-Path $envFile) {
    Write-Step "Uploading .env"
    scp $envFile "${SshTarget}:${DeployDir}/.env"
} else {
    Write-Host "  No .env file found - make sure one exists on the server" -ForegroundColor Yellow
}

# --- 3. Deploy on server ---
Write-Step "Deploying on $Server"

# We override docker-compose to use the pre-built jar instead of building in Docker
$remoteScript = @"
cd $DeployDir

# Create a minimal Dockerfile that just runs the pre-built jar
cat > Dockerfile.deploy << 'DEOF'
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y python3 && rm -rf /var/lib/apt/lists/*
COPY app.jar app.jar
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
DEOF

# Patch docker-compose to use our deploy Dockerfile
sed 's|build: \.|build:\n      context: .\n      dockerfile: Dockerfile.deploy|' docker-compose.yml > docker-compose.deploy.yml

# Stop old containers, rebuild, start
docker compose -f docker-compose.deploy.yml down --remove-orphans 2>/dev/null || true
docker compose -f docker-compose.deploy.yml build --no-cache app
docker compose -f docker-compose.deploy.yml up -d

echo ""
echo "Waiting for health check..."
sleep 5
docker compose -f docker-compose.deploy.yml ps
echo ""
curl -sf http://localhost:8181/ai/stats && echo " <- /ai/stats OK" || echo "WARNING: app not responding yet (may still be starting)"
"@

ssh $SshTarget $remoteScript

Write-Step "Done! App should be running at http://${Server}:8181"
Write-Host "  SPA:       http://${Server}:8181/ai/" -ForegroundColor Green
Write-Host "  Dashboard: http://${Server}:8181/" -ForegroundColor Green
Write-Host "  DB:        PostgreSQL on port 5433" -ForegroundColor Green

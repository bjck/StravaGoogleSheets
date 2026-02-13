# FitnessExtractor

FitnessExtractor is a Spring Boot app that syncs Strava activities and Garmin health metrics into Google Sheets, then renders a lightweight dashboard to visualize trends and recovery.

## Features
- Sync Strava activities into a "Strava Activities" sheet.
- Sync Garmin daily metrics into a "Garmin Metrics" sheet.
- Sync Garmin stress and heart-rate samples into a "Garmin Stress HR" sheet.
- Web UI to trigger syncs and view a dashboard.
- Download a zip bundle of CSV exports for all sheets.
- Recovery snapshot calculated from the latest workout and post-workout stress data.

## Tech stack
- Java 21, Spring Boot 3.2, Thymeleaf
- Google Sheets API
- Apache HttpClient 5
- Chart.js (dashboard)
- Spring Modulith (optional docs)

## Prerequisites
- Java 21+
- Maven 3.9+
- Google Sheets API enabled with a service account JSON
- Strava API app (client id, secret, refresh token)
- Garmin Connect credentials

## Configuration
Configuration is read from environment variables or a local `.env` file. Keys are normalized to uppercase with underscores.

Required for Google Sheets:
- `GOOGLE_SPREADSHEET_ID`
- `GOOGLE_SERVICE_ACCOUNT_KEY_PATH` (file path or `classpath:service-account.json`)

Required for Strava sync:
- `STRAVA_CLIENT_ID`
- `STRAVA_CLIENT_SECRET`
- `STRAVA_REFRESH_TOKEN`

Required for Garmin sync:
- `GARMIN_USERNAME`
- `GARMIN_PASSWORD`

Optional Garmin helpers (used to bypass or refresh sessions):
- `GARMIN_SESSION_COOKIE`
- `GARMIN_GARTH_TOKEN` (base64 Garth bundle or OAuth2 JWT)
- `GARMIN_TOKEN_SCRIPT` (path to a Python token refresh script)
- `GARMIN_PYTHON_PATH` (path to the Python executable)

Optional Modulith docs:
- `MODULITH_DOCS_ENABLED=true`
- `MODULITH_DOCS_OUTPUT=target/modulith-docs`

Spring AI (required for AI workout queries):
- `SPRING_AI_OPENAI_API_KEY`
- Optional: `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` (defaults to the OpenAI default model)
- `SPRING_AI_VERTEX_AI_GEMINI_API_KEY` (if using Gemini)
- `SPRING_AI_VERTEX_AI_GEMINI_PROJECT_ID` (if using Gemini)
- `SPRING_AI_VERTEX_AI_GEMINI_LOCATION` (if using Gemini)
- `FITNESS_AI_PROVIDER` (set to `gemini` to use Gemini instead of OpenAI)

Example `.env` (use your own values):
```env
STRAVA_CLIENT_ID=your_client_id
STRAVA_CLIENT_SECRET=your_client_secret
STRAVA_REFRESH_TOKEN=your_refresh_token
GOOGLE_SPREADSHEET_ID=your_spreadsheet_id
GOOGLE_SERVICE_ACCOUNT_KEY_PATH=classpath:service-account.json
GARMIN_USERNAME=your_email
GARMIN_PASSWORD=your_password
# Optional Garmin token refresh
# GARMIN_GARTH_TOKEN=base64_garth_bundle_or_jwt
# GARMIN_TOKEN_SCRIPT=/path/to/get_tokens_example.py
# GARMIN_PYTHON_PATH=/usr/bin/python3
# Spring AI
# SPRING_AI_OPENAI_API_KEY=your_openai_api_key
# FITNESS_AI_PROVIDER=gemini
# SPRING_AI_VERTEX_AI_GEMINI_API_KEY=your_gemini_api_key
# SPRING_AI_VERTEX_AI_GEMINI_PROJECT_ID=your_project_id
# SPRING_AI_VERTEX_AI_GEMINI_LOCATION=us-central1
```

Notes:
- Share the Google Sheet with the service account email from the JSON key.
- The app will create missing tabs and headers automatically.
- If `GARMIN_TOKEN_SCRIPT` is configured, the app may update `GARMIN_GARTH_TOKEN` inside `.env` after a refresh.

## Run locally
```bash
mvn spring-boot:run
```

Then open:
- `http://localhost:8080/` (sync console)
- `http://localhost:8080/visualize` (dashboard)

## Run tests
```bash
mvn test
```

## Docker
```bash
docker build -t fitness-extractor .
docker run --rm -p 8080:8080 ^
  --env-file .env ^
  -e GOOGLE_SERVICE_ACCOUNT_KEY_PATH=/app/service-account.json ^
  -v /path/to/service-account.json:/app/service-account.json ^
  fitness-extractor
```

If you use a Garmin token refresh script in Docker, mount the script and set:
- `GARMIN_TOKEN_SCRIPT=/app/get_tokens_example.py`
- `GARMIN_PYTHON_PATH=/usr/bin/python3`

## Endpoints
- `GET /` - Sync console
- `POST /sync/all` - Sync Strava and Garmin
- `POST /sync/strava` - Sync Strava only
- `POST /sync/garmin` - Sync Garmin only
- `GET /sync/export` - Download a zip of CSV exports for all sheets
- `POST /ai/workouts` - AI-powered workout query for a date range
- `GET /visualize` - Dashboard

## Troubleshooting
- Strava 401 errors usually mean missing scopes; ensure the refresh token includes `activity:read` or `activity:read_all`.
- Garmin 401/403 can happen due to CAPTCHA or expired sessions; try `GARMIN_GARTH_TOKEN`, `GARMIN_SESSION_COOKIE`, or the refresh script.
- Google Sheets 400 range errors typically indicate a sheet name mismatch or missing sharing permissions.

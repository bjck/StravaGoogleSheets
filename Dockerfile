# --- Stage 1: Build the React SPA ---
FROM node:20-alpine AS spa
WORKDIR /spa
COPY ai-ui/package.json ai-ui/package-lock.json ./
RUN npm ci --ignore-scripts
COPY ai-ui/ ./
RUN npm run build

# --- Stage 2: Build the fat jar (SPA already in static/) ---
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -q
COPY src ./src
# Overwrite stale SPA build with freshly built one
COPY --from=spa /src/main/resources/static/ai/ ./src/main/resources/static/ai/
RUN mvn -DskipTests package -q

# --- Stage 3: Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y python3 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/FitnessExtractor-2.0.0.jar app.jar
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

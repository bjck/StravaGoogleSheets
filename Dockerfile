# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn -DskipTests package -q

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y python3 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/FitnessExtractor-2.0.0.jar app.jar
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

## Multi-stage Dockerfile for Spring Boot server on Cloud Run

### Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy pom and source
COPY pom.xml .
COPY src ./src

# Build the application (skip tests for faster container build)
RUN mvn -q -DskipTests package


### Run stage
FROM eclipse-temurin:17-jre AS runner

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /workspace/target/server-0.0.1-SNAPSHOT.jar /app/app.jar

# Cloud Run sets PORT env var; Spring Boot reads it via application.properties
ENV PORT=8080

# Use a non-root user for better security (Cloud Run requirement)
RUN useradd -m spring
USER spring

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]



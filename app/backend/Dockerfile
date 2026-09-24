# Multi-stage build for SEAJ Trading Platform
# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and source code
COPY app/backend/pom.xml ./pom.xml
COPY app/backend/src ./src

# Build the application
RUN mvn -q clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/seaj-app.jar /app/seaj-app.jar

# Create logs directory
RUN mkdir -p /app/logs

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/gateway/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "seaj-app.jar"]

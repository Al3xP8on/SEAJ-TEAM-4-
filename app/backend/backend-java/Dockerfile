# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/team-skeleton.jar app.jar

# OpenTelemetry Java agent (v2.31.1) from the official image, pinned by digest.
# docker-compose.yml loads it through JAVA_TOOL_OPTIONS and points it at the collector.
RUN mkdir -p /opt/otel && chmod 755 /opt/otel
COPY --chmod=644 --from=ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:2.31.1@sha256:6f70427eee0ddfaaa861632eadf2d1c9ba03f4daa30e68ae352eac269e9caf7e \
    /javaagent.jar /opt/otel/opentelemetry-javaagent.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

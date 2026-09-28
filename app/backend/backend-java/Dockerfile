# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/team-skeleton.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

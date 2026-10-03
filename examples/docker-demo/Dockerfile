# Multi-stage build ensuring zero build secrets in final image layers
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy SDK parent and submodules
COPY sdk /build/sdk
RUN mvn -f /build/sdk/pom.xml clean install -DskipTests

# Copy and build Spring Boot Demo app
COPY examples/spring-boot-demo /build/app
RUN mvn -f /build/app/pom.xml clean package -DskipTests

# Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/app/target/spring-boot-demo-1.0.0.jar /app/app.jar

# Zero hardcoded secrets, Zero .env files on disk
# Runtime environment variables configure SecretVault connection & workload identity
ENV SECRETVAULT_ENDPOINT="http://secretvault-backend:8080"
ENV SECRETVAULT_WORKSPACE="default"
ENV SECRETVAULT_PROJECT="payment-gateway"
ENV SECRETVAULT_ENVIRONMENT="production"
ENV SECRETVAULT_AUTH_MODE="ENV"

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

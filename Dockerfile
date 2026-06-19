# =============================================================================
# Multi-stage Dockerfile for securebank-fraud-service.
#   Stage 1 (build): run Maven on JDK 21, producing the executable Spring Boot jar.
#   Stage 2 (run):   copy ONLY the jar onto a slim JRE 21 runtime image.
# Exposes 8084 (HTTP/actuator/REST) and 9094 (gRPC FraudService).
# =============================================================================

# ---- Stage 1: build -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first and warm the dependency cache (better layer caching: deps only
# re-download when pom.xml changes, not on every source edit).
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Now copy sources (including the vendored protos) and build the jar.
COPY src ./src
# Skip tests in the image build; CI runs them separately.
RUN mvn -q -B -DskipTests package

# ---- Stage 2: runtime ---------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user for safety.
RUN useradd --system --uid 10001 appuser
USER appuser

# Copy the fat jar from the build stage.
COPY --from=build /build/target/securebank-fraud-service-*.jar app.jar

# Default to the docker profile (Redis at host "redis", AI key from secret/env).
ENV SPRING_PROFILES_ACTIVE=docker
# Virtual threads are enabled in config; nothing special needed for the JVM here.

# 8084 = HTTP (actuator + optional REST + Swagger); 9094 = gRPC FraudService.
EXPOSE 8084 9094

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

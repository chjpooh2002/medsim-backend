# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY backend/gradlew .
COPY backend/gradle gradle/
COPY backend/build.gradle .
COPY backend/settings.gradle .
COPY backend/src src/

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# ── Run stage ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

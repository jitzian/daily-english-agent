# =============================================================
# Stage 1 – Build
# Uses eclipse-temurin JDK 21 + the project's own Gradle wrapper
# so the wrapper version (9.x) satisfies Spring Boot 4.1's
# requirement of Gradle 8.14+.
# =============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Install bash (needed by the Gradle wrapper script)
RUN apk add --no-cache bash

# Copy the wrapper first — it downloads the right Gradle version
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/

# Make wrapper executable and pre-fetch Gradle distribution
RUN chmod +x gradlew && ./gradlew --version --no-daemon

# Copy dependency descriptors for layer caching
COPY build.gradle.kts settings.gradle.kts ./

# Pre-resolve dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy full source and build the WAR
COPY src/ src/
RUN ./gradlew clean build -x test --no-daemon

# =============================================================
# Stage 2 – Runtime
# Minimal JRE-only Alpine image.  No build tools, no source,
# smallest possible attack surface.
# =============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the runnable artifact from the builder stage
COPY --from=builder /app/build/libs/daily-english-agent-0.0.1-SNAPSHOT.war app.war

# =============================================================
# Timezone – must be America/New_York so Spring's @Scheduled
# cron "0 0 7 * * *" fires at 7 AM EST / 8 AM EDT correctly.
# Documented requirement: deploy with TZ=America/New_York.
# =============================================================
ENV TZ=America/New_York
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo "$TZ" > /etc/timezone

USER appuser

# Spring Boot / Tomcat  →  8090
# Ktor                  →  8091
EXPOSE 8090 8091

ENTRYPOINT ["java", "-jar", "app.war"]



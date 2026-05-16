# syntax=docker/dockerfile:1.7
#
# Multi-stage build:
#   1. `build` uses the full JDK + Gradle wrapper to produce the boot jar.
#   2. `runtime` is a slim JRE image that just runs the jar.
#
# Layer caching: Gradle files are copied separately from source so dependency
# resolution caches as long as build.gradle.kts / settings.gradle.kts don't
# change.

# ──────────────────────────────────────────────────────────────────────────────
# Build stage
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Wrapper + gradle files first (cache layer)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew && \
    ./gradlew --no-daemon --version

# Resolve dependencies into the build cache. The `|| true` guard keeps this
# layer reproducible even if a transient module can't be resolved — we'll
# re-attempt during the real build below.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Source last
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ──────────────────────────────────────────────────────────────────────────────
# Runtime stage
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# Non-root user for the JVM process
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app

# Copy the fat jar (single artifact — Spring Boot bootJar)
COPY --from=build /app/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

# Container-aware JVM defaults; -XX:MaxRAMPercentage caps heap relative to the
# container's cgroup memory limit so we behave well under restricted memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

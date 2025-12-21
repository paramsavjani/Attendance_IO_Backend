# Multi-stage build for Spring Boot application
# Using latest Gradle (8.14+ or 9.x) as required by Spring Boot 4.0.0
FROM gradle:jdk17 AS build
WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src
COPY attendance-io-param-firebase-adminsdk-fbsvc-e6420e1501.json ./attendance-io-param-firebase-adminsdk-fbsvc-e6420e1501.json

# Build the application
RUN gradle bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port (default Spring Boot port)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health 2>/dev/null || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]


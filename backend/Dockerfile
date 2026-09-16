# ==============================================================================
# ResolveAI Backend — Multi-Stage Production Dockerfile (Java 21 / Spring Boot)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Stage
# ------------------------------------------------------------------------------
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven parent & backend project definitions first for layer caching
COPY pom.xml ./
COPY backend/pom.xml ./backend/

# Download dependencies in offline-ready layer
RUN mvn -B -f backend/pom.xml dependency:go-offline -DexcludeGroupIds=org.projectlombok || true

# Copy backend source code
COPY backend/src ./backend/src

# Package backend executable jar (skipping unit/integration tests during container build)
RUN mvn -B -f backend/pom.xml clean package -DskipTests

# ------------------------------------------------------------------------------
# Stage 2: Production Runtime Stage
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Security: Create non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Install curl for reliable container health check
RUN apk add --no-cache curl

# Copy packaged fat jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/backend/target/backend-*.jar app.jar

# Switch to non-root user
USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

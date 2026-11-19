# Multi-stage build for PatientVault
# Stage 1: Build the application
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Build the application
RUN mvn clean package -DskipTests=true -q

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/target/patientvault-*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# Run the application
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]

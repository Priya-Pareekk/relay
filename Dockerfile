# Stage 1: Build stage with JDK and Maven
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and POM for dependency layer caching
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy application source code and compile optimized production jar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage with minimal, slim JRE (no build tools or JDK)
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create a dedicated non-root service user for container security
RUN addgroup -S relay && adduser -S relay -G relay

# Copy solely the built artifact from the builder stage
COPY --from=builder /workspace/target/relay-*.jar app.jar
RUN chown -R relay:relay /app

USER relay

EXPOSE 8080

# Configure JVM container memory ergonomics
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=20s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

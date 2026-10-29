# syntax=docker/dockerfile:1
# Build context: smart-garden-backend/ (needs grpc-contracts). See docker-compose.yml.

# ---- build stage: compile grpc-contracts + ai-service with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY grpc-contracts/pom.xml grpc-contracts/pom.xml
COPY common/pom.xml common/pom.xml
COPY grpc-contracts/src grpc-contracts/src
COPY common/src common/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f grpc-contracts/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f common/pom.xml install -DskipTests
COPY ai-service/pom.xml ai-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f ai-service/pom.xml dependency:go-offline -DskipTests || true
COPY ai-service/src ai-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f ai-service/pom.xml package -DskipTests \
    && cp ai-service/target/ai-service-*.jar /workspace/app.jar

# ---- runtime stage: JRE 21 only, non-root user
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 9093 8083
# El healthcheck usa el puerto de gestión (actuator)
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD ["sh", "-c", "curl -fs http://localhost:8083/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

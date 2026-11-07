# syntax=docker/dockerfile:1
# Build context: smart-garden-backend/ (needs grpc-contracts and common). Used by docker-compose.yml (local) and by
# the Azure Container Apps deployment (infra/azure, .github/workflows/deploy-telemetry-service.yml).

# ---- build stage: compile grpc-contracts + common + telemetry-service with Maven
# Maven runs on the build machine architecture; only the runtime stages are per-platform.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY grpc-contracts/pom.xml grpc-contracts/pom.xml
COPY common/pom.xml common/pom.xml
COPY grpc-contracts/src grpc-contracts/src
COPY common/src common/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f grpc-contracts/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f common/pom.xml install -DskipTests
COPY telemetry-service/pom.xml telemetry-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f telemetry-service/pom.xml dependency:go-offline -DskipTests || true
COPY telemetry-service/src telemetry-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f telemetry-service/pom.xml package -DskipTests \
    && cp telemetry-service/target/telemetry-service-*.jar /workspace/app.jar

# ---- CDS stage: extract the jar (faster class loading) and record a Class Data Sharing archive (AppCDS).
# The training run (-Dspring.context.exit=onRefresh) creates the beans and exits before anything starts (no gRPC
# server, no MQTT, no HTTP). The database is not reachable at build time, so Flyway and the JPA schema validation
# are switched off ONLY for this run, with throw-away values. If the training fails the image still works, only
# without the archive (-Xshare:auto).
FROM eclipse-temurin:21-jre AS cds
WORKDIR /app
COPY --from=build /workspace/app.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && test -f /app/app.jar && test -d /app/lib
RUN SPRING_PROFILES_ACTIVE=docker \
    SPRING_FLYWAY_ENABLED=false \
    SPRING_JPA_HIBERNATE_DDL_AUTO=none \
    SPRING_JPA_PROPERTIES_HIBERNATE_BOOT_ALLOW_JDBC_METADATA_ACCESS=false \
    SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect \
    SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:1/cds \
    SPRING_DATASOURCE_USERNAME=cds SPRING_DATASOURCE_PASSWORD=cds \
    SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT=-1 \
    SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=1000 \
    JWT_SECRET=cds-training-only-0123456789abcdefghij ADMIN_PASSWORD=cds-training-only \
    GEMINI_API_KEY=cds-training-only \
    java -XX:ArchiveClassesAtExit=/app/app.jsa -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k \
      -Dspring.context.exit=onRefresh -jar /app/app.jar > /tmp/cds.log 2>&1 \
    || (echo "CDS training failed; the image runs without the archive" && tail -20 /tmp/cds.log)

# ---- runtime stage: JRE 21 only, non-root user
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=cds /app /app
USER app
# Small containers: SerialGC and C1 only (TieredStopAtLevel=1) start faster on 0.25-0.5 vCPU.
# -Xshare:auto uses /app/app.jsa when present and valid, otherwise the JVM starts normally.
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError" \
    JAVA_CDS_OPTS="-XX:SharedArchiveFile=/app/app.jsa -Xshare:auto"
EXPOSE 9091 8081
# El healthcheck usa el puerto de gestión (actuator)
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD ["sh", "-c", "curl -fs http://localhost:8081/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS $JAVA_CDS_OPTS -jar /app/app.jar"]

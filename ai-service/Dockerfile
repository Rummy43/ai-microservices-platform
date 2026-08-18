# syntax=docker/dockerfile:1
# Layered Spring Boot image. Build the jar on the host first:
#   ./gradlew bootJar
#   docker build -t ai-platform/ai-service:1.0.0 .

# ---- Stage 1: explode the fat jar into cache-friendly layers ----
FROM eclipse-temurin:21-jre AS extractor
WORKDIR /build
COPY build/libs/*-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre

COPY build/otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

RUN groupadd --gid 1001 spring && useradd --uid 1001 --gid spring spring
WORKDIR /app
RUN mkdir -p /app/logs && chown spring:spring /app/logs

COPY --from=extractor /build/extracted/dependencies/ ./
COPY --from=extractor /build/extracted/spring-boot-loader/ ./
COPY --from=extractor /build/extracted/snapshot-dependencies/ ./
COPY --from=extractor /build/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0" \
    OTEL_SERVICE_NAME=ai-service \
    OTEL_TRACES_EXPORTER=otlp \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none \
    OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318

USER 1001:1001
EXPOSE 8083
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml .

COPY libs/         libs/
COPY services/     services/
COPY platform/     platform/

RUN mvn clean package \
  -pl services/transaction-service \
  -am \
  -DskipTests \
  --no-transfer-progress \
  -q

FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="fintechwave-transaction" \
  org.opencontainers.image.description="FintechWave Transaction Service" \
  org.opencontainers.image.version="1.0.0"

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

COPY --from=builder /workspace/services/transaction-service/target/transaction-1.0.0.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

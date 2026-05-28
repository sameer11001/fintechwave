FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml .

COPY libs/         libs/
COPY services/     services/
COPY platform/     platform/

RUN mvn clean package \
  -pl services/iam-service \
  -am \
  -DskipTests \
  --no-transfer-progress \
  -q

FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="fintechwave-iam" \
  org.opencontainers.image.description="FintechWave IAM Service" \
  org.opencontainers.image.version="1.0.0"

# Non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

COPY --from=builder /workspace/services/iam-service/target/iam-1.0.0.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

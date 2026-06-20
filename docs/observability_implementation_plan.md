# Observability & Monitoring Implementation Plan

This plan details the steps required to implement the target observability architecture for the FintechWave microservices platform. By following this plan, we will establish a unified telemetry pipeline that avoids vendor lock-in and provides centralized visibility into logs, metrics, and traces.

## 🏗️ Target Architecture Overview

*   **Instrumentation**: OpenTelemetry (OTel) Java Agent or SDK in Spring Boot services.
*   **Telemetry Pipeline**: OpenTelemetry Collector (receives OTLP data, processes it, and routes it).
*   **Logs**: Grafana Loki
*   **Traces**: Grafana Tempo
*   **Metrics**: Prometheus (suitable for the current scale)
*   **Visualization**: Grafana

---

## Phase 1: Infrastructure Setup (Docker Compose)

We need to add the foundational observability components to our `docker-compose.yml` and provide their respective configuration files.

1.  **Create Configuration Directories**:
    *   `docker/observability/otel-collector/`
    *   `docker/observability/prometheus/`
    *   `docker/observability/loki/`
    *   `docker/observability/tempo/`
    *   `docker/observability/grafana/`
2.  **Define OpenTelemetry Collector (`otel-collector-config.yaml`)**:
    *   Configure **Receivers**: `otlp` (gRPC and HTTP) for application telemetry, and specific receivers for infrastructure (Redis, MongoDB, Postgres).
    *   Configure **Processors**: `batch`, `memory_limiter`.
    *   Configure **Exporters**:
        *   Metrics -> `prometheusremotewrite` or Prometheus exporter.
        *   Logs -> `loki`.
        *   Traces -> `otlp` (pointing to Tempo).
    *   Configure **Pipelines**: Link receivers to exporters via processors.
3.  **Define Backend Configurations**:
    *   `prometheus.yml`: Scrape the OTel collector or act as a remote-write destination.
    *   `loki-config.yaml`: Basic local storage configuration.
    *   `tempo-config.yaml`: Receive OTLP traces and store locally.
4.  **Define Grafana Provisioning**:
    *   Automatically provision datasources for Prometheus, Loki, and Tempo in `grafana/provisioning/datasources/`.
5.  **Update `docker-compose.yml`**:
    *   Add the `otel-collector`, `prometheus`, `loki`, `tempo`, and `grafana` services.
    *   Ensure they run on the `fintechwave` network.

---

## Phase 2: Application Instrumentation (Spring Boot)

We must instrument the Spring Boot microservices to send data (Logs, Metrics, Traces) over OTLP to the OpenTelemetry Collector. We have two primary options:

**Option A: OpenTelemetry Java Agent (Recommended for zero-code changes)**
*   Modify the service Dockerfiles to download the `opentelemetry-javaagent.jar`.
*   Update `JAVA_OPTS` to include `-javaagent:/opentelemetry-javaagent.jar`.
*   Set environment variables in `docker-compose.yml` for each service:
    *   `OTEL_SERVICE_NAME=user-service`
    *   `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318`
    *   `OTEL_METRICS_EXPORTER=otlp`
    *   `OTEL_LOGS_EXPORTER=otlp`

**Option B: Spring Boot 3 Micrometer OTLP (Code/Dependency based)**
*   Add dependencies to shared `pom.xml`:
    *   `micrometer-registry-otlp`
    *   `micrometer-tracing-bridge-otel`
    *   `opentelemetry-exporter-otlp`
*   Configure `application.yml` (via Config Server):
    *   Management endpoints, tracing sampling rate, and OTLP endpoint settings.

*Decision needed*: Let me know if you prefer the **Java Agent** (auto-instrumentation, no code changes) or the **Micrometer OTLP** route (native Spring Boot integration).

---

## Phase 3: Infrastructure Monitoring

The OTel Collector needs to be configured to scrape metrics from our existing databases and brokers.

1.  **PostgreSQL**: Add the `postgresql` receiver to the collector config.
2.  **Redis**: Add the `redis` receiver to the collector config.
3.  **MongoDB**: Add the `mongodb` receiver to the collector config.
4.  **Kafka**: Add JMX integration or utilize Kafka Exporter scraped by the collector.

*Note: Credentials and connection strings for these databases will need to be passed to the OTel Collector.*

---

## Phase 4: Verification and Dashboards

1.  **Verify Flow**:
    *   Start the stack (`docker-compose up -d`).
    *   Perform a sample action (e.g., initiating a KYC or Transfer).
2.  **Explore in Grafana**:
    *   Navigate to Grafana (port 3000).
    *   Verify Logs in the **Loki** explorer (e.g., `{service_name="kyc-service"}`).
    *   Verify Traces in the **Tempo** explorer (search by trace ID found in logs).
    *   Verify Metrics in the **Prometheus** explorer.
3.  **Dashboards**:
    *   Import standard Spring Boot Observability dashboards into Grafana.

---

### ❓ Questions to decide on before we start:
1.  **Instrumentation Method**: Do you prefer the OpenTelemetry Java Agent (no code changes, injected via Dockerfile) or the Spring Boot Micrometer + OTLP dependencies (managed via Maven and application configs)?
2.  **Scale / Resource Usage**: Running all these observability containers will consume significant RAM. Are you running this locally, and if so, is your system equipped with 16GB+ RAM? We can optimize memory limits for local development if needed.

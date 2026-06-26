# Observability & Monitoring Implementation Plan

This plan details the steps required and successfully executed to implement the target observability architecture for the FintechWave microservices platform. Centralized visibility into logs, metrics, and traces is fully established.

## 🏗️ Target Architecture Overview (✅ Fully Implemented)

*   **Instrumentation**: OpenTelemetry (OTel) Java Agent mounted as a volume and run inside each microservice container.
*   **Telemetry Pipeline**: OpenTelemetry Collector (receives OTLP data via gRPC/HTTP, processes, and routes to appropriate backends).
*   **Logs**: Grafana Loki (logs exported from collector via `otlp_http`).
*   **Traces**: Grafana Tempo (traces exported from collector via `otlp_grpc`).
*   **Metrics**: Prometheus (receives metrics from collector via `prometheus_remote_write`).
*   **Visualization**: Grafana (pre-configured with Prometheus, Loki, and Tempo datasources, integrated with Keycloak OAuth).

---

## Phase 1: Infrastructure Setup (Docker Compose) — ✅ COMPLETED

The foundational observability components have been added to the microservices ecosystem.

1.  **Configuration Directories**:
    *   `infra/observability/otel-collector/`
    *   `infra/observability/prometheus/`
    *   `infra/observability/loki/`
    *   `infra/observability/tempo/`
    *   `infra/observability/grafana/`
    *   `infra/observability/alertmanager/`
2.  **OpenTelemetry Collector Config (`otel-collector-config.yaml`)**:
    *   **Receivers**: `otlp` (gRPC at port 4317, HTTP at port 4318) for application telemetry, plus database and broker metrics (`postgresql`, `redis`, `kafka_metrics`).
    *   **Processors**: `batch`, `memory_limiter`, `resource` (sets deployment environment to `local-dev`), and `transform/logs` (severity level mapping).
    *   **Exporters**:
        *   Metrics -> `prometheus_remote_write` (endpoint: `http://prometheus:9090/api/v1/write`).
        *   Logs -> `otlp_http/loki` (endpoint: `http://loki:3100/otlp`).
        *   Traces -> `otlp_grpc` (endpoint: `tempo:4317`).
    *   **Pipelines**: Structured `traces`, `metrics`, and `logs` pipelines linking the respective receivers, processors, and exporters.
3.  **Backend Configurations**:
    *   `prometheus.yml`: Configured to scrape self-metrics and receive remote-write data.
    *   `alerts.yml`: Standardized alerts for SLAs, Kafka DLQs, high failure rates, and virtual thread pinning.
    *   `loki-config.yaml`: Structured local storage and chunk settings using MinIO as the S3-compatible backend.
    *   `tempo-config.yaml`: Pre-configured to receive OTLP traces and store chunks locally.
    *   `alertmanager.yml`: Alerting routing configuration.
4.  **Grafana Provisioning**:
    *   Datasources (Loki, Tempo, Prometheus) are automatically provisioned via `grafana/provisioning/datasources/datasources.yaml`.
5.  **Docker Compose Wiring**:
    *   Services `otel-collector`, `loki`, `tempo`, `prometheus`, `alertmanager`, and `grafana` are running within the `fintechwave` network.
    *   Loki storage bucket initialization is automated via `minio-setup` container.

---

## Phase 2: Application Instrumentation (Spring Boot) — ✅ COMPLETED

Application instrumentation was completed using the **OpenTelemetry Java Agent** (Option A).

*   **Injected Agent**: The OpenTelemetry Java Agent (`opentelemetry-javaagent.jar`) is stored under `infra/observability/agents/` and mounted to each application service container.
*   **Java Options**: Configured via JVM arguments:
    ```bash
    -javaagent:/agents/opentelemetry-javaagent.jar
    ```
*   **Service Environment Variables**:
    *   `OTEL_SERVICE_NAME=<service-name>` (e.g. `transaction-service`)
    *   `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318` (OTLP HTTP Receiver)
    *   `OTEL_METRICS_EXPORTER=otlp`
    *   `OTEL_LOGS_EXPORTER=otlp`

---

## Phase 3: Infrastructure Monitoring — ✅ COMPLETED

The OpenTelemetry Collector scrapes performance metrics from the database and broker clusters at 15-second intervals:

1.  **PostgreSQL**: Collector scrapes database and query performance using the `postgresql` receiver (authenticating with database credentials).
2.  **Redis**: Collector scrapes memory, eviction, and lock usage via the `redis` receiver.
3.  **Kafka**: Collector scrapes partition sizes, consumer lag, and offsets via the `kafka_metrics` receiver.

---

## Phase 4: Verification and Dashboards — ✅ COMPLETED

1.  **Centralized Trace Propagation**:
    *   `BusinessContextMdc` inside `libs/core` standardizes tracing variables (`traceId`, `spanId`, `user_id`, `transaction_id`, `event_type`) inside SLF4J MDC.
    *   `KafkaMdcEnrichmentAspect` propagates MDC headers across Kafka topic boundaries, enabling unified traces across microservice call-graphs.
2.  **Grafana Portal**:
    *   Grafana runs at port `3000` with pre-defined datasources.
    *   Supports single sign-on (SSO) authentication mapped to the **Keycloak** realm (`fintechwave` client client-secret OAuth).
3.  **Validation and Alerting**:
    *   Loki logs can be explored using queries like `{service_name="kyc-service"}` or `{service_name="transaction-service"}`.
    *   Traces can be queried in Tempo by searching for `traceId` captured in the structured logs.
    *   Prometheus monitors JVM stats, garbage collection, and custom business metrics, triggering alerts on critical failures.

# FintechWave — System Resilience Audit & Implementation Plan

> **Date:** 2026-06-30  
> **Scope:** All backend services — `gateway`, `user-service`, `kyc-service`, `ledger-service`,
> `transaction-service`, `fraud-service`, `notification-service`, `reporting-service`  
> **Author:** System Audit (Antigravity AI)

---

## 1. Audit Summary

The system is a well-structured Spring Boot 3.5 / Java 21 microservices platform. Several resilience
patterns are **already in place**, but several critical **gaps** exist, particularly in services that
handle money movement or cross-service coordination without a formal circuit breaker strategy at the
service level.

### Overall Resilience Score

| Layer                    | Pattern                           | Status               |
| ------------------------ | --------------------------------- | -------------------- |
| API Gateway              | Circuit Breaker (Resilience4j)    | ✅ Implemented       |
| API Gateway              | Rate Limiter (Redis)              | ✅ Partial (tx only) |
| API Gateway              | Retry (GET routes)                | ✅ Partial           |
| All Services             | Config-server retry (startup)     | ✅ All services      |
| transaction-service      | `@Retryable` (Stripe + gRPC)      | ✅ Implemented       |
| transaction-service      | Semaphore Bulkhead (Stripe)       | ✅ Implemented       |
| transaction-service      | DLT + compensation handler        | ✅ Skeleton only     |
| transaction/fraud/ledger | Kafka DLT + ExponentialBackOff    | ✅ Implemented       |
| transaction-service      | Outbox Pattern                    | ✅ Implemented       |
| user-service / kyc       | Outbox Pattern                    | ✅ Implemented       |
| fraud-service            | Outbox Pattern                    | ✅ Implemented       |
| ledger-service           | Outbox Pattern                    | ✅ Implemented       |
| All Services             | Idempotency (Redis key check)     | ✅ user-service      |
| Observability            | RED metrics + alerts              | ✅ Prometheus        |
| user/kyc/notification    | Kafka DLT config                  | ❌ Missing           |
| reporting-service        | Kafka DLT config                  | ❌ Missing           |
| All services             | Resilience4j at service level     | ❌ Missing           |
| transaction-service      | DLT compensation (implementation) | ⚠️ Skeleton only     |
| All services             | Idempotency (all consumers)       | ⚠️ Inconsistent      |
| Kafka (Infra)            | Replication factor                | ❌ Single replica    |
| docker-compose           | `restart` policy                  | ❌ All `restart: no` |
| gateway                  | Per-service CB tuning             | ⚠️ All use defaults  |
| Circuit Breaker state    | No metrics exposed                | ❌ Missing           |

---

## 2. What Is Already Working Well

### 2.1 Gateway — Circuit Breakers + Rate Limiting + Retry

**File:** `platform/gateway/src/main/java/com/fintechwave/gateway/GatewayApplication.java`
Every route proxied by the Spring Cloud Gateway has a named Resilience4j circuit breaker:
| Route | Circuit Breaker Name | Rate Limiter | Retry |
|----------------------|-----------------------------|:------------:|:-----:|
| user-service | `iamCircuitBreaker` | ❌ | ❌ |
| kyc-service | ❌ **Missing** | ❌ | ❌ |
| ledger-service (GET) | ❌ | ❌ | ✅ |
| ledger-service (write) | `ledgerCircuitBreaker` | ❌ | ❌ |
| transaction-service | `transactionCircuitBreaker` | ✅ | ❌ |
| fraud-service | `fraudCircuitBreaker` | ❌ | ❌ |
| notification-service | `notificationCircuitBreaker`| ❌ | ❌ |
| reporting-service | `reportingCircuitBreaker` | ❌ | ✅ |
**Issues found:**

- All circuit breakers use `CircuitBreakerConfig.ofDefaults()` — no custom failure rate threshold,
  slow call threshold, or wait duration in open state.
- `TimeLimiterConfig` is set globally to **10 seconds**. This is too long for transaction flows
  where the gRPC ledger call should ideally fail within 3 seconds.
- **KYC service has no circuit breaker at all**. It processes file uploads and calls Minio — a
  critical path for user onboarding.
- The rate limiter (`10 req/s burst 20`) is only applied to `/api/v1/transactions/**`. There is no
  protection on fraud, KYC admin endpoints, or the reporting service.

---

### 2.2 Transaction Service — `@Retryable` + Semaphore Bulkhead

**Files:**

- `StripeGatewayAdapter.java` — `@Retryable(maxAttempts=3, delay=500ms)` on `createCardPaymentIntent`
- `LedgerGrpcClient.java` — `@Retryable(maxAttempts=3, delay=1000ms, multiplier=2)` on gRPC call
- `Stripe` uses a `Semaphore(50)` as a thread-based bulkhead to limit concurrent Stripe API calls
  **Issues found:**
- `initiateInstantPayout` (cash-out path) has **no `@Retryable` annotation**. If a Stripe network
  blip occurs during payout, the transaction fails permanently with no retry.
- The `@Recover` fallback on the Stripe adapter re-throws a `PaymentGatewayException`. This is
  correct, but there is **no DLT write for failed card operations** — the transaction gets stuck.
- `@EnableRetry` is enabled on `TransactionApplication`, but **not** on `FraudApplication` or
  `LedgerApplication`, which don't currently use `@Retryable`. This is consistent but should be
  documented.

---

### 2.3 Kafka — Dead Letter Topic (DLT) + Exponential Backoff

**Files:** `KafkaConsumerConfig.java` in `transaction-service`, `fraud-service`, `ledger-service`
All three services use an identical, well-designed pattern:

```
ExponentialBackOff(1s, x2, maxAttempts=3) → DLT topic (record.topic() + ".DLT")
```

- Manual `IMMEDIATE` acknowledgement prevents premature offset commit.
- `IllegalArgumentException` and `JsonParseException` are marked as **not retryable** (poison-pill
  protection).
- Virtual thread executor (`kafka-vt-`) is used for listeners — good for I/O-bound workloads.
  **Issues found:**
- `user-service`, `kyc-service`, `notification-service`, and `reporting-service` have **no
  `KafkaConsumerConfig`** — they use Spring Boot auto-configuration defaults. This means:
  - No DLT — failed messages are lost or cause infinite retry loops depending on defaults.
  - No custom error handler — poison-pill messages can halt the consumer.
  - No virtual thread executor for Kafka listeners.

---

### 2.4 Outbox Pattern (Transactional Messaging)

**Services:** `user-service`, `kyc-service`, `transaction-service`, `ledger-service`, `fraud-service`
Each of these services has:

- An `OutboxEvent` entity persisted in the same DB transaction as the domain aggregate.
- An `OutboxCleanupJob` (via ShedLock) for cleaning processed events.
- An `OutboxEventRepository`.
  This correctly prevents the **dual-write problem** and guarantees events are eventually published
  even if Kafka is temporarily down.
  **Issues found:**
- `notification-service` and `reporting-service` are **consume-only** — they don't produce events,
  so no outbox is needed. ✅ This is correct.
- The DLT compensation handler in `DltCompensationConsumer.java` contains **skeleton code** with
  `// implementation details` comments. The CASH_IN and TRANSFER_COMPLETED compensations are not
  implemented, leaving a critical business recovery gap.

---

### 2.5 Idempotency (Consumer-side)

**File:** `user-service/UserEventConsumer.java`
Uses Redis `setIfAbsent` with a 7-day TTL as an idempotency check:

```java
redisTemplate.opsForValue().setIfAbsent("processed:user-view:" + eventIdStr, "1", Duration.ofDays(7));
```

**Issues found:**

- This pattern exists **only in user-service**. Other consumers (kyc, reporting, notification,
  fraud view consumers) do NOT implement idempotency guards — they risk processing the same event
  twice in case of Kafka rebalance or redelivery.
- The `FraudServiceImpl` likely has its own idempotency via the `ProcessedEvent` table (found), but
  this is not universally applied.

---

### 2.6 Observability & Alerting

**File:** `infra/observability/prometheus/alerts.yml`
Full OTEL instrumentation via `opentelemetry-javaagent.jar` + Prometheus + Grafana + Loki + Tempo.
Alerts defined:

- `HighErrorRate` (5xx > 5% over 5m) ✅
- `HighP99Latency` (P99 > 2s) ✅
- `KafkaConsumerLagHigh` (lag > 1000) ✅
- `JvmHeapUsageCritical` (heap > 90%) ✅
- `StuckTransactions` ✅
- `HighTransactionFailureRate` (failure rate > 10%) ✅
- `P2PTransferHighLatency` (P95 > 5s) ✅
- `OutboxRelayStalled` ✅
  **Issues found:**
- No Resilience4j circuit breaker state metrics exposed (`resilience4j_circuitbreaker_state`).
- No alert for circuit breaker being in OPEN state.
- `StripeWebhookProcessingFailed` alert is **commented out** (noted with `#TODO`).
- No alert for DLT message accumulation (e.g., `kafka_consumer_group_lag` on `.DLT` topics).

---

### 2.7 Infrastructure Resilience

**File:** `infra/docker-compose.yml`
**All services have `restart: no`** — this is appropriate for local development (compose), but
signals that no auto-restart policy has been defined for production deployment (Kubernetes or Swarm).
Kafka is configured with **replication factor = 1** across all internal topics:

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
```

This means a single Kafka broker failure causes total message loss. Acceptable for dev, critical to
address before production.

---

## 3. Gap Summary — Critical vs. Important

### 🔴 Critical Gaps (Must Fix Before Production)

| #   | Gap                                         | Affected Service                   | Risk                                              |
| --- | ------------------------------------------- | ---------------------------------- | ------------------------------------------------- |
| C1  | No Kafka `DefaultErrorHandler` / DLT        | user, kyc, notification, reporting | Poison-pill events can halt consumers permanently |
| C2  | DLT compensation not implemented            | transaction-service                | Money stuck with no auto-recovery                 |
| C3  | `initiateInstantPayout` has no `@Retryable` | transaction-service                | Cash-out failures not retried                     |
| C4  | No circuit breaker for KYC service route    | gateway                            | KYC outages cascade to gateway                    |
| C5  | Kafka replication factor = 1                | infra (kafka)                      | Single broker = full message loss                 |

### 🟡 Important Gaps (Fix in Next Sprint)

| #   | Gap                                        | Affected Service                         | Risk                                       |
| --- | ------------------------------------------ | ---------------------------------------- | ------------------------------------------ |
| I1  | CB config uses all defaults (no tuning)    | gateway                                  | Slow calls not protected; wrong thresholds |
| I2  | Time limiter = 10s globally                | gateway                                  | Too long for financial transactions        |
| I3  | Idempotency missing in most consumers      | kyc, notification, reporting, fraud-view | Duplicate processing on rebalance          |
| I4  | No CB metrics / alerts                     | gateway                                  | Cannot observe CB state transitions        |
| I5  | Rate limiter only on transactions          | gateway                                  | No protection on KYC upload or fraud admin |
| I6  | `restart: no` on all containers            | docker-compose                           | No automatic recovery on service crash     |
| I7  | Stripe webhook failure alert commented out | observability                            | Silent failures go undetected              |

### 🟢 Nice to Have (Future Improvement)

| #   | Gap                                            | Affected Service | Risk                                          |
| --- | ---------------------------------------------- | ---------------- | --------------------------------------------- |
| N1  | No service-level Resilience4j (per-service CB) | all services     | Gateway CB fires before service can self-heal |
| N2  | No bulkhead on DB connection pool              | all services     | DB thread exhaustion under spike              |
| N3  | No timeout on Kafka producer `send()`          | all services     | Slow Kafka blocks virtual threads             |
| N4  | No chaos engineering / fault injection tests   | all services     | Unknown failure modes                         |

---

## 4. Implementation Plan

## The plan is organized into **3 phases** ordered by business criticality.

### Phase 1 — Critical Fixes 🔴 (1–2 Sprints)

#### Task C1: Add `KafkaConsumerConfig` to Missing Services

**Target:** `user-service`, `kyc-service`, `notification-service`, `reporting-service`
For each service, create `config/KafkaConsumerConfig.java` (same pattern as `transaction-service`):

```java
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class, JsonParseException.class);
        return handler;
    }
    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> cf, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("kafka-vt-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);
        return factory;
    }
}
```

**Required pom additions** (for user/kyc/notification/reporting):

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

---

#### Task C2: Implement DLT Compensation Logic

**Target:** `transaction-service/DltCompensationConsumer.java`
Complete the compensation logic for all three cases:

- **CASH_IN_COMPLETED**: Call Stripe Refunds API → mark transaction as `REFUNDED` in DB.
- **TRANSFER_COMPLETED**: Emit `LEDGER_ROLLBACK_REQUESTED` event → ledger-service reverses the
  reservation.
- **CASH_OUT_COMPLETED**: Log a `SEV-1` alert, write to a `ManualReconciliation` table, notify
  ops team via notification-service.
  This requires implementing:

1. A `ManualReconciliationRepository` for SEV-1 events.
2. A `StripeRefundService` that calls `Refund.create(params)` via the Stripe SDK.
3. A compensation event publisher that emits rollback events on the Kafka bus.

---

#### Task C3: Add `@Retryable` to `initiateInstantPayout`

**Target:** `transaction-service/StripeGatewayAdapter.java`

```java
@Override
@Retryable(
    retryFor = { PaymentGatewayException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public PayoutResult initiateInstantPayout(String stripePaymentMethodId, Money amount) {
    // existing implementation
}
@Recover
public PayoutResult fallbackInstantPayout(PaymentGatewayException e,
        String stripePaymentMethodId, Money amount) {
    log.error("All payout retries failed for amount={}. Requires manual reconciliation.", amount, e);
    // Publish to a PAYOUT_FAILED compensation topic for DLT handler
    throw new PaymentGatewayException("Payout processor unavailable after retries.", e);
}
```

---

#### Task C4: Add Circuit Breaker for KYC Route in Gateway

**Target:** `platform/gateway/GatewayApplication.java`
Update the KYC route:

```java
.route("kyc-service", p -> p
    .path("/api/v1/kyc/**", "/api/v1/admin/kyc/**")
    .filters(f -> f
        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
        .circuitBreaker(config -> config
            .setName("kycCircuitBreaker")
            .setFallbackUri("forward:/fallback/kyc")))
    .uri("http://kyc-service:8082"))
```

## Also add a `/fallback/kyc` endpoint in a `FallbackController`.

#### Task C5: Kafka Replication Factor (Pre-production)

**Target:** `infra/docker-compose.yml` + Kafka topic configuration
For production (outside of docker-compose), set replication factor to ≥ 3 for all application
topics. For the dev compose, annotate clearly:

```yaml
# NOTE: RF=1 is for development only. Production requires RF=3 with min.insync.replicas=2.
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
```

---

### Phase 2 — Important Improvements 🟡 (2–3 Sprints)

#### Task I1+I2: Tune Circuit Breaker Configs Per Service

**Target:** `platform/gateway/GatewayApplication.java`
Replace the single `defaultCircuitBreakerCustomizer` with per-service configurations:

```java
@Bean
public Customizer<ReactiveResilience4JCircuitBreakerFactory> transactionCbCustomizer() {
    return factory -> factory.configure(builder -> builder
        .circuitBreakerConfig(CircuitBreakerConfig.custom()
            .failureRateThreshold(50)          // Open after 50% failures
            .slowCallRateThreshold(80)          // Also open on slow calls
            .slowCallDurationThreshold(Duration.ofSeconds(3)) // 3s = slow for tx
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build())
        .timeLimiterConfig(TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(4)) // Tight for financial ops
            .build())
        .build(), "transactionCircuitBreaker", "ledgerCircuitBreaker");
}
@Bean
public Customizer<ReactiveResilience4JCircuitBreakerFactory> reportingCbCustomizer() {
    return factory -> factory.configure(builder -> builder
        .circuitBreakerConfig(CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .slidingWindowSize(20)
            .build())
        .timeLimiterConfig(TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(15)) // Reports can be slow
            .build())
        .build(), "reportingCircuitBreaker");
}
```

---

#### Task I3: Idempotency Guard — All Kafka Consumers

**Target:** All Kafka consumer classes in `kyc-service`, `notification-service`, `reporting-service`
Extract the Redis idempotency pattern from `UserEventConsumer` into a shared utility (in `libs/core`):

```java
// libs/core → com.fintechwave.core.messaging.IdempotencyGuard
@Component
public class IdempotencyGuard {
    private final StringRedisTemplate redis;
    public boolean isAlreadyProcessed(String namespace, String eventId) {
        Boolean isNew = redis.opsForValue()
            .setIfAbsent("processed:" + namespace + ":" + eventId, "1", Duration.ofDays(7));
        return Boolean.FALSE.equals(isNew);
    }
}
```

Apply to every `@KafkaListener` consumer:

```java
if (idempotencyGuard.isAlreadyProcessed("kyc-view", eventId)) {
    ack.acknowledge();
    return;
}
```

---

#### Task I4: Circuit Breaker Metrics & Alerts

**Target:** `gateway` + `infra/observability/prometheus/alerts.yml`

1. Add the Resilience4j actuator metrics endpoint to the gateway's `application.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

2. Add Prometheus alerts:

```yaml
- alert: CircuitBreakerOpen
  expr: resilience4j_circuitbreaker_state{state="open"} == 1
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Circuit breaker {{ $labels.name }} is OPEN"
    description: "Downstream service is failing. Fallback responses are being served."
- alert: DLTMessagesAccumulating
  expr: kafka_consumer_group_lag{topic=~".*\\.DLT"} > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "DLT messages accumulating for topic {{ $labels.topic }}"
    description: "{{ $value }} unprocessed dead-letter messages. Manual investigation required."
```

---

#### Task I5: Rate Limiting on KYC and Admin Routes

**Target:** `platform/gateway/GatewayApplication.java`
Add rate limiters to KYC upload and fraud admin paths:

```java
@Bean
public RedisRateLimiter kycRateLimiter() {
    return new RedisRateLimiter(5, 10, 1); // 5 req/s, burst 10
}
// In KYC route:
.requestRateLimiter(config -> config
    .setRateLimiter(kycRateLimiter())
    .setKeyResolver(userKeyResolver()))
```

---

#### Task I6: Restart Policy for Production

**Target:** `infra/docker-compose.yml`
For production-like environments, change `restart: no` to `restart: unless-stopped` for all
application services (not databases/infra which need controlled restart):

```yaml
# Application services only
user-service:
  restart: unless-stopped
transaction-service:
  restart: unless-stopped
```

**Note:** For Kubernetes deployments, this is handled by `restartPolicy` in Deployment specs
and is the preferred path for production.

---

#### Task I7: Enable Stripe Webhook Failure Alert

**Target:** `infra/observability/prometheus/alerts.yml`
Uncomment and fix the `StripeWebhookProcessingFailed` alert. The metric
`fintechwave_stripe_webhook_received_total{outcome="failed"}` must be emitted from
`transaction-service` when webhook processing throws an exception.

---

### Phase 3 — Advanced Resilience 🟢 (Future)

#### Task N1: Service-Level Resilience4j (Bulkhead + Time Limiter)

Add `spring-boot-starter-aop` + `resilience4j-spring-boot3` to individual services.
Use `@CircuitBreaker` on service calls that go to external dependencies (DB, Redis, Minio):

```java
@CircuitBreaker(name = "minioStorage", fallbackMethod = "fallbackStore")
public String uploadDocument(byte[] content, String filename) { ... }
```

---

#### Task N2: Database Connection Pool Bulkhead

Add HikariCP connection pool limits via config:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 3000 # 3s max wait for a connection
      idle-timeout: 600000
      max-lifetime: 1800000
```

## Emit a `DataSourceHealthIndicator` metric and alert on connection pool saturation.

#### Task N3: Kafka Producer Timeout

In each service's Kafka producer config, add:

```yaml
spring:
  kafka:
    producer:
      properties:
        delivery.timeout.ms: 10000 # 10s total delivery timeout
        request.timeout.ms: 5000
        retry.backoff.ms: 500
```

---

#### Task N4: Chaos Engineering

- Introduce [Chaos Monkey for Spring Boot](https://github.com/codecentric/chaos-monkey-spring-boot)
  in test profiles.
- Simulate Kafka lag, latency spikes, and DB connection loss.
- Document baseline SLOs (Service Level Objectives) per service.

---

## 5. Priority Execution Order

```
Phase 1 (Weeks 1-2)  →  C1 → C3 → C4 → C2 → C5(annotation)
Phase 2 (Weeks 3-6)  →  I1+I2 → I3 → I4 → I5 → I6 → I7
Phase 3 (Weeks 7+)   →  N1 → N2 → N3 → N4
```

```
C1 (Kafka DLT for 4 services)
    ↓
C3 (Payout retry)          C4 (KYC circuit breaker)
    ↓                          ↓
C2 (DLT compensation)      I5 (KYC rate limiter)
    ↓
I3 (Idempotency guards)
    ↓
I1+I2 (CB tuning)
    ↓
I4 (CB metrics + alerts)
```

---

## 6. Files to Create / Modify

### Create

| File                                                                                     | Purpose                    |
| ---------------------------------------------------------------------------------------- | -------------------------- |
| `services/user-service/src/main/java/.../config/KafkaConsumerConfig.java`                | DLT + virtual threads      |
| `services/kyc-service/src/main/java/.../config/KafkaConsumerConfig.java`                 | DLT + virtual threads      |
| `services/notification-service/src/main/java/.../config/KafkaConsumerConfig.java`        | DLT + virtual threads      |
| `services/reporting-service/src/main/java/.../config/KafkaConsumerConfig.java`           | DLT + virtual threads      |
| `libs/core/src/main/java/.../messaging/IdempotencyGuard.java`                            | Shared idempotency utility |
| `services/transaction-service/src/main/java/.../service/StripeRefundService.java`        | Stripe refund for DLT      |
| `services/transaction-service/src/main/java/.../domain/entity/ManualReconciliation.java` | SEV-1 events               |
| `platform/gateway/src/main/java/.../controller/FallbackController.java`                  | Fallback endpoints         |

### Modify

| File                                                                      | Change                                                        |
| ------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `platform/gateway/GatewayApplication.java`                                | Add KYC CB, tune per-service CB configs, add KYC rate limiter |
| `services/transaction-service/.../adapter/StripeGatewayAdapter.java`      | Add `@Retryable` to payout                                    |
| `services/transaction-service/.../messaging/DltCompensationConsumer.java` | Implement compensations                                       |
| `infra/observability/prometheus/alerts.yml`                               | Add CB state alert, DLT alert, enable Stripe alert            |
| `infra/docker-compose.yml`                                                | Add restart policy for app services, add kafka RF annotation  |

---

_End of Resilience Audit — FintechWave Platform_

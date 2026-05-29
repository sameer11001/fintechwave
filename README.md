# FintechWave — Engineering Reference

> **Classification:** Internal Engineering Documentation
> **Maintained by:** Platform Engineering
> **Status:** Active Development

---

## Table of Contents

1. [Overview](#1-overview)
2. [System Architecture](#2-system-architecture)
3. [Bounded Contexts & Roadmap](#3-bounded-contexts--roadmap)
4. [Database Design Strategy](#4-database-design-strategy)
5. [Event-Driven Architecture](#5-event-driven-architecture)
6. [Security Model](#6-security-model)
7. [Scalability & Infrastructure](#7-scalability--infrastructure)
8. [Observability](#8-observability)
9. [Testing Strategy](#9-testing-strategy)
10. [Build & Local Development](#10-build--local-development)
11. [Engineering Standards](#11-engineering-standards)

---

## 1. Overview

**FintechWave** is a production-grade, distributed e-wallet platform engineered for high throughput, strict financial consistency, and regulatory compliance. The system is designed around six Domain-Driven Design (DDD) bounded contexts, each implemented as an independent microservice with its own isolated database, communication contract, and deployment lifecycle.

### Business Model

| Revenue Stream | Mechanism |
|---|---|
| Merchant Discount Rate (MDR) | Fee recorded per merchant-side transaction commit |
| Transfer & Withdrawal Fees | Flat or percentage-based fee ledger entries |
| Cross-Border Remittance | Spread captured on FX conversion at payout |
| VAS Commissions | Commission entries on third-party service settlement |
| Float Interest | Interest accrued on pooled user balances (treasury) |

### Core Non-Negotiables

Every bug is a potential money loss. Every race condition is financial corruption. Every missed event is state divergence. These are not abstract concerns — they are hard system invariants that every line of code must respect:

- **PostgreSQL is the sole source of truth.** Redis, Kafka, and in-memory state are never authoritative.
- **The Outbox pattern is non-negotiable.** Domain state mutation and event publication must share one ACID transaction boundary. No exceptions.
- **All mutations are idempotent.** Retry storms and duplicate Kafka deliveries must produce no side effects.
- **No money is ever destroyed.** Double-entry bookkeeping ensures every debit has a corresponding credit. Records are never deleted; errors are corrected through reversing journal entries.

---

## 2. System Architecture

### Module Layout

```
fintechwave/
├── platform/
│   └── gateway/                  # Spring Cloud Gateway — single ingress point
├── services/
│   ├── iam-service/              # Identity, Authentication, Authorization
│   └── transaction-service/      # Transaction orchestration, ledger commits
├── libs/
│   ├── common/                   # BOM — version governance for the ecosystem
│   ├── core/                     # Shared ApiResponse, exceptions, utilities
│   ├── security/                 # Custom security auto-configuration starter
│   └── events/                   # Kafka event schemas and Outbox contracts
├── docker/                       # Docker Compose for local development
├── infra/                        # Kubernetes manifests and Helm charts
└── scripts/                      # CI/CD and operational tooling
```

### Shared Libraries

| Module | Role |
|---|---|
| `libs/common` | BOM artifact. Centralizes all dependency versions. Every service imports this as a BOM import — no version is declared outside it. |
| `libs/core` | `ApiResponse<T>` envelope, `GlobalExceptionHandler`, `BaseServiceException` hierarchy. Eliminates response-structure drift across services. |
| `libs/security` | Spring Boot auto-configuration starter for JWT filter, `SecurityFilterChain`, and zero-trust defaults. Services inherit security policy without duplication. |
| `libs/events` | Versioned Kafka event POJOs with schema contracts (eventId, eventType, version, occurredAt). All producers and consumers share this artifact. |

### Dependency Flow Rule

```
Controller → Service Interface → Repository
                  ↓
             Domain (Entity, Enum)
                  ↓
             DTO (Request / Response)
```

No layer may import from a layer above it. Controllers never touch repositories. Entities never appear in controller signatures.

---

## 3. Bounded Contexts & Roadmap

### 3.1 Identity & Access Management (IAM) — `iam-service`
**Current Status:** Active

Owns the user identity lifecycle: registration, credential management, JWT issuance, token refresh rotation, and role-based access control.

**Key Responsibilities:**
- Stateless JWT authentication (access + refresh token pair)
- BCrypt password hashing with configurable cost factor
- Refresh token rotation on every use — previous token immediately invalidated
- `ROLE_USER`, `ROLE_ADMIN`, `ROLE_MERCHANT` RBAC model

**Roadmap:**
- OAuth 2.0 / OIDC integration (Authorization Server via Spring Authorization Server)
- Device fingerprinting and session anomaly detection
- Passkey / WebAuthn support for passwordless authentication
- Privileged access management (PAM) for internal operator roles

---

### 3.2 Onboarding & KYC — _(Planned)_
**Current Status:** Roadmap — Phase 2

Manages the user lifecycle from initial sign-up through regulatory verification. This context owns all PII data for the onboarding flow and is the only service permitted to store raw identity documents.

**Key Responsibilities:**
- Tiered KYC levels (Tier 1: phone/email, Tier 2: national ID, Tier 3: facial biometric)
- Document ingestion and secure storage (encrypted at rest, AES-256)
- Integration with third-party KYC providers (e.g., Smile Identity, Jumio)
- Regulatory hold management (emits `KYCVerified` / `KYCRejected` events)

**Critical Event:** `KYCVerified` — consumed by the Ledger service to provision the user wallet. Without this event, no financial account is created. This is an intentional compliance gate.

**Roadmap:**
- AML (Anti-Money Laundering) screening at registration and on periodic schedule
- Automated re-verification triggers for high-risk profile changes
- Regulatory reporting hooks for FATF compliance

---

### 3.3 Core Ledger & Wallet — _(Planned)_
**Current Status:** Roadmap — Phase 2 (Critical Path)

The financial heart of the platform. This service implements double-entry bookkeeping and is the only authority on account balances. It is engineered for strict ACID compliance and auditability above all else.

**Database Schema (Double-Entry):**

```sql
-- Chart of accounts: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
CREATE TABLE ledger_account (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID NOT NULL,          -- FK to user or system account
    account_type  VARCHAR(20) NOT NULL,   -- ASSET | LIABILITY | EQUITY
    currency      CHAR(3) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Immutable journal. Never deleted. Errors corrected via reversing entries.
CREATE TABLE ledger_entry (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID NOT NULL,       -- groups the debit+credit pair
    account_id       UUID NOT NULL REFERENCES ledger_account(id),
    entry_type       CHAR(6) NOT NULL,    -- DEBIT | CREDIT
    amount           NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency         CHAR(3) NOT NULL,
    idempotency_key  UUID NOT NULL UNIQUE,
    description      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Materialized running balance — updated within the same transaction as ledger_entry
CREATE TABLE balance (
    account_id    UUID PRIMARY KEY REFERENCES ledger_account(id),
    amount        NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    currency      CHAR(3) NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_ledger_entry_transaction_id ON ledger_entry(transaction_id);
CREATE INDEX idx_ledger_entry_account_id     ON ledger_entry(account_id);
CREATE INDEX idx_ledger_account_owner_id     ON ledger_account(owner_id);
```

**Invariants:**
- `SUM(DEBIT) = SUM(CREDIT)` for every `transaction_id` — enforced at commit time
- Balance can never go negative: `CHECK (amount >= 0)` enforced in DB, not only in application code
- Pessimistic locking (`SELECT FOR UPDATE`) on `balance` rows during any write operation to prevent concurrent overdraft

**Roadmap:**
- Multi-currency balance support with FX rate recording at entry time
- Float interest calculation engine (daily accrual, monthly settlement)
- Reconciliation job for balance-to-entry divergence detection

---

### 3.4 Transaction & Money Movement — `transaction-service`
**Current Status:** Active (core scaffolding)

Orchestrates all financial operations: P2P transfers, cash-in (deposit), cash-out (withdrawal), and fee calculation. This service owns transaction intent and state machine transitions, but delegates all ledger mutations to the Ledger service.

**Transaction State Machine:**
```
INITIATED → FRAUD_CHECK → RESERVED → COMMITTED → COMPLETED
                 ↓                         ↓
            FLAGGED                    FAILED → REVERSED
```

**Key Responsibilities:**
- Fee matrix computation (MDR, flat fees, FX spread)
- Payment gateway integration (Stripe, local payment networks)
- Outbox-based event emission (`TransferInitiated`, `FundsReserved`, `TransferCompleted`)

**Roadmap:**
- Scheduled recurring transfers (direct debit)
- Cross-border remittance corridor management
- Refund and chargeback orchestration
- Merchant settlement batch processing

---

### 3.5 Fraud & Risk Management — _(Planned)_
**Current Status:** Roadmap — Phase 3

A reactive and proactive risk evaluation engine. It subscribes to transaction events, scores them in near-real-time, and emits decisions that the Transaction service uses to allow or block operations.

**Key Responsibilities:**
- Velocity checks (transaction count and volume per time window, via Redis)
- Geolocation anomaly detection (impossible travel detection)
- Device fingerprint and behavioral scoring
- Rule engine (configurable fraud rules stored in DB, hot-reloaded)

**Critical Event Flow:**
`TransferInitiated` → FraudService evaluates → emits `TransactionApproved` or `TransactionFlagged`

**Roadmap:**
- ML-based transaction scoring model integration
- Account freeze workflow (`AccountFrozen` event with cross-service enforcement)
- Regulatory STR (Suspicious Transaction Report) automated filing hooks

---

### 3.6 Notifications & Engagement — _(Planned)_
**Current Status:** Roadmap — Phase 3

A stateless notification delivery service. It subscribes to domain events from all other contexts and delivers user-facing notifications through push, SMS, and email channels.

**Key Responsibilities:**
- Multi-channel delivery: push (FCM/APNs), SMS (Twilio), Email (SendGrid)
- Idempotent delivery: notification log with `idempotency_key` prevents duplicate sends
- User preference management (opt-in/out per channel per event type)
- Delivery status tracking and retry management

**Roadmap:**
- In-app notification feed (WebSocket / SSE)
- Templating engine for localized, dynamic notification content
- Marketing engagement campaigns (separated from transactional notifications by design)

---

## 4. Database Design Strategy

### Isolation Model

Each bounded context owns a dedicated database. No service may issue a query against another service's database. Cross-context data access is exclusively through published domain events or REST API calls.

| Context | Database | Isolation Rationale |
|---|---|---|
| IAM | `iam_db` | Credential data must never co-reside with financial data |
| KYC | `kyc_db` | PII document store requires dedicated encryption and access policies |
| Ledger | `ledger_db` | Financial records require dedicated IOPS, backup, and retention policy |
| Transaction | `transaction_db` | High-write workload — sharding strategy applied independently |
| Fraud | `fraud_db` | Rule store and scoring history have independent retention and purge needs |
| Notifications | `notification_db` | Delivery log has high-volume, time-bound retention (90-day rolling) |

### Retention & Archival Policy

| Context | Hot Storage | Archive Trigger | Archive Target |
|---|---|---|---|
| Ledger Entries | Indefinite (immutable by law) | Never purged | Append-only replicas |
| Transactions | 3 years | Older than 3 years | AWS S3 Parquet |
| KYC Documents | Duration of customer relationship + 7 years | Regulatory window | Encrypted cold storage |
| Notification Logs | 90 days rolling | TTL-based partition drop | None required |
| Fraud Scoring | 2 years | Age-based archival | S3 |

---

## 5. Event-Driven Architecture

### Message Broker: Apache Kafka

Kafka is selected over RabbitMQ for the following reasons specific to FintechWave's requirements:

| Requirement | Kafka | RabbitMQ |
|---|---|---|
| Event ordering per wallet | ✅ Partition-key ordering | ❌ Queue-level only |
| Long-term event log retention | ✅ Configurable retention | ❌ Messages are consumed and dropped |
| Replay for Ledger audit | ✅ Offset-based replay | ❌ Not supported |
| Throughput at 10K+ TPS | ✅ Designed for this | ⚠️ Requires significant tuning |

### Topic & Partition Strategy

```
Topic                   Partition Key         Retention
──────────────────────  ────────────────────  ──────────
iam.user-events         user_id               30 days
kyc.verification-events user_id               90 days
ledger.wallet-events    wallet_id             1 year
tx.transaction-events   transaction_id        1 year
fraud.risk-events       transaction_id        1 year
notification.events     user_id               7 days
```

Partitioning by `wallet_id` and `transaction_id` guarantees that all events for a single financial entity are processed in strict order within a single partition.

### Domain Event Schema Contract

All events published to Kafka must conform to this envelope. This is enforced by the `libs/events` shared module:

```json
{
  "eventId":       "uuid-v4",
  "eventType":     "TRANSFER_INITIATED",
  "eventVersion":  1,
  "aggregateId":   "uuid-of-the-aggregate",
  "aggregateType": "TRANSACTION",
  "occurredAt":    "2025-01-15T10:30:00Z",
  "idempotencyKey":"uuid-v4",
  "payload":       { }
}
```

### Critical Workflow: P2P Transfer

```
User → Gateway → TransactionService
                      │
                      ├─ Validate limits & balance (sync DB read)
                      ├─ Persist TransactionRecord (status=INITIATED)
                      ├─ Write OutboxEvent (TransferInitiated)
                      └─ COMMIT ─────────────────────────────────────────────┐
                                                                              │
              Outbox Relay polls and publishes to Kafka                       │
                      │                                                       │
                      ▼                                                       │
              FraudService consumes TransferInitiated                         │
                      │                                                       │
              ┌───────┴────────┐                                              │
              ▼                ▼                                              │
     Emits TransactionApproved  Emits TransactionFlagged                      │
              │                        │                                      │
              ▼                        ▼                                      │
     LedgerService commits      TransactionService → status=FLAGGED           │
     double-entry entries        NotificationService → alert user             │
              │
     Emits LedgerEntryCommitted
              │
     TransactionService → status=COMPLETED
              │
     NotificationService → confirmation to both parties
```

### Idempotency at Consumer Level

Every Kafka consumer persists the `idempotencyKey` from the event envelope into a `processed_events` table within the same transaction as its state mutation. Duplicate events are detected with a unique index and silently discarded.

### Dead Letter Queue Strategy

Failed messages (after 3 retry attempts with exponential backoff) are routed to a DLQ topic (`*.dlq` convention). An operator dashboard provides tooling for inspecting, replaying, or discarding poisoned messages. No message is silently lost.

---

## 6. Security Model

### Zero-Trust Principles

- **No service trusts another by default.** All inter-service calls are authenticated.
- **No session state.** JWTs are stateless; revocation is handled via a Redis-backed short-TTL blocklist.
- **No PII is ever logged.** Emails, phone numbers, national IDs, and JWT claims are masked at the logging layer.
- **No secrets in code or version control.** All credentials are injected via environment variables or Kubernetes Secrets.

### Authentication & Authorization

| Layer | Mechanism |
|---|---|
| External clients → Gateway | JWT Bearer token (RS256 signed) |
| Gateway → Services | mTLS (enforced via Istio Service Mesh) |
| Service → Service (sync) | Feign client with propagated JWT context |
| RBAC | `@PreAuthorize` annotations on controller methods |

### Data Protection

| Concern | Control |
|---|---|
| Data in transit | TLS 1.3 for all external communication; mTLS between services |
| Data at rest (PII) | AES-256 column-level encryption for identity fields |
| Card / payment data | Tokenization only — raw card data never stored (PCI-DSS) |
| Secrets management | Kubernetes Secrets + HashiCorp Vault (production) |

### API Security Controls

- **Rate Limiting:** Token-bucket algorithm enforced at the Gateway per `user_id` and `IP`
- **Request Signing:** High-value mutation endpoints (transfers, withdrawals) require HMAC request signatures
- **Webhook Validation:** Stripe callbacks validated via `Stripe-Signature` header before any processing
- **Audit Trail:** All financial operations produce an immutable audit log entry. Audit records are write-only.

---

## 7. Scalability & Infrastructure

### Service Scalability

All Spring Boot microservices are stateless. Horizontal scaling is achieved by increasing pod replicas behind a Kubernetes Service. No in-memory state is permitted.

### Database Scaling Plan

| Context | Strategy |
|---|---|
| IAM | Primary + 2 read replicas (via PgBouncer) |
| Ledger | Primary + read replicas + hash-range sharding by `wallet_id` for 10K+ TPS |
| Transaction | Partitioned tables by `created_at` month; archived partitions detached quarterly |
| Fraud | Read replicas + Redis for velocity-check counters (short TTL) |

### Caching Strategy (Redis)

| Cache | TTL | Invalidation |
|---|---|---|
| JWT blocklist | Matches token expiry | Explicit on logout / compromise |
| User balance (read cache) | 500ms | Event-driven on `LedgerEntryCommitted` |
| Fraud velocity counters | Sliding window (60s, 1h, 24h) | TTL-based expiry |
| KYC status | 5 minutes | Event-driven on `KYCVerified` / `KYCRejected` |

> **Critical:** Redis is never authoritative. A Redis cache miss must always fall back to PostgreSQL. A Redis eviction must never produce a user-visible balance discrepancy.

### Auto-Scaling Triggers (KEDA)

- **CPU > 70%** → scale up transaction-service pods
- **Kafka consumer lag > 1000 messages** → scale up the lagging consumer group
- **Request rate > 5000 req/s** → scale up gateway replicas

### Resilience Patterns

| Pattern | Implementation | Applied To |
|---|---|---|
| Circuit Breaker | Resilience4j | All Feign inter-service clients |
| Bulkhead | Separate HikariCP pools | Ledger DB isolated from other service DB pools |
| Retry with Backoff | Resilience4j | External gateway calls (Stripe, KYC providers) |
| Timeout | Feign + Resilience4j | All synchronous calls — max 3s enforced |

---

## 8. Observability

### Three Pillars

**Metrics (Prometheus + Grafana):**
- Business: TPS, MDR collected per minute, P2P transfer volume, failed transaction rate
- Technical: JVM heap, GC pause, HikariCP pool utilization, Kafka consumer lag
- Alerting: PagerDuty escalation on > 1% failed transactions, > 5s ledger commit latency, > 10,000 Kafka DLQ messages

**Distributed Tracing (OpenTelemetry):**
- `trace_id` injected at the Gateway on every inbound request
- Propagated through all synchronous Feign calls via HTTP headers
- Propagated through Kafka events via message headers
- Stored in Grafana Tempo or Jaeger

**Log Aggregation (Grafana Loki / ELK):**
- Structured JSON logging via Logback
- MDC context: `requestId`, `userId`, `tenantId`, `traceId` attached to every log line
- Log levels: `ERROR` for unexpected failures, `WARN` for business-rule violations, `INFO` for state changes, `DEBUG` off in production

### Health Checks

Every service exposes Spring Boot Actuator endpoints consumed by Kubernetes:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```

---

## 9. Testing Strategy

### Pyramid Targets

| Layer | Coverage Target | Framework |
|---|---|---|
| Unit (service logic) | ≥ 80% branch | JUnit 5, Mockito |
| Integration (web layer) | All endpoints | `@WebMvcTest`, MockMvc |
| Repository (real DB) | All custom queries | `@DataJpaTest` + Testcontainers |
| Full integration | Critical flows | `@SpringBootTest` + Testcontainers |
| Contract | All Feign clients | Pact |
| Load | Ledger TPS target | Gatling |
| Chaos | Resilience patterns | Gremlin / Chaos Monkey |

### Hard Rules

- **No H2.** All database tests run against a real PostgreSQL instance via Testcontainers. H2 behavioral differences have caused production ledger bugs.
- **No shared mutable state between tests.** Each test manages its own data lifecycle.
- **No order-dependent tests.** Test suites must pass in any execution order.
- **Ledger correctness is mandatory coverage.** Double-entry balance integrity, concurrent transfer safety, and idempotency under retry are required test scenarios — not optional.

### Test Naming Convention

```
methodName_StateUnderTest_ExpectedBehavior()

// Examples:
commitTransfer_WhenInsufficientBalance_ThrowsInsufficientFundsException()
commitTransfer_WhenIdempotencyKeyDuplicated_ReturnsOriginalResult()
commitTransfer_WhenConcurrentUpdate_RetrySucceedsWithoutDuplication()
```

---

## 10. Build & Local Development

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21 (Eclipse Temurin) |
| Maven | 3.9+ |
| Docker | 24+ |
| Docker Compose | v2 |

### Build

```bash
# Full build — compile, test, install all modules to local Maven repository
mvn clean install

# Skip tests for fast iteration (not for CI)
mvn clean install -DskipTests
```

### Containerization (Jib)

The project uses **Google Jib** — no Dockerfiles required. Jib builds optimized, layered, distroless images without a Docker daemon during CI.

```bash
# Build and load directly into local Docker daemon
mvn compile jib:dockerBuild

# Build and push to remote registry (CI/CD usage)
mvn compile jib:build -Dimage.tag=1.2.0
```

### Local Development Stack

```bash
# Start the full local stack:
# PostgreSQL, Kafka, Zookeeper, Redis, all microservices, Gateway
cd docker
docker compose up -d

# Tail logs from all services
docker compose logs -f

# Stop and clean volumes
docker compose down -v
```

### Environment Variables

All service configuration is externalized. A `.env.example` file in the `docker/` directory documents all required variables. **Never commit a `.env` file with real credentials.**

Key variables:

| Variable | Description |
|---|---|
| `JWT_SECRET` | RS256 private key (min 256-bit for HS256, full key for RS256) |
| `DB_URL` | JDBC URL for the service's dedicated database |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection |

---

## 11. Engineering Standards

This codebase enforces the following non-negotiable standards. These are codified in `.agents/skills/code-standards.md` and `.agents/skills/.md` and are the authoritative references for all contributors and AI agents.

### Code Review Checklist (Mandatory before merge)

- [ ] No entity exposed in a controller method signature
- [ ] No repository called directly from a controller
- [ ] All write service methods annotated with `@Transactional`
- [ ] All API responses wrapped in `ApiResponse<T>` envelope
- [ ] No `@Autowired` field injection — constructor injection only via `@RequiredArgsConstructor`
- [ ] No hardcoded credentials, URLs, or secrets
- [ ] All relationships use `FetchType.LAZY` — `JOIN FETCH` only where explicitly needed
- [ ] No `Optional.get()` without `.orElseThrow()`
- [ ] No swallowed exceptions (empty catch blocks)
- [ ] No PII or JWT token logged at any log level
- [ ] Logging uses structured parameters, not string concatenation
- [ ] Unit test exists for every public service method
- [ ] All custom exceptions extend `BaseServiceException` with a machine-readable `errorCode`

### Key Forbidden Patterns

| Pattern | Reason |
|---|---|
| Kafka publish inside a service method (without Outbox) | DB commits without event = permanent inconsistency (SEV-1) |
| Redis used as balance source of truth | Redis eviction = silent financial corruption |
| Non-idempotent Kafka consumers | Duplicate events = duplicate financial execution |
| `Optional.get()` without guard | Silent NPE in production |
| H2 in financial integration tests | Behavioral differences cause undetected ledger bugs |
| Generic base services with reflection mapping | Hidden coupling, cognitive overhead, DRY trap |

---

*This document is a living reference. Engineers are expected to keep it synchronized with architectural decisions. Proposals for changes to core patterns must go through an RFC process before implementation.*

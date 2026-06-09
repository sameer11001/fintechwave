# FintechWave — E-Money Platform

FintechWave is a production-grade, event-driven **e-money platform** built on a Java/Spring Boot microservices architecture. It enables custodial e-wallet management, tiered KYC onboarding, double-entry bookkeeping, and Stripe-powered payment flows within a secure, containerised ecosystem.

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [Repository Layout](#2-repository-layout)
3. [Platform Layer](#3-platform-layer)
4. [Shared Libraries](#4-shared-libraries)
5. [Service Catalogue](#5-service-catalogue)
   - [IAM / User Service](#51-iam--user-service-port-8081)
   - [KYC Service](#52-kyc-service-port-8082)
   - [Ledger Service](#53-ledger-service-port-8083)
   - [Transaction Service](#54-transaction-service-port-8084)
   - [Fraud Service](#55-fraud-service-port-8085)
   - [Notification Service](#56-notification-service-port-8086)
   - [Reporting Service](#57-reporting-service-port-8087)
6. [Security Architecture](#6-security-architecture)
7. [Event-Driven Messaging — Transactional Outbox](#7-event-driven-messaging--transactional-outbox)
8. [Financial Engine — Double-Entry Bookkeeping](#8-financial-engine--double-entry-bookkeeping)
9. [Stripe Payment Integration](#9-stripe-payment-integration)
10. [KYC Lifecycle State Machine](#10-kyc-lifecycle-state-machine)
11. [User Registration Flow (End-to-End)](#11-user-registration-flow-end-to-end)
12. [Infrastructure & Data Stores](#12-infrastructure--data-stores)
13. [Technology Stack](#13-technology-stack)
14. [Port Reference](#14-port-reference)

---

## 1. High-Level Architecture

```
Client Applications
        │  Bearer JWT (Keycloak)
        ▼
┌───────────────────┐
│   API Gateway     │  Spring Cloud Gateway — JWT validation, routing
│   (port 8080)     │
└────────┬──────────┘
         │  Internal HTTP (no re-auth required)
    ┌────┴──────────────────────────────────────┐
    │                                           │
    ▼                                           ▼
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────────┐   ┌──────────┐   ┌──────────────┐   ┌──────────┐
│  IAM /   │   │  KYC     │   │  Ledger  │   │  Transaction     │   │  Fraud   │   │ Notification │   │ Reporting│
│  User    │   │  Service │   │  Service │   │  Service         │   │  Service │   │  Service     │   │  Service │
│  :8081   │   │  :8082   │   │  :8083   │   │  :8084           │   │  :8085   │   │  :8086       │   │  :8087   │
└────┬─────┘   └────┬─────┘   └────┬─────┘   └────────┬─────────┘   └────┬─────┘   └──────┬───────┘   └────┬─────┘
     │              │              │                   │                 │                │                │
     └──────────────┴──────────────┴───────────────────┴─────────────────┴────────────────┴────────────────┘
                             │  Apache Kafka (KRaft mode)
                    Domain Events (Outbox Pattern)
                             │
                  ┌──────────┴──────────┐
                  │                     │
              PostgreSQL            Keycloak
          (one DB per service)    (Identity Provider)
                                       │
                                   MinIO (S3)
                                 (KYC Documents)
```

All inter-service communication is **asynchronous via Kafka**. Services never call each other over HTTP at runtime. Consistency is achieved through the **Transactional Outbox Pattern**.

---

## 2. Repository Layout

```
fintechwave/
├── libs/                      # Shared Java libraries (Maven artifacts)
│   ├── common/                # Bill-of-materials POM (dependency versions)
│   ├── core/                  # ApiResponse wrapper, BaseServiceException
│   ├── security/              # Keycloak JWT converter, AudienceValidator
│   ├── events/                # DomainEvent interface, BaseEvent, publisher
│   └── payment-gateway/       # PaymentGatewayPort interface, Money, DTOs
│
├── platform/                  # Infrastructure services
│   ├── config-server/         # Spring Cloud Config Server (Git-backed)
│   └── gateway/               # Spring Cloud Gateway (routing + JWT gate)
│
├── services/                  # Business domain microservices
│   ├── user-service/           # User profiles + Keycloak webhook receiver
│   ├── kyc-service/           # KYC onboarding, document management
│   ├── ledger-service/        # Double-entry bookkeeping, wallet balances
│   ├── transaction-service/   # P2P, Cash-in, Cash-out, Stripe integration
│   ├── fraud-service/         # Risk engine, velocity checks, rules
│   ├── notification-service/  # Multi-channel alerts (Email, SMS, Push)
│   └── reporting-service/     # CQRS read models, dashboards
│
├── docker/
│   ├── docker-compose.yml     # Full local dev stack
│   ├── keycloak/              # Keycloak realm export & webhook provider JAR
│   └── postgres-init/         # Database initialisation scripts
│
└── pom.xml                    # Root Maven aggregator (Java 21, Spring Boot 3.5)
```

---

## 3. Platform Layer

### Spring Cloud Config Server (port 8888)

The Config Server is the **single source of truth for all application configuration**. On startup it clones `https://github.com/sameer11001/fintechwave-config.git` and serves property files over HTTP. Every microservice includes `spring-cloud-starter-config` and sets `spring.config.import=configserver:` in its bootstrap phase. If the Config Server is unavailable, services fail fast rather than starting with stale or default config. `spring-retry` is included in every service to allow the initial connection to be retried with back-off during container startup ordering delays.

### API Gateway (Spring Cloud Gateway, WebFlux)

The Gateway is the **sole entry point** for all external traffic. It performs:

- **JWT Validation** — Uses `NimbusReactiveJwtDecoder` to validate tokens against Keycloak's JWK Set URI before any request reaches a downstream service. Unauthenticated requests are rejected at this layer with `401 Unauthorized`.
- **Route proxying** — Forwards validated requests to the correct internal service based on path prefix.
- **Webhook pass-through** — `/api/v1/internal/webhook/**` and `/api/v1/webhooks/**` are permitted without authentication to allow Keycloak and Stripe to deliver events.
- **Actuator exposure** — `/actuator/health` and `/actuator/info` are open for container health checks.

---

## 4. Shared Libraries

Libraries are built as Maven JAR artifacts and declared as dependencies in each service's `pom.xml`. They are versioned alongside the monorepo.

### `libs/core`

Provides the platform-wide `ApiResponse<T>` envelope (wraps all REST responses with a `success` flag, `data`, and `message`), and `BaseServiceException` which all domain exceptions extend from.

### `libs/security`

Auto-configured via Spring Boot's `@AutoConfiguration` mechanism. Every service that includes this library automatically gets:

- **`KeycloakJwtAuthenticationConverter`** — Reads the `realm_access.roles` and `resource_access.<client>.roles` claims from the Keycloak JWT and maps them to Spring Security `GrantedAuthority` objects prefixed with `ROLE_`. This means method-level security annotations like `@PreAuthorize("hasRole('ADMIN')")` work transparently across all services.
- **`AudienceValidator`** — Enforces that the JWT `aud` claim matches the expected service audience, preventing token reuse across services.
- **`KeycloakProperties`** — Binds `keycloak.*` configuration properties.

### `libs/events`

Defines the `DomainEvent` interface — the contract for all domain events. Every event carries: a unique `eventId` (UUIDv4), an `eventType` discriminator string, an `eventVersion` for schema evolution, an `aggregateId`, an `aggregateType`, a UTC `occurredAt` timestamp, and an `idempotencyKey`. The `BaseEvent` abstract class provides a default implementation. The `DomainEventPublisher` utility wraps `KafkaTemplate` for publishing.

### `libs/payment-gateway`

Defines the `PaymentGatewayPort` hexagonal architecture port (interface). The Transaction Service depends on this interface at compile time. The concrete `StripeGatewayAdapter` that implements it lives inside the Transaction Service, keeping the core domain decoupled from the Stripe SDK. The port exposes three operations: `createCardPaymentIntent`, `initiateInstantPayout`, and `parseAndValidateWebhook`.

---

## 5. Service Catalogue

### 5.1 IAM / User Service (port 8081)

**Responsibility:** Maintains the platform's internal `UserProfile` table, which is a mirror of the identity held in Keycloak enriched with platform-specific attributes (KYC tier, Stripe customer linkage, phone hash).

**Internal Design:**

The IAM service operates as a **Keycloak webhook consumer**. When a user registers in Keycloak, Keycloak fires an HTTP POST to `/api/v1/internal/webhook/keycloak/user-registered` (via a custom Keycloak SPI event listener JAR deployed in `docker/keycloak/providers`). The `KeycloakWebhookController` receives this call and delegates to `UserProfileServiceImpl.createProfileFromKeycloak()`.

The profile creation is **idempotent** — if a profile already exists for the Keycloak user ID, the call is a no-op. If the webhook payload does not include the user's email, the service makes a back-channel call to the **Keycloak Admin REST API** (`KeycloakAdminClient`) to retrieve it.

The phone number is **never stored in plain text**. On profile update, `HashUtil.sha256()` hashes the normalised phone string before persisting it. This means phone numbers can be used for lookup (compare hashes) but cannot be reverse-engineered.

**Outbox Event Published:** On every new `UserProfile` creation, an `USER_REGISTERED` outbox event is written to the `outbox_events` table targeting the `iam.user-events` Kafka topic. The `OutboxRelay` scheduler picks it up within 1 second and delivers it to Kafka.

**KYC Tier Sync:** The `updateKycTier` method is called by downstream consumers (Kafka listeners) when a `KYC_VERIFIED` event arrives, updating the user's `kycTier` column to reflect their new compliance level.

**Database:** `fintechwave_users` (PostgreSQL). Schema managed by Flyway.

---

### 5.2 KYC Service (port 8082)

**Responsibility:** Manages the full Know-Your-Customer lifecycle — from initial shell creation through document upload, submission for review, and admin adjudication.

**Tiered Model:**

Users start at `TIER_0` (unverified). KYC approval promotes them to `TIER_1` (standard) or higher tiers. Each tier unlocks higher transaction limits. The `currentTier` and `requestedTier` fields on `KycApplication` track the user's present status and what they applied for.

**Internal Design:**

When a `USER_REGISTERED` event arrives on Kafka, the KYC service's consumer calls `createKycShell()`, which atomically creates a `KycApplication` record in `PENDING_SUBMISSION` status for the new user. This ensures every user has a KYC record and can begin the onboarding flow immediately.

**Document Upload Flow:**

1. The user uploads a document via `POST /api/v1/kyc/documents/{documentType}` as a multipart file.
2. `KycApplicationServiceImpl.uploadDocument()` delegates to `MinioStorageService`, which streams the file directly to the MinIO bucket, returning a `StorageReference` (bucket name + object key).
3. A `KycDocument` entity is saved to PostgreSQL referencing the MinIO object key — the actual file bytes are never stored in the relational database.
4. Document retrieval (`GET /api/v1/kyc/documents`) generates **pre-signed MinIO URLs with a 15-minute TTL**, so the client can download files directly from MinIO without routing through the service.

**State Machine:**

```
PENDING_SUBMISSION → UNDER_REVIEW → VERIFIED
                                  ↘ REJECTED → PENDING_SUBMISSION (re-submit)
```

Transitions are strictly validated. Attempting an illegal transition (e.g., submitting an already-verified application) throws `InvalidKycStateTransitionException`. The `reviewApplication()` method is restricted to admin roles and sets `reviewedBy` and `reviewedAt` audit fields.

**Outbox Events Published:**

- `KYC_SUBMITTED` — when a user submits their application
- `KYC_VERIFIED` — when an admin approves (consumed by ledger-service to provision wallet)
- `KYC_REJECTED` — when an admin rejects

**Database:** `fintechwave_kyc` (PostgreSQL). Schema managed by Flyway.

---

### 5.3 Ledger Service (port 8083)

**Responsibility:** The core financial engine. Implements a strict double-entry bookkeeping system. Every money movement in the platform results in ledger entries here. No wallet is ever created without a `KYC_VERIFIED` event.

**Internal Design:**

**Chart of Accounts:** The ledger uses a typed account system. Each account has an `AccountType` (`ASSET`, `LIABILITY`, `EQUITY`) and an `AccountCode`. User wallets are `LIABILITY` accounts (the platform owes the user their balance). A `SUSPENSE` platform account acts as an intermediate holding account during reservation/commit cycles.

**Double-Entry Validation:** Before any entries are persisted, `validateBalance()` asserts that the sum of all DEBIT amounts equals the sum of all CREDIT amounts in the request. Any imbalance throws `LedgerBalanceViolationException`, making it impossible to corrupt the ledger through unbalanced writes.

**Reserve / Commit / Release Lifecycle:**

For any outbound or peer-to-peer transaction, funds go through a three-phase lifecycle to prevent race conditions and double-spending:

```
RESERVE:  User Wallet (DEBIT) → Suspense Account (CREDIT)
          Funds are locked; user balance decreases.

COMMIT:   Suspense Account (DEBIT) → Destination Wallet (CREDIT)
          Funds are released to the recipient upon payment confirmation.

RELEASE:  Suspense Account (DEBIT) → User Wallet (CREDIT)
          Funds are returned to user if the payment fails.
```

**Pessimistic Locking:** Balance reads inside `commitDoubleEntry()` use `findByIdWithLock()` which issues a `SELECT ... FOR UPDATE` query, ensuring concurrent transactions on the same account serialise correctly at the database level.

**Wallet Provisioning:** The `KYCVerifiedConsumer` listens on the `kyc.verification-events` Kafka topic. On receiving a `KYC_VERIFIED` event, it calls `provisionWallet()` which creates an `Account` and a zero `Balance` record atomically. This is idempotent — duplicate events are safely skipped.

**Reconciliation:** The `reconcile()` method sums all liability balances (total user funds) and compares against the platform float. Any divergence is a SEV-1 condition — it throws `LedgerBalanceViolationException` and logs a critical error.

**Idempotency:** Every `LedgerEntry` carries an `idempotencyKey`. Before persisting, the service checks `existsByIdempotencyKey()`. Duplicate entries (from Kafka redelivery) are silently skipped.

**Database:** `fintechwave_ledger` (PostgreSQL). Schema managed by Flyway.

---

### 5.4 Transaction Service (port 8084)

**Responsibility:** The orchestration layer for all money movements. Handles the business logic of P2P transfers, card cash-in, and card cash-out. Acts as the bridge between user-facing operations and the Ledger Service (via Kafka events) and Stripe (via the `PaymentGatewayPort`).

**Idempotency:** Every write endpoint requires an `idempotencyKey` (UUID) in the request body. The service calls `guardDuplicate()` before any database write — if the key already exists in `transaction_records`, a `DuplicateTransactionException` is thrown. This makes all transaction endpoints safe to retry from the client side.

**P2P Transfer Flow:**

1. Client sends `POST /api/v1/transactions/p2p` with `receiverId`, `amount`, `currency`, and `idempotencyKey`.
2. `FeeServiceImpl` calculates the applicable fee for the `P2P` transaction type.
3. A `TransactionRecord` is saved with status `INITIATED`.
4. A `TRANSFER_INITIATED` outbox event is published, triggering the Ledger Service to **reserve** the funds (locking the sender's balance).
5. The **Fraud Service** asynchronously consumes this event, performs velocity checks, and publishes a `TRANSACTION_APPROVED` or `TRANSACTION_FLAGGED` decision.
6. If approved, the funds are **committed** to the receiver. If flagged, the reserved funds are **released** back to the sender.

**Cash-In Flow (card deposit):**

1. Client sends `POST /api/v1/transactions/cash-in` with a Stripe Payment Method ID.
2. The service calls `paymentGateway.createCardPaymentIntent()` via the `StripeGatewayAdapter`, which creates a Stripe `PaymentIntent` and returns its ID.
3. A `TransactionRecord` is saved with the `stripePaymentIntentId` in status `INITIATED`.
4. **No ledger entry is made yet.** The ledger credit happens only when Stripe confirms payment via webhook.
5. When Stripe fires `payment_intent.succeeded`, the `WebhookController` routes it to `handlePaymentIntentSucceeded()`, which marks the transaction `COMPLETED` and publishes a `CASH_IN_COMPLETED` outbox event for the Ledger Service to credit the user's wallet.

**Cash-Out Flow (card withdrawal):**

1. Client sends `POST /api/v1/transactions/cash-out` with a Stripe Payment Method ID.
2. `FeeServiceImpl` calculates the cash-out fee.
3. A `TransactionRecord` is saved with status `INITIATED`.
4. The service immediately calls `paymentGateway.initiateInstantPayout()` via Stripe to start the payout.
5. The `TransactionRecord` is updated to `RESERVED` with the `stripePayoutId`.
6. A `CASH_OUT_INITIATED` outbox event is published for the Ledger Service to reserve funds.
7. When Stripe confirms via `payout.paid` webhook, the transaction moves to `COMPLETED` and a `CASH_OUT_COMPLETED` event triggers the ledger commit. On `payout.failed`, a `CASH_OUT_FAILED` event triggers the ledger release (refund to user).

**Stripe Webhook Validation:** The `WebhookController` receives raw Stripe webhook payloads and passes both the body and the `Stripe-Signature` header to `paymentGateway.parseAndValidateWebhook()`. The `StripeGatewayAdapter` uses the Stripe Java SDK's `Webhook.constructEvent()` to cryptographically verify the signature using the configured `STRIPE_WEBHOOK_SECRET`. Invalid signatures are rejected before any state is mutated.

**Authorization on Reads:** `getTransactionById()` verifies that the calling user is either the sender or receiver of the transaction. If not, it returns `404 Not Found` (not `403`) to avoid leaking information about whether a transaction exists.

**Database:** `fintechwave_tx` (PostgreSQL). Schema managed by Flyway.

---

### 5.5 Fraud Service (port 8085)

**Responsibility:** Evaluates transactions against predefined risk rules (velocity checks, limits). Operates asynchronously to prevent slowing down the transaction reservation phase.

**Internal Design:**
Consumes `TRANSFER_INITIATED` events, evaluates Redis sliding windows (e.g. 60s tx count, 1h volume limits), and publishes `TRANSACTION_APPROVED` or `TRANSACTION_FLAGGED` outbox events. Contains a configurable rule engine.

**Database:** `fintechwave_fraud` (PostgreSQL) + Redis for sliding windows. Schema managed by Flyway.

---

### 5.6 Notification Service (port 8086)

**Responsibility:** Centralized multi-channel communication (Email, SMS, Push). Listens to platform-wide events and dispatches user notifications.

**Internal Design:**
Consumes events from `kyc`, `ledger`, `tx`, and `fraud` topics. Idempotent delivery. Retains history with a 90-day rolling retention sweeper (`NotificationRetentionSweeper`).

**Database:** `fintechwave_notif` (PostgreSQL). Schema managed by Flyway.

---

### 5.7 Reporting Service (port 8087)

**Responsibility:** Provides event-sourced read models (CQRS) for dashboards and administration without impacting transactional databases.

**Internal Design:**
Consumes events to build projections like `transaction_summary`, `daily_volume`, `balance_snapshot`, and `kyc_status_summary`. Exposes paginated admin endpoints.

**Database:** `fintechwave_report` (PostgreSQL). Schema managed by Flyway.

---

## 6. Security Architecture

### Authentication Flow

All external requests must carry a Keycloak-issued Bearer JWT. The API Gateway validates the token's signature against Keycloak's JWK Set endpoint before forwarding the request downstream. Downstream services also validate the JWT independently as OAuth2 Resource Servers (defence in depth).

### Role Mapping

The `KeycloakJwtAuthenticationConverter` in `libs/security` extracts roles from two JWT claims:

- **`realm_access.roles`** — Realm-level roles, mapped to `ROLE_<ROLE_NAME>` (e.g., `ROLE_ADMIN`, `ROLE_USER`).
- **`resource_access.<client>.roles`** — Client-level roles, mapped to `ROLE_<CLIENT>_<ROLE_NAME>` (e.g., `ROLE_FINTECHWAVE_KYC_REVIEWER`).

Keycloak's internal roles (`offline_access`, `uma_authorization`, `default-roles-*`) are filtered out.

### Audience Validation

The `AudienceValidator` ensures the JWT `aud` claim contains the expected audience string for each service. This prevents a JWT issued for one service from being replayed against another.

### Webhook Security

Internal Keycloak webhooks (`/api/v1/internal/webhook/**`) are excluded from JWT authentication at the Gateway level. These endpoints are secured instead by Keycloak's own SPI mechanism — the webhook is fired server-to-server from within the Docker network. Stripe webhooks (`/api/v1/webhooks/**`) are similarly excluded from JWT auth but secured via HMAC signature verification inside the Transaction Service.

---

## 7. Event-Driven Messaging — Transactional Outbox

### The Problem Solved

Without the Outbox Pattern, a service might save a database record and then attempt to publish a Kafka event. If the service crashes between these two steps, the database change is committed but the event is never published — causing permanent data inconsistency.

### How the Outbox Works

Every service has an `outbox_events` table in its own database. When a business operation occurs:

1. **Within the same database transaction**, the domain entity is saved AND an `OutboxEvent` row is inserted into `outbox_events` (with `published = false`). Both writes are atomic. If either fails, both roll back.
2. A scheduled `OutboxRelay` component (annotated `@Scheduled(fixedDelay = 1000)`) polls for unpublished events every second.
3. For each pending event, `kafkaTemplate.send(...).get()` is called synchronously (the `.get()` makes the publish blocking, ensuring the acknowledgement is received before marking as published).
4. Successfully published event IDs are batch-marked as `published = true` using `markPublished()`.
5. On failure to publish a single event, an error is logged and that event is left as unpublished for the next polling cycle to retry.

### Kafka Topics

| Topic                     | Producer            | Consumer                                                               |
| ------------------------- | ------------------- | ---------------------------------------------------------------------- |
| `iam.user-events`         | IAM Service         | KYC Service                                                            |
| `kyc.verification-events` | KYC Service         | IAM Service, Ledger Service, Notification Service                      |
| `ledger.wallet-events`    | Ledger Service      | Transaction Service, Notification Service                              |
| `tx.transaction-events`   | Transaction Service | Ledger Service, Fraud Service, Notification Service, Reporting Service |
| `fraud.risk-events`       | Fraud Service       | Transaction Service, Notification Service                              |

### Consumer Idempotency

Every consumer service maintains a `processed_events` table. Before processing any Kafka message, the consumer checks whether the event's `idempotencyKey` already exists in this table. If it does, the message is a duplicate (Kafka redelivery) and is silently skipped. If not, the event is processed and the key is inserted atomically. This guarantees exactly-once processing semantics at the application level even when Kafka delivers a message more than once.

---

## 8. Financial Engine — Double-Entry Bookkeeping

Every money movement in FintechWave follows the double-entry principle: for every debit there is an equal and opposite credit. The ledger is structured as follows:

**Accounts Table** — Each account has an `accountType` (`LIABILITY`, `ASSET`, `EQUITY`), an `accountCode` (`USER_WALLET`, `SUSPENSE`, `FEE_REVENUE`), an `ownerId` (null for platform accounts), and a `currency`.

**Balances Table** — One balance row per account. Updated atomically using pessimistic locking (`SELECT ... FOR UPDATE`) to prevent concurrent balance corruption. An optimistic `version` column provides additional concurrency protection via JPA's `@Version`.

**Ledger Entries Table** — An immutable append-only log of every individual debit and credit. No entry is ever updated or deleted. Each entry references a `transactionId` (the business transaction that caused it) and carries its own `idempotencyKey`.

**Balance Invariant:** The sum of all USER_WALLET liability balances must always equal the platform's FLOAT asset balance. The `reconcile()` endpoint validates this invariant on demand.

---

## 9. Stripe Payment Integration

The Stripe integration follows the **Hexagonal Architecture (Ports and Adapters)** pattern. `libs/payment-gateway` defines `PaymentGatewayPort` — a pure Java interface with no Stripe SDK dependency. The Transaction Service depends only on this interface. The concrete `StripeGatewayAdapter` inside the Transaction Service implements the port using the Stripe Java SDK v26.

This decoupling means the payment provider can be swapped (e.g., to PayPal, Adyen) without touching any business logic — only the adapter needs to change.

**Cash-In (card deposit):** Uses Stripe's `PaymentIntent` API. The intent is created server-side with `MANUAL` capture mode, returned to the client for card confirmation, and the ledger is credited only after Stripe's `payment_intent.succeeded` webhook confirms actual card charge.

**Cash-Out (card withdrawal):** Uses Stripe's `Payout` API for instant payouts to the user's card. The payout is initiated immediately, funds are reserved in the ledger, and the reserve is either committed (on `payout.paid`) or released (on `payout.failed`).

---

## 10. KYC Lifecycle State Machine

```
                  User Registers
                        │
                        ▼
             ┌─────────────────────┐
             │  PENDING_SUBMISSION  │  ← KYC shell auto-created
             └──────────┬──────────┘
                        │ User uploads documents + submits
                        ▼
             ┌─────────────────────┐
             │    UNDER_REVIEW     │  ← KYC_SUBMITTED event fired
             └──────────┬──────────┘
                  ┌─────┴─────┐
                  │           │
                  ▼           ▼
           ┌──────────┐  ┌──────────┐
           │ VERIFIED │  │ REJECTED │
           └──────────┘  └────┬─────┘
     KYC_VERIFIED fired        │ User can re-submit
     Wallet provisioned        ▼
                     PENDING_SUBMISSION
```

The KYC tier is a compliance gate. Without `KYC_VERIFIED`, no wallet exists in the Ledger Service, and any attempt to transact will fail with a `WalletNotFoundException`.

---

## 11. User Registration Flow (End-to-End)

This sequence shows how a new user flows through the entire platform from sign-up to wallet readiness:

```
1.  User registers via Keycloak (standard OIDC register flow).

2.  Keycloak fires webhook → IAM Service /api/v1/internal/webhook/keycloak/user-registered

3.  IAM Service creates UserProfile (TIER_0) in DB + writes USER_REGISTERED outbox event.

4.  OutboxRelay publishes USER_REGISTERED → Kafka topic: iam.user-events

5.  KYC Service consumes USER_REGISTERED → createKycShell() → KycApplication (PENDING_SUBMISSION)

6.  User authenticates → receives Keycloak JWT

7.  User uploads ID documents → KYC Service → stored in MinIO

8.  User submits KYC application → status: UNDER_REVIEW → KYC_SUBMITTED outbox event

9.  Admin reviews and approves → status: VERIFIED → KYC_VERIFIED outbox event

10. Ledger Service consumes KYC_VERIFIED → provisionWallet() → Account + Balance (0.00 JOD)

11. IAM Service consumes KYC_VERIFIED → updateKycTier(TIER_1) on UserProfile

12. User is now fully onboarded and can initiate transactions.
```

---

## 12. Infrastructure & Data Stores

| Component                    | Technology                 | Purpose                                                 |
| ---------------------------- | -------------------------- | ------------------------------------------------------- |
| **PostgreSQL 16**            | Relational DB              | Persistent store for all services (one DB per service)  |
| **Apache Kafka 7.6 (KRaft)** | Message broker             | Async inter-service event bus (no ZooKeeper)            |
| **Keycloak 26**              | Identity Provider          | OAuth2/OIDC authentication, JWT issuance, user registry |
| **MinIO**                    | S3-compatible object store | KYC document binary storage                             |
| **Redis 7.2**                | In-memory cache            | Rate limiting, session caching, ephemeral state         |
| **Spring Cloud Config**      | Git-backed config server   | Centralised, versioned configuration for all services   |

**Database Isolation:** Each service has its own dedicated PostgreSQL database (`fintechwave_users`, `fintechwave_kyc`, `fintechwave_ledger`, `fintechwave_tx`, `fintechwave_fraud`, `fintechwave_notif`, `fintechwave_report`). Services never share a database schema, enforcing domain ownership boundaries.

**Schema Migration:** Every service uses **Flyway** for database schema migrations. Migrations run automatically on startup before the application accepts traffic. This ensures schema and application code are always in sync.

**Container Images:** Every service is packaged as a Docker image using **Google Jib** (`jib-maven-plugin`), which builds optimised layered images without requiring a local Docker daemon. The base image is `eclipse-temurin:21-jre-jammy`. JVM flags enable container-aware memory limits (`-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage=75.0`).

---

## 13. Technology Stack

| Layer            | Technology                             | Version             |
| ---------------- | -------------------------------------- | ------------------- |
| Language         | Java                                   | 21 (LTS)            |
| Framework        | Spring Boot                            | 3.5.x               |
| Gateway          | Spring Cloud Gateway                   | (WebFlux, reactive) |
| Config           | Spring Cloud Config                    | Git-backed          |
| Persistence      | Spring Data JPA + Hibernate            | —                   |
| Database         | PostgreSQL                             | 16                  |
| Migrations       | Flyway                                 | —                   |
| Messaging        | Apache Kafka                           | 7.6.1 (KRaft)       |
| Identity         | Keycloak                               | 26.6.2              |
| Security         | Spring Security OAuth2 Resource Server | —                   |
| Object Storage   | MinIO                                  | RELEASE.2024-06-13  |
| Cache            | Redis                                  | 7.2                 |
| Payment          | Stripe Java SDK                        | 26.3.0              |
| API Docs         | SpringDoc OpenAPI (Swagger UI)         | —                   |
| Code Gen         | Lombok + MapStruct                     | —                   |
| Build            | Apache Maven                           | Multi-module        |
| Packaging        | Google Jib                             | 3.4.3               |
| Containerisation | Docker Compose                         | —                   |

---

## 14. Port Reference

| Service              | Port                           |
| -------------------- | ------------------------------ |
| API Gateway          | 8080                           |
| IAM / User Service   | 8081                           |
| KYC Service          | 8082                           |
| Ledger Service       | 8083                           |
| Transaction Service  | 8084                           |
| Fraud Service        | 8085                           |
| Notification Service | 8086                           |
| Reporting Service    | 8087                           |
| Config Server        | 8888                           |
| Keycloak             | 8180 (host) → 8080 (container) |
| Kafka (external)     | 29092                          |
| PostgreSQL           | 5432                           |
| Redis                | 6379                           |
| MinIO API            | 9000                           |
| MinIO Console        | 9001                           |

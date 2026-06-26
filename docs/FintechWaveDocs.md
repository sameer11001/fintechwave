# FintechWave — Master Development Plan & Decision Record

> **Every AI agent and developer must read this before touching any service.**

---

## 1. Business Model (Locked)

FintechWave is a **custodial e-money platform** (Orange Money model).

- The platform holds real money in a pooled float account
- Every user wallet is a **liability** to the platform
- The platform is the financial custodian — not a payment orchestrator
- Target region: **MENA** (UAE, KSA, Egypt primary markets)

### Core Non-Negotiables (Financial)

| Rule                                           | Enforcement                                                      |
| ---------------------------------------------- | ---------------------------------------------------------------- |
| PostgreSQL is the sole source of truth         | Redis/Kafka are never authoritative                              |
| Outbox pattern mandatory for all domain events | DB mutation + event publication = one ACID transaction           |
| All Kafka consumers are idempotent             | `idempotency_key` stored in `processed_events` with UNIQUE index |
| Double-entry bookkeeping on all money movement | `SUM(DEBIT) = SUM(CREDIT)` per transaction — enforced at commit  |
| Balance can never go negative                  | `CHECK (amount >= 0)` in DB + application layer                  |
| Records are never deleted                      | Errors corrected via reversing journal entries only              |
| Pessimistic locking on balance rows            | `SELECT FOR UPDATE` on every balance write                       |

---

## 2. Actor Map (Locked)

| Actor        | Phase   | Responsibilities                                |
| ------------ | ------- | ----------------------------------------------- |
| **User**     | Phase 2 | Wallet owner — cash-in, cash-out, P2P, bill pay |
| **Admin**    | Phase 2 | KYC review, user management, monitoring         |
| **Merchant** | Future  | Receives payments — deferred                    |
| **Agent**    | Never   | Out of scope                                    |

---

## 3. Money Flow Catalog (Locked)

| Flow            | Phase       | Channel                                    | Provider               |
| --------------- | ----------- | ------------------------------------------ | ---------------------- |
| P2P Transfer    | Phase 2     | In-app wallet-to-wallet                    | Internal               |
| Cash-in         | Phase 2     | **Debit/credit card only**                 | Stripe Payment Intents |
| Cash-out        | Phase 2     | **Stripe Instant Payouts → original card** | Stripe                 |
| Bill Pay        | Phase 2     | Third-party aggregator API                 | Aggregator             |
| Bank Transfer   | **Removed** | ❌ Dropped entirely                        | —                      |
| Virtual Account | **Removed** | ❌ Dropped entirely                        | —                      |
| Merchant Pay    | Future      | QR / In-app                                | Deferred               |
| QR Payment      | Future      | —                                          | Deferred               |
| Remittance      | Future      | —                                          | Phase 4+               |
| Agent Channel   | Never       | —                                          | Out of scope           |

### Cash-in Flow (Card)

```
User enters card → Stripe Payment Intents → payment_intent.succeeded webhook
→ Transaction Service validates idempotency → Outbox event → Ledger credits wallet
```

### Cash-out Flow (Card)

```
User requests cash-out → Ledger reserves funds → Stripe Instant Payouts → card credited
→ payout.paid webhook → Ledger commits → Transaction COMPLETED
```

> **Note:** Stripe Instant Payouts has limited MENA coverage. The PaymentGatewayPort
> abstraction allows a Tap Payments adapter to be plugged in for payout-only in
> countries where Stripe doesn't cover — zero transaction service changes needed.

### P2P Transfer Flow

```
User A initiates → RESERVE (Dr User A Wallet, Cr Suspense)
→ Fraud check (async — funds already locked)
→ APPROVED: COMMIT (Dr Suspense, Cr User B Wallet) + FEE booking
→ FLAGGED: RELEASE (Dr Suspense, Cr User A Wallet)
```

### Bill Pay Flow

```
User selects biller + amount → Ledger reserves funds
→ Transaction Service calls Aggregator API → Aggregator confirms
→ Ledger commits → COMPLETED
→ On aggregator failure: RELEASE reserved funds → FAILED
```

---

## 4. Payment Gateway Architecture (Locked)

### Port & Adapter Pattern — `libs/payment-gateway`

The system **never depends on Stripe directly**. All external payment calls go through a port interface. This allows swapping or combining providers per region without changing business logic.

```
libs/payment-gateway/
  ├── PaymentGatewayPort.java        ← interface (the contract)
  ├── CardPaymentIntent.java
  ├── PayoutResult.java
  ├── WebhookEvent.java
  └── Money.java

transaction-service/adapter/
  ├── StripeGatewayAdapter.java      ← implements PaymentGatewayPort (Phase 2)
  └── TapPaymentsAdapter.java        ← implements PaymentGatewayPort (MENA fallback)
```

**Interface contract:**

```java
public interface PaymentGatewayPort {
    CardPaymentIntent createCardPaymentIntent(Money amount, String stripeCustomerId);
    PayoutResult      initiateInstantPayout(String stripePaymentMethodId, Money amount);
    WebhookEvent      parseAndValidateWebhook(String payload, String signature);
}
```

### Provider Split

| Flow         | Phase 2 Provider                        | Fallback (MENA)      |
| ------------ | --------------------------------------- | -------------------- |
| Card Cash-in | Stripe Payment Intents                  | —                    |
| Cash-out     | Stripe Instant Payouts                  | Tap Payments adapter |
| Bill Pay     | Aggregator API (not PaymentGatewayPort) | —                    |

### User Card Storage Model

```
User (in user-service)
  ├── stripe_customer_id          ← created on first cash-in
  └── stripe_payment_method_id   ← saved card token (no raw card data — PCI SAQ A)
```

### Webhook Endpoints

```
POST /webhooks/stripe    → StripeGatewayAdapter.parseAndValidateWebhook()
POST /webhooks/tap       → TapPaymentsAdapter.parseAndValidateWebhook()
```

Stripe-Signature header validated via HMAC before any processing.

---

## 5. Identity Architecture — Keycloak Migration (Locked)

### Decision

**Keycloak replaces the custom IAM auth layer.**

Keycloak handles: authentication, MFA, brute-force protection, account lockout, OAuth2/OIDC, JWT issuance, session management.

The existing `user-service` is **repurposed as `user-service`** — it owns user business profile data, not credentials.

### What Keycloak Owns vs What Services Own

| Concern                                        | Owner               |
| ---------------------------------------------- | ------------------- |
| Login, registration, password                  | **Keycloak**        |
| MFA, OTP, TOTP                                 | **Keycloak**        |
| Brute-force, account lockout                   | **Keycloak**        |
| JWT issuance (RS256)                           | **Keycloak**        |
| Session revocation                             | **Keycloak**        |
| SSO (Admin portal, future Merchant portal)     | **Keycloak**        |
| User business profile (name, phone, status)    | **user-service**    |
| KYC tier, wallet reference                     | **user-service**    |
| Wallet ownership, balance limits               | **ledger-service**  |
| Financial authorization, KYC gates             | **domain services** |
| Business RBAC (can_withdraw, wallet ownership) | **domain services** |

### Keycloak → System Integration

```
User registers in Keycloak
       ↓
Keycloak fires webhook (HTTP POST) to user-service
       ↓
user-service creates UserProfile record
       ↓
user-service writes UserRegistered to Outbox
       ↓
Outbox relay publishes to Kafka iam.user-events
       ↓
kyc-service listens → creates KYC application shell
```

No Keycloak SPI plugins. Webhook only — keeps Kafka publishing inside our Outbox pattern.

### Updated libs/security

The `libs/security` starter changes JWT validation source from custom public key → **Keycloak JWKS endpoint**. Config change only — no rewrite.

### Architecture

```
User → Keycloak (authenticate) → JWT (RS256)
     → API Gateway (validates JWT via Keycloak public key)
     → Business Services (extract claims, enforce domain authorization)
```

---

## 6. Bounded Context Map (Locked)

| Context          | Service                                    | Status            | Port        |
| ---------------- | ------------------------------------------ | ----------------- | ----------- |
| Identity & Auth  | **Keycloak** 26.6.2                        | ✅ Done — Phase 2 | 8180 (host) |
| User Profile     | **user-service** (user-service repurposed) | ✅ Done — Phase 2 | 8081        |
| KYC / Onboarding | **kyc-service**                            | ✅ Done — Phase 2 | 8082        |
| Wallet / Ledger  | **ledger-service** ⭐ CORE                 | ✅ Done — Phase 2 | 8083        |
| Transactions     | **transaction-service**                    | ✅ Done — Phase 2 | 8084        |
| Fraud / Risk     | **fraud-service**                          | ✅ Done — Phase 3 | 8085        |
| Notifications    | **notification-service**                   | ✅ Done — Phase 3 | 8086        |
| Reporting        | **reporting-service**                      | ✅ Done — Phase 3 | 8087        |
| API Gateway      | **gateway**                                | ✅ Done — Phase 1 | 8080        |
| Config Server    | **config-server**                          | ✅ Done — Phase 1 | 8888        |
| Merchant         | Future                                     | —                 | TBD         |
| Settlement       | Deferred                                   | —                 | TBD         |

---

## 7. Ledger Design (Locked)

### Chart of Accounts

```
ASSETS
  1000  Platform Float Account    ← Real money held (Stripe balance → platform bank)
  1001  Stripe Escrow             ← Funds in Stripe pending settlement
  1002  Suspense / Hold           ← Reserved funds during in-flight transactions

LIABILITIES
  2000  [userId] User Wallet      ← One account per user. Platform owes user this amount.

REVENUE
  3000  P2P Transfer Fee Revenue
  3001  Cash-out Fee Revenue
  3002  Bill Pay Fee Revenue

EXPENSES
  4000  Stripe Processing Cost    ← Stripe charges per transaction
  4001  Bill Pay Aggregator Cost  ← Aggregator charges per bill payment
```

### Master Reconciliation Invariant

```
SUM(all User Wallet liabilities) = Platform Float Account balance
```

If these diverge → financial incident → PagerDuty SEV-1 alert.

### Reservation / Hold Model

Every transaction follows this 3-step posting flow:

```
RESERVE → Dr Source Wallet,  Cr Suspense/Hold    (funds locked)
COMMIT  → Dr Suspense/Hold,  Cr Destination      (transfer completes)
RELEASE → Dr Suspense/Hold,  Cr Source Wallet    (on failure — funds returned)
```

This prevents concurrent overdraft: two simultaneous transfers cannot both pass balance checks because the first RESERVE immediately reduces the available balance.

### P2P Transfer Double-Entry Example

```
RESERVE:   Dr User A Wallet  100.00 / Cr Suspense       100.00
COMMIT:    Dr Suspense       100.00 / Cr User B Wallet  100.00
FEE:       Dr User A Wallet    1.50 / Cr Fee Revenue      1.50
```

### Cash-in Double-Entry Example

```
Dr  Platform Float Account  100.00   (real money arrived via Stripe)
Cr  User Wallet             100.00   (platform now owes user)
```

### Cash-out Double-Entry Example

```
RESERVE:  Dr User Wallet     100.00 / Cr Suspense         100.00
COMMIT:   Dr Suspense        100.00 / Cr Platform Float   100.00
FEE:      Dr User Wallet       1.00 / Cr Cash-out Revenue   1.00
```

### Database Schema

```sql
CREATE TABLE ledger_account (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID,
    account_type VARCHAR(20) NOT NULL,  -- ASSET | LIABILITY | REVENUE | EXPENSE
    account_code VARCHAR(10) NOT NULL,  -- e.g. '2000'
    currency     CHAR(3) NOT NULL DEFAULT 'USD',
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entry (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID NOT NULL,
    account_id       UUID NOT NULL REFERENCES ledger_account(id),
    entry_type       CHAR(6) NOT NULL,  -- DEBIT | CREDIT
    amount           NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency         CHAR(3) NOT NULL,
    idempotency_key  UUID NOT NULL UNIQUE,
    description      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE balance (
    account_id   UUID PRIMARY KEY REFERENCES ledger_account(id),
    amount       NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    currency     CHAR(3) NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entry_transaction_id ON ledger_entry(transaction_id);
CREATE INDEX idx_ledger_entry_account_id     ON ledger_entry(account_id);
CREATE INDEX idx_ledger_account_owner_id     ON ledger_account(owner_id);
```

---

## 8. Event Ownership Table (Locked — No Kafka Producer Written Before This)

> This table is the contract. No service publishes an event not listed here.
> No service consumes an event it is not listed as a consumer for.

| Event                  | Producer                            | Consumer(s)                                             | Topic                     |
| ---------------------- | ----------------------------------- | ------------------------------------------------------- | ------------------------- |
| `UserRegistered`       | user-service (via Keycloak webhook) | kyc-service                                             | `iam.user-events`         |
| `KYCSubmitted`         | kyc-service                         | —                                                       | `kyc.verification-events` |
| `KYCVerified`          | kyc-service                         | ledger-service, notification-service                    | `kyc.verification-events` |
| `KYCRejected`          | kyc-service                         | notification-service                                    | `kyc.verification-events` |
| `WalletProvisioned`    | ledger-service                      | notification-service                                    | `ledger.wallet-events`    |
| `LedgerEntryCommitted` | ledger-service                      | transaction-service                                     | `ledger.wallet-events`    |
| `TransferInitiated`    | transaction-service                 | fraud-service                                           | `tx.transaction-events`   |
| `TransactionApproved`  | fraud-service                       | transaction-service                                     | `fraud.risk-events`       |
| `TransactionFlagged`   | fraud-service                       | transaction-service, notification-service               | `fraud.risk-events`       |
| `TransferCompleted`    | transaction-service                 | notification-service, reporting-service                 | `tx.transaction-events`   |
| `TransferFailed`       | transaction-service                 | notification-service                                    | `tx.transaction-events`   |
| `TransferReversed`     | transaction-service                 | ledger-service, notification-service                    | `tx.transaction-events`   |
| `CashInCompleted`      | transaction-service                 | ledger-service, notification-service, reporting-service | `tx.transaction-events`   |
| `CashInFailed`         | transaction-service                 | notification-service                                    | `tx.transaction-events`   |
| `CashOutCompleted`     | transaction-service                 | ledger-service, notification-service, reporting-service | `tx.transaction-events`   |
| `CashOutFailed`        | transaction-service                 | notification-service                                    | `tx.transaction-events`   |
| `BillPayCompleted`     | transaction-service                 | ledger-service, notification-service, reporting-service | `tx.transaction-events`   |
| `BillPayFailed`        | transaction-service                 | notification-service                                    | `tx.transaction-events`   |

### Event Envelope (All events must use this — enforced by `libs/events`)

```json
{
  "eventId": "uuid-v4",
  "eventType": "TRANSFER_INITIATED",
  "eventVersion": 1,
  "aggregateId": "uuid",
  "aggregateType": "TRANSACTION",
  "occurredAt": "2026-01-15T10:30:00Z",
  "idempotencyKey": "uuid-v4",
  "payload": {}
}
```

### Idempotency at Consumer Level

```sql
CREATE TABLE processed_events (
    idempotency_key UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Every consumer inserts `idempotency_key` in the same transaction as its state mutation. Duplicate events hit the unique constraint and are silently discarded.

---

## 9. Shared Libraries — Final Structure

```
libs/
  ├── common/           ← BOM — all dependency versions here, nowhere else
  ├── core/             ← ApiResponse<T>, BaseServiceException, GlobalExceptionHandler, MDC Context Management
  ├── security/         ← Keycloak JWT validation, SecurityFilterChain defaults
  ├── events/           ← Kafka event POJOs + envelope contract
  ├── grpc-contracts/   ← gRPC service definitions (Protobuf files) and compiled classes (NEW)
  └── payment-gateway/  ← PaymentGatewayPort interface + shared DTOs (NEW)
```

**Rule:** No service declares a dependency version in its own `pom.xml`. All versions live in `libs/common` BOM only.

---

## 10. Phase Roadmap

### Phase 1 — Platform Foundation ✅ COMPLETE

| Component                                | Status                                                                                        |
| ---------------------------------------- | --------------------------------------------------------------------------------------------- |
| Config Server (`platform/config-server`) | ✅ Done — Git-backed, port 8888, Dockerized                                                   |
| API Gateway (`platform/gateway`)         | ✅ Done — Spring Cloud Gateway, JWT pre-filter                                                |
| `libs/common`                            | ✅ Done — BOM                                                                                 |
| `libs/core`                              | ✅ Done — ApiResponse, exceptions                                                             |
| `libs/security`                          | ✅ Done — Keycloak JWKS validation, `KeycloakJwtAuthenticationConverter`, `AudienceValidator` |
| `libs/events`                            | ✅ Done — Kafka event schemas, `DomainEvent` interface, `BaseEvent`                           |
| IAM Service auth layer                   | ✅ Done — repurposed as `user-service` (auth removed, profile kept)                           |
| Transaction Service                      | ✅ Done — full Stripe adapter + state machine implemented                                     |

---

### Phase 2 — Core Financial Engine ✅ COMPLETE

**Build order is strict. Do not skip steps.**

#### Step 1 — Keycloak Migration ✅ DONE

- ✅ Keycloak 26.6.2 deployed in `docker/docker-compose.yml` (port 8180)
- ✅ Custom webhook SPI (`docker/keycloak-webhook/`) fires `HTTP POST` on user registration
- ✅ `user-service` fully repurposed as `user-service` (port 8081) — profile CRUD + outbox relay
- ✅ `libs/security` rewritten: `KeycloakJwtAuthenticationConverter`, `AudienceValidator`, JWKS auto-config
- ✅ Old `AuthServiceImpl`, `TokenServiceImpl`, `RefreshToken` entity removed

#### Step 2 — `libs/payment-gateway` ✅ DONE

- ✅ `PaymentGatewayPort` interface defined
- ✅ Shared DTOs: `CardPaymentIntent`, `PayoutResult`, `WebhookEvent`, `Money` (with minor-unit conversion)
- ✅ Registered in BOM (`libs/common`)

#### Step 3 — Ledger Service ✅ DONE

Location: `services/ledger-service` | Port: 8083 | Database: `fintechwave_ledger`

- ✅ Double-entry posting engine (`LedgerServiceImpl.commitDoubleEntry`)
- ✅ RESERVE / COMMIT / RELEASE operations with pessimistic locking
- ✅ Balance integrity check — SEV-1 alert on divergence
- ✅ `KYCVerifiedConsumer` → idempotent wallet provisioning
- ✅ `WalletProvisioned` + `LedgerEntryCommitted` published via outbox
- ✅ `ReconciliationController` (ADMIN only)
- ✅ Keycloak JWT security config, Flyway V1 migration

```
com.fintechwave.ledger/
  ├── api/          LedgerController, ReconciliationController
  ├── service/      ILedgerService, impl/LedgerServiceImpl
  ├── domain/entity Account, LedgerEntry, Balance, ProcessedEvent
  ├── domain/enums  AccountType, EntryType, AccountCode
  ├── repository/   AccountRepository, LedgerEntryRepository, BalanceRepository
  ├── messaging/    KYCVerifiedConsumer
  └── config/       SecurityConfig
```

#### Step 4 — KYC Service ✅ DONE

Location: `services/kyc-service` | Port: 8082 | Database: `fintechwave_kyc`

KYC Tiers:
| Tier | Requirements | Wallet |
|---|---|---|
| Tier 0 | Email verified (Keycloak) | None |
| Tier 1 | National ID photo | Basic, low limits |
| Tier 2 | Facial biometric (selfie) | Standard |
| Tier 3 | Enhanced due diligence | High-value |

- ✅ `UserRegisteredConsumer` → creates KYC shell (idempotent)
- ✅ `KycController` — submit, upload documents, view status
- ✅ `AdminKycController` — paginated review queue, approve/reject
- ✅ `MinioStorageService` — UUID-keyed object storage (PII-safe), 15-min pre-signed URLs
- ✅ `KYC_VERIFIED` published via outbox — **sole compliance gate** for wallet creation
- ✅ `KYC_SUBMITTED`, `KYC_REJECTED` events published on `kyc.verification-events`
- ✅ Flyway V1 migration: `kyc_applications`, `kyc_documents`, `processed_events`, `kyc_outbox_events`

```
com.fintechwave.kyc/
  ├── api/          KycController, AdminKycController
  ├── service/      IKycApplicationService, impl/KycApplicationServiceImpl
  ├── domain/entity KycApplication, KycDocument, ProcessedEvent, OutboxEvent
  ├── domain/enums  KycTier, KycStatus, DocumentType
  ├── repository/   KycApplicationRepository, KycDocumentRepository, ...
  ├── storage/      IDocumentStorageService (port), MinioStorageService (adapter)
  ├── messaging/    UserRegisteredConsumer, KycOutboxRelay
  └── config/       SecurityConfig, MinioConfig, MinioProperties
```

#### Step 5 — Transaction Service ✅ DONE

Location: `services/transaction-service` | Port: 8084 | Database: `fintechwave_tx`

Transaction State Machine:

```
INITIATED → FRAUD_CHECK → RESERVED → COMMITTED → COMPLETED
                ↓                         ↓
            FLAGGED                   FAILED → REVERSED
```

- ✅ `StripeGatewayAdapter` implements `PaymentGatewayPort` — fully provider-independent
- ✅ Cash-in: `createCardPaymentIntent` → returns `clientSecret` for Stripe.js
- ✅ Cash-out: `initiateInstantPayout` → Stripe Instant Payouts
- ✅ `WebhookController` — HMAC-validated (raw body, no JWT); routes `payment_intent.succeeded`, `payout.paid`, and `*.failed`
- ✅ `FeeServiceImpl` — 0.5% P2P (min $0.10), 1% cash-out, 0% cash-in
- ✅ `TransactionOutboxRelay` — publishes to `tx.transaction-events` every 1s
- ✅ Client idempotency key on all write endpoints — duplicate requests return 409
- ✅ Flyway V1 migration: `transactions`, `tx_outbox_events`, `tx_processed_events`

```
com.fintechwave.transaction/
  ├── api/          TransactionController, WebhookController
  ├── service/      ITransactionService, IFeeService, impl/*
  ├── domain/entity TransactionRecord, OutboxEvent, ProcessedEvent
  ├── domain/enums  TransactionType, TransactionStatus
  ├── adapter/      StripeGatewayAdapter
  ├── messaging/    TransactionOutboxRelay
  └── config/       SecurityConfig, StripeProperties
```

#### Step 6 — Docker Compose & Infrastructure ✅ DONE

- ✅ Keycloak 26.6.2 (KRaft-mode Kafka, no Zookeeper)
- ✅ `kyc-service`, `ledger-service`, `transaction-service` containers with health checks
- ✅ MinIO `RELEASE.2024-06-13` for KYC document storage
- ✅ PostgreSQL 16 with `01-create-databases.sh` init: `keycloak_db`, `fintechwave_users`, `fintechwave_kyc`, `fintechwave_ledger`, `fintechwave_tx`
- ✅ Redis 7.2 for Phase 3 (fraud velocity checks)
- ✅ All service environment variables wired via `docker-compose.yml`

---

### Phase 3 — Risk, Fraud & Engagement ✅ COMPLETE

#### Fraud & Risk Service ✅ DONE

Location: `services/fraud-service` | Port: 8085 | Database: `fintechwave_fraud` + Redis

Velocity Checks (Redis sliding windows):
| Rule | Window | Redis Key |
|---|---|---|
| Max 10 transactions | 60s | `velocity:tx_count:{userId}:60s` |
| Max equivalent $500 | 1h | `velocity:tx_volume:{userId}:1h` |
| Max equivalent $2000 | 24h | `velocity:tx_volume:{userId}:24h` |
| Failed auth attempts | 15m | `velocity:auth_fail:{ip}:15m` |

- ✅ `TransferInitiatedConsumer` — consumes `tx.transaction-events`, filters `TRANSFER_INITIATED`
- ✅ `FraudServiceImpl.evaluate()` — Redis sliding window velocity checks + configurable rule engine
- ✅ `FraudOutboxRelay` — publishes `TRANSACTION_APPROVED` / `TRANSACTION_FLAGGED` to `fraud.risk-events`
- ✅ `FraudRule` DB table — hot-reloadable rules with threshold, window, action (APPROVE/FLAG/BLOCK)
- ✅ `FraudDecision` — immutable audit record of every evaluation
- ✅ Idempotency guard on `TransferInitiated` consumer
- ✅ `FraudController` — ADMIN-only paginated decision history endpoint
- ✅ Flyway V1 migration: `fraud_rule`, `fraud_decision`, `fraud_processed_events`, `fraud_outbox_events`
- ✅ Default seeded rules (4 rules: tx_count_60s, tx_volume_1h, tx_volume_24h, auth_fail_15m)

```
com.fintechwave.fraud/
  ├── api/          FraudController
  ├── service/      IFraudService, impl/FraudServiceImpl
  ├── domain/entity FraudRule, FraudDecision, ProcessedEvent, OutboxEvent
  ├── domain/enums  FraudDecisionType, RuleAction
  ├── repository/   FraudRuleRepository, FraudDecisionRepository, ...
  ├── messaging/    TransferInitiatedConsumer, FraudOutboxRelay
  └── config/       SecurityConfig
```

#### Notification Service ✅ DONE

Location: `services/notification-service` | Port: 8086 | Database: `fintechwave_notif` (90-day rolling retention)

Channels: Email (SendGrid SMTP), SMS (Twilio — stubbed), Push (FCM/APNs — stubbed)

- ✅ `DomainEventConsumer` — unified consumer for 4 topics: kyc, ledger, tx, fraud.risk-events
- ✅ `NotificationServiceImpl` — idempotent `send()` with channel dispatch, failure tracking
- ✅ `NotificationRetentionSweeper` — daily 02:00 UTC sweep, purges records older than 90 days
- ✅ Idempotency: `idempotency_key` per notification — duplicate events produce one delivery
- ✅ All 11 event types mapped: KYC_VERIFIED, KYC_REJECTED, WALLET_PROVISIONED, TRANSFER_COMPLETED, TRANSFER_FAILED, TRANSACTION_FLAGGED, CASH_IN/OUT/BILL_PAY COMPLETED/FAILED
- ✅ SMS and PUSH stubs with clean adapter injection points for Twilio/FCM
- ✅ Flyway V1 migration: `notification`, `notif_processed_events`

```
com.fintechwave.notification/
  ├── service/      INotificationService, impl/NotificationServiceImpl
  ├── domain/entity Notification, ProcessedEvent
  ├── domain/enums  NotificationChannel, NotificationStatus
  ├── repository/   NotificationRepository, ProcessedEventRepository
  ├── messaging/    DomainEventConsumer, NotificationRetentionSweeper
  └── config/       SecurityConfig
```

#### Reporting Service ✅ DONE

Location: `services/reporting-service` | Port: 8087 | Database: `fintechwave_report`

Event-sourced read models (CQRS projections) — no OLAP, no data warehouse:

| Read Model            | Built From             | Description                       |
| --------------------- | ---------------------- | --------------------------------- |
| `transaction_summary` | All `tx.*` events      | Paginated history per user        |
| `daily_volume`        | Completed events       | Daily aggregate (admin dashboard) |
| `balance_snapshot`    | `LedgerEntryCommitted` | Running balance history           |
| `kyc_status_summary`  | KYC events             | Admin KYC pipeline overview       |
| `failed_tx_rate`      | `*.Failed` events      | Daily failure rate for alerting   |

- ✅ `ReportingEventConsumer` — consumes tx, ledger, kyc topics; builds all 5 projections
- ✅ Daily volume upsert (idempotent via `findByReportDateAndTransactionTypeAndCurrency`)
- ✅ Failure rate calculation (BigDecimal, 4dp precision)
- ✅ KYC status upsert (create on SUBMITTED, update on VERIFIED/REJECTED)
- ✅ `ReportingController` — 5 ADMIN-only paginated endpoints
- ✅ Idempotency guard per consumer
- ✅ Flyway V1 migration: all 5 read model tables + `report_processed_events`

```
com.fintechwave.reporting/
  ├── api/          ReportingController
  ├── domain/entity TransactionSummary, DailyVolume, BalanceSnapshot, KycStatusSummary, FailedTxRate, ProcessedEvent
  ├── repository/   (one repository per entity)
  ├── messaging/    ReportingEventConsumer
  └── config/       SecurityConfig
```

---

### Phase 4 — Production Hardening & CQRS Migration 🔲

- ✅ **Debezium CDC** replaces `@Scheduled` Outbox relay (Kafka Connect connectors registered for ledger, fraud, kyc, tx, and users)
- ✅ **Distributed CQRS Architecture** across core services (using MongoDB `fintechwave_views` database for view projections)
- ✅ **Elasticsearch** integrated into `reporting-service` for advanced fast search indexing and querying of transactions/users
- ✅ **Java 21 Virtual Threads Migration** (platform-wide enabled, custom gRPC/Kafka listener executors)
- ✅ **Distributed Scheduling with ShedLock** (using RedisLockProvider for Outbox/Notification cleanups)
- ✅ **Kafka Dead-Letter Queue (DLQ) & Resiliency** (exponential backoff, poison-pill protection, DLT compensation flows)
- ✅ **Centralized Context Propagation** (traceId, spanId, userId/keycloakId context propagation using MDC in `BusinessContextMdc`)
- ✅ **Unified Observability Stack** (integrated Loki, Tempo, Prometheus, Alertmanager, Otel-Collector, and Grafana in Docker Compose)
- Kubernetes manifests (`infra/`) — Deployments, Services, HPAs, Secrets
- KEDA autoscaling (CPU, Kafka consumer lag, request rate triggers)
- Pact contract tests for all Feign clients
- Gatling load tests for Ledger TPS target
- HashiCorp Vault for secrets management
- Istio mTLS between services

#### Phase 4 Design & Architectural Decisions

##### 1. Event-Driven Outbox Relay with Debezium CDC
To prevent data inconsistency and remove database polling overhead, the custom scheduled `OutboxRelay` tasks were replaced by Debezium CDC. 
- **Mechanism**: Every business state mutation writes an outbox event in the same ACID transaction to PostgreSQL. Debezium reads the database write-ahead log (WAL) and streams these events in real-time to corresponding Kafka topics.
- **Infrastructure**: Configured Kafka Connect (`debezium/connect:2.7.3.Final`) running at port 8088 and registered separate outbox connectors for each microservice.
- **Outbox Cleanups**: Scheduled cron jobs (`OutboxCleanupJob`) run periodically to prune successfully processed outbox records, guarded by **ShedLock** (`RedisLockProvider`) to prevent concurrent executions across microservice replicas.

##### 2. Distributed CQRS Projections (MongoDB & Elasticsearch)
To decouple write-heavy transaction/financial engines from read-heavy queries, read models were completely separated:
- **MongoDB Views**: Real-time read views are built by listening to domain events. Views like `UserProfileView`, `KycApplicationView`, `WalletSummaryView`, `TransactionHistoryView`, and `FraudRiskProfileView` are updated in the MongoDB database (`fintechwave_views` collection).
- **Elasticsearch Search Projections**: `reporting-service` acts as an OLAP read model using Elasticsearch to index `TransactionDocument` and `UserDocument`. The indexes are updated via `SearchIndexingService` as events arrive, supporting fast pagination and full-text searches.

##### 3. Concurrency Optimization via Java 21 Virtual Threads
The entire platform is migrated to Java 21 Virtual Threads to maximize concurrency and throughput on I/O-bound operations:
- **Global Config**: Virtual threads are enabled via `spring.threads.virtual.enabled: true`.
- **gRPC Integration**: The ledger service utilizes a custom gRPC server executor configured with `Executors.newVirtualThreadPerTaskExecutor()`.
- **Kafka Listener Concurrency**: Kafka message listeners use `SimpleAsyncTaskExecutor` with `virtualThreads = true` to process events concurrently without pinning system threads.

##### 4. DLQ Resiliency & Compensating Transactions
Distributed events are secured with robust error recovery patterns:
- **Retry Strategy**: Default error handlers utilize exponential backoff (starting at 1s, multiplier 2.0, max 3 attempts) for transient errors.
- **Poison-Pill Bypass**: Serialization exceptions (`JsonParseException`) and bad arguments (`IllegalArgumentException`) bypass retries and are sent directly to DLQ topics (e.g. `tx.transaction-events.DLT`).
- **DLT Compensation Flows**: A `DltCompensationConsumer` listens to DLT events to initiate automated compensating actions (such as card refunds on failed ledger credits or rollback notifications).

##### 5. Structured Tracing & Centralized Context Propagation
- **MDC Context Propagation**: Implemented `BusinessContextMdc` to capture and log key contextual parameters (`traceId`, `spanId`, `user_id`, `transaction_id`, `event_type`).
- **Distributed Logging**: MDC values are automatically enriched in logging formats using `logstash-logback-encoder` across all microservices.
- **Observability Pipeline**: Trace, log, and metric streams are routed through the OpenTelemetry Collector to Loki, Tempo, and Prometheus, visualizing end-to-end request flows in Grafana.
- **Alerting Rules**: Defined business alerts in `alerts.yml` for SLA tracking, covering high transaction failure rates (>1%), virtual thread pinning (`tracePinnedThreads`), and critical DLQ volumes.

---

## 11. Infrastructure Map

### Port Registry

| Service              | Port (host)         | Notes                               |
| -------------------- | ------------------- | ----------------------------------- |
| Keycloak             | 8180 → 8080         | KRaft mode, no Zookeeper dependency |
| Config Server        | 8888                |                                     |
| API Gateway          | 8080                |                                     |
| user-service         | 8081                | Uses MongoDB/Redis for read-models  |
| kyc-service          | 8082                | MinIO bucket: `kyc-documents`       |
| ledger-service       | 8083                | Double-entry core                   |
| transaction-service  | 8084                | Stripe adapter, webhook receiver    |
| fraud-service        | 8085                | Phase 4 — Redis velocity checks     |
| notification-service | 8086                | Phase 4 — SendGrid/Twilio/FCM       |
| reporting-service    | 8087                | Phase 4 — Elasticsearch reporting   |
| PostgreSQL           | 5432                | Primary ACID Database               |
| Redis                | 6379                | Caching / Fraud sliding windows     |
| Kafka                | 29092 (host) / 9092 | KRaft mode (no Zookeeper)           |
| Kafka Connect        | 8088                | Debezium CDC for outbox             |
| MongoDB              | 27017               | CQRS Read Models (`views`)          |
| Elasticsearch        | 9200                | Reporting read-models               |
| MinIO API            | 9000                |                                     |
| MinIO Console        | 9001                |                                     |
| Grafana Loki         | 3100                | Observability: log aggregation      |
| Grafana Tempo        | 3200                | Observability: distributed tracing  |
| Prometheus           | 9090                | Observability: system metrics       |
| Alertmanager         | 9093                | Observability: system alerting      |
| OTel Collector       | 4317 / 4318         | Observability: OTLP gRPC / HTTP     |
| Grafana Dashboard    | 3000                | Observability: visualization UI     |

### Database Registry

| Service              | Database             | Init Script                                    |
| -------------------- | -------------------- | ---------------------------------------------- |
| Keycloak             | `keycloak_db`        | `01-create-databases.sh`                       |
| user-service         | `fintechwave_users`  | Flyway V1 (`V1__add_user_profiles_and_outbox`) |
| kyc-service          | `fintechwave_kyc`    | Flyway V1 (`kyc_applications` …)               |
| ledger-service       | `fintechwave_ledger` | Flyway V1 (`ledger_account` …)                 |
| transaction-service  | `fintechwave_tx`     | Flyway V1 (`transactions` …)                   |
| fraud-service        | `fintechwave_fraud`  | Flyway V1 (`fraud_rule` …)                     |
| notification-service | `fintechwave_notif`  | Flyway V1 (`notification` …)                   |
| reporting-service    | `elasticsearch`      | Indexing read-models (Transactions/Users)      |
| CQRS Read Models     | `fintechwave_views`  | MongoDB Database                               |

---

## 12. Engineering Rules (Non-Negotiable)

These rules apply to every line of code in this repository.
Full reference: `.agents/skills/code-standards.md`

### Forbidden Patterns

| Pattern                                   | Consequence                                                  |
| ----------------------------------------- | ------------------------------------------------------------ |
| Kafka publish without Outbox              | DB success + event failure = permanent inconsistency (SEV-1) |
| Redis as balance source of truth          | Redis eviction = silent financial corruption                 |
| Non-idempotent Kafka consumer             | Duplicate event = duplicate financial execution              |
| `Optional.get()` without `.orElseThrow()` | Silent NPE in production                                     |
| H2 in integration tests                   | Behavioral differences cause undetected ledger bugs          |
| `@Autowired` field injection              | Hidden dependency, untestable                                |
| Entity in controller signature            | Domain leak, tight coupling                                  |
| PII or JWT in any log line                | Regulatory violation                                         |
| Hardcoded secrets                         | Security incident                                            |
| Business RBAC in Keycloak                 | Authorization chaos — keep in domain services                |
| Financial logic in notification/reporting | Wrong bounded context                                        |

### Code Review Gate (Mandatory Before Every Merge)

- [ ] No entity in controller method signature
- [ ] No repository called from controller directly
- [ ] All write methods annotated `@Transactional`
- [ ] All responses wrapped in `ApiResponse<T>`
- [ ] Constructor injection only (`@RequiredArgsConstructor`)
- [ ] No hardcoded credentials or secrets
- [ ] All relationships `FetchType.LAZY`
- [ ] No `Optional.get()` without `.orElseThrow()`
- [ ] No empty catch blocks
- [ ] No PII or JWT in logs
- [ ] Structured log parameters (not string concatenation)
- [ ] Unit test for every public service method
- [ ] All custom exceptions extend `BaseServiceException`

---

## 13. Open Items (Deferred — Not Forgotten)

| Item                                 | Decision                                                                       | Revisit |
| ------------------------------------ | ------------------------------------------------------------------------------ | ------- |
| Settlement domain                    | Deferred — depends on external gateway model                                   | Phase 4 |
| Merchant Pay                         | Deferred                                                                       | Future  |
| QR Payment                           | Deferred                                                                       | Future  |
| Debezium CDC                         | ✅ **Done in Phase 4** — replaced `@Scheduled` Outbox relay with Kafka Connect | —       |
| Tap Payments full integration        | `PaymentGatewayPort` adapter scaffolded; full impl when Stripe payout gaps hit | Phase 3 |
| Kubernetes Secrets + HashiCorp Vault | Phase 4                                                                        | Phase 4 |
| KYC AES-256 PII encryption           | Documents in MinIO (UUID keys); field-level encryption deferred to Phase 4     | Phase 4 |
| Bill Pay aggregator integration      | `BILL_PAY` transaction type defined; aggregator adapter not yet implemented    | Phase 3 |
| Direct biller integration            | **Permanently out of scope**                                                   | Never   |
| Agent channel                        | **Permanently out of scope**                                                   | Never   |

---

_Read this before every session. Update this when decisions change. Never start implementation without confirming your service is in the current phase._

---

## 14. Build Status (Last Updated: 2026-06-26)

```
mvn clean install -DskipTests  →  BUILD SUCCESS

fintechwave-bom                ✅
fintechwave (root aggregator)  ✅
fintechwave-core               ✅
fintechwave-security-starter   ✅
fintechwave-events-starter     ✅
fintechwave-payment-gateway    ✅
fintechwave-grpc-contracts     ✅  Phase 4
fintechwave-gateway            ✅
fintechwave-config-server      ✅
user-service                   ✅
transaction-service            ✅
kyc-service                    ✅
ledger-service                 ✅
fraud-service                  ✅  Phase 4 Complete
notification-service           ✅  Phase 4 Complete
reporting-service              ✅  Phase 4 Complete

Total modules: 16  |  Failures: 0  |  Errors: 0
```

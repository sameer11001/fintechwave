# FintechWave — E2E Testing Lifecycle

This document provides the complete API lifecycle testing guide for Phase 1 and Phase 2. You can import these calls into Postman or run them via `curl`. 

Ensure all containers (`docker-compose up -d`) and Spring Boot services (`config-server`, `gateway`, `user-service`, `kyc-service`, `ledger-service`, `transaction-service`) are running.

---

## 1. Authentication Lifecycle (Keycloak)

Before making requests to the microservices, you need to acquire JSON Web Tokens (JWT) from Keycloak.

### 1.1 Get Standard User Token (e.g., Alice)
```bash
curl -X POST http://localhost:8180/realms/fintechwave/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=fintechwave-platform" \
  -d "username=alice" \
  -d "password=password" \
  -d "grant_type=password"
```
*Extract the `access_token` from the response. This will be your `$USER_TOKEN`.*

### 1.2 Get Admin Token
```bash
curl -X POST http://localhost:8180/realms/fintechwave/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=fintechwave-platform" \
  -d "username=admin" \
  -d "password=password" \
  -d "grant_type=password"
```
*Extract the `access_token`. This will be your `$ADMIN_TOKEN`.*

---

## 2. KYC & Wallet Provisioning Lifecycle

A user cannot transact until their KYC is submitted and an Admin approves it. The approval triggers the Ledger Service to create a wallet.

### 2.1 Submit KYC Application (User)
The user submits a request for a specific tier.

```bash
curl -X POST http://localhost:8082/api/v1/kyc/submit \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "requestedTier": "TIER_1"
  }'
```
*Take note of the `"id"` in the response. This is the `$APPLICATION_ID`.*

### 2.2 View KYC Status (User)
```bash
curl -X GET http://localhost:8082/api/v1/kyc/me \
  -H "Authorization: Bearer $USER_TOKEN"
```

### 2.3 Review & Approve KYC (Admin) — 🚨 COMPLIANCE GATE
The admin approves the application. 
**Background action:** The KYC service publishes a `KYCVerified` Kafka event. The Ledger service consumes this and creates the User Wallet in PostgreSQL.

```bash
curl -X POST http://localhost:8082/api/v1/admin/kyc/applications/$APPLICATION_ID/review \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "APPROVED",
    "rejectionReason": null
  }'
```

---

## 3. Transaction Lifecycle

Once the wallet is created, the user can perform transactions. All transactions go through the State Machine (`INITIATED` -> `RESERVED` -> `COMMITTED`).

### 3.1 Cash-In via Stripe (User)
Adds funds to the user's wallet via a card.

```bash
curl -X POST http://localhost:8084/api/v1/transactions/cash-in \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 500.00,
    "currency": "USD",
    "stripePaymentMethodId": "pm_card_visa",
    "idempotencyKey": "4c942e65-27a9-467a-8d14-368688461ab7"
  }'
```
*In a real flow, Stripe returns a webhook (`payment_intent.succeeded`) to `/api/v1/webhooks/stripe` which completes the flow.*

### 3.2 Peer-to-Peer (P2P) Transfer (User)
Transfers money from the user's wallet to another user's wallet.
*(Requires obtaining a second user's Keycloak UUID, e.g., Bob's ID).*

```bash
curl -X POST http://localhost:8084/api/v1/transactions/p2p \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "receiverId": "INSERT_BOB_UUID_HERE",
    "amount": 150.00,
    "currency": "USD",
    "description": "Dinner split",
    "idempotencyKey": "9d903e00-df33-47d0-a08b-967a544a42cd"
  }'
```
*The ledger instantly `RESERVES` the funds, takes the fee, and then `COMMITS` the funds to the receiver.*

### 3.3 Cash-Out via Stripe Instant Payouts (User)
Withdraws funds from the wallet to the user's card.

```bash
curl -X POST http://localhost:8084/api/v1/transactions/cash-out \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50.00,
    "currency": "USD",
    "stripePaymentMethodId": "pm_card_visa",
    "idempotencyKey": "c3b7b2ca-1175-470a-af27-8ccb9eb9d592"
  }'
```

### 3.4 View Transaction History (User)
```bash
curl -X GET "http://localhost:8084/api/v1/transactions?page=0&size=20" \
  -H "Authorization: Bearer $USER_TOKEN"
```

---

## 4. Ledger Verification (Admin)

### 4.1 Run Global Reconciliation (Admin)
Ensures double-entry integrity (Total Assets == Total Liabilities + Equity).

```bash
curl -X GET http://localhost:8083/api/v1/admin/ledger/reconcile \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## Important Testing Notes
1. **Idempotency Keys:** Ensure you generate a new UUID for the `idempotencyKey` field for every new transaction or cash-in request. If you reuse a key, the system will reject it with a `409 Conflict`.
2. **Ports Mapping:** 
   - Gateway: 8080
   - User: 8081
   - KYC: 8082
   - Ledger: 8083
   - Transaction: 8084
   - Keycloak: 8180 (mapped from 8080)

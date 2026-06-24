#!/bin/bash
CONNECT_URL="http://kafka-connect:8083/connectors"

echo "Waiting for Kafka Connect to start listening on $CONNECT_URL ⏳"
while ! curl -s "$CONNECT_URL" > /dev/null; do 
  sleep 2
done

echo "Kafka Connect is up! Registering connectors... 🚀"

curl -sf -X POST "$CONNECT_URL" -H "Content-Type: application/json" -d @/debezium/outbox-connector-users.json
curl -sf -X POST "$CONNECT_URL" -H "Content-Type: application/json" -d @/debezium/outbox-connector-kyc.json
curl -sf -X POST "$CONNECT_URL" -H "Content-Type: application/json" -d @/debezium/outbox-connector-tx.json
curl -sf -X POST "$CONNECT_URL" -H "Content-Type: application/json" -d @/debezium/outbox-connector-fraud.json
curl -sf -X POST "$CONNECT_URL" -H "Content-Type: application/json" -d @/debezium/outbox-connector-ledger.json

echo "Debezium Connectors Registered! ✅"

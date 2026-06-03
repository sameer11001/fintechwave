#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE keycloak_db;
    CREATE DATABASE fintechwave_iam;
    CREATE DATABASE fintechwave_kyc;
    CREATE DATABASE fintechwave_ledger;
    CREATE DATABASE fintechwave_tx;
EOSQL

echo "All FintechWave databases created."

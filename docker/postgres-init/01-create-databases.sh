#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE fintechwave_users;
    CREATE DATABASE fintechwave_kyc;
    CREATE DATABASE fintechwave_ledger;
    CREATE DATABASE fintechwave_tx;
    CREATE DATABASE fintechwave_fraud;
    CREATE DATABASE fintechwave_notif;
    CREATE DATABASE fintechwave_report;
EOSQL

echo "All FintechWave databases created."

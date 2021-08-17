#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER james;
    ALTER USER james WITH PASSWORD 'ZXBiaye001';
	CREATE DATABASE james;
    GRANT ALL PRIVILEGES ON DATABASE james TO james;
EOSQL

#!/usr/bin/env bash
# Apply all migrations to a scratch PostgreSQL database and run backend tests.
# Usage: PGHOST=... PGPORT=... PGUSER=postgres ./supabase/tests/run_local.sh
set -euo pipefail
cd "$(dirname "$0")/.."
DB=${TEST_DB:-swad_sangam_test}
psql -v ON_ERROR_STOP=1 -q -d postgres -c "drop database if exists $DB" -c "create database $DB"
psql -v ON_ERROR_STOP=1 -q -d "$DB" -f tests/supabase_stub.sql
for f in migrations/*.sql; do psql -v ON_ERROR_STOP=1 -q -d "$DB" -f "$f"; done
psql -v ON_ERROR_STOP=1 -q -d "$DB" -f tests/backend_tests.sql

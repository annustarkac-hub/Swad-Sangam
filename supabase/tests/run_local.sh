#!/usr/bin/env bash
# Applies all migrations to scratch PostgreSQL databases and runs the backend tests twice:
#  1. "default" — API roles get table privileges by default (older Supabase projects)
#  2. "strict"  — no default privileges (newer Supabase projects)
# Usage: PGHOST=... PGPORT=... PGUSER=postgres ./supabase/tests/run_local.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export TMPDIR="${TMPDIR:-/tmp}"
PSQL=(psql -v ON_ERROR_STOP=1 -q)
for mode in default strict; do
  DB="swad_sangam_test_$mode"
  echo "== $mode privileges"
  "${PSQL[@]}" -d postgres -c "drop database if exists $DB" -c "create database $DB"
  if [ "$mode" = strict ]; then
    sed '/alter default privileges/d' tests/supabase_stub.sql | "${PSQL[@]}" -d "$DB"
  else
    "${PSQL[@]}" -d "$DB" -f tests/supabase_stub.sql
  fi
  for f in migrations/*.sql; do
    "${PSQL[@]}" -d "$DB" -f "$f" 2>"$TMPDIR/$DB.err" || { cat "$TMPDIR/$DB.err"; exit 1; }
  done
  rm -f "$TMPDIR/$DB.err"
  for t in backend_tests erp_tests pos_upgrade_tests customer_tests delivery_tests state_machine_tests; do
    "${PSQL[@]}" -d "$DB" -f "tests/$t.sql" > "$TMPDIR/$DB.out" 2>&1 || { grep -E "ERROR|FAILED|CONTEXT" "$TMPDIR/$DB.out"; exit 1; }
    grep -E "PASSED" "$TMPDIR/$DB.out"
  done
done

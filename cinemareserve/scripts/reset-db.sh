#!/usr/bin/env bash
# Deletes the local database file so the next run starts from a clean schema.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -f data/cinemareserve.db data/cinemareserve.db-shm data/cinemareserve.db-wal
echo "Database reset. Run scripts/seed.sh to reload sample data."

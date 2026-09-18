#!/usr/bin/env bash
# Loads development seed data (admin account, demo user, sample catalog).
# Safe to re-run: it detects existing seed data and skips instead of duplicating.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/_env.sh
java -cp "target/classes${CP_SEP}lib/*${CP_SEP}src/main/resources" com.cinemareserve.cli.SeedData

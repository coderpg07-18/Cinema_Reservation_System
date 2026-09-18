#!/usr/bin/env bash
# Runs the CLI application. Assumes build.sh has already been run.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/_env.sh
java -cp "target/classes${CP_SEP}lib/*${CP_SEP}src/main/resources" com.cinemareserve.cli.Main

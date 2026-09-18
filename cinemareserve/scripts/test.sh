#!/usr/bin/env bash
# Runs the full JUnit 5 test suite and writes a copy of the results to test_run.txt.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/_env.sh
java -cp "target/classes${CP_SEP}target/test-classes${CP_SEP}lib/*${CP_SEP}src/main/resources" \
  org.junit.platform.console.ConsoleLauncher \
  --scan-classpath target/test-classes \
  --disable-banner --disable-ansi-colors --details=tree | tee test_run.txt

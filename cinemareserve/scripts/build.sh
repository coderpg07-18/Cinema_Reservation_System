#!/usr/bin/env bash
# Compiles the application and its tests. No internet access is required --
# every dependency ships as a jar under lib/.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/_env.sh

NATIVE_ENTRY_COUNT=$(unzip -l lib/sqlite-jdbc.jar 2>/dev/null | grep -c "org/sqlite/native/" || true)
if [ "${NATIVE_ENTRY_COUNT:-0}" -eq 0 ]; then
    echo "WARNING: lib/sqlite-jdbc.jar does not look like the official multi-platform" >&2
    echo "         Xerial build (no org/sqlite/native/... entries found). The app may" >&2
    echo "         fail with NativeLibraryNotFoundException on this OS. See the" >&2
    echo "         'lib/sqlite-jdbc.jar' note in README.md if you hit that error." >&2
fi

echo "== Compiling main sources =="
rm -rf target/classes
mkdir -p target/classes
javac -cp "lib/*" -d target/classes $(find src/main/java -name "*.java")

echo "== Compiling test sources =="
rm -rf target/test-classes
mkdir -p target/test-classes
javac -cp "lib/*${CP_SEP}target/classes" -d target/test-classes $(find src/test/java -name "*.java")

echo "Build succeeded."

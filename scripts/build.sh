#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/out"
mkdir -p "$ROOT/out/main" "$ROOT/out/test"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 21 -d "$ROOT/out/main"
find "$ROOT/src/test/java" -name '*.java' -print0 | xargs -0 javac --release 21 -cp "$ROOT/out/main" -d "$ROOT/out/test"
jar --create --file "$ROOT/out/schemacsv-0.3.0.jar" --main-class io.schemacsv.cli.Main -C "$ROOT/out/main" .
echo "Built out/schemacsv-0.3.0.jar"

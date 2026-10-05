#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh" >/dev/null
java -Dschemacsv.root="$ROOT" -cp "$ROOT/out/main:$ROOT/out/test" io.schemacsv.TestRunner

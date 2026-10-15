#!/usr/bin/env bash
# Local CI: format check, static analysis, unit tests, coverage rules, lint, offline-manifest guard.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon assembleDebug check "$@"

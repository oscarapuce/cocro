#!/usr/bin/env bash
# Wrapper to run Gradle commands for cocro-bff from anywhere
# Usage: ./scripts/bff-gradle.sh build
#        ./scripts/bff-gradle.sh test
#        ./scripts/bff-gradle.sh bootRun

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BFF_DIR="$SCRIPT_DIR/../cocro-bff"

exec "$BFF_DIR/gradlew" -p "$BFF_DIR" "$@"


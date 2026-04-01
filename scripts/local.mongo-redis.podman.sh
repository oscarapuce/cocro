#!/usr/bin/env bash
set -e

# Resolve project root (parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE="$PROJECT_ROOT/infra/compose.dev.yml"
ENV_FILE="$PROJECT_ROOT/.env.dev"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ .env.dev file not found at $ENV_FILE"
  exit 1
fi

cleanup() {
  podman compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down
}

trap cleanup EXIT INT TERM

podman compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
podman compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs -f

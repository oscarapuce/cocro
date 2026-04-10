#!/usr/bin/env bash

# Resolve project root (parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE="$PROJECT_ROOT/infra/compose.dev.yml"
ENV_FILE="$PROJECT_ROOT/.env.dev"

if [ ! -f "$ENV_FILE" ]; then
  echo "!! .env.dev file not found at $ENV_FILE"
  exit 1
fi

cleanup() {
  echo ""
  echo "⏹ Stopping containers..."
  podman compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down 2>/dev/null
  echo "✅ Containers stopped."
}

trap cleanup EXIT

echo "▶️ Starting Mongo + Redis..."
# Foreground mode: logs stream to stdout, IntelliJ stop button (SIGTERM) triggers cleanup
podman compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up --abort-on-container-exit

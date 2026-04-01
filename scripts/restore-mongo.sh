#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKUP_DIR="$PROJECT_ROOT/backups/mongo"

# Find the latest backup or use argument
if [ -n "$1" ]; then
  ARCHIVE="$1"
else
  ARCHIVE=$(find "$BACKUP_DIR" -name "cocro.archive" -type f | sort -r | head -1)
fi

if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  echo "❌ No backup found. Usage: $0 [path/to/cocro.archive]"
  exit 1
fi

echo "♻️  Restoring MongoDB from $ARCHIVE"
podman exec -i infra-mongo-1 mongorestore \
  --username admin \
  --password admin \
  --authenticationDatabase admin \
  --db cocro \
  --drop \
  --archive \
  < "$ARCHIVE"

echo "✅ Restore complete"


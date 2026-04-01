#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKUP_DIR="$PROJECT_ROOT/backups/mongo"
TIMESTAMP=$(date +%Y-%m-%d_%H%M%S)
BACKUP_PATH="$BACKUP_DIR/$TIMESTAMP"

mkdir -p "$BACKUP_PATH"

echo "📦 Dumping MongoDB → $BACKUP_PATH"
podman exec infra-mongo-1 mongodump \
  --username admin \
  --password admin \
  --authenticationDatabase admin \
  --db cocro \
  --archive \
  > "$BACKUP_PATH/cocro.archive"

echo "✅ Backup done: $(du -h "$BACKUP_PATH/cocro.archive" | cut -f1)"
echo "   📁 $BACKUP_PATH/cocro.archive"


#!/bin/sh
# Entry point of the pg-backup service: a backup at start and then every BACKUP_INTERVAL_HOURS.
set -eu
BACKUP_INTERVAL_HOURS=${BACKUP_INTERVAL_HOURS:-24}
mkdir -p "${BACKUP_DIR:-/backups}"
until pg_isready -q; do sleep 2; done

while true; do
  sh /scripts/backup.sh || echo "backup: FAILED (will retry next cycle)" >&2
  sleep $((BACKUP_INTERVAL_HOURS * 3600))
done

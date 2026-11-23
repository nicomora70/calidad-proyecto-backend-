#!/bin/sh
# One backup now: custom-format pg_dump (compressed), then prune files older than the retention.
# Connection comes from PGHOST, PGUSER, PGPASSWORD and PGDATABASE.
set -eu
BACKUP_DIR=${BACKUP_DIR:-/backups}
BACKUP_RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-14}

ts=$(date -u +%Y%m%dT%H%M%SZ)
file="$BACKUP_DIR/ritech-$ts.dump"
# Write to a temp name first: a half-written dump never looks like a valid backup
pg_dump --format=custom --no-owner --file="$file.partial"
mv "$file.partial" "$file"
echo "backup: $file ($(du -h "$file" | cut -f1))"

find "$BACKUP_DIR" -name 'ritech-*.dump' -type f -mtime +"$BACKUP_RETENTION_DAYS" -print -delete \
  | sed 's/^/backup: pruned /'

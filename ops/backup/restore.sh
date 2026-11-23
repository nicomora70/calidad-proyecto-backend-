#!/bin/sh
# Restores a dump into <target-db>, creating it if needed.
#   sh /scripts/restore.sh /backups/ritech-<ts>.dump ritech_restore_check      (test restore)
#   sh /scripts/restore.sh /backups/ritech-<ts>.dump ritech --yes              (live database)
# Restoring over the live database requires --yes and the api stopped (docker compose stop api).
set -eu
dump=${1:?usage: restore.sh <file.dump> <target-db> [--yes]}
target=${2:?usage: restore.sh <file.dump> <target-db> [--yes]}

case "$target" in
  ""|*[!a-zA-Z0-9_]*) echo "restore: invalid database name '$target'" >&2; exit 1 ;;
esac
[ -f "$dump" ] || { echo "restore: $dump not found" >&2; exit 1; }
if [ "$target" = "${PGDATABASE:-}" ] && [ "${3:-}" != "--yes" ]; then
  echo "restore: refusing to overwrite the live database '$target' without --yes (stop the api first)" >&2
  exit 1
fi

if ! psql -d postgres -tAc "select 1 from pg_database where datname = '$target'" | grep -q 1; then
  createdb "$target"
fi
pg_restore --clean --if-exists --no-owner --exit-on-error --dbname="$target" "$dump"
echo "restore: $dump -> $target"

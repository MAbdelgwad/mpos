#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v flock >/dev/null || { echo 'flock is required (util-linux).'; exit 1; }
exec 9>.units-upgrade.lock
flock -n 9 || { echo 'Another unit upgrade is running.'; exit 1; }
compose=(docker compose -p mpos-test-architecture -f compose.yaml)
python3 scripts/setup.py
"${compose[@]}" build api web
"${compose[@]}" stop web api
umask 077
mkdir -p backups
backup="backups/before-communications-$(date +%Y%m%d-%H%M%S)-$$.dump"
cp .env "${backup%.dump}.env"
if ! "${compose[@]}" exec -T db pg_dump -U mpos -d mpos -Fc > "$backup"; then
  echo 'Backup failed; migration was not started. Restarting existing containers.'
  "${compose[@]}" start api web
  exit 1
fi
if ! "${compose[@]}" exec -T db pg_restore --list < "$backup" >/dev/null; then
  echo 'Backup validation failed; migration was not started.'
  "${compose[@]}" start api web
  exit 1
fi
echo "Database backup: $backup"
"${compose[@]}" up -d --no-build
python3 scripts/verify.py --wait
printf '%s\n' 'Currency, email and invoice sharing upgrade completed. Reload your browser with Ctrl+Shift+R.'

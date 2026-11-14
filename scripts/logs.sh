#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
service="${1:-all}"
case "$service" in
  all) services=(api web db) ;;
  api|web|db) services=("$service") ;;
  *) echo 'Usage: bash scripts/logs.sh [api|web|db|all]'; exit 2 ;;
esac
docker compose --project-name mpos-test-architecture --project-directory . -f compose.yaml logs --no-color --since 30m --tail=200 -f "${services[@]}"

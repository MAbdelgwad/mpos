#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v python3 >/dev/null || { echo 'Install python3 first.'; exit 1; }
command -v docker >/dev/null || { echo 'Docker is required.'; exit 1; }
docker compose version >/dev/null
docker info >/dev/null || { echo 'Docker is not running or your user cannot access it.'; exit 1; }
python3 scripts/setup.py
docker compose --project-name mpos-test-architecture --project-directory . -f compose.yaml up --build -d
if ! python3 scripts/verify.py --wait; then
  docker compose --project-name mpos-test-architecture --project-directory . -f compose.yaml logs --tail=80
  exit 1
fi
python3 scripts/setup.py --show-login

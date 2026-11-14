#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
docker compose --project-name mpos-test-architecture --project-directory . -f compose.yaml stop
echo 'Stopped. Database retained. Run bash scripts/start.sh to resume.'

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
docker compose --project-name mpos-test-architecture --project-directory . -f compose.yaml logs --tail=100 -f

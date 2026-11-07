#!/bin/bash
# Pulls your country's catalogue, adds ratings and trailers, sends it to your engine, then builds the first rows.
# Resumable. It uses your Movie of the Night quota, and stops at 420 requests unless you pass a different number.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
cd worker/catalog
python3 pull.py "${1:-420}"
python3 enrich.py
python3 ingest.py "$WORKER_URL"
post() { curl -fsS -X "$1" "$WORKER_URL$2" -H "authorization: Bearer $APP_TOKEN" >/dev/null; }
post POST /admin/sync; post POST /admin/world
for _ in 1 2 3 4 5; do post POST /admin/blurbs; done
post GET /admin/pool
echo "Catalogue is in, and your first rows are ready."

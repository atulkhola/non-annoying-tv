#!/bin/bash
# Sends taste.md to your engine. Run it whenever you edit that file.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
[ -f taste.md ] || { echo "No taste.md yet. Run: cp taste.example.md taste.md, then write yours."; exit 1; }
python3 -c "import json; print(json.dumps({'taste': open('taste.md').read()}))" | curl -fsS -X POST "$WORKER_URL/admin/taste" -H "authorization: Bearer $APP_TOKEN" -H 'content-type: application/json' --data-binary @- >/dev/null
echo "Taste updated."

#!/bin/bash
# The slot for a memory source. Each call adds one line about what you've been saying, and the newest 60 are kept.
# Example: scripts/add_note.sh "A friend recommended Dark and we both sounded keen."
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
python3 -c "import json,sys; print(json.dumps({'note': sys.argv[1]}))" "$1" | curl -fsS -X POST "$WORKER_URL/admin/note" -H "authorization: Bearer $APP_TOKEN" -H 'content-type: application/json' --data-binary @- >/dev/null
echo "Noted."

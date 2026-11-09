#!/bin/bash
# Creates everything on your own Cloudflare account and deploys the engine. Safe to run again.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "No .env yet. Run: cp .env.example .env, then fill it in."; exit 1; }
set -a; . ./.env; set +a
: "${ANTHROPIC_API_KEY:?Add ANTHROPIC_API_KEY to .env}" "${MOTN_KEY:?Add MOTN_KEY to .env}"
setvar() { python3 - "$1" "$2" <<'PY'
import re,sys
k,v=sys.argv[1:]; s=open('.env').read()
s=re.sub(rf'^{k}=.*$',f'{k}={v}',s,flags=re.M) if re.search(rf'^{k}=',s,re.M) else s+f'\n{k}={v}\n'
open('.env','w').write(s)
PY
}
if [ -z "${APP_TOKEN:-}" ]; then APP_TOKEN=$(openssl rand -hex 24); setvar APP_TOKEN "$APP_TOKEN"; fi

cd worker
npm install --silent
npx wrangler whoami 2>&1 | grep -q 'You are logged in' || npx wrangler login

echo "Database"
d1id() { npx wrangler d1 list --json | python3 -c "import json,sys; print(next((d['uuid'] for d in json.load(sys.stdin) if d['name']=='non_annoying_tv'),''))"; }
ID=$(d1id); if [ -z "$ID" ]; then npx wrangler d1 create non_annoying_tv >/dev/null; ID=$(d1id); fi
[ -n "$ID" ] || { echo "Couldn't create the database."; exit 1; }
python3 - "$ID" <<'PY'
import os,sys
s=open('wrangler.template.jsonc').read().replace('__D1_ID__',sys.argv[1])
for k in ('SERVICES','COUNTRY','CATALOGS'): s=s.replace(f'__{k}__',os.environ[k])
open('wrangler.jsonc','w').write(s)
PY
npx wrangler d1 execute non_annoying_tv --remote --file schema.sql --yes >/dev/null

echo "Search index"
if ! npx wrangler vectorize get non-annoying-tv-titles >/dev/null 2>&1; then
  npx wrangler vectorize create non-annoying-tv-titles --dimensions=384 --metric=cosine >/dev/null
fi
HAVE=$(npx wrangler vectorize list-metadata-index non-annoying-tv-titles 2>/dev/null || true)
index() { echo "$HAVE" | grep -qw "$1" || npx wrangler vectorize create-metadata-index non-annoying-tv-titles --propertyName="$1" --type="$2" >/dev/null; }
index type string; index orig string; index year number; index runtime number
for l in hin eng tam tel mal kan; do index "has_$l" boolean; done

echo "Keys"
printf '%s' "$ANTHROPIC_API_KEY" | npx wrangler secret put ANTHROPIC_API_KEY >/dev/null
printf '%s' "$MOTN_KEY" | npx wrangler secret put MOTN_KEY >/dev/null
printf '%s' "$APP_TOKEN" | npx wrangler secret put APP_TOKEN >/dev/null

echo "Deploy"
URL=$(npx wrangler deploy 2>&1 | grep -oE 'https://[a-zA-Z0-9.-]+\.workers\.dev' | head -1)
[ -n "$URL" ] || { echo "Deploy finished without a URL. Run: cd worker && npx wrangler deploy"; exit 1; }
cd ..; setvar WORKER_URL "$URL"
echo "Engine is live at $URL"

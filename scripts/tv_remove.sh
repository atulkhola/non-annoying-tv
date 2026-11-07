#!/bin/bash
# Takes the app off your TV and gives Google's home back.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; ADB="$SDK/platform-tools/adb"; command -v "$ADB" >/dev/null || ADB=adb
"$ADB" connect "$TV_IP:5555" | tail -1
"$ADB" -s "$TV_IP:5555" uninstall tv.home.app
echo "Removed."

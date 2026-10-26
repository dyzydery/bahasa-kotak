#!/usr/bin/env bash
# Pulls the app's always-fresh export (written after every session) into postepy/ and prints its path.
set -euo pipefail
cd "$(dirname "$0")/../.."
source tools/loop/adb.sh

mkdir -p postepy
out="postepy/export-$(date +%Y%m%d-%H%M).json"
if ! "${ADB[@]}" exec-out run-as "$PKG" cat files/export/latest.json > "$out" || [[ ! -s $out ]]; then
    rm -f "$out"
    echo "ERROR: no export on the phone yet. Open the app once (or finish a session) and retry." >&2
    exit 3
fi
python3 -I - "$out" <<'PY'
import json, sys
e = json.load(open(sys.argv[1]))
first = [a for a in e["answers"] if not a["retry"] and a["exerciseType"] != "placement"]
print(f"{sys.argv[1]}: exported {e['exportedAt']} ({e['timeZone']}), "
      f"{len(e['answers'])} answers, {len(e['sessions'])} sessions, packs "
      + ", ".join(f"{p['packId']} v{p['packVersion']}" for p in e["installedPacks"]))
PY

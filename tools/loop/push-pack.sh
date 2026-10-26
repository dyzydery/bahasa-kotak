#!/usr/bin/env bash
# Drops a pack into the app's inbox over adb and brings the app to the front, which offers it for import.
set -euo pipefail
cd "$(dirname "$0")/../.."
source tools/loop/adb.sh

pack=${1:?usage: push-pack.sh PACK.json}
name=$(basename "$pack")
[[ $name =~ ^[A-Za-z0-9._-]+\.json$ ]] || { echo "ERROR: unexpected file name $name" >&2; exit 1; }

"${ADB[@]}" shell "run-as $PKG sh -c 'umask 077 && mkdir -p files/inbox && cat > files/inbox/$name.part && mv files/inbox/$name.part files/inbox/$name'" < "$pack"
remote=$("${ADB[@]}" exec-out run-as "$PKG" cat "files/inbox/$name" | sha256sum | cut -d' ' -f1)
local=$(sha256sum < "$pack" | cut -d' ' -f1)
[[ $remote == "$local" ]] || { echo "ERROR: checksum mismatch after upload" >&2; exit 4; }

# -S would restart the app and drop an unfinished session, so only bring it forward.
"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Pushed $name to the app inbox; the import dialog is waiting on the phone."

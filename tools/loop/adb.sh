# Shared by the loop scripts: resolves adb and the phone, exits with a clear message if it isn't reachable.
# Source it; it sets ADB (command with -s serial) and PKG.

PKG=io.github.dyzydery.bahasakotak
ADB_BIN=${ADB_BIN:-$HOME/Android/Sdk/platform-tools/adb}

# Paired wireless-debugging phones show up via mDNS; give discovery a moment on a fresh adb server.
"$ADB_BIN" start-server >/dev/null 2>&1
mapfile -t SERIALS < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -n ${ANDROID_SERIAL:-} ]]; then
    SERIAL=$ANDROID_SERIAL
elif (( ${#SERIALS[@]} == 0 )); then
    sleep 3
    mapfile -t SERIALS < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
fi
if [[ -z ${SERIAL:-} ]]; then
    case ${#SERIALS[@]} in
        0) echo "ERROR: no phone connected. Plug in USB or turn on Wireless debugging (same Wi-Fi), then retry." >&2; exit 2 ;;
        # The same phone over USB and Wi-Fi shows up twice; any of its serials works.
        *) SERIAL=${SERIALS[0]} ;;
    esac
fi
ADB=("$ADB_BIN" -s "$SERIAL")

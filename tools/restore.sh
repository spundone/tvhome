#!/bin/bash
. "$(cd "$(dirname "$0")" && pwd)/env.sh"
# Re-enable every package disabled during the TCL debloat.
# Usage: ./restore.sh [adb-serial]        (default: <serial>)
SER=${1:-${TV_SERIAL:?set TV_SERIAL in tools/local.env or pass the serial}}
DIR="$(cd "$(dirname "$0")" && pwd)"
LIST="$DIR/disable-list.txt"
FAIL=0
adb -s "$SER" connect "$SER" 1>/dev/null 2>&1
while IFS= read -r line; do
  PKG=$(echo "$line" | awk '{print $1}')
  case "$PKG" in
    ''|\#*) continue ;;
  esac
  echo -n "enabling $PKG ... "
  if adb -s "$SER" shell pm enable "$PKG" >/dev/null 2>&1; then
    echo "ok"
  else
    adb -s "$SER" shell pm enable "$PKG" 2>&1 | tail -1
    FAIL=$((FAIL+1))
  fi
done < "$LIST"
echo "done (failures: $FAIL)"
echo
echo "Note: this restores the stock launchers, but the default-home preference"
echo "may still point at tvHome. To switch back to the stock Google TV launcher:"
echo "  adb -s $SER shell cmd package set-home-activity \\"
echo "    com.google.android.apps.tv.launcherx/.home.HomeActivity"
echo "(If it errors, open tvHome on the TV first so the resolver sees both, or use"
echo " Settings -> Apps -> Default apps.)"

#!/bin/bash
. "$(cd "$(dirname "$0")" && pwd)/env.sh"
# Wi-Fi hotspot control for the TCL TV, for when the parents need internet
# somewhere without Wi-Fi (phone tether alternative).
#
# Usage: ./hotspot.sh start | stop | status | [adb-serial]
#   ./hotspot.sh start
#   ./hotspot.sh stop <serial>
#
# The hotspot runs alongside the TV's normal Wi-Fi client connection,
# so the TV keeps streaming while sharing its network.
#
# SSID/passphrase come from launcher config.xml (see tools/local.env);
# edit launcher/app/src/main/res/values/config.xml and rebuild to change them.
set -e
SER=${2:-${TV_SERIAL:?set TV_SERIAL in tools/local.env or pass the serial}}
SSID=${TVHOME_HOTSPOT_SSID:-TvHome-Hotspot}
PASS=${TVHOME_HOTSPOT_PASS:?set TVHOME_HOTSPOT_PASS in tools/local.env}

case "$1" in
  start)
    echo "Starting hotspot ($SSID) on $SER ..."
    adb -s "$SER" shell cmd wifi start-softap "$SSID" wpa2 "$PASS" && echo "Hotspot is up: $SSID / $PASS"
    ;;
  stop)
    echo "Stopping hotspot on $SER ..."
    adb -s "$SER" shell cmd wifi stop-softap && echo "Hotspot stopped" || true
    ;;
  status)
    echo "== Wi-Fi / hotspot state on $SER"
    adb -s "$SER" shell cmd wifi status 2>/dev/null || adb -s "$SER" shell dumpsys wifi 2>/dev/null | grep -i -m5 -E 'softap|Wi-Fi is|mWifiState' || true
    ;;
  *)
    echo "Usage: $0 start|stop|status [adb-serial]"
    exit 1
    ;;
esac

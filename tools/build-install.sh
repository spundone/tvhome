#!/bin/bash
. "$(cd "$(dirname "$0")" && pwd)/env.sh"
# Build tvHome.apk and install onto the TV over ADB.
# Usage: ./build-install.sh [adb-serial]
set -e
SER=${1:-${TV_SERIAL:?set TV_SERIAL in tools/local.env or pass the serial}}
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$HOME/Library/Android/sdk"
JDK17="$HOME/Projects/tcl-debloat/tools/jdk17"
PLAT="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/34.0.0"
SRC="$ROOT/launcher/app/src/main"
OUT="$ROOT/launcher/app/build"
mkdir -p "$OUT/gen" "$OUT/classes"

# Private strings (name, wifi, hotspot, weather) come from tools/local.env; the generated
# file is git-ignored so none of it is committed.
esc() { printf '%s' "$1" | sed -e "s/&/\\&amp;/g" -e "s/</\\&lt;/g" -e "s/'/\\\\'/g"; }
cat > "$SRC/res/values/local_secrets.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="greeting_name">$(esc "${TVHOME_GREETING_NAME:-Guest}")</string>
    <string name="wifi_name">$(esc "${TVHOME_WIFI_NAME:-}")</string>
    <string name="hotspot_ssid">$(esc "${TVHOME_HOTSPOT_SSID:-TvHome-Hotspot}")</string>
    <string name="hotspot_pass">$(esc "${TVHOME_HOTSPOT_PASS:-change-me-please}")</string>
    <string name="weather_lat">$(esc "${TVHOME_WEATHER_LAT:-}")</string>
    <string name="weather_lon">$(esc "${TVHOME_WEATHER_LON:-}")</string>
    <string name="weather_city">$(esc "${TVHOME_WEATHER_CITY:-}")</string>
</resources>
XML


export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

echo "== aapt2 compile"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "== aapt2 link"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLAT" \
  --manifest "$SRC/AndroidManifest.xml" \
  -A "$SRC/assets" \
  --min-sdk-version 34 --target-sdk-version 34 \
  --java "$OUT/gen" "$OUT/res.zip"

echo "== javac"
JAVA_SRCS=$(find "$SRC/java" -name '*.java' | sort)
javac -source 17 -target 17 -nowarn \
  -classpath "$PLAT" \
  -d "$OUT/classes" \
  "$OUT/gen/com/spandan/tvhome/R.java" \
  $JAVA_SRCS

echo "== d8"
"$BT/d8" --release --min-api 34 --lib "$PLAT" \
  --output "$OUT" \
  $(find "$OUT/classes" -name '*.class')

echo "== package dex"
cd "$OUT" && zip -q -j base.apk classes.dex && cd - >/dev/null

echo "== zipalign"
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/tvhome-unsigned.apk"

KS="$HOME/.android/tvhome-debug.keystore"
if [ ! -f "$KS" ]; then
  echo "== generating debug keystore"
  mkdir -p "$HOME/.android"
  keytool -genkeypair -v -keystore "$KS" -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass tvhome -keypass tvhome -dname "CN=tvhome" >/dev/null
fi
DATA=`"$BT/zipalign" -c 4 "$OUT/tvhome-unsigned.apk"` && echo "aligned: $DATA"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:tvhome --key-pass pass:tvhome \
  --min-sdk-version 34 --out "$OUT/tvhome.apk" "$OUT/tvhome-unsigned.apk"
echo "== built: $OUT/tvhome.apk"

echo "== installing on $SER"
adb -s "$SER" install -r "$OUT/tvhome.apk" || {
  echo "-- adb install hung/failed; retrying via push + pm install"
  adb -s "$SER" push "$OUT/tvhome.apk" /data/local/tmp/tvhome.apk
  adb -s "$SER" shell pm install -r /data/local/tmp/tvhome.apk
}

# Reinstall clears runtime grants; re-apply the ones the launcher needs.
echo "== re-granting runtime permissions"
for P in android.permission.BLUETOOTH_CONNECT android.permission.BLUETOOTH_SCAN; do
  adb -s "$SER" shell pm grant com.spandan.tvhome $P >/dev/null 2>&1 \
    && echo "  ok: $P" || echo "  FAILED: $P"
done

# Reinstalls can knock the default-home preference off the freshly installed package;
# re-assert it (only works while the stock launchers are disabled, or resolver may prompt).
adb -s "$SER" shell cmd package set-home-activity com.spandan.tvhome/.MainActivity >/dev/null 2>&1 \
  && echo "== default home: com.spandan.tvhome" \
  || echo "== default home: NOT set (run: cmd package set-home-activity com.spandan.tvhome/.MainActivity)"

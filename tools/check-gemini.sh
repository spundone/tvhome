#!/usr/bin/env bash
. "$(cd "$(dirname "$0")" && pwd)/env.sh"
# Prints whether the TV's assistant is running Gemini for TV or the old Google
# Assistant, by reading the eligibility flag katniss logs every time the
# assistant opens.
#
#   tools/check-gemini.sh [serial]
#
set -u
S="${1:-${TV_SERIAL:?set TV_SERIAL in tools/local.env or pass the serial}}"
ADB="adb -s $S"

$ADB shell logcat -c >/dev/null 2>&1
$ADB shell am start -a android.search.action.GLOBAL_SEARCH >/dev/null 2>&1
sleep 4
$ADB shell input keyevent KEYCODE_BACK >/dev/null 2>&1

line="$($ADB shell "logcat -d 2>/dev/null" | grep -m1 'KatnissVoiceInteractionUtilsImpl')"
echo "${line:-no katniss log line - is the assistant installed?}"

case "$line" in
  *"Gemini enabled : true"*)
    echo
    echo "=> Gemini for TV is ON."
    ;;
  *"geminiChatSetupRequired : true"*)
    echo
    echo "=> Gemini is offered but setup is unfinished. Open the deeplink above"
    echo "   (or Settings > Accounts & Profiles > <account> > Voice assistant)."
    ;;
  *"geminiChatDeviceEligible: false"*)
    echo
    echo "=> Google Assistant. Gemini needs this TV inside a Google Home that has"
    echo "   acknowledged Gemini for TV; check the Google Home app on your phone."
    ;;
esac

#!/bin/bash
. "$(cd "$(dirname "$0")" && pwd)/env.sh"
# Fix Google Assistant / Gemini commands on the TV.
#
# Symptom: asking the Home pill (or the remote mic) anything opens the
# "Verify network change - We need to verify your identity to resume using
# home controls" dialog forever, and katniss dies with:
#   ActivityNotFoundException ... com.google.android.apps.tv.launcherx/
#   .networkreauth.NetworkReauthActivity
#
# Cause: the stock Google TV launcher (launcherx) is disabled for debloat, so
# the re-verification activity that katniss wants to show no longer exists.
# Clearing katniss and finishing the one-time setup once makes it stop asking.
#
# Usage: ./fix-assistant.sh [adb-serial]
set -e
S=${1:-${TV_SERIAL:?set TV_SERIAL in tools/local.env or pass the serial}}

echo "== fixing Assistant / Gemini on $S"
echo "== clearing com.google.android.katniss (login + setup state)"
adb -s "$S" shell pm clear com.google.android.katniss
echo

cat <<'EOF'
== one-time setup on the TV, with the remote (required)
   1. Press the Google Assistant / Gemini button (or pick Home in tvHome).
   2. On "Search across all your TV apps" choose  Allow.
   3. On "Activate Voice Match" choose  No, thanks
      (choose Agree only if you actually want the TV to recognise your voice).
   4. Let the "HomeGraph" step finish, then return to the home screen.

   Without this the dialog comes back on every assistant command, because the
   activity that handles re-verification lives in the disabled stock launcher
   package (com.google.android.apps.tv.launcherx), so katniss crashes instead.

== verify
   From the tvHome Home pill, press the Google Assistant / Gemini command.
   The answer should appear normally instead of the verify dialog.
EOF

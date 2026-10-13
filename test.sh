#!/usr/bin/env bash
# Fastest test loop: emulator -> build -> install -> launch -> screenshot.
#
#   ./test.sh                 # build, install, launch, screenshot to shots/check.png
#   ./test.sh --no-build      # reuse the APK already in app/build/outputs/apk/debug
#   ./test.sh --cold          # force-stop first, i.e. test a cold start
#   AVD=pixel7_api34 ./test.sh
#   LOG=1 SETTLE=6 ./test.sh  # wait longer before the shot + dump a logcat tail
#
# The emulator is started headless only when no device is attached, and is left running
# afterwards: boot is the slow part (~20 s), every later iteration is a few seconds.
#
# The app id and activity are read from app/build.gradle.kts, so renaming the app does not
# mean editing this script. Override with APP_ID=… ACTIVITY=… if you move MainActivity.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT"

# --- SDK: trust local.properties first, then the environment, then the usual location ------
sdk_from_props=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -1)
for cand in "$sdk_from_props" "${ANDROID_HOME:-}" "$HOME/.android-sdk" "$HOME/Android/Sdk"; do
  if [ -n "$cand" ] && [ -x "$cand/platform-tools/adb" ]; then ANDROID_HOME=$cand; break; fi
done
: "${ANDROID_HOME:?no Android SDK found — set ANDROID_HOME or create local.properties}"
export ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# --- gradle: env override, PATH, wrapper, then ~/tools/gradle-* ---------------------------
GRADLE="${GRADLE_BIN:-}"
if [ -z "$GRADLE" ]; then
  if command -v gradle >/dev/null 2>&1; then GRADLE=$(command -v gradle)
  elif [ -x ./gradlew ]; then GRADLE=./gradlew
  else GRADLE=$(ls -1 "$HOME"/tools/gradle-*/bin/gradle 2>/dev/null | sort -V | tail -1 || true); fi
fi
: "${GRADLE:?gradle not found — install it or set GRADLE_BIN}"

# --- app identity -------------------------------------------------------------------------
APP_ID=${APP_ID:-$(sed -n 's/^[[:space:]]*namespace = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)}
: "${APP_ID:?cannot read namespace from app/build.gradle.kts}"
ACTIVITY=${ACTIVITY:-$APP_ID/.MainActivity}
AVD=${AVD:-pixel7_api34}
APK=$ROOT/app/build/outputs/apk/debug/app-debug.apk
SHOT=${SHOT:-$ROOT/shots/check.png}

BUILD=1 COLD=0
for arg in "$@"; do
  case $arg in
    --no-build) BUILD=0 ;;
    --cold)     COLD=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

step() { printf '\033[1m==> %s\033[0m\n' "$*"; }

# --- device -------------------------------------------------------------------------------
if ! adb get-state >/dev/null 2>&1; then
  step "booting $AVD (headless)"
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-window -no-audio \
    -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect >/tmp/emu-$AVD.log 2>&1 &
fi
adb wait-for-device
for _ in $(seq 1 90); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
  sleep 2
done
[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || {
  echo "emulator did not boot — see /tmp/emu-$AVD.log" >&2; exit 1; }

# --- build & install ----------------------------------------------------------------------
if [ "$BUILD" = 1 ]; then
  step "gradle :app:installDebug"
  if ! "$GRADLE" :app:installDebug -q --console=plain 2>&1 | grep -v 'SDK XML'; then
    # A release-signed build of the same id cannot be upgraded in place — the install fails
    # with INSTALL_FAILED_UPDATE_INCOMPATIBLE and no amount of retrying helps.
    step "install failed -> uninstalling (signature mismatch?) and retrying"
    adb uninstall "$APP_ID" >/dev/null 2>&1 || true
    "$GRADLE" :app:installDebug -q --console=plain 2>&1 | grep -v 'SDK XML' || true
    adb install -r -t "$APK" | tail -1
  fi
fi

# --- launch & capture ---------------------------------------------------------------------
[ "$COLD" = 1 ] && adb shell am force-stop "$APP_ID"
step "launching $ACTIVITY"
adb shell am start -n "$ACTIVITY" >/dev/null
sleep "${SETTLE:-3}"
mkdir -p "$(dirname "$SHOT")"
adb exec-out screencap -p > "$SHOT"
step "screenshot: $SHOT ($(magick identify -format '%wx%h' "$SHOT" 2>/dev/null || echo '?'))"

if [ "${LOG:-0}" = 1 ]; then
  # Only the lines that matter when something crashed or misbehaved.
  adb logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|${APP_ID##*.}" | tail -120 \
    > "${SHOT%.png}.log" || true
  step "logcat tail: ${SHOT%.png}.log"
fi

#!/usr/bin/env bash
# Fastest test loop: emulator -> build -> install -> launch -> screenshot.
#
#   ./test.sh                 # build, install, launch, screenshot to shots/check.png
#   ./test.sh --no-build      # reuse the APK already in app/build/outputs/apk/debug
#   ./test.sh --cold          # force-stop first, i.e. test a cold start
#   AVD=pixel7_api34 ./test.sh
#   LOG=1 ./test.sh           # also dump a filtered logcat tail next to the shot
#   SETTLE=4 ./test.sh        # extra settle after the app takes focus (default 1.5 s)
#   WAIT_FOCUS=80 ./test.sh   # give a slow start longer to take focus (default 40 x 0.5 s)
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
  # Capture the log and test the *task's* exit status. The obvious `if ! gradle … | grep -v pat`
  # is wrong twice over: the pipeline reports grep's status, so a silent success (grep finds
  # nothing, exits 1) reads as a failure and reinstalls the app for no reason — and a real
  # failure was reported as "signature mismatch?" whatever it actually was.
  log=$(mktemp)
  if "$GRADLE" :app:installDebug -q --console=plain >"$log" 2>&1; then
    grep -v 'SDK XML' "$log" || true
  else
    grep -v 'SDK XML' "$log" >&2 || true
    if grep -qE 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match' "$log"; then
      # A release-signed build of the same id cannot be upgraded in place.
      step "signature mismatch — uninstalling and retrying"
      adb uninstall "$APP_ID" >/dev/null 2>&1 || true
      "$GRADLE" :app:installDebug -q --console=plain 2>&1 | grep -v 'SDK XML' || true
      adb install -r -t "$APK" | tail -1
    else
      # Anything else is a build failure. The usual cause is a stale build/ intermediate
      # (NoSuchFileException under app/build/intermediates), which no amount of uninstalling
      # will fix and which has nothing to do with signing.
      step "build failed — this is not a signature problem"
      grep -E 'Execution failed for task|NoSuchFileException|error:|^e: ' "$log" | head -5 >&2 || true
      echo "  a stale build/ intermediate looks like this: rm -rf app/build && ./test.sh" >&2
      rm -f "$log"
      exit 1
    fi
  fi
  rm -f "$log"
fi

# --- launch & capture ---------------------------------------------------------------------
[ "$COLD" = 1 ] && adb shell am force-stop "$APP_ID"
step "launching $ACTIVITY"
adb shell am start -n "$ACTIVITY" >/dev/null

# `am start` returns before the window exists, and whatever was focused before is still on
# screen — a fixed sleep once captured a screenshot of a *different app* and looked like a
# pass. Wait until our own window has focus, then let the first frame draw.
focused() { adb shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus=.*$APP_ID/"; }
for _ in $(seq 1 "${WAIT_FOCUS:-40}"); do
  focused && break
  sleep 0.5
done
focused || echo "warning: $APP_ID never took focus — the screenshot may show another app" >&2
sleep "${SETTLE:-1.5}"
mkdir -p "$(dirname "$SHOT")"
adb exec-out screencap -p > "$SHOT"
step "screenshot: $SHOT ($(magick identify -format '%wx%h' "$SHOT" 2>/dev/null || echo '?'))"

if [ "${LOG:-0}" = 1 ]; then
  # Only the lines that matter when something crashed or misbehaved.
  adb logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|${APP_ID##*.}" | tail -120 \
    > "${SHOT%.png}.log" || true
  step "logcat tail: ${SHOT%.png}.log"
fi

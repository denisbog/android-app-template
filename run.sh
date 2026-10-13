#!/usr/bin/env bash
# Open the emulator WITH A WINDOW, install the app and launch it, then leave it alone for you
# to click around in.
#
#   ./run.sh                 # build + install + launch, half-size window
#   SCALE=0.75 ./run.sh      # bigger window
#   GPU=host ./run.sh        # NVIDIA instead of swiftshader
#   ./run.sh --no-build      # just relaunch
#   ./run.sh --kill          # stop the emulator
#
# Two things this does for you, both measured on this machine:
#   * the emulator restores its window position from emulator-user.ini and can come up fully
#     off-screen while still capturing fine in screenshots — check the window, not the PNG;
#   * under Wayland the compositor decides which window gets the keys: click the emulator once
#     before typing, otherwise the keystrokes go to the terminal.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT"

sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -1)
for cand in "$sdk" "${ANDROID_HOME:-}" "$HOME/.android-sdk" "$HOME/Android/Sdk"; do
  if [ -n "$cand" ] && [ -x "$cand/platform-tools/adb" ]; then ANDROID_HOME=$cand; break; fi
done
: "${ANDROID_HOME:?no Android SDK found}"
export ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

GRADLE="${GRADLE_BIN:-}"
if [ -z "$GRADLE" ]; then
  if command -v gradle >/dev/null 2>&1; then GRADLE=$(command -v gradle)
  elif [ -x ./gradlew ]; then GRADLE=./gradlew
  else GRADLE=$(ls -1 "$HOME"/tools/gradle-*/bin/gradle 2>/dev/null | sort -V | tail -1 || true); fi
fi
: "${GRADLE:?gradle not found}"

APP_ID=${APP_ID:-$(sed -n 's/^[[:space:]]*namespace = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)}
ACTIVITY=${ACTIVITY:-$APP_ID/.MainActivity}
AVD=${AVD:-pixel7_api34}
SCALE=${SCALE:-0.5}
GPU=${GPU:-swiftshader_indirect}

if [ "${1:-}" = "--kill" ]; then
  adb emu kill || true
  exit 0
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "==> booting $AVD (windowed, scale $SCALE, gpu $GPU)"
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-audio -no-boot-anim \
    -gpu "$GPU" -scale "$SCALE" >/tmp/emu-$AVD.log 2>&1 &
  adb wait-for-device
  for _ in $(seq 1 90); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
    sleep 2
  done
fi

if [ "${1:-}" != "--no-build" ]; then
  echo "==> building + installing"
  "$GRADLE" :app:installDebug -q --console=plain || {
    adb uninstall "$APP_ID" >/dev/null 2>&1 || true
    "$GRADLE" :app:installDebug -q --console=plain
  }
fi

echo "==> launching"
adb shell am start -n "$ACTIVITY" >/dev/null
echo
echo "Drive it from the terminal with ./ui.sh (tap/type/swipe by text or pixel)."
echo "Tip: ./ui.sh here  prints every text and content-description on screen with its centre."

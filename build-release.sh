#!/usr/bin/env bash
#
# Build a signed release APK (and optionally an .aab for Play).
#
#   ./build-release.sh                 # app-release.apk + <name>-<version>-release.apk
#   ./build-release.sh --clean         # wipe build/ first
#   ./build-release.sh --bundle        # also build the .aab
#   ./build-release.sh --install       # install on the connected device/emulator afterwards
#   ./build-release.sh --debug         # debug variant instead
#
# Environment overrides:
#   ANDROID_HOME   Android SDK root      (default: $ANDROID_SDK_ROOT, else ~/.android-sdk)
#   GRADLE_BIN     gradle executable     (default: gradle on PATH, else ./gradlew, else ~/tools/gradle-*/bin/gradle)
#   KS_PASSWORD    keystore password     (default: a generated random one, stored in keystore.properties)
#
# On the first run this generates release.keystore + keystore.properties (both git-ignored).
# Keep them: the keystore is the app's identity, and Play refuses an update signed with a
# different key. Back them up outside the repo.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

CLEAN=0; BUNDLE=0; INSTALL=0; VARIANT="Release"
for arg in "$@"; do
    case "$arg" in
        --clean)   CLEAN=1 ;;
        --bundle)  BUNDLE=1 ;;
        --install) INSTALL=1 ;;
        --debug)   VARIANT="Debug" ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

# ── SDK ─────────────────────────────────────────────────────────────────────────────────
SDK=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/.android-sdk" \
                 "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/lib/android-sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate/platform-tools" ]; then SDK="$candidate"; break; fi
done
[ -n "$SDK" ] || { echo "!! no Android SDK found — set ANDROID_HOME" >&2; exit 1; }
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
echo "» SDK:    $SDK"

# ── Gradle ──────────────────────────────────────────────────────────────────────────────
GRADLE="${GRADLE_BIN:-}"
if [ -z "$GRADLE" ]; then
    if command -v gradle >/dev/null 2>&1; then GRADLE="$(command -v gradle)"
    elif [ -x "$PROJECT_DIR/gradlew" ]; then GRADLE="$PROJECT_DIR/gradlew"
    else GRADLE="$(ls -1 "$HOME"/tools/gradle-*/bin/gradle 2>/dev/null | sort -V | tail -1 || true)"; fi
fi
[ -n "$GRADLE" ] && [ -x "$GRADLE" ] || {
    echo "!! gradle not found — install it or set GRADLE_BIN" >&2; exit 1; }
echo "» Gradle: $GRADLE"

# ── app identity, read from the Gradle file so the scripts never disagree with the build ─
APP_ID=$(sed -n 's/^[[:space:]]*namespace = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)
: "${APP_ID:?cannot read namespace from app/build.gradle.kts}"
VERSION=$(sed -n 's/.*versionName = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)
NAME=$(basename "$APP_ID")
echo "» app:    $APP_ID ($VERSION)"

# ── signing material (generated once) ───────────────────────────────────────────────────
KS_FILE="$PROJECT_DIR/release.keystore"
KS_PROPS="$PROJECT_DIR/keystore.properties"
if [ ! -f "$KS_PROPS" ] || [ ! -f "$KS_FILE" ]; then
    KS_PASSWORD="${KS_PASSWORD:-$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24)}"
    echo "» generating release.keystore + keystore.properties (git-ignored, back them up)"
    keytool -genkeypair \
        -keystore "$KS_FILE" \
        -alias "$NAME" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KS_PASSWORD" -keypass "$KS_PASSWORD" \
        -dname "CN=$NAME, OU=dev, O=$NAME, C=EU" >/dev/null
    cat > "$KS_PROPS" <<EOF
storeFile=release.keystore
storePassword=$KS_PASSWORD
keyAlias=$NAME
keyPassword=$KS_PASSWORD
EOF
    chmod 600 "$KS_FILE" "$KS_PROPS"
fi

echo "sdk.dir=$SDK" > local.properties

# ── build ───────────────────────────────────────────────────────────────────────────────
[ "$CLEAN" = 1 ] && { echo "» cleaning build/"; rm -rf app/build build; }

if [ "$VARIANT" = "Release" ]; then
    echo "» gradle :app:assembleRelease"
    "$GRADLE" :app:assembleRelease --console=plain
    APK="app/build/outputs/apk/release/app-release.apk"
    if [ "$BUNDLE" = 1 ]; then
        echo "» gradle :app:bundleRelease"
        "$GRADLE" :app:bundleRelease --console=plain
    fi
else
    echo "» gradle :app:assembleDebug"
    "$GRADLE" :app:assembleDebug --console=plain
    APK="app/build/outputs/apk/debug/app-debug.apk"
fi

[ -f "$APK" ] || { echo "!! no APK at $APK" >&2; exit 1; }

OUT="$PROJECT_DIR/$NAME-$VERSION-$VARIANT.apk"
cp "$APK" "$OUT"

echo "» APK:    $OUT"
echo "» size:   $(du -h "$OUT" | cut -f1)"
echo "» sha256: $(sha256sum "$OUT" | cut -d' ' -f1)"

# Verify the signature rather than assuming it: an unsigned "release" APK installs fine on
# some devices via adb and then fails on a real phone.
APKSIGNER="$(ls -1 "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$APKSIGNER" ] && [ "$VARIANT" = "Release" ]; then
    "$APKSIGNER" verify --print-certs "$OUT" | head -4 || true
fi

if [ "$INSTALL" = 1 ]; then
    ADB="$SDK/platform-tools/adb"
    echo "» installing"
    # A debug build of the same id cannot be upgraded to this one in place: uninstall first.
    "$ADB" install -r "$OUT" || { "$ADB" uninstall "$APP_ID" >/dev/null 2>&1 || true; "$ADB" install "$OUT"; }
    "$ADB" shell am start -n "$APP_ID/.MainActivity" >/dev/null
fi

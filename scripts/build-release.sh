#!/usr/bin/env bash
# Build a release APK of Tide.
#
# Runs the unit tests, builds :app:assembleRelease (minified with R8, signed
# with the shared light-sdk dev key), and copies the artifact to dist/.
#
# Requirements: JDK 17, an Android SDK with platform 36, and the Light SDK
# checked out at ../light-sdk.
#
# Toolchain resolution order:
#   JDK 17:      $JAVA_HOME (if 17) -> sdkman -> /usr/lib/jvm -> /tmp/tide-jdk17
#   Android SDK: $ANDROID_HOME -> sdk.dir in local.properties -> ~/Android/Sdk
#                -> /tmp/tide-android-sdk
#
# Usage: scripts/build-release.sh [--skip-tests]
set -euo pipefail
cd "$(dirname "$0")/.."

# --- skip-tests flag ----------------------------------------------------------

run_tests=true
for arg in "$@"; do
    case "$arg" in
        --skip-tests) run_tests=false ;;
        *) echo "unknown argument: $arg" >&2; exit 2 ;;
    esac
done

# --- JDK 17 ---------------------------------------------------------------------

jdk_is_17() {
    [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q 'version "17'
}

if jdk_is_17 "${JAVA_HOME:-}"; then
    : "${JAVA_HOME:?}"
else
    found=""
    for candidate in \
        "$HOME"/.sdkman/candidates/java/17* \
        /usr/lib/jvm/*17* \
        /tmp/tide-jdk17; do
        if jdk_is_17 "$candidate"; then
            found="$candidate"
            break
        fi
    done
    if [ -z "$found" ]; then
        echo "error: no JDK 17 found. Install one and set JAVA_HOME." >&2
        exit 1
    fi
    export JAVA_HOME="$found"
fi

# --- Android SDK ------------------------------------------------------------------

detect_android_sdk() {
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/platforms" ]; then
        return
    fi
    if grep -q '^sdk.dir=' local.properties 2>/dev/null; then
        sdk_dir="$(sed -n 's/^sdk.dir=//p' local.properties | head -1)"
        if [ -d "$sdk_dir/platforms" ]; then
            export ANDROID_HOME="$sdk_dir"
            return 0
        fi
    fi
    for candidate in "$HOME/Android/Sdk" /tmp/tide-android-sdk; do
        if [ -d "$candidate/platforms" ]; then
            export ANDROID_HOME="$candidate"
            return
        fi
    done
    echo "error: no Android SDK found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
}

detect_android_sdk

# --- Light SDK checkout -------------------------------------------------------------

if [ ! -d ../light-sdk ]; then
    echo "error: expected Light SDK checkout at ../light-sdk" >&2
    exit 1
fi

sdk_branch="$(git -C ../light-sdk branch --show-current 2>/dev/null || echo unknown)"
if [ "$sdk_branch" != "codex/tide-official-sdk" ]; then
    echo "warning: ../light-sdk is on '$sdk_branch' (expected 'codex/tide-official-sdk')" >&2
fi

# --- Version + artifact paths --------------------------------------------------------

version_name="$(awk -F'"' '/^versionName *=/ {print $2; exit}' app/lighttool.toml)"
version_code="$(awk '/^versionCode *=/ {gsub(/[^0-9]/, ""); print; exit}' app/lighttool.toml)"
if [ -z "$version_name" ] || [ -z "$version_code" ]; then
    echo "error: could not read versionName/versionCode from app/lighttool.toml" >&2
    exit 1
fi
artifact="tide-v${version_name}-vc${version_code}.apk"

echo "==> environment"
echo "    JAVA_HOME=$JAVA_HOME"
echo "    ANDROID_HOME=$ANDROID_HOME"
echo "    light-sdk branch: $sdk_branch"

if $run_tests; then
    echo "==> unit tests"
    ./gradlew :protocol:test :app:testDebugUnitTest :server:testDebugUnitTest
fi

echo "==> release build"
./gradlew :app:assembleRelease

built_apk="app/build/outputs/apk/release/app-release.apk"
mkdir -p dist
cp "$built_apk" "dist/$artifact"
hash="$(sha256sum "dist/$artifact" | awk '{print $1}')"

echo "==> done"
echo "    dist/$artifact"
echo "    sha256: $hash"
echo "    install: adb install -r dist/$artifact"

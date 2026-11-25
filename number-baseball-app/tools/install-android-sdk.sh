#!/usr/bin/env bash
# Installs the Android SDK pieces the app needs (idempotent). Needs network access to dl.google.com.
# Usage: bash number-baseball-app/tools/install-android-sdk.sh   (then: export ANDROID_HOME=/opt/android-sdk)
set -euo pipefail
SDK="${ANDROID_HOME:-/opt/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -sSfL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  unzip -q -o "$tmp/tools.zip" -d "$tmp/x"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/x/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/36.0.0" ]; then
  yes | "$SDKM" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
  "$SDKM" --sdk_root="$SDK" --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null
fi
here="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$SDK" > "$here/android/local.properties"
echo "Android SDK ready at $SDK"

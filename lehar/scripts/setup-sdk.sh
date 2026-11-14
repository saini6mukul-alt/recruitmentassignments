#!/usr/bin/env bash
# Installs the minimal Android SDK pieces Lehar needs (no Gradle).
set -euo pipefail
SDK=/opt/android-sdk
if [ -x "$SDK/build-tools/35.0.0/aapt2" ] && [ -f "$SDK/platforms/android-34/android.jar" ]; then echo "SDK ready"; exit 0; fi
mkdir -p "$SDK/cmdline-tools" && cd /tmp
curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
unzip -q clt.zip -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;35.0.0" >/dev/null
echo "SDK ready"

#!/usr/bin/env bash
# Builds a signed APK without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs ANDROID_HOME with "platforms;android-34" and "build-tools;36.0.0" installed, plus a JDK.
set -euo pipefail

cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME}}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.0.0}"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT=build
APK_NAME="${APK_NAME:-MyLiveTV.apk}"
KEYSTORE="${KEYSTORE:-$HOME/.android/mylivetv.keystore}"

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$PLATFORM" \
  --manifest AndroidManifest.xml --java "$OUT/gen" "$OUT/res.zip" --auto-add-overlay

javac --release 11 -nowarn -encoding UTF-8 \
  -classpath "$PLATFORM" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

"$BT/d8" --release --min-api 21 --lib "$PLATFORM" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -alias mylivetv -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android -dname "CN=My Live TV" >/dev/null
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias mylivetv \
  --ks-pass pass:android --key-pass pass:android --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/$APK_NAME"

echo "APK ready: $(pwd)/$OUT/$APK_NAME"

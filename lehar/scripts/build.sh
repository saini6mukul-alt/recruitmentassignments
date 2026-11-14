#!/usr/bin/env bash
# Builds and signs build/lehar-1.0.apk without Gradle (aapt2 -> javac -> d8 -> zipalign -> apksigner).
set -euo pipefail
cd "$(dirname "$0")/.."
SDK=/opt/android-sdk; BT=$SDK/build-tools/35.0.0; JAR=$SDK/platforms/android-34/android.jar
VC=${VERSION_CODE:-1}; VN=${VERSION_NAME:-1.0}
OUT=build; rm -rf $OUT/res $OUT/gen $OUT/classes $OUT/dex $OUT/apk $OUT/*.apk $OUT/res.zip
mkdir -p $OUT/gen $OUT/classes $OUT/dex $OUT/apk/assets
$BT/aapt2 compile --dir android/res -o $OUT/res.zip
$BT/aapt2 link -I $JAR --manifest android/AndroidManifest.xml -o $OUT/base.apk \
  --java $OUT/gen --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VC" --version-name "$VN" $OUT/res.zip
javac -encoding UTF-8 -source 17 -target 17 -nowarn -Xlint:-options -classpath $JAR -d $OUT/classes \
  $(find android/src $OUT/gen -name '*.java') 2>&1 | grep -v '^Picked up' || true
[ -n "$(find $OUT/classes -name 'MainActivity.class')" ] || { echo "javac failed"; exit 1; }
$BT/d8 --min-api 26 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class') 2>&1 | grep -v '^Picked up' || true
cp -r www $OUT/apk/assets/www
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -qj ../unsigned.apk classes.dex)
(cd $OUT/apk && zip -qr ../unsigned.apk assets)
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
KS=$HOME/.lehar/lehar.jks
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.lehar"; head -c 18 /dev/urandom | base64 | tr -d '/+=' > "$HOME/.lehar/pass"
  keytool -genkeypair -keystore "$KS" -alias lehar -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$(cat $HOME/.lehar/pass)" -keypass "$(cat $HOME/.lehar/pass)" -dname "CN=Lehar" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass "file:$HOME/.lehar/pass" --out $OUT/lehar-$VN.apk $OUT/aligned.apk 2>&1 | grep -v '^Picked up' || true
if $BT/apksigner verify $OUT/lehar-$VN.apk >/dev/null 2>&1; then echo "BUILT $OUT/lehar-$VN.apk"; else echo "SIGN VERIFY FAILED"; exit 1; fi

#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK path}"
export JAVA_HOME=${JAVA_HOME:-/opt/java/openjdk}
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS='-Xmx1g -XX:ActiveProcessorCount=2'
bt="$ANDROID_SDK_ROOT/build-tools/35.0.0"
android="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/classes build/dex dist
find core/src android/src probe/src -name '*.java' -print > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$android:$bt/core-lambda-stubs.jar" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
"$bt/d8" --min-api 23 --lib "$android" --output build/dex build/classes.jar
"$bt/aapt2" link -I "$android" --manifest probe/AndroidManifest.xml -o build/resources.apk
python3 - <<'PY'
from pathlib import Path
import zipfile
base = Path('build')
with zipfile.ZipFile(base/'resources.apk') as src, zipfile.ZipFile(base/'unsigned.apk','w') as dst:
    for info in src.infolist():
        dst.writestr(info,src.read(info))
    for p in (base/'dex').glob('*.dex'):
        dst.write(p,p.name,compress_type=zipfile.ZIP_DEFLATED)
PY
"$bt/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# Dedicated debug signing material is private and is not part of the source/delivery bundle.
signing="$HOME/.local/share/teyes-probe-signing"
umask 077
mkdir -p "$signing"
if [[ ! -f "$signing/password" ]]; then
    python3 -c 'import secrets,sys; open(sys.argv[1],"x").write(secrets.token_urlsafe(40))' "$signing/password"
fi
if [[ ! -f "$signing/debug.p12" ]]; then
    keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$signing/debug.p12" \
        -alias probe -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Teyes CAN Probe Development' \
        -storepass:file "$signing/password" -keypass:file "$signing/password"
fi
"$bt/apksigner" sign --ks "$signing/debug.p12" --ks-key-alias probe \
    --ks-pass "file:$signing/password" --out dist/teyes-can-probe-0.3.0.apk build/aligned.apk
"$bt/apksigner" verify --verbose --print-certs dist/teyes-can-probe-0.3.0.apk
"$bt/aapt" dump badging dist/teyes-can-probe-0.3.0.apk
sha256sum dist/teyes-can-probe-0.3.0.apk > dist/SHA256SUMS

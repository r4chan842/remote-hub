#!/usr/bin/env bash
set -euo pipefail

APK="$(find composeApp/build/outputs/apk/release -name '*.apk' | head -1)"
test -n "$APK"
mkdir -p build-report

SDK_BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
APKANALYZER="$SDK_BUILD_TOOLS/apkanalyzer"

{
  echo "APK: $APK"
  echo "Bytes: $(stat -c%s "$APK")"
  echo
  echo "== signature =="
  "$SDK_BUILD_TOOLS/apksigner" verify --print-certs --verbose "$APK"
  echo
  echo "== manifest =="
  "$APKANALYZER" manifest print "$APK" | head -60
  echo
  echo "== permissions =="
  "$APKANALYZER" manifest permissions "$APK"
  echo
  echo "== native libraries =="
  unzip -l "$APK" | grep -E '\.so$'
  echo
  echo "== dex method count =="
  "$APKANALYZER" dex references "$APK" || true
  echo
  echo "== largest entries =="
  unzip -l "$APK" | sort -k1 -n -r | head -20
} > build-report/apk-report.txt 2>&1

cat build-report/apk-report.txt

grep -q "libremotehub-rdp.so" build-report/apk-report.txt
grep -q "libgojni.so" build-report/apk-report.txt
grep -q "android.permission.INTERNET" build-report/apk-report.txt
if grep -q "android.permission.READ_EXTERNAL_STORAGE\|android.permission.ACCESS_FINE_LOCATION\|android.permission.CAMERA" build-report/apk-report.txt; then
  echo "unexpected permission requested" >&2
  exit 1
fi
SIZE="$(stat -c%s "$APK")"
echo "APK size ${SIZE} bytes"
if [ "$SIZE" -gt 125829120 ]; then
  echo "APK exceeds the 120 MB budget" >&2
  exit 1
fi

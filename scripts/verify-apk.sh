#!/usr/bin/env bash
set -euo pipefail

APK="$(find composeApp/build/outputs/apk/release -name '*.apk' | head -1)"
if [ -z "$APK" ]; then
  echo "no release APK was produced" >&2
  exit 1
fi
mkdir -p build-report
REPORT=build-report/apk-report.txt

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
BUILD_TOOLS="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
APKSIGNER="${BUILD_TOOLS}/apksigner"
AAPT=""
for candidate in "${BUILD_TOOLS}/aapt2" "${BUILD_TOOLS}/aapt"; do
  if [ -x "$candidate" ]; then AAPT="$candidate"; break; fi
done

{
  echo "APK: $APK"
  echo "Bytes: $(stat -c%s "$APK")"
  echo "SHA256: $(sha256sum "$APK" | cut -d' ' -f1)"
  echo "Build tools: ${BUILD_TOOLS}"
  echo
  echo "== signature =="
  if [ -x "$APKSIGNER" ]; then
    "$APKSIGNER" verify --print-certs --verbose "$APK"
  else
    echo "apksigner not available" >&2
    exit 1
  fi
  echo
  echo "== package and permissions =="
  if [ -n "$AAPT" ] && [ "$(basename "$AAPT")" = "aapt" ]; then
    "$AAPT" dump badging "$APK" | grep -E "^package:|^sdkVersion|^targetSdkVersion|^uses-permission|^application-label:|^native-code"
  elif [ -n "$AAPT" ]; then
    "$AAPT" dump badging "$APK" | grep -E "^package:|^sdkVersion|^targetSdkVersion|^uses-permission|^application-label:|^native-code"
  else
    echo "aapt not available; listing manifest entry only"
    unzip -l "$APK" | grep AndroidManifest.xml
  fi
  echo
  echo "== native libraries =="
  unzip -l "$APK" | grep -E '\.so$' || true
  echo
  echo "== largest entries =="
  unzip -l "$APK" | sort -k1 -n -r | head -20
} > "$REPORT" 2>&1

cat "$REPORT"

grep -q "libremotehub-rdp.so" "$REPORT"
grep -q "libgojni.so" "$REPORT"
grep -q "Verified using v2 scheme" "$REPORT"

if grep -qE "uses-permission:.*(READ_EXTERNAL_STORAGE|ACCESS_FINE_LOCATION|CAMERA|READ_CONTACTS|READ_SMS)" "$REPORT"; then
  echo "unexpected permission requested" >&2
  exit 1
fi

SIZE="$(stat -c%s "$APK")"
echo "APK size ${SIZE} bytes"
if [ "$SIZE" -gt 125829120 ]; then
  echo "APK exceeds the 120 MB budget" >&2
  exit 1
fi
echo "APK verification passed"

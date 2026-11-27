#!/usr/bin/env bash
# Verifies every release APK produced by Gradle (per-ABI splits and the universal
# fallback): signature, permissions, native libraries, 16 KB packaging, size budget.
#
# Environment:
#   APK_DIR              where to look (default composeApp/build/outputs/apk/release)
#   EXPECTED_CERT_SHA256 when set, every APK must be signed by this certificate
#                        (lower-case hex, no colons). Used by the release workflow.
#   ALLOW_4K_LIBS        space separated 64-bit libraries tolerated with 4 KB ELF
#                        alignment (third-party prebuilts; reported as warnings)
set -euo pipefail

APK_DIR="${APK_DIR:-composeApp/build/outputs/apk/release}"
REQUIRE_UNIVERSAL="${REQUIRE_UNIVERSAL:-true}"
ALLOW_4K_LIBS="${ALLOW_4K_LIBS:-libsodium.so}"
mapfile -t APKS < <(find "$APK_DIR" -name '*.apk' | sort)
if [ "${#APKS[@]}" -eq 0 ]; then
  echo "no release APK was produced" >&2
  exit 1
fi
mkdir -p build-report
REPORT=build-report/apk-report.txt
: > "$REPORT"
: > build-report/apk-assets.tsv
declare -A SEEN=()

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
# Entries may be symlinks (cached SDKs), so do not filter with find -type d.
BUILD_TOOLS="$(ls -1d "$SDK"/build-tools/*/ 2>/dev/null | sed 's:/*$::' | sort -V | tail -1)"
APKSIGNER="${BUILD_TOOLS}/apksigner"
ZIPALIGN="${BUILD_TOOLS}/zipalign"
AAPT=""
for candidate in "${BUILD_TOOLS}/aapt2" "${BUILD_TOOLS}/aapt"; do
  if [ -x "$candidate" ]; then AAPT="$candidate"; break; fi
done
if [ ! -x "$APKSIGNER" ] || [ ! -x "$ZIPALIGN" ] || [ -z "$AAPT" ]; then
  echo "apksigner, zipalign and aapt2/aapt are required" >&2
  exit 1
fi
ALLOW_ARGS=()
for lib in $ALLOW_4K_LIBS; do ALLOW_ARGS+=(--allow-4k "$lib"); done

FAIL=0
fail() { echo "FAIL [$1]: $2" | tee -a "$REPORT" >&2; FAIL=1; }

for APK in "${APKS[@]}"; do
  NAME="$(basename "$APK")"
  BADGING="$("$AAPT" dump badging "$APK")" || { fail "$NAME" "cannot read manifest"; continue; }
  BADGING_ABIS="$(printf '%s\n' "$BADGING" | sed -n "s/^native-code: //p" | tr -d "'" | tr ' ' '\n' | sort | xargs)"
  case "$BADGING_ABIS" in
    arm64-v8a|armeabi-v7a) LABEL="$BADGING_ABIS"; BUDGET=$((45 * 1024 * 1024)) ;;
    'arm64-v8a armeabi-v7a') LABEL=universal; BUDGET=$((70 * 1024 * 1024)) ;;
    *) fail "$NAME" "unexpected ABI set: $BADGING_ABIS"; continue ;;
  esac
  if [ "${SEEN[$LABEL]:-}" ]; then fail "$NAME" "duplicate $LABEL APK"; continue; fi
  SEEN[$LABEL]=1
  EXPECT=()
  for abi in $BADGING_ABIS; do EXPECT+=(--expect-abi "$abi"); done
  printf '%s\t%s\n' "$LABEL" "$APK" >> build-report/apk-assets.tsv
  SIZE="$(stat -c%s "$APK")"
  OUT="build-report/${NAME%.apk}.txt"
  {
    echo "################ $NAME"
    echo "APK: $APK"
    echo "Bytes: $SIZE"
    echo "SHA256: $(sha256sum "$APK" | cut -d' ' -f1)"
    echo "Build tools: ${BUILD_TOOLS}"
    echo
    echo "== signature =="
    "$APKSIGNER" verify --print-certs --verbose "$APK" && echo "signature: OK" || echo "signature: FAILED"
    echo
    echo "== package and permissions =="
    printf '%s\n' "$BADGING" | grep -E "^package:|^sdkVersion|^minSdkVersion|^targetSdkVersion|^uses-permission|^application-label:|^native-code" || true
    echo
    echo "== 16 KB zip alignment =="
    "$ZIPALIGN" -c -P 16 4 "$APK" && echo "zipalign -P 16: OK" || echo "zipalign -P 16: FAILED"
    echo
    echo "== native libraries =="
    python3 scripts/apk-inspect.py "$APK" "${EXPECT[@]}" "${ALLOW_ARGS[@]}" 2>&1 && echo "inspect: OK" || echo "inspect: FAILED"
    echo
    echo "== largest entries =="
    unzip -l "$APK" | sort -k1 -n -r | sed -n '1,20p'
    echo
  } > "$OUT" 2>&1
  cat "$OUT" | tee -a "$REPORT"

  grep -qx "signature: OK" "$OUT" || fail "$NAME" "invalid signature"
  grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" "$OUT" || fail "$NAME" "no valid v2 signature"
  grep -q "inspect: OK" "$OUT" || fail "$NAME" "native packaging check failed"
  if grep -q "zipalign -P 16: FAILED" "$OUT"; then fail "$NAME" "not 16 KB zip-aligned"; fi
  if grep -qE "uses-permission:.*(READ_EXTERNAL_STORAGE|ACCESS_FINE_LOCATION|CAMERA|READ_CONTACTS|READ_SMS)" "$OUT"; then
    fail "$NAME" "unexpected permission requested"
  fi
  if [ -n "${EXPECTED_CERT_SHA256:-}" ]; then
    CERT="$(awk '/^Signer #[0-9]+ certificate SHA-256 digest:/ { print $NF }' "$OUT")"
    if [ "$CERT" != "${EXPECTED_CERT_SHA256,,}" ]; then
      fail "$NAME" "signed by $CERT, expected ${EXPECTED_CERT_SHA256,,}"
    fi
  fi
  echo "$NAME size ${SIZE} bytes (budget ${BUDGET})" | tee -a "$REPORT"
  if [ "$SIZE" -gt "$BUDGET" ]; then fail "$NAME" "exceeds the size budget"; fi
done

for label in arm64-v8a armeabi-v7a; do
  [ "${SEEN[$label]:-}" ] || fail "$label" "required split is missing"
done
if [ "$REQUIRE_UNIVERSAL" = true ] && [ -z "${SEEN[universal]:-}" ]; then
  fail universal "required universal APK is missing"
fi
if [ "$FAIL" -ne 0 ]; then
  echo "APK verification failed" >&2
  exit 1
fi
echo "APK verification passed (${#APKS[@]} APKs)"

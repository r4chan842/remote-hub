#!/usr/bin/env bash
set -euo pipefail

WANTED="${NDK_VERSION:-}"
NDK_ROOT=""

for candidate in "${ANDROID_NDK_LATEST_HOME:-}" "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" "${ANDROID_NDK:-}"; do
  if [ -n "$candidate" ] && [ -f "$candidate/source.properties" ]; then
    NDK_ROOT="$candidate"
    break
  fi
done

if [ -z "$NDK_ROOT" ] && [ -d "${ANDROID_HOME:-}/ndk" ]; then
  for candidate in "${ANDROID_HOME}"/ndk/*; do
    if [ -f "$candidate/source.properties" ]; then
      NDK_ROOT="$candidate"
    fi
  done
fi

if [ -n "$WANTED" ] && [ -f "${ANDROID_HOME:-}/ndk/${WANTED}/source.properties" ]; then
  NDK_ROOT="${ANDROID_HOME}/ndk/${WANTED}"
fi

if [ -z "$NDK_ROOT" ]; then
  SDKMANAGER="$(find "${ANDROID_HOME}/cmdline-tools" -name sdkmanager -type f | head -1)"
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 </dev/null || true
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" "ndk;${WANTED}" </dev/null
  NDK_ROOT="${ANDROID_HOME}/ndk/${WANTED}"
fi

test -f "$NDK_ROOT/source.properties"
REVISION="$(grep Pkg.Revision "$NDK_ROOT/source.properties" | cut -d= -f2 | tr -d ' ')"
echo "Using NDK $REVISION at $NDK_ROOT"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "ANDROID_NDK_ROOT=$NDK_ROOT"
    echo "GOMOBILE_NDK=$NDK_ROOT"
    echo "NDK_REVISION=$REVISION"
  } >> "$GITHUB_ENV"
fi

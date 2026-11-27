#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
: "${FREERDP_SOURCE_DIR:?}"
: "${ANDROID_NDK_ROOT:?}"
: "${ANDROID_ABI:?}"

BUILD_DIR="${HERE}/build/${ANDROID_ABI}"
OUT_DIR="${HERE}/out/${ANDROID_ABI}"
STAGE_DIR="${FREERDP_SOURCE_DIR}/client/Android/Studio/freeRDPCore/src/main/jniLibs/${ANDROID_ABI}"

cmake -S "$HERE" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="${ANDROID_ABI}" \
  -DANDROID_PLATFORM=android-26 \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DFREERDP_SOURCE_DIR="${FREERDP_SOURCE_DIR}" \
  -DFREERDP_DEFAULT_PROJECT_VERSION=3.33.1

cmake --build "$BUILD_DIR" --parallel "$(nproc)"

mkdir -p "$OUT_DIR"
cp "${BUILD_DIR}/libremotehub-rdp.so" "$OUT_DIR/"
for lib in "$STAGE_DIR"/*.so; do
  [ -e "$lib" ] || continue
  cp "$lib" "$OUT_DIR/"
done

"${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$OUT_DIR"/*.so
ls -l "$OUT_DIR"

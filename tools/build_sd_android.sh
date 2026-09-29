#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
SRC="$ROOT/.engine-build/stable-diffusion.cpp"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
[[ -n "$NDK" && -f "$NDK/build/cmake/android.toolchain.cmake" ]] || { echo "Set ANDROID_NDK_HOME"; exit 1; }
mkdir -p "$OUT" "$(dirname "$SRC")"
SD_COMMIT="3f8527a46c54ecf4cb4ed6003da8e8982283c73c"
if [[ ! -d "$SRC/.git" ]]; then
  mkdir -p "$SRC"
  git -C "$SRC" init
  git -C "$SRC" remote add origin https://github.com/leejet/stable-diffusion.cpp
fi
git -C "$SRC" fetch --depth 1 origin "$SD_COMMIT"
git -C "$SRC" checkout --detach FETCH_HEAD
git -C "$SRC" submodule update --init --recursive --depth 1
rm -rf "$SRC/build-android"
VULKAN="${LOCALAI_SD_VULKAN:-1}"
COMMON=(
  -G Ninja
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
  -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM=android-28
  -DGGML_OPENMP=OFF
  -DGGML_NATIVE=OFF
  -DSD_BUILD_EXAMPLES=ON
  -DSD_BUILD_SHARED_LIBS=OFF
  -DSD_BUILD_SHARED_GGML_LIB=OFF
  -DSD_WEBP=OFF
  -DSD_WEBM=OFF
)
if [[ "$VULKAN" == "1" ]]; then
  echo "Trying Vulkan build for Mali/Android…"
  if ! cmake -S "$SRC" -B "$SRC/build-android" "${COMMON[@]}" -DSD_VULKAN=ON; then
    echo "Vulkan configure failed; falling back to CPU"
    rm -rf "$SRC/build-android"
    cmake -S "$SRC" -B "$SRC/build-android" "${COMMON[@]}" -DSD_VULKAN=OFF
  fi
else
  cmake -S "$SRC" -B "$SRC/build-android" "${COMMON[@]}" -DSD_VULKAN=OFF
fi
if ! cmake --build "$SRC/build-android" --target sd-cli -j2; then
  echo "First build failed; retrying CPU-only"
  rm -rf "$SRC/build-android"
  cmake -S "$SRC" -B "$SRC/build-android" "${COMMON[@]}" -DSD_VULKAN=OFF
  cmake --build "$SRC/build-android" --target sd-cli -j2
fi
BIN="$(find "$SRC/build-android" -type f -name 'sd-cli' | head -1 || true)"
[[ -n "$BIN" ]] || { echo "sd-cli not found"; exit 1; }
find "$SRC/build-android" -type f -name '*.so' -exec cp -f {} "$OUT/" \;
cp -f "$BIN" "$OUT/libsd_cli.so"; chmod +x "$OUT/libsd_cli.so"
echo "stable-diffusion.cpp Android engine prepared in $OUT"

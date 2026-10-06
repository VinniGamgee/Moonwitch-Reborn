#!/usr/bin/env bash
set -euo pipefail

VARIANT="${1:-performance}"
MESA_VERSION="26.2.4"
MESA_COMMIT="96cb43121031992b85767f9c1be8f3f48e22b1d2"
MESA_URL="https://gitlab.freedesktop.org/mesa/mesa.git"
NDK_VERSION="android-ndk-r29"
ANDROID_TARGET="34"
PLATFORM_SDK="36"

case "$VARIANT" in
  performance)
    DISPLAY_NAME="Moonwitch Turnip ${MESA_VERSION} Performance"
    DEBUG_FLAGS="0"
    DESCRIPTION="Mesa Turnip ${MESA_VERSION} for Adreno A6xx/A7xx. Release build with upstream rendering heuristics and no forced diagnostic path."
    ;;
  foliage-nolrz)
    DISPLAY_NAME="Moonwitch Turnip ${MESA_VERSION} Foliage No-LRZ"
    DEBUG_FLAGS="TU_DEBUG_NOLRZ"
    DESCRIPTION="Diagnostic Turnip build for disappearing foliage. LRZ is disabled by default to isolate depth/rejection issues."
    ;;
  foliage-sysmem)
    DISPLAY_NAME="Moonwitch Turnip ${MESA_VERSION} Foliage Sysmem"
    DEBUG_FLAGS="TU_DEBUG_SYSMEM"
    DESCRIPTION="Diagnostic Turnip build for disappearing foliage. Forces sysmem rendering to isolate GMEM/tile-path issues."
    ;;
  foliage-safe)
    DISPLAY_NAME="Moonwitch Turnip ${MESA_VERSION} Foliage Safe"
    DEBUG_FLAGS="TU_DEBUG_NOLRZ | TU_DEBUG_NO_CONCURRENT_RESOLVES | TU_DEBUG_NO_CONCURRENT_UNRESOLVES"
    DESCRIPTION="Conservative diagnostic Turnip build for disappearing foliage and black tiles. Disables LRZ and concurrent resolve/unresolve paths."
    ;;
  *)
    echo "Unknown variant: $VARIANT" >&2
    exit 2
    ;;
esac

ROOT="$(pwd)"
WORK="$ROOT/.turnip-work/$VARIANT"
SRC="$WORK/mesa"
PREFIX="$WORK/prefix"
DIST="$ROOT/dist"
NDK_ROOT="$ROOT/.turnip-work/$NDK_VERSION"
NDK_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"

rm -rf "$WORK"
mkdir -p "$WORK" "$DIST"

if [[ ! -d "$NDK_ROOT" ]]; then
  mkdir -p "$ROOT/.turnip-work"
  curl -fsSL "https://dl.google.com/android/repository/${NDK_VERSION}-linux.zip" -o "$ROOT/.turnip-work/ndk.zip"
  unzip -q "$ROOT/.turnip-work/ndk.zip" -d "$ROOT/.turnip-work"
  rm -f "$ROOT/.turnip-work/ndk.zip"
fi

git init -q "$SRC"
git -C "$SRC" remote add origin "$MESA_URL"
git -C "$SRC" fetch -q --depth=1 origin "$MESA_COMMIT"
git -C "$SRC" checkout -q --detach FETCH_HEAD

ACTUAL_COMMIT="$(git -C "$SRC" rev-parse HEAD)"
if [[ "$ACTUAL_COMMIT" != "$MESA_COMMIT" ]]; then
  echo "Mesa source mismatch: expected $MESA_COMMIT, got $ACTUAL_COMMIT" >&2
  exit 1
fi

if [[ "$DEBUG_FLAGS" != "0" ]]; then
  python3 - "$SRC/src/freedreno/vulkan/tu_util.cc" "$DEBUG_FLAGS" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
flags = sys.argv[2]
text = path.read_text(encoding="utf-8")
needle = '   tu_env.start_debug = tu_env.debug = parse_debug_string(os_get_option("TU_DEBUG"), tu_debug_options);'
replacement = needle + f"""
   /* Moonwitch diagnostic default. User-provided TU_DEBUG options are preserved. */
   tu_env.start_debug |= {flags};
   tu_env.debug.store(tu_env.start_debug, std::memory_order_release);"""
if needle not in text:
    raise SystemExit("Turnip debug initialization changed; refusing to apply a blind patch")
path.write_text(text.replace(needle, replacement, 1), encoding="utf-8")
PY
fi

cat > "$SRC/android-aarch64.txt" <<EOF
[binaries]
ar = '$NDK_BIN/llvm-ar'
c = ['$NDK_BIN/aarch64-linux-android${ANDROID_TARGET}-clang']
cpp = ['$NDK_BIN/aarch64-linux-android${ANDROID_TARGET}-clang++', '-fno-exceptions', '-fno-unwind-tables', '-fno-asynchronous-unwind-tables', '-static-libstdc++']
c_ld = '$NDK_BIN/ld.lld'
cpp_ld = '$NDK_BIN/ld.lld'
strip = '$NDK_BIN/llvm-strip'
pkg-config = ['env', 'PKG_CONFIG_LIBDIR=/nonexistent', '/usr/bin/pkg-config']

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8'
endian = 'little'
EOF

pushd "$SRC" >/dev/null
MESON_ARGS=(
  "--cross-file=android-aarch64.txt"
  "--wrap-mode=nofallback"
  "--force-fallback-for=zlib"
  "--prefix=$PREFIX"
  "-Dbuildtype=release"
  "-Dtools="
  "-Dstrip=true"
  "-Dplatforms=android"
  "-Dvideo-codecs="
  "-Dplatform-sdk-version=$PLATFORM_SDK"
  "-Dandroid-stub=true"
  "-Dandroid-libbacktrace=disabled"
  "-Dzlib=enabled"
  "-Dzstd=disabled"
  "-Dgallium-drivers="
  "-Dvulkan-drivers=freedreno"
  "-Dfreedreno-kmds=kgsl"
  "-Dvulkan-beta=true"
  "-Degl=disabled"
)
meson setup build-android-aarch64 "${MESON_ARGS[@]}"
ninja -C build-android-aarch64 install
popd >/dev/null

LIB="$PREFIX/lib/libvulkan_freedreno.so"
if [[ ! -s "$LIB" ]]; then
  echo "Turnip output missing: $LIB" >&2
  exit 1
fi

PACKAGE_DIR="$WORK/package"
mkdir -p "$PACKAGE_DIR"
cp "$LIB" "$PACKAGE_DIR/libvulkan_freedreno.so"

SHORT_COMMIT="${MESA_COMMIT:0:12}"
cat > "$PACKAGE_DIR/meta.json" <<EOF
{
  "schemaVersion": 1,
  "name": "$DISPLAY_NAME",
  "description": "$DESCRIPTION",
  "author": "Moonwitch Project / Mesa",
  "packageVersion": "1",
  "vendor": "Mesa Turnip",
  "driverVersion": "Mesa $MESA_VERSION ($SHORT_COMMIT)",
  "minApi": 28,
  "libraryName": "libvulkan_freedreno.so"
}
EOF

cat > "$PACKAGE_DIR/MOONWITCH_BUILD.txt" <<EOF
Moonwitch Turnip
Variant: $VARIANT
Mesa: $MESA_VERSION
Mesa commit: $MESA_COMMIT
Target: Android arm64 / KGSL / Adreno A6xx-A7xx
Forced Turnip flags: $DEBUG_FLAGS
Build type: release
EOF

OUT="$DIST/Moonwitch-Turnip-${MESA_VERSION}-${VARIANT}.zip"
(
  cd "$PACKAGE_DIR"
  zip -q -9 "$OUT" libvulkan_freedreno.so meta.json MOONWITCH_BUILD.txt
)

sha256sum "$OUT" | tee "$OUT.sha256"
echo "Built $OUT"

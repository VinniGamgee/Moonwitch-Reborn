#!/usr/bin/env bash
set -euo pipefail
PROJECT=$(cd "$(dirname "$0")/.." && pwd)
WORK="$PROJECT/work"
mkdir -p "$WORK" "$PROJECT/dist"
fetch() {
  local url=$1 sha=$2 dir=$3
  git init "$dir"
  git -C "$dir" remote add origin "$url"
  git -C "$dir" fetch --depth=1 origin "$sha"
  git -C "$dir" checkout --detach FETCH_HEAD
  test "$(git -C "$dir" rev-parse HEAD)" = "$sha"
}
fetch https://github.com/Evolution-X-Devices/kernel_xiaomi_sm8450.git c3d1c3d93062911060e8460fdb88cab235f6603e "$WORK/kernel"
git init "$WORK/clang"
git -C "$WORK/clang" remote add origin https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86
git -C "$WORK/clang" fetch --depth=1 --filter=blob:none origin b54cd2c8e9e0bb057aef3804d42d39c5ac4674c7
git -C "$WORK/clang" sparse-checkout init --cone
git -C "$WORK/clang" sparse-checkout set clang-r563880c
git -C "$WORK/clang" checkout --detach FETCH_HEAD
export PATH="$WORK/clang/clang-r563880c/bin:$PATH"
clang --version
curl -fL --retry 3 'https://cdn.evolution-x.org/marble/17/boot/boot.img/download' -o "$WORK/boot.img"
curl -fL --retry 3 'https://cdn.evolution-x.org/marble/17/vendor_boot/vendor_boot.img/download' -o "$WORK/vendor_boot.img"
python3 "$PROJECT/scripts/prepare.py" "$WORK" "$PROJECT/dist"
mkdir -p "$WORK/out"
cp "$WORK/stock.config" "$WORK/out/.config"
make -C "$WORK/kernel" O="$WORK/out" ARCH=arm64 LLVM=1 LLVM_IAS=1 TARGET_PRODUCT=marble olddefconfig
python3 "$PROJECT/scripts/check_config.py" "$WORK/stock.config" "$WORK/out/.config"
make -C "$WORK/kernel" O="$WORK/out" ARCH=arm64 LLVM=1 LLVM_IAS=1 TARGET_PRODUCT=marble -j2 Image
test "$(cat "$WORK/out/include/config/kernel.release")" = 5.10.269-gki-gc3d1c3d93062
cp "$WORK/out/.config" "$PROJECT/dist/kernel.config"
fetch https://github.com/osm0sis/AnyKernel3.git 020dfeccf9d7e962a48400fc94d3e451df92eead "$WORK/ak3"
python3 "$PROJECT/scripts/package.py" "$PROJECT"

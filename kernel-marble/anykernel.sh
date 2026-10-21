properties() { '
kernel.string=Marble EvoX 12.3 Full LTO - GPU 591.5 MHz
do.devicecheck=1
do.modules=0
do.systemless=1
do.cleanup=1
do.cleanuponabort=0
device.name1=marble
device.name2=marblein
supported.versions=17
'; }
BLOCK=vendor_boot
IS_SLOT_DEVICE=auto
RAMDISK_COMPRESSION=auto
PATCH_VBMETA_FLAG=auto
. tools/ak3-core.sh
split_boot
hash=$(sha256sum "$SPLITIMG/dtb" | cut -d' ' -f1)
case "$hash" in
  5036448420d9ce349da846cef489a6a134fc83cf1678bc0be2507101b12355b8|c1f888aa9a5362b1f7937d2c01815c66d6d604d276626dc27940acccfe424abc) ;;
  *) abort 'DTB diferente da EvoX 12.3 validada. Nenhuma particao foi alterada.' ;;
esac
backup=/sdcard/MarbleKernelBackup/$(date +%Y%m%d-%H%M%S)
mkdir -p "$backup" || abort 'Nao foi possivel criar o backup.'
cp "$BOOTIMG" "$backup/vendor_boot${SLOT}.img" || abort 'Falha no backup de vendor_boot.'
flash_boot
BLOCK=boot
RAMDISK_COMPRESSION=auto
reset_ak
dump_boot
cp "$BOOTIMG" "$backup/boot${SLOT}.img" || abort 'Falha no backup de boot.'
write_boot
ui_print 'Kernel instalado. Backups em /sdcard/MarbleKernelBackup/'
ui_print 'Instale Marble-Profiles.zip pelo gerenciador root para alternar os perfis.'

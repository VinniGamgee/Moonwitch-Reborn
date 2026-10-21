#!/system/bin/sh
MODDIR=${0%/*}
case "$(cat "$MODDIR/selected" 2>/dev/null)" in
  balanced) mode=performance ;;
  performance) mode=powersave ;;
  *) mode=balanced ;;
esac
"$MODDIR/profilectl" apply "$mode"

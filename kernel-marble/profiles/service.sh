#!/system/bin/sh
MODDIR=${0%/*}
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
# Remove only a stale lock surviving an interrupted previous boot.
rmdir "$MODDIR/.lock" 2>/dev/null
while [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ]; do
  mode=$(cat "$MODDIR/selected" 2>/dev/null)
  [ -n "$mode" ] || mode=balanced
  "$MODDIR/profilectl" apply "$mode" > "$MODDIR/last-apply.log" 2>&1
  sleep 10
done

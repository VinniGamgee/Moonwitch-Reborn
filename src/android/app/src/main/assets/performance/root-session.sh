#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs under Magisk su. All state is session-scoped; no boot-time settings.
owner=$1
lease=$2
case "$owner" in ''|*[!0-9]*) exit 2;; esac
[ "$(id -u)" = 0 ] || { echo 'ERROR root access denied'; exit 1; }
identity=$(cat "/proc/$owner/stat") || exit 1
# Strip comm (which may contain spaces) before reading starttime.
identity=${identity##*) }
identity=$(echo "$identity" | awk '{print $20}')
alive() {
    [ -f "$lease" ] || return 1
    current=$(cat "/proc/$owner/stat" 2>/dev/null) || return 1
    current=${current##*) }
    [ "$(echo "$current" | awk '{print $20}')" = "$identity" ]
}
lock=/data/adb/moonwitch-performance.lock
attempt=0
until mkdir "$lock" 2>/dev/null; do
    alive || exit 0
    attempt=$((attempt + 1))
    [ "$attempt" -lt 6 ] || { echo 'ERROR another root session owns the controller'; exit 1; }
    sleep 1
done
chmod 700 "$lock"
state=$lock/state
: > "$state"
restore() {
    trap '' HUP INT TERM
    # Snapshots are prepended: restore floors before restoring ceilings.
    while IFS='|' read -r node value; do
        if printf '%s\n' "$value" > "$node"; then
            echo "RESTORE $node=$(cat "$node" 2>/dev/null)"
        else
            echo "ERROR restoring $node"
        fi
    done < "$state"
    rm -f "$state" "$lock/next"
    rmdir "$lock"
    echo 'STOPPED original values restored where accepted by kernel'
}
trap restore EXIT
trap 'exit 0' HUP INT TERM
set_node() {
    node=$1
    value=$2
    [ -f "$node" ] || { echo "UNSUPPORTED $node"; return; }
    old=$(cat "$node") || return
    # Journal before writing, including failures that may partially apply.
    { printf '%s|%s\n' "$node" "$old"; cat "$state"; } > "$lock/next"
    mv "$lock/next" "$state"
    if printf '%s\n' "$value" > "$node"; then
        actual=$(cat "$node")
        echo "APPLIED $node requested=$value readback=$actual"
    else
        echo "REJECTED $node requested=$value"
    fi
}
number() { case "$1" in ''|*[!0-9]*|0) return 1;; *) return 0;; esac; }
alive || exit 0
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    number "$max" || continue
    set_node "$policy/scaling_max_freq" "$max"
    set_node "$policy/scaling_min_freq" "$max"
    governors=$(cat "$policy/scaling_available_governors" 2>/dev/null)
    case " $governors " in *' performance '*) set_node "$policy/scaling_governor" performance;; esac
done
gpu=/sys/class/kgsl/kgsl-3d0/devfreq
if [ -d "$gpu" ]; then
    max=$(tr ' ' '\n' < "$gpu/available_frequencies" | sort -n | tail -n 1)
    if number "$max"; then
        set_node "$gpu/max_freq" "$max"
        set_node "$gpu/min_freq" "$max"
    else
        echo 'UNSUPPORTED GPU frequency table'
    fi
else
    echo 'UNSUPPORTED KGSL devfreq'
fi
echo 'READY root session active; values above are kernel readbacks'
while alive; do sleep 1; done

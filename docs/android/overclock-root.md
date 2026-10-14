# Moonwitch Overclock Root — first build

Branch: `overclock-root`. Android flavor: `overclockRoot`.
Package: `dev.moonwitch.emulator.overclock`; launcher: **Moonwitch Overclock**.

Open Settings → Performance Engine → Maximum CPU/GPU, then launch a game
and grant Magisk root access. The setting defaults to disabled. Diagnostics
show requested values, immediate kernel readbacks, rejected writes and restoration.
The separate package has its own emulator data and game configuration.

The controller detects CPUFreq policies and their `cpuinfo_max_freq`, raises
max/min bounds, and selects the performance governor when advertised. For
Adreno it selects the largest advertised KGSL devfreq frequency. CPU values
are in kHz; KGSL devfreq values are in Hz. It uses the original kernel's
frequency table; the build name does not imply new frequency/voltage entries.
Reference: https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html

A root child process journals original settings in a root-only directory,
restores them on game stop or process death, and prevents simultaneous
controllers from replacing each other's snapshots. An Activity recreation
keeps the same session. The controller checks the app PID and process start
time once a second. There is no persistent Magisk boot module.

This first build implements session clock control and diagnostics. It does
not yet implement thread placement, memory changes or Vulkan changes.
Readbacks are settings accepted by the kernel, not continuous measured clocks;
vendor power management may subsequently change them. On-device validation
must check the diagnostics and actual clocks during TOTK gameplay.

Build: `.ci/android/build.sh -n -t overclock -b Release`.
The existing Android workflow selects this flavor on `overclock-root`.

Host checks: `python3 .ci/android/test_root_performance.py` tests temporary
CPU/GPU/proc fixtures for normal stop, app death, TERM and missing KGSL;
`python3 .ci/android/check_ui_resources.py` checks Android XML resources.
These do not substitute for an Android build or a Magisk device test.

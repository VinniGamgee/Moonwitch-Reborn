# Android performance: ThinLTO, shader reuse and native PGO

## Normal APK

The standard Android build enables CMake IPO with NDK Clang (ThinLTO).
The linker is limited to two ThinLTO backend jobs to bound CI memory use.
PGO defaults to OFF: no synthetic, missing or uncollected profile is used.

Lemon Project's fragment translation cache and SSA optimization are integrated;
see LEMON_PROJECT_PORTS.md for full original attribution. Moonwitch adds shader
precision and SPIR-V optimization mode to the reuse key. This is session-local
translation reuse, not a replacement for the existing disk pipeline cache.

For a controlled baseline build:
`.ci/android/build.sh -n -t standard -b Release -DENABLE_LTO=OFF`
For the normal optimized build omit that override. Compare the same game save,
scene, resolution, driver, settings and disk-cache warmth. Record frame-time
spikes and sustained FPS separately; successful compilation is not a measured
performance improvement.

## Collect a real Android PGO profile

The existing **Moonwitch Android** workflow has `pgo_mode` choices OFF, GENERATE
and USE. Pull requests compile and verify both OFF and GENERATE; regular main
pushes build OFF. GENERATE produces a training APK labeled PGO Training. It uses
the regular application ID and data directory, so it replaces the installed
Moonwitch package. The UI offers **Support → Export PGO profile** only in this
instrumented build. Normal APKs have no active profiling instrumentation.

1. Install the GENERATE APK. Record the exact source commit from its workflow.
2. Play representative sessions: load TOTK, explore, fight, visit demanding areas
   and include both shader compilation and steady gameplay. Other games may be
   included to avoid training exclusively for one workload.
3. Exit emulation normally. The app explicitly writes the LLVM profile; Android
   process termination is not relied upon to run `atexit`.
4. Open Settings → Support → Export PGO profile and share the ZIP.

Each process writes one cumulative snapshot without LLVM online merging. Repeated
exports overwrite that process's counters, rather than repeatedly adding them.
Profiles are separated by APK version code. Use the latest export of a given
build; do not combine duplicate snapshots from several exports. The ZIP includes
build version, native build identifier, device, ABI and NDK metadata.

## Build with the collected profile

Extract one export, then use the **same NDK 28.2.13676358**:

```sh
python3 .ci/android/merge_pgo.py --ndk /path/to/android-ndk \
  --profiles /path/to/extracted-export --output /path/to/moonwitch.profdata
.ci/android/build.sh -n -t standard -b Release \
  -DMOONWITCH_PGO_MODE=USE -DMOONWITCH_PGO_PROFILE=/absolute/path/to/moonwitch.profdata
```

For GitHub Actions, add the reviewed merged profile and its build metadata to
the repository, then use workflow_dispatch USE with its repository-relative
path. Match source revision/configuration to the training build and inspect
profile mismatch warnings. A missing profile fails configuration. Measure USE
against OFF with ThinLTO enabled in both; keep the profile only if results justify
it. Raw profiles and training builds are not evidence of a PGO FPS gain.

Instrumentation targets the emulator libraries linked into libyuzu-android.so,
not independent libraries, the game executable run by NCE or GPU shader code.
The GENERATE mode uses atomic counter updates for multi-threaded workloads.
The native runtime sets a writable path and explicitly flushes counters.

Reference: https://developer.android.com/ndk/guides/pgo

Root control, CPU frequency changes and benchmark package spoofing are not part
of this integration. Affinity/ADPF already present in Moonwitch are unchanged.

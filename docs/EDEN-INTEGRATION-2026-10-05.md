# Eden integration checkpoint — 2026-10-05

Target upstream: `10bcd2d849843b146a79c61a21df615e1bdcfcde` from the official `eden-emulator/mirror`.
Moonwitch baseline: `a1b8d4eb6b30c406f3428f3980f9503bee60d123`.

This branch integrates the Eden emulation core and required Android JNI/build dependencies while retaining Moonwitch resources, layouts, branding, and settings screens. It is not approved for main until Android CI passes and the application is tested on a device.

Recovery fixes include the matching HID/vector and Dynarmic memory callback migrations, the missing post-processing JNI declaration class, removal of obsolete texture-cache includes, and preservation of Moonwitch FP16/FP32 frame generation, GPU buffer readback, the Strict fence enum, and existing frame generation queue default. Oboe remains available alongside SDL3.

The Eden post-processing engine is present; its upstream settings UI is deliberately not imported. Exposing new UI controls requires a separate Moonwitch frontend design.

Validation so far: resource validator passed (370 XML files); no changes to Android resource or manifest files relative to baseline; no existing native settings keys removed; patch whitespace checks passed. Full Android compilation and runtime validation remain pending.

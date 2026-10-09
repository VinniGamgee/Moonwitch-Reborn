# Post-processing effects

Android/Vulkan integration adapted from Eden commit
`ed566919f468e45ecb56e4e61185d98142a2cd1c` (PR #4348), authored by
CamilleLaVey <camillelavey99@gmail.com>:
https://git.eden-emu.dev/eden-emu/eden/pulls/4348

Original notices are retained in the implementation and bundled effects.
Upstream additionally credits the PPSSPP contributors, Niklas Haas and bloc97;
see each effect header for its specific attribution and license.
ReShade FX compiler v6.8.0 is pinned in cpmfile.json.

## Usage

Open Post-Processing Effects from Settings, the game's properties, or Graphics.
Choose a preset or add effects and adjust their values. During gameplay, the
quick settings panel exposes the effect controls. Global and per-game settings
are stored separately by the upstream configuration code.

Includes 22 effects and six presets. The chain starts empty, so no new effect is
applied by default. The old Moonwitch color style and its intensity control are
removed, including their shader and Vulkan pass. Old color-style config keys
are no longer read; they are not silently mapped to a new preset.

Effects use screen images rather than scene depth. Their cost depends on the
selected effects, resolution and device. Scaling, CAS and anti-aliasing controls
remain independent.

## Integration scope

Ports the initial Eden Android/Vulkan implementation, adapting its settings
entries to Moonwitch's navigation and quick settings. Desktop UI changes from
the upstream commit are excluded. Later Eden applet-layer filtering requires
layer-stack metadata absent from this Moonwitch base and is not included.

The Lemon NCE image-preservation fix in `4e6eaab` is retained unchanged. This port
does not alter reactive flushing, the NCE invalidation path, GPU image download
preservation or the buffer cache. On-device rendering and performance validation
remain necessary after a successful Android build.

# Moonwitch themes

The main interface includes the paged game library, per-game controls and in-game
panels from reformulation. Interface settings offer the original Moonwitch palette
plus Gaming (violet/mint), AMOLED (black), Cyberpunk (yellow/cyan), Monocromático
(neutral greys) and Atmosphere (teal/sand with a subtle static background gradient).

A single persisted app preference selects stable IDs 0–5. Invalid IDs fall back to
Moonwitch. Liquid Glass's runtime renderer, callbacks, bitmap captures and resources
have been removed. Its old preference is ignored and cleared when selecting a theme.
Shared palette attributes cover layouts, cards, borders, artwork scrims, controls,
Material components, dialogs and menus. The app logo and game artwork retain their
original colors. Android's initial system splash uses the default brand color;
the following app splash uses the selected theme. These themes add no frame loops.

The stable application ID is dev.moonwitch.emulator; existing reformulation and
TOTK Diagnostics installations remain separate. Back up/export experimental saves
before uninstalling those experimental apps. The release does not delete app data.

## Removed experiments and cleanup

The c202a827 / 91c3d38 / cdee263 TOTK diagnostic revisions are not promoted to main.
The older Android TOTK-specific compute workaround and unused write-classification
helper are also removed, following unsuccessful device testing. There is no claim
that TOTK foliage corruption is fixed. Ordinary renderer synchronization is retained.
The GPU blackbox recorder now returns before timestamps, locking or file output
unless the existing renderer-debug setting is enabled. Normal error logs remain.

The obsolete theme listener retained an Activity in a process-global callback and
could register repeatedly. Theme changes now use the existing settings recreation
and MainActivity resume checks; unused legacy theme-selection code was removed.

Validation: XML parse checks, static palette contrast checks, resource compilation
and full Android CI before promotion. Device visual/performance checks remain a
follow-up; a successful build does not validate every rendered screen on hardware.

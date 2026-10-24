# Lemon interface adaptation

Moonwitch's Android interface is adapted from **Ghael-V / Lemon Project**, version
1.2.2, commit `25adc8800050828deb6f14123f6724bf92feec4b`:
https://github.com/Ghael-V/Lemon-Project

Original interface design and Compose implementation belong to Lemon Project and
its contributors. Original copyright notices and GPL-3.0-or-later licensing are
retained. Existing Eden and yuzu notices remain in their respective files.
Moonwitch contributes the integration with its existing models, palette and actions.

The library (carousel, grid and list), game details, settings cards and rows,
setup, statistics, About screen, loading screen and in-game menu reuse Lemon's
components. The right quick-settings drawer keeps Moonwitch's live controls with
the matching panel styling. Existing secondary managers and dialogs continue using
their Android Views, with Moonwitch colors and the shared typography.

Adapters keep Moonwitch's launch flow, per-game configurations, PGO export,
Core Director/Unleashed controls, drivers, saves and custom artwork/music. Session
statistics use Moonwitch's existing native-emulation session store. No Lemon-only
performance presets, bundled drivers, migration actions or native APIs are imported.
Search on the home settings page filters Moonwitch categories and existing actions;
advanced setting search remains available in the existing settings screen.

The six existing Moonwitch palettes supply Compose colors through ThemeHelper.
Bricolage Grotesque and DM Sans are copied unchanged from Lemon. Their original
SIL Open Font License notices are in `dist/fonts/` and the app's Licenses page.
The About page prominently credits Lemon and links to its source repository.

Loading and menu compositions are removed while hidden, so their timers, focus
requests and animations do not continue during normal emulation.

## Responsive layout and menu follow-up

Settings values now live below their titles in the same weighted text column. Previously a long
unweighted value plus trailing controls could consume the entire row width, leaving the title and
description with zero width and a huge wrapped height. This applies to all choice, slider, path,
input and GPU-unswizzle rows. Home cards grow with their content and use adaptive grid columns.
Library actions share available width, long titles use two lines with ellipsis, and shortcut labels
wrap. Buttons grow for translated labels and larger system fonts.

The in-game Compose panel calls the fragment's shared action handler directly. MoonwitchControlPanel
is a FrameLayout with a PopupMenu model, not a NavigationView: performIdentifierAction does not call
its separately stored navigation listener. The old Material buttons previously called that listener
themselves. The panel now honors exact widths, hides the performance card when it cannot fit, and
uses one menu column on narrow drawers. Hidden legacy buttons no longer sync on every pre-draw.

Validation: XML resource validation and whitespace checks; CI compiles the Android interface before
the native APK build. Device checks should cover long Portuguese values, increased system font size,
portrait/landscape library buttons, and pause/overlay/quick settings/settings/exit menu actions.

## Moonwitch shader startup screen

The startup screen is now a separate Moonwitch layout: game icon, three phase markers, native cache
entry counts, percentage and a progress bar. It reads a single immutable StateFlow snapshot instead
of polling hidden progress Views. It does not perform additional shader compilation or discover
shaders from unvisited gameplay. Counts describe processed disk-cache entries (pipelines), not a
promise that every entry was successfully compiled or that all shaders in the game are known.

Session initialization remains independent from first-frame presentation. The existing early native
session callback still initializes session accounting; a new JNI first-frame callback reveals the
surface and input controls. Disk-cache completion retains 100% and transitions to Starting game;
empty/disabled caches go directly to that phase. Intent launches follow the same path. Shutdown
has its own indeterminate display and late first-frame callbacks cannot dismiss it.

Regression coverage: EmulationStartupTest exercises early initialization, cache completion,
empty/disabled cache, stop/next-game reset and bounded/large counts. CI runs it before building the
APK. On-device checks still need cached/uncached launches, intent launches, rotation and game swaps.

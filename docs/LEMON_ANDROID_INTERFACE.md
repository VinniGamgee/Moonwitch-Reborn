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

# Reformulation — experimental Android interface

This branch explores the approved Moonwitch interface concept. It does not change
native emulation settings, defaults, graphics algorithms or compatibility behavior.

- One game per viewport in portrait and landscape, with horizontal finger-following
  paging, snap, previous/next buttons, title search, favorites and library refresh.
- Main navigation has only Library and Settings. Settings are grouped into the
  essential categories, System, Data and Library, and Diagnostics.
- Existing game artwork under `moonwitch/metadata/<settingsName>` is reused;
  without artwork, the game's icon supplies the background and foreground cover.
  No game artwork or game content is downloaded or bundled.
- Mint/navy/lavender theme, compact controls, system-bar/cutout/keyboard insets,
  per-game/global scope labels and updated application branding.
- The supplied logo arrived as JPEG despite its `.svg` filename. The circular mark
  is reproduced as an Android vector, with a transparent exterior; monochrome
  notification variants follow Android's small-icon requirement.
- Experimental application ID: `dev.moonwitch.emulator.reformulation`.
  It installs alongside the stable app and has separate data. Select game folders
  and import your own system files; use the existing data export/import actions
  if you want to transfer settings and saves. Do not uninstall the stable app.

## Build

Pushes to `reformulation` run `build-android-pgo.yml`. This builds the standard ARM64 release APK with the existing
Android script. PGO follows the existing Android build workflow. APKs are attached as Actions
artifacts named `MOONWITCH-REFORMULATION`. Nothing merges into `main`.

## Device checks (POCO F5)

The UI measures available Android dp; it does not hard-code the physical chassis
size or stretch a 1080×2400 image across controls. Verify on-device:

1. Portrait and landscape, status bar, camera cutout and gesture/navigation areas.
2. Empty library, one game, multiple games, long titles, large system font scale.
3. Drag in either direction, swipe cancellation, first/last page, rotation and return
   from a game. The selected game should remain selected.
4. Search, recent/favorites filters, add folder, refresh and missing-file handling.
5. Play with global/per-game settings, details, custom artwork and favorite changes.
6. Every settings category, driver manager, controls, data management and diagnostics.
7. Launcher, splash, about and notifications show the new application mark.

Mockup game names, artwork and performance values are not application content.
Visual/device validation remains separate from successful compilation.

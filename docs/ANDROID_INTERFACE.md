# Moonwitch Android interface

The current Android interface is the stable Moonwitch frontend on the
`dev.moonwitch.emulator` application ID.

## Library and navigation

- One game per viewport in portrait and landscape, with horizontal paging and snap behavior.
- Previous/next controls, title search, favorites, recent filters and library refresh.
- Main navigation keeps Library and Settings as the primary surfaces.
- Existing artwork under `moonwitch/metadata/<settingsName>` is reused. When custom artwork is
  unavailable, the game icon is used without downloading or bundling game artwork.

## Theme and layout

The interface uses the Moonwitch palette system documented in `THEMES.md`. Layouts account for
system bars, display cutouts and the software keyboard. The application mark is shared by launcher,
splash, headers, about surfaces and notification-specific monochrome assets.

## Build

Pushes to `main` and stabilization branches run `.github/workflows/build-android.yml`. The
workflow builds the standard ARM64 Release APK with the Android build script.
Artifacts are published as `MOONWITCH-ANDROID`.

## Device validation

Successful compilation is necessary but does not replace physical-device checks. Before a release,
verify at least:

1. Portrait and landscape with status bar, camera cutout and gesture/navigation areas.
2. Empty library, one game, multiple games, long titles and large system font scale.
3. Paging in both directions, swipe cancellation, first/last page, rotation and return from a game.
4. Search, recent/favorites filters, add folder, refresh and missing-file handling.
5. Global/per-game settings, details, custom artwork and favorite changes.
6. Settings categories, driver manager, controls, data management and diagnostics.
7. Launcher, splash, about and notifications use the Moonwitch branding consistently.

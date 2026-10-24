# Android UI and startup review

Reviewed baseline: af9ff2fec7e1a8980a5442ca919100097d9b65c8.
Scope: recent Compose integration, game startup callbacks, in-game drawers, per-game
post-processing scope and existing cache regression checks. This is not a whole-emulator
correctness or game-compatibility certification.

## Corrections

- Drawer state was both a report and a command: opening the right drawer published true,
  whose observer opened the left drawer. Closing either drawer then published false even
  if the other was visible. State now reports actual visibility; Back explicitly opens or
  closes drawers. Focus goes to the drawer that opened, and locks restore only when both close.
- The Back callback belonged to the Activity rather than the fragment view, retaining an old
  callback across view recreation. It now uses viewLifecycleOwner. View setup is guarded
  against duplicate registrations and resets on destruction.
- programChanged defaults to -1, but its collector tested != 0. This treated the initial
  sentinel as a real transition and ignored program zero. The collector now accepts >= 0.
- The drawer width was only computed at setup. Configuration changes now recompute it.
- A synthetic MotionEvent allocated on every drawer slide was not recycled. It now is.
- Temporarily loaded per-game configuration now unloads in finally if an operation throws.
- Every interactive Compose element ran a perpetual pulse, even when unselected. Only
  selected elements now start that animation; press/focus transitions remain intact.
- CI now runs the complete release unit-test suite rather than filtering one class.

## Validation

The baseline Android interface compilation and startup unit-test step passed in run
40126146533. The native APK build was still running during this review.
Local resource validation passed for 399 XML files. git diff --check passed.
The existing single-flight cache executable passed: one producer, independent keys,
LRU eviction, retained values, retry after failure, and key-byte budget.
The follow-up CI checks the changed interface and unit tests before building the APK.

Still requires device verification: switching left/right drawers, Back after view recreation,
rotation while a drawer is open, external-intent launches, program switches, and first-frame
handoff. Host tests do not establish graphics correctness or performance on Adreno.

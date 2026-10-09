# NCE GPU image preservation

Backported without functional changes from Lemon commit
`3b9ed8782de5b2322755d11f9f9734214501f77f`, included in Lemon v1.1.0:
https://github.com/Ghael-V/Lemon-Project/commit/3b9ed8782de5b2322755d11f9f9734214501f77f

Author: Ghael-V <ghael_v@outlook.es>.
Original co-author: Claude Opus 5.5 <noreply@anthropic.com>.
Original source license notices are preserved.

NCE reports accesses at page granularity. Invalidating an entire page can mark
GPU-written images CPU-modified even though guest memory does not contain their
latest GPU contents. Uploading that memory again can discard those contents.

Before this NCE invalidation, the backport checks for GPU-written images, queues
their download on the GPU thread, waits for completion, and then invalidates the
region. Downloads use the existing safety checks, modification ordering and
image layout conversion, with one GPU completion wait for the batch.
Other invalidation callers retain their existing behavior through a default-false
preservation parameter. No buffer-cache or shader changes are included.

## Runtime status

The owner reports Lemon v1.1.0 preserves TOTK grass with Reactive Flushing enabled
and still loses it with that option disabled. This is the reference configuration
for Moonwitch testing. The patch itself is not gated by the option; it does not
establish that the disabled configuration is fixed. Existing option defaults and
saved per-game settings are unchanged.

Moonwitch device validation is pending. Use NCE and enable Reactive Flushing in
the game's graphics settings before starting TOTK. Source/build verification is
not a substitute for confirming rendering on the device. The extra GPU-to-CPU
copies and waits can affect performance.

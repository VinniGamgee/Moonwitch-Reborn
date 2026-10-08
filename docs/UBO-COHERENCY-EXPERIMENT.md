# UBO coherency experiment v1

Base: `main` at `b99ebdb7e12933ad35c2775d355d786077d6e555` (emulation source unchanged
from `56aa3425d67bdc47fd4328e67b1e1cf3366078a1`).

The experiment targets the uniform-buffer alignment streaming path introduced upstream
in Eden `a27d35362d288b9af6c76cadec664e9453a9d237`. Graphics and compute UBOs that
are both unaligned for the host and GPU-modified are synchronized for pending CPU
uploads, then copied GPU-to-GPU into a zero-offset, device-local staging allocation.
The allocation retains the original descriptor range and uses the staging pool's
existing completion-tick lifetime management. CopyBuffer's barriers order producers,
transfer and shader consumption. The CPU-clean streaming path is unchanged.

For byte offsets or rounded allocation tails that cannot satisfy vkCmdCopyBuffer's
four-byte requirements, only the affected GPU-modified range is downloaded before
the existing CPU streaming path. This fallback can stall and is separately counted.
No unconditional WaitIdle or readback is added.

`UBO_COHERENCY v1` logs graphics/compute totals after the first frame and every 300
frames: `bindings`, `unaligned`, `gpu_modified`, `gpu_copies`, `readbacks`. The first
four copies/readbacks per stage also log immediately. Counters are session-local.

- `unaligned=0`: this execution has not exercised the alignment branch.
- `unaligned>0`, `gpu_modified=0`: alignment streaming occurred, but the suspect
  GPU-written case has not occurred according to the existing memory tracker.
- `gpu_copies>0` or `readbacks>0`: the corrected case was exercised. Disappearance
  despite that means this correction alone was insufficient; stability is evidence
  requiring longer confirmation, not proof of a unique cause.
- Missing log lines do not prove a zero count: ensure Info logging for HW_GPU is
  enabled and the log is from this build. Logs cannot prove the absence of a write
  missed by the underlying memory tracker.

The SSBO size heuristic, texture descriptor experiments, UI and driver capabilities
are unchanged. `.ci/tests/test_ubo_coherency.py` compiles the actual helper bodies
against separate CPU/GPU-memory mocks and checks both stages, mixed CPU/GPU data,
snapshot reuse, clean-memory bypass, transfer bounds and readback fallback. It is
not a Vulkan driver or Android integration test; the Android workflow provides the
full compilation gate, and the on-device game test is still required.

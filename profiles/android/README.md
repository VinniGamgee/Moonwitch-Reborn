# Android native PGO training profile

Collected by the repository owner on POCO F5 from training build
`822fa33e01` (PR #7 merge tree, head `ca91fe964d65af21d5764a233b16489bf33cef19`).
NDK: 28.2.13676358. Source export: Moonwitch-PGO-1792088571121.zip.

The latest export includes three process profiles. Older exports duplicate those
sessions. The LLVM writer appended cumulative snapshots: two in process 557609e5,
one in 08fba9ab, and three in 7e53b068. Only the last complete snapshot of each
process was retained and validated with the matching NDK llvm-profdata before
merging. No sparse filtering was used. The merged profile contains 39,983 functions.

The xz file expands to a native LLVM indexed profile. CI builds OFF and USE
for comparison, plus GENERATE for collection validation. Normal main builds still
default to OFF until device measurements justify using the profile by default.
The profile records emulator execution, not game binaries or assets.

Uncompressed SHA-256: `2a7de8fd05ea5acd92b17d89d834fcca687b48b8250dc2c5322380fa1008f57c`.

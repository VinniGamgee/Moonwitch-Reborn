# Android native PGO training profile

Collected by the repository owner on POCO F5 from training build
`dfd43ca318632c40d6ee2e19bba5658cd466b14b` (PR #7 merge tree,
head `0582d3769b0b3f4a71bd9a5b0d8845e0c6d6ce87`).
NDK: 28.2.13676358. Source export: pgo.zip.
Export SHA-256: `6ad95f79f3c8ed93fc131eaf25216c1336749e58a0b700d1e31be093e11e4150`.

The owner reports nearly 20 minutes of TOTK exploration, combat, Kakariko,
horse riding, paraglider flight, abilities, bombs, teleportation and dialogue.
The export contains one process (1c1fe215-3458-4a84-a418-e8842bcb18b9), with
two cumulative LLVM snapshots at offsets 0 and 6024568. Only the final complete
snapshot was retained, then validated and merged with the matching NDK
llvm-profdata. This replaces the earlier short collection; it does not add
old cumulative profiles again. No sparse filtering was used.
The merged profile contains 39,983 functions. Profile validity is not evidence
of a performance gain; compare USE with OFF on the same scenes and settings.

The xz file expands to a native LLVM indexed profile. CI builds OFF and USE
for comparison, plus GENERATE for collection validation. Normal main builds still
default to OFF until device measurements justify using the profile by default.
The profile records emulator execution, not game binaries or assets.

Uncompressed SHA-256: `29005329b78524a39d075fcf9ff63a3dab62cc76acd553e1ae1fdd41a260af4a`.

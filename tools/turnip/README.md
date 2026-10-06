# Moonwitch Turnip A7xx

Reproducible Android/KGSL Turnip builds for Moonwitch, based on Mesa 26.2.4
(commit `96cb43121031992b85767f9c1be8f3f48e22b1d2`).

The driver packages are standard AdrenoTools ZIPs containing
`libvulkan_freedreno.so` and `meta.json`.

## Variants

### performance

The baseline Moonwitch driver. It keeps Mesa/Turnip's upstream GMEM/sysmem and LRZ decisions and
uses Mesa's release optimization profile. No diagnostic `TU_DEBUG` mode is forced.

Use this first when evaluating frame time, shader compilation behavior and general compatibility.

### foliage-nolrz

For disappearing vegetation/depth-rejection investigation. It forces:

```
TU_DEBUG=nolrz
```

If this alone fixes the foliage issue, LRZ/depth rejection becomes the primary suspect.

### foliage-sysmem

For GMEM/tile-rendering investigation. It forces:

```
TU_DEBUG=sysmem
```

If this fixes foliage or black tiles while `performance` does not, the issue is likely tied to
the GMEM/tile path, resolves, or attachment lifetime rather than shader compilation alone.

### foliage-safe

A more conservative compatibility diagnostic. It forces the equivalent of:

```
TU_DEBUG=nolrz,noconcurrentresolves,noconcurrentunresolves
```

This intentionally trades some performance for simpler depth/resolve behavior. It is not intended
to become the default performance driver unless device testing shows a clear need.

## TOTK test procedure

Use the same game version, save, emulator settings and shader-cache state for every run. Test the
same foliage-heavy scene for at least a few minutes.

Recommended order:

1. performance
2. foliage-nolrz
3. foliage-sysmem
4. foliage-safe

Record whether grass disappears, whether black tiles appear, approximate time until failure, FPS,
frame-time behavior and any device loss/crash.

The variants are diagnostic by design. A positive result should be narrowed to the smallest
responsible Turnip path before any workaround is promoted to the normal driver.

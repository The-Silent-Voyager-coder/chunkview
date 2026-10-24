# Changelog

## 0.4.1 — 2026-10-24

- Added a one-line join message showing the active LOD/DH mode and the F6/status hints (`showJoinMessage`).

## 0.4.0 — 2026-10-24

- Added Sodium integration: Sodium's own render-distance slider now reaches the ChunkView maximum, and Sodium's terrain section radius is capped to the full-detail ring while LOD or Distant Horizons owns the far terrain.
- Sodium's culling radius follows the adaptive governor when LOD is off, so the governor actually reduces Sodium's terrain work.
- Added an integrated-server view-distance cap so the server stops requesting the raw 64-chunk slider on low-memory devices.
- `/chunkview reload` and presets now reset the section renderer, so a new `fullDetailDistance` takes effect without rejoining the world.
- The governor now grows the visible radius when VSync pins FPS to the refresh rate; previously it could shrink once and never grow back.
- LOD building now respects the heap-safe render distance instead of the raw slider.
- Lower default startup pressure: `initialVisibleDistance` is 48 (was 64) and entity culling is 192 blocks (was 128).

## 0.3.1 — 2026-10-23

- Fixed the `ViewArea` constructor clamping an uninitialized render distance of 0 to 2 chunks.
- Only clamp the vanilla section grids when ChunkView LOD (or DH mode) is actually active; with LOD disabled and no DH, vanilla now keeps its full render distance.
- Logging is quieter: grid changes are logged only when the value actually changes.

## 0.3.0 — 2026-10-23

- Added automatic **Distant Horizons mode**: when DH is installed, ChunkView disables its own heightmap LOD so DH owns the far terrain.
- Fog matching is disabled in DH mode so DH's far LOD is not hidden.
- `/chunkview status` now reports when DH owns the far terrain.
- Added `disableLodWithDistantHorizons` config option.
- Added Modrinth release description.
- Verified the DH stack with Minecraft 26.2, Distant Horizons 3.3.3-26.2 and Sodium 0.9.2+mc26.2.

## 0.2.0 — 2026-10-23

- Added adaptive visible-radius governor, entity culling and block-entity culling.
- Added 2-block LOD quality option, leaf-skirt suppression and improved seam handling.
- Raised default full-detail radius to 24 chunks.
- Added FPSMaxx attribution.

## 0.1.0 — 2026-10-23

- Initial ChunkView release: 64-chunk render-distance slider, heightmap LOD, commands, memory guard and Pojav support.

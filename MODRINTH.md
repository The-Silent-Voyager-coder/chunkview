# ChunkView

ChunkView is a client-side Fabric mod for **Minecraft 26.2** that raises the render-distance slider from 32 to **64 chunks** and then protects the frame rate with an adaptive visible-radius governor.

The governor keeps chunks **loaded** at the requested distance but shrinks only the submitted/visible ring when FPS falls, so moving does not cause repeated chunk-loading stutter. A lightweight heightmap LOD fills the far ring when Distant Horizons is not installed.

## Features

- **64-chunk slider** — Video Settings now reaches 64 instead of 32.
- **Adaptive FPS governor** — targets 62 FPS by default, keeps chunks loaded, and adjusts only the visible ring. Toggle with **F6**.
- **Vanilla full detail near the player** — `fullDetailDistance` defaults to 24 chunks.
- **Heightmap LOD fallback** — 2/4/8-block cells, color sampling, skirts for height steps, and no leaf skirts.
- **Distant Horizons auto mode** — if DH is installed, ChunkView disables its own heightmap LOD and lets DH own the far terrain, preserving 3D shapes and structures.
- **Sodium integration** — extends Sodium's render-distance slider to 64 and caps Sodium's terrain meshing to the full-detail ring while LOD/DH fills the distance.
- **Entity and block-entity culling** — configurable cull distances.
- **Fog matching** — fog follows the governed visible ring; disabled automatically in DH mode so DH's far terrain stays visible.
- **Memory guard** — caps the slider/server distance from the JVM heap (64 chunks at 4 GB+, 48 at 3 GB, 32 at 2 GB, 24 at 1.5 GB, 16 below).
- **Presets** — `/chunkview preset phone|balanced|quality`.
- **PC and PojavLauncher** — one standard Fabric jar. Pojav needs Java 25.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0+26.2 or newer |
| Java | 25 |

Optional, recommended for the best far-terrain quality:

- Distant Horizons `3.3.3-26.2` or newer
- Sodium `0.9.2+mc26.2` or newer

## Installation

1. Install Fabric Loader 0.19.5+ for Minecraft 26.2.
2. Install Fabric API 0.161.0+26.2.
3. Put `ChunkView-0.4.2-mc26.2.jar` in `.minecraft/mods`.
4. Launch with Java 25.
5. Set Render Distance to 64 in Video Settings.
6. Turn VSync off and raise the frame cap if you want to measure above 60 FPS.

For Distant Horizons mode, also install DH and Sodium. ChunkView detects DH and disables its own LOD automatically.

## Commands

| Command | Description |
| --- | --- |
| `/chunkview status` | Visible radius, full-detail radius, FPS, target, loaded chunks, heap-safe max. |
| `/chunkview adaptive <true/false>` | Toggle the governor. |
| `/chunkview target <30-240>` | Set the target FPS. |
| `/chunkview min <4-64>` | Lowest visible radius. |
| `/chunkview preset phone\|balanced\|quality` | Apply a preset. |
| `/chunkview reload` | Reload `config/chunkview.json`. |

## Notes

- The heightmap fallback is designed for one-jar setups and weak hardware. It is cheaper than full-detail 64 chunks, but distant structures lose detail.
- Distant Horizons mode is the visual-fidelity option: DH keeps 3D terrain and structures, at a higher hardware cost.
- ChunkView does not make a dedicated server send chunks it does not have.
- Simulation distance is not forced by ChunkView. Lower it manually on CPU-limited devices.

## Links

- Modrinth: https://modrinth.com/mod/chunkview
- Source: https://github.com/The-Silent-Voyager-coder/chunkview
- Issues: https://github.com/The-Silent-Voyager-coder/chunkview/issues
- License: MIT

## Credits

The keep-chunks-loaded adaptive visible-radius strategy and several vanilla mixins were adapted from **FPSMaxx** by Adaptablxus (CC0-1.0): https://github.com/Adaptablxus/fpsmaxxing. The heightmap LOD renderer, governor thresholds, commands and configuration are original ChunkView code. See the repository's `THIRD_PARTY_NOTICES.md` for the specific mixins.

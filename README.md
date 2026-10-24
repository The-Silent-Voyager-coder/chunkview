# ChunkView

ChunkView is a client-side Fabric mod for Minecraft 26.2 that raises the render-distance slider from 32 to 64 chunks. It keeps vanilla full-detail terrain near the player and draws the far ring with a lightweight heightmap LOD, then protects the frame rate with an adaptive visible-radius governor.

The governor keeps chunks **loaded** at the requested distance but shrinks only the part that is submitted when FPS falls. That avoids the chunk-load hitch you get from changing the real render distance. This is the same broad strategy used by [FPSMaxx](https://github.com/Adaptablxus/fpsmaxxing) (CC0-1.0). Several vanilla mixins are adapted from FPSMaxx; the heightmap LOD renderer, governor thresholds, commands and configuration are ChunkView's own code. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Features

- **64-chunk slider** — Options > Video Settings > Render Distance now reaches 64 instead of 32.
- **Adaptive FPS governor** — targets 62 FPS by default, shrinks the visible ring when FPS drops, and grows it back after a stable period. Chunks stay loaded. Press **F6** to toggle it.
- **Full-detail clamp** - vanilla chunk meshes, lighting and entities are limited to `fullDetailDistance` (24 by default). Everything beyond is LOD.
- **Distant Horizons auto mode** - if DH is installed, ChunkView disables its own heightmap LOD and lets DH own the far terrain. The 64 slider, governor, culling and memory guard stay active.
- **Sodium integration** - extends Sodium's own render-distance slider to 64 and caps Sodium's terrain section radius to the full-detail ring, so Sodium does not mesh all 64 chunks at full detail. Chunks stay loaded; the LOD/DH fills the far ring.
- **Heightmap LOD** — far terrain is merged into 2/4/8-block cells with skirts for height steps. Leaves do not get skirts, which avoids giant green walls under far canopies.
- **Entity and block-entity culling** — entities and block entities beyond the configured distance are skipped before extraction.
- **Fog matching** — fog follows the governed visible radius so the reduced ring never shows a hard edge.
- **Memory guard** — the slider/server distance is capped from the JVM heap (64 chunks at 4 GB+, 48 at 3 GB, 32 at 2 GB, 24 at 1.5 GB, 16 below). Override with `-Dchunkview.ignoreMemoryLimit=true`.
- **Presets** — `phone`, `balanced` and `quality` change full-detail radius, LOD cell size and culling in one command.
- **Client-side** — no server installation. The server mixins only run in the integrated server used by singleplayer/LAN.
- **PC and PojavLauncher** — one standard Fabric jar. Pojav needs a Java 25 runtime for 26.2.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0+26.2 or newer |
| Java | 25 |

Java 25 is mandatory for Minecraft 26.2. PojavLauncher must use a Java 25 runtime; Java 17 or 21 fails before the title screen.

Optional: [Sodium](https://modrinth.com/mod/sodium) `0.9.2+mc26.2` for the Sodium renderer path, and [Distant Horizons](https://modrinth.com/mod/distanthorizons) `3.3.3-26.2` for voxel far terrain.

## Installation

### PC

1. Install Fabric Loader 0.19.5 or newer for Minecraft 26.2.
2. Install Fabric API 0.161.0+26.2.
3. Put the Fabric API jar and the ChunkView jar in `%APPDATA%\.minecraft\mods`.
4. Launch the Fabric 26.2 profile with Java 25.

### PojavLauncher

1. Update PojavLauncher to a build that supports Minecraft 26.2.
2. Add/select a Java 25 runtime for the instance.
3. Install Fabric Loader 0.19.5+ for 26.2 and put Fabric API + ChunkView in the instance's `mods` folder.
4. Use the `phone` preset and a render distance of 32 or lower on most phones. The slider can still reach 64, but 64 chunks is memory-heavy.

## Usage

1. Launch the game and open **Options > Video Settings**.
2. Set **Render Distance** to 64 (PC) or 32–48 (phone).
3. Turn **VSync off** and set the framerate cap to 120+ if you want to measure above 60 FPS. With VSync on, a 60 Hz panel physically caps at 60.
4. Press **F6** to toggle the adaptive governor. `/chunkview status` shows the current visible radius, FPS, target and loaded chunks.

The LOD fills the band beyond `fullDetailDistance`. Near LOD cells are discarded under the vanilla terrain, and the LOD starts just inside the vanilla grid edge to avoid a gap, with a small overlap hidden by vanilla terrain.

### Commands

| Command | What it does |
| --- | --- |
| `/chunkview` or `/chunkview status` | Show visible radius, full-detail radius, FPS, target and heap-safe max. |
| `/chunkview adaptive <true/false>` | Toggle the governor. |
| `/chunkview target <30-240>` | Set the target FPS (default 62). |
| `/chunkview min <4-64>` | Lowest visible radius the governor may use. |
| `/chunkview preset phone\|balanced\|quality` | Apply a preset and save it. |
| `/chunkview reload` | Reload `chunkview.json` and rebuild LOD. |

### Configuration

The config is created at `.minecraft/config/chunkview.json` (Pojav: inside the instance's `.minecraft/config`). Edit it with the game closed, or run `/chunkview reload`.

| Field | Default | Description |
| --- | --- | --- |
| `maxRenderDistance` | `64` | Highest slider/server value. Also capped by the memory guard. |
| `fullDetailDistance` | `24` | Vanilla full-detail radius in chunks. |
| `lodCellSize` | `2` | Blocks per LOD cell: `2`, `4` or `8`. `2` is best for structures, `8` is phone-friendly. |
| `adaptiveEnabled` | `true` | Enable the FPS governor. |
| `targetFps` | `62` | FPS the governor tries to hold. |
| `minVisibleDistance` | `16` | Lowest visible radius. |
| `initialVisibleDistance` | `48` | Starting visible radius on world join. |
| `entityCullDistance` | `192` | Skip entities beyond this many blocks. `0` = off. |
| `blockEntityCullDistance` | `128` | Skip block entities beyond this many blocks. `0` = off. |
| `matchFogToVisibleRing` | `true` | Move fog with the governed visible radius. |
| `regionBuildsPerFrame` | `1` | LOD regions rebuilt per tick (1–4). |
| `lodOverworldOnly` | `true` | Build LOD only in the Overworld. |
| `lodEnabled` | `true` | Master LOD switch. |
| `disableLodWithDistantHorizons` | `true` | When DH is installed, disable ChunkView's heightmap LOD and let DH own the far terrain. |
| `sodiumIntegration` | `true` | Cap Sodium's terrain section radius in LOD/DH mode and follow the governor without LOD. |
| `sodiumConfigOverlay` | `true` | Extend Sodium's own render-distance slider to the ChunkView maximum. |
| `lodSkirts` | `true` | Vertical skirts on solid cells. |
| `debug` | `false` | Diagnostic region logging. |

```json
{
  "maxRenderDistance": 64,
  "fullDetailDistance": 24,
  "lodCellSize": 2,
  "adaptiveEnabled": true,
  "targetFps": 62,
  "minVisibleDistance": 16,
  "initialVisibleDistance": 48,
  "entityCullDistance": 192,
  "blockEntityCullDistance": 128,
  "matchFogToVisibleRing": true,
  "regionBuildsPerFrame": 1,
  "lodOverworldOnly": true,
  "lodEnabled": true,
  "disableLodWithDistantHorizons": true,
  "sodiumIntegration": true,
  "sodiumConfigOverlay": true,
  "lodSkirts": true,
  "debug": false
}
```

### Presets

| Preset | Full detail | LOD cell | Min visible | Entity cull |
| --- | --- | --- | --- | --- |
| `phone` | 8 | 8 | 8 | 48 |
| `balanced` | 12 | 4 | 12 | 96 |
| `quality` | 24 | 2 | 16 | 128 |

Use `quality` on a desktop GPU when you care about distant structures. Use `phone` on Pojav/integrated graphics.

## Performance notes

The full-detail radius is the main cost. 20 chunks is 1,681 chunk columns; 24 is 2,401. The LOD is cheaper per chunk but not free. At 64 chunks the client loads 16,641 columns, so allocate **4–8 GB** of Java heap; 6 GB is a reasonable starting point.

The governor does not speed up mob AI, block ticking, world generation or the server. Lower simulation distance if the CPU is the bottleneck. On a dedicated server the server's own view distance still applies; ChunkView cannot make a server send chunks it does not have.

### Far structures

The far ring is a heightmap LOD, so distant buildings lose interior detail and overhangs. To keep structures recognizable:

- Use `/chunkview preset quality` (24 full-detail chunks, 2-block LOD cells).
- Keep `lodSkirts` enabled; structure walls are preserved as side faces.
- Raise `fullDetailDistance` if you have GPU/CPU headroom. It costs quadratically.

### Sodium

Sodium replaces vanilla's `ViewArea` and `SectionUpdateTracker`, so ChunkView has a separate Sodium path:

- Sodium's own render-distance slider is extended from 32 to the ChunkView maximum (`sodiumConfigOverlay`).
- While LOD or Distant Horizons owns the far ring, Sodium's terrain search radius is capped to `fullDetailDistance`. Sodium stops meshing the 64-chunk ring at full detail; chunks stay loaded and the LOD/DH draws the distance.
- Without LOD, Sodium's radius follows the adaptive governor's visible ring.
- Sodium's fog is left alone in LOD/DH mode so the far ring is not hidden.

Tested with Sodium `0.9.2+mc26.2`. Set `sodiumIntegration` to `false` only if another mod already manages Sodium's render distance (for example C2ME).

### Distant Horizons mode

If [Distant Horizons](https://modrinth.com/mod/distanthorizons) is installed, ChunkView detects it and **turns off its own heightmap LOD**. DH's voxel LOD keeps 3D shapes, overhangs and structures, which is the better choice for visual fidelity.

Tested stack for Minecraft 26.2:

- ChunkView 0.4.0
- Distant Horizons 3.3.3-26.2
- Sodium 0.9.2+mc26.2

In this mode:

- ChunkView still raises the slider, keeps chunks loaded, adapts the visible radius and culls distant entities/block entities.
- DH owns the far terrain and its own config screen (`LOD Chunk Render Distance Radius`, quality preset, CPU load).
- ChunkView's fog override is disabled so DH's far LOD is not hidden by the vanilla fog ring.
- Set `disableLodWithDistantHorizons` to `false` only if you deliberately want both LOD systems at once (not recommended).

On a phone, lower DH's quality preset and LOD radius first. DH is much heavier than the heightmap fallback, but it looks dramatically better.

## Troubleshooting

- **Crash before title screen / `UnsupportedClassVersionError`** — not on Java 25.
- **Mixin apply failed / NoSuchMethodError** — confirm Minecraft 26.2, Loader 0.19.5+, Fabric API 0.161.0+26.2.
- **Slider still stops at 32** — another mod is replacing the video settings screen, or the memory guard capped it. Check `/chunkview status`.
- **FPS stuck at 60** — VSync is on, or the monitor is 60 Hz. Turn VSync off and raise the framerate cap.
- **Far water looks tiled near the detail seam** — that is vanilla animated water inside the full-detail ring; the LOD water beyond it is flat. Increase `fullDetailDistance` if you want the animated water to extend farther.
- **Far terrain looks blocky** — heightmap LOD. Use `quality` or raise `fullDetailDistance`.
- **Out of memory** — allocate 6–8 GB, use the `phone` preset, or lower render distance.

Logs are in `.minecraft/logs/latest.log`; search for `[ChunkView]`.

## How it works

1. **Slider mixin** raises the vanilla render-distance maximum to `maxRenderDistance`.
2. **ViewArea/SectionUpdateTracker mixins** keep vanilla chunk meshes at the full-detail radius. **Sodium** gets the same cap through its section-manager radius and its config slider is extended.
3. **Heightmap LOD** samples the top block per cell, averages colors, emits top faces and capped skirts, and skips leaf skirts.
4. **One GPU pass** draws visible LOD regions with frustum/distance culling and a near-discard at the full-detail boundary.
5. **Governor** samples FPS once per second, caps the section-occlusion graph and LOD ring without unloading chunks, and matches fog to the reduced ring.
6. **Culling** rejects distant entities and block entities before extraction.

## Credits

The keep-chunks-loaded adaptive visible-radius approach and several vanilla mixins were adapted from **FPSMaxx** by Adaptablxus (CC0-1.0): https://github.com/Adaptablxus/fpsmaxxing. The specific mixins are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The heightmap LOD renderer, governor thresholds, commands and configuration are original ChunkView code.

## License

MIT. See [LICENSE](LICENSE).


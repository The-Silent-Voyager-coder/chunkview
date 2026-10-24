# Third-party notices

## FPSMaxx

- **Author:** Adaptablxus
- **Source:** https://github.com/Adaptablxus/fpsmaxxing
- **License:** CC0-1.0 (public domain dedication)

ChunkView adapts the keep-chunks-loaded adaptive visible-radius approach and several vanilla mixin patterns from FPSMaxx. The adapted mixins are:

- `ChunkMapMixin` — raise the integrated-server view-distance clamp.
- `DistanceManagerMixin` — widen the player ticket tracker.
- `SectionOcclusionGraphMixin` — cap the submitted visible section ring.
- `PlayerListMixin` — cap the broadcast view distance.
- `BlockEntityRenderDispatcherMixin` — cull distant block entities.
- `GameRendererMixin` — match fog to the governed visible radius.
- `LevelExtractorMixin` — cull distant entities and clamp their view scale.
- `OptionsMixin` — extend the render-distance slider range.
- `IntegratedServerMixin` — cap the integrated server's requested view distance.
- `SodiumRenderSectionManagerMixin` — cap Sodium's terrain culling radius.
- `SodiumWorldRendererMixin` — cap Sodium's fog to the governed ring.
- Sodium config entrypoint — extend Sodium's render-distance slider.

These files have been modified for ChunkView's configuration, memory guard and Distant Horizons mode. The heightmap LOD renderer, governor thresholds, commands and configuration format are ChunkView's own implementation.

CC0-1.0 license text: https://creativecommons.org/publicdomain/zero/1.0/legalcode

## Distant Horizons

Distant Horizons is an optional runtime dependency. ChunkView does not include or copy Distant Horizons code; it only detects the mod and disables its own heightmap LOD when DH is present.

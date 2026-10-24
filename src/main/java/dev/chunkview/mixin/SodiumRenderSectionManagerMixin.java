package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium does not use vanilla's ViewArea or SectionOcclusionGraph. Its
 * RenderSectionManager culls and schedules section meshes from a private
 * render-distance getter, in blocks. Cap that getter so Sodium only meshes the
 * full-detail ring while ChunkView's LOD (or Distant Horizons) draws the rest.
 *
 * <p>The cap is evaluated on every call, so the adaptive governor can shrink
 * the Sodium ring without rebuilding the renderer. Chunks themselves stay
 * loaded at the slider distance.</p>
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager")
public abstract class SodiumRenderSectionManagerMixin {
	private static int chunkview$lastLoggedCap = -1;

	@Inject(
		method = "getRenderDistance",
		at = @At("RETURN"),
		cancellable = true,
		remap = false,
		require = 0
	)
	private void chunkview$capSodiumSectionRadius(CallbackInfoReturnable<Float> cir) {
		int cap = ChunkView.sodiumVisibleChunks();
		if (cap <= 0) {
			return;
		}
		if (cap != chunkview$lastLoggedCap) {
			chunkview$lastLoggedCap = cap;
			ChunkView.LOGGER.info("[ChunkView] Sodium terrain radius capped to {} chunks", cap);
		}
		float capBlocks = cap * 16.0F;
		if (cir.getReturnValueF() > capBlocks) {
			cir.setReturnValue(capBlocks);
		}
	}
}

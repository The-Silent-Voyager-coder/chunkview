package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Vanilla builds its section grid (ViewArea) from the effective render distance.
 * Clamp only that grid to the near, full-detail radius. Chunk data still streams
 * at the full 64-chunk distance; the far terrain is drawn by our LOD.
 */
@Mixin(ViewArea.class)
public class ViewAreaMixin {
	@ModifyVariable(
		method = "<init>",
		at = @At("HEAD"),
		argsOnly = true,
		index = 6
	)
	private static int chunkview$clampFullDetailGrid(int renderDistance) {
		if (renderDistance <= 0 || !shouldClampFullDetail()) {
			return renderDistance;
		}
		int clamped = Math.min(renderDistance, Math.max(1, ChunkView.fullDetailDistance()));
		if (clamped != renderDistance) {
			ChunkView.LOGGER.info("[ChunkView] ViewArea render distance {} -> {}", renderDistance, clamped);
		}
		return clamped;
	}

	private static boolean shouldClampFullDetail() {
		return ChunkView.config().lodActive() || ChunkView.config().distantHorizonsMode();
	}
}

package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.client.SectionUpdateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The extractor builds a second rotating section grid from the effective render
 * distance. Keep that grid at the full-detail radius too; otherwise vanilla
 * still extracts and compiles sections all the way out to 64 chunks even though
 * ViewArea only draws the near ones.
 */
@Mixin(SectionUpdateTracker.class)
public class SectionUpdateTrackerMixin {
	@ModifyVariable(
		method = "<init>",
		at = @At("HEAD"),
		argsOnly = true,
		index = 2
	)
	private static int chunkview$clampExtractionGrid(int renderDistance) {
		if (renderDistance <= 0 || !shouldClampFullDetail()) {
			return renderDistance;
		}
		int clamped = Math.min(renderDistance, Math.max(1, ChunkView.fullDetailDistance()));
		if (clamped != renderDistance) {
			ChunkView.LOGGER.info("[ChunkView] SectionUpdateTracker render distance {} -> {}", renderDistance, clamped);
		}
		return clamped;
	}

	private static boolean shouldClampFullDetail() {
		return ChunkView.config().lodActive() || ChunkView.config().distantHorizonsMode();
	}
}

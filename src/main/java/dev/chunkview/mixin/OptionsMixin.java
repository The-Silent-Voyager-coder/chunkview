package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The vanilla render-distance option is constructed as IntRange(2, 32, false).
 * Raise only that range. Matching on the constructor arguments instead of an
 * ordinal keeps the mixin correct if Mojang reorders the options.
 */
@Mixin(Options.class)
public class OptionsMixin {
	@Redirect(
		method = "<init>",
		at = @At(value = "NEW", target = "net/minecraft/client/OptionInstance$IntRange")
	)
	private static OptionInstance.IntRange chunkview$raiseRenderDistanceMaximum(
		int min,
		int max,
		boolean applyValueImmediately
	) {
		if (min == 2 && max == 32) {
			int raised = Math.max(max, ChunkView.maxRenderDistance());
			ChunkView.LOGGER.info("[ChunkView] Render-distance max {} -> {} (heap {})",
				max, raised, Runtime.getRuntime().maxMemory());
			return new OptionInstance.IntRange(min, raised, applyValueImmediately);
		}
		return new OptionInstance.IntRange(min, max, applyValueImmediately);
	}
}

package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The vanilla render-distance option is constructed as IntRange(2, 32/16, false).
 * Raise the maximum to the configured value (64 by default) while leaving the
 * simulation-distance range (5..32) alone. Ordinal 3 is the render-distance
 * IntRange call in Options#&lt;init&gt; (ordinal 4 is simulation distance).
 */
@Mixin(Options.class)
public class OptionsMixin {
	@ModifyArg(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/OptionInstance$IntRange;<init>(IIZ)V",
			ordinal = 3
		),
		index = 1
	)
	private int chunkview$raiseRenderDistanceMaximum(int max) {
		int raised = Math.max(max, ChunkView.maxRenderDistance());
		ChunkView.LOGGER.info("[ChunkView] Render-distance max {} -> {} (heap {})",
			max, raised, Runtime.getRuntime().maxMemory());
		return raised;
	}
}

package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import dev.chunkview.ChunkViewConfig;
import dev.chunkview.PerformanceGovernor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fog follows the visible radius, not the loaded radius. When the governor
 * shrinks the ring the horizon stays hidden instead of exposing a hard edge.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@ModifyArg(
		method = "extractCamera",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;"
		),
		index = 1
	)
	private int chunkview$fogMatchesVisibleRing(int vanillaDistance) {
		ChunkViewConfig config = ChunkView.config();
		return config.matchFogToVisibleRing
			? PerformanceGovernor.fogDistanceChunks(vanillaDistance)
			: vanillaDistance;
	}
}

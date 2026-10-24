package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keep the integrated server's requested view distance in sync with the
 * memory-safe client maximum. Without this, a 64-chunk slider on a low-memory
 * device asks the server to broadcast 64 every tick even after PlayerList caps
 * the stored value.
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
	@ModifyArg(
		method = "tickServer",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/Math;max(II)I",
			ordinal = 0
		),
		index = 1
	)
	private int chunkview$capIntegratedViewDistance(int renderDistance) {
		return Math.min(renderDistance, ChunkView.maxRenderDistance());
	}
}

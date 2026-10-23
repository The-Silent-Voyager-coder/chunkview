package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * ChunkMap hard-clamps the server view distance to 32. Raise that clamp so the
 * integrated server can actually send 64-chunk data in singleplayer/LAN.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {
	@ModifyArg(
		method = "setServerViewDistance",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/Mth;clamp(III)I"
		),
		index = 2
	)
	private int chunkview$raiseServerViewDistanceClamp(int max) {
		return Math.max(max, ChunkView.maxRenderDistance());
	}
}

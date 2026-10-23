package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keep the view distance the server broadcasts in sync with the memory-safe
 * client maximum. Without this a 2 GB phone can request 64 chunks, receive a
 * 32-chunk world, and leave a permanent hole at the edge.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {
	@ModifyVariable(
		method = "setViewDistance",
		at = @At("HEAD"),
		argsOnly = true,
		index = 1
	)
	private int chunkview$capBroadcastViewDistance(int viewDistance) {
		return Math.min(viewDistance, ChunkView.maxRenderDistance());
	}
}

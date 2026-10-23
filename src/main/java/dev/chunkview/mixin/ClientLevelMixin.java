package dev.chunkview.mixin;

import dev.chunkview.LodWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Invalidate the cached heightmap for a chunk when the client sees a block change. */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "setBlocksDirty", at = @At("HEAD"))
	private void chunkview$onBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		if (oldState != newState) {
			LodWorld.onBlockChanged(pos);
		}
	}
}

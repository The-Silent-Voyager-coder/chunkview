package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * PlayerTicketTracker is sized for a maximum of 32 chunks. It must be sized for
 * 64, otherwise its chunk-level graph cannot represent the outer ring.
 */
@Mixin(DistanceManager.class)
public class DistanceManagerMixin {
	@ModifyArg(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;<init>(Lnet/minecraft/server/level/DistanceManager;I)V"
		),
		index = 1
	)
	private int chunkview$raisePlayerTicketTrackerSize(int maxDistance) {
		return Math.max(maxDistance, Math.min(68, ChunkView.maxRenderDistance() + 4));
	}
}

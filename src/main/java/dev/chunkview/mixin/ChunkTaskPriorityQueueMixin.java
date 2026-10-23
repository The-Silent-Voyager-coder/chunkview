package dev.chunkview.mixin;

import net.minecraft.server.level.ChunkTaskPriorityQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * ChunkTaskPriorityQueue sizes its priority buckets from ChunkLevel.MAX_LEVEL
 * (46 buckets). A 64-chunk view produces queue levels above that, which throws
 * ArrayIndexOutOfBoundsException during chunk generation. Enlarge the bucket
 * array; all queue methods read the same static field.
 */
@Mixin(ChunkTaskPriorityQueue.class)
public class ChunkTaskPriorityQueueMixin {
	@ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 2))
	private static int chunkview$raisePriorityLevelCount(int original) {
		return 84;
	}
}

package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chests, signs, banners and similar block entities are expensive to extract.
 * Skip them beyond the cull distance, but leave globally rendered ones (beacon
 * beams and the like) alone.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Shadow
	private Vec3 cameraPos;

	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void chunkview$cullDistantBlockEntities(
		BlockEntity blockEntity,
		float partialTicks,
		ModelFeatureRenderer.CrumblingOverlay breakProgress,
		boolean isGloballyRendered,
		CallbackInfoReturnable<BlockEntityRenderState> cir
	) {
		if (isGloballyRendered || this.cameraPos == null) {
			return;
		}
		int cull = ChunkView.config().blockEntityCullDistance;
		if (cull <= 0) {
			return;
		}
		Vec3 center = Vec3.atCenterOf(blockEntity.getBlockPos());
		if (center.distanceToSqr(this.cameraPos) > (double) cull * cull) {
			cir.setReturnValue(null);
		}
	}
}

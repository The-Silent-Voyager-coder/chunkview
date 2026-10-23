package dev.chunkview.mixin;

import dev.chunkview.PerformanceGovernor;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The section graph decides which chunk sections are eligible to be submitted.
 * Capping it here lets the governor shrink the drawn ring without changing the
 * loaded render distance, so moving never triggers a chunk-load hitch.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {
	@Shadow
	private ViewArea viewArea;

	@Inject(method = "isInViewDistance", at = @At("HEAD"), cancellable = true)
	private void chunkview$limitVisibleRing(long from, long to, CallbackInfoReturnable<Boolean> cir) {
		if (this.viewArea == null) {
			return;
		}
		int cap = PerformanceGovernor.visibleChunks();
		if (cap <= 0) {
			return;
		}
		SectionPos center = this.viewArea.getCameraSectionPos();
		if (!ChunkTrackingView.isInViewDistance(center.x(), center.z(), cap, SectionPos.x(to), SectionPos.z(to))) {
			cir.setReturnValue(false);
		}
	}
}

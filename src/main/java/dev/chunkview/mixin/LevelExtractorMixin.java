package dev.chunkview.mixin;

import dev.chunkview.PerformanceGovernor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity extraction is surprisingly expensive at 64 chunks. Keep entity render
 * distance inside the near ring, then reject anything past the configured cull
 * distance before it reaches the render list.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
	@ModifyArg(
		method = "extractVisibleEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;setViewScale(D)V"
		),
		index = 0
	)
	private double chunkview$clampEntityViewScale(double scale) {
		return Math.min(scale, Math.max(1.0, PerformanceGovernor.fullDetailChunks() / 8.0));
	}

	@Inject(method = "isEntityVisible", at = @At("RETURN"), cancellable = true)
	private void chunkview$cullDistantEntities(
		Entity entity, Frustum frustum, double camX, double camY, double camZ,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValue()) {
			return;
		}
		int cull = PerformanceGovernor.entityCullDistance();
		if (cull <= 0) {
			return;
		}
		Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
		if (entity == cameraEntity) {
			return;
		}
		double dx = entity.getX() - camX;
		double dy = entity.getY() - camY;
		double dz = entity.getZ() - camZ;
		if (dx * dx + dy * dy + dz * dz > (double) cull * cull) {
			cir.setReturnValue(false);
		}
	}
}

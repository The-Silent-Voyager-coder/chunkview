package dev.chunkview.mixin;

import dev.chunkview.ChunkView;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sodium builds its own fog. Keep its far edge at the governed visible ring so
 * shrinking the radius never exposes a hard world edge.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
public abstract class SodiumWorldRendererMixin {
	@ModifyVariable(
		method = "setupTerrain",
		at = @At("HEAD"),
		argsOnly = true,
		index = 3,
		remap = false,
		require = 0
	)
	private FogParameters chunkview$capSodiumFog(FogParameters fog) {
		int cap = ChunkView.sodiumFogChunks();
		if (cap <= 0
			|| fog == null
			|| fog == FogParameters.NONE
			|| fog.renderEnd() <= 0.0F
			|| !Float.isFinite(fog.renderEnd())) {
			return fog;
		}

		float capBlocks = cap * 16.0F;
		return new FogParameters(
			fog.red(),
			fog.green(),
			fog.blue(),
			fog.alpha(),
			fog.environmentalStart(),
			Math.min(fog.environmentalEnd(), capBlocks),
			fog.renderStart(),
			Math.min(fog.renderEnd(), capBlocks),
			Math.min(fog.cullDistance(), capBlocks)
		);
	}
}

package dev.chunkview.compat;

import dev.chunkview.ChunkView;
import dev.chunkview.ChunkViewCompat;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sodium hardcodes its render-distance slider to 2..32. Register an overlay so
 * the option follows ChunkView's memory-safe maximum as well.
 *
 * This class is only loaded when Sodium invokes its config entrypoint.
 */
public final class ChunkViewSodiumConfig implements ConfigEntryPoint {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChunkView.MOD_ID);
	private static final Identifier RENDER_DISTANCE =
		Identifier.fromNamespaceAndPath("sodium", "general.render_distance");

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		if (!ChunkView.config().sodiumConfigOverlay) {
			return;
		}

		// C2ME ships its own Sodium render-distance override. Leave it alone.
		if (ChunkViewCompat.C2ME) {
			LOGGER.info("[ChunkView] C2ME detected; leaving Sodium's render-distance slider to C2ME");
			return;
		}

		try {
			builder.registerOwnModOptions().registerOptionOverlay(
				RENDER_DISTANCE,
				builder.createIntegerOption(RENDER_DISTANCE)
					.setRange(2, ChunkView.maxRenderDistance(), 1)
			);
			LOGGER.info("[ChunkView] Sodium render-distance slider extended to {} chunks",
				ChunkView.maxRenderDistance());
		} catch (Throwable throwable) {
			LOGGER.warn("[ChunkView] Could not extend Sodium's render-distance slider", throwable);
		}
	}
}

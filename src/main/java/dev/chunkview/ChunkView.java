package dev.chunkview;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ChunkView implements ClientModInitializer {
	public static final String MOD_ID = "chunkview";
	public static final Logger LOGGER = LoggerFactory.getLogger("ChunkView");

	private static volatile ChunkViewConfig config = new ChunkViewConfig();
	private static KeyMapping toggleKey;

	public static ChunkViewConfig config() {
		return config;
	}

	/**
	 * Slider/server maximum. The configured value is also capped by heap so a
	 * 2 GB phone does not try to hold 18,000 chunks and die.
	 */
	public static int maxRenderDistance() {
		return Math.min(config.maxRenderDistance, memorySafeRenderDistance());
	}

	public static int memorySafeRenderDistance() {
		long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		return ChunkViewMath.memorySafeRenderDistance(
			heapMb,
			Boolean.getBoolean("chunkview.ignoreMemoryLimit")
		);
	}

	public static int fullDetailDistance() {
		return Math.min(config.fullDetailDistance, maxRenderDistance());
	}

	public static void reloadConfig() {
		config = ChunkViewConfig.load();
		LodWorld.reload();
		PerformanceGovernor.reset();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			// Dispose the section renderer so a new fullDetailDistance rebuilds
			// the ViewArea/SectionUpdateTracker grids immediately instead of
			// waiting for a world reload.
			minecraft.levelRenderer.resetLevelRenderData();
		}
	}

	/**
	 * Radius Sodium should build and cull sections for. In LOD/DH mode Sodium
	 * only needs the full-detail ring; without LOD the governor still owns the
	 * visible ring.
	 */
	public static int sodiumVisibleChunks() {
		ChunkViewConfig current = config();
		if (!current.sodiumIntegration) {
			return -1;
		}
		if (current.lodActive() || current.distantHorizonsMode()) {
			return fullDetailDistance();
		}
		return PerformanceGovernor.visibleChunks();
	}

	/** Fog radius for Sodium, or -1 when Sodium should keep its own value. */
	public static int sodiumFogChunks() {
		ChunkViewConfig current = config();
		if (!current.sodiumIntegration
			|| !current.matchFogToVisibleRing
			|| current.distantHorizonsMode()
			// The LOD fills the far ring, so capping fog to the governor would
			// hide terrain that is actually being drawn.
			|| current.lodActive()) {
			return -1;
		}
		return PerformanceGovernor.visibleChunks();
	}

	@Override
	public void onInitializeClient() {
		config = ChunkViewConfig.load();

		LodRenderer.initialize();
		LodWorld.initialize();
		ChunkViewCommands.initialize();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.chunkview.adaptive",
			GLFW.GLFW_KEY_F6,
			KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				config.adaptiveEnabled = !config.adaptiveEnabled;
				config.save();
				PerformanceGovernor.reset();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal(
						"[ChunkView] Adaptive FPS " + (config.adaptiveEnabled ? "ON" : "OFF")
					).withStyle(config.adaptiveEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
				}
			}
			PerformanceGovernor.tick(client);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ChunkViewConfig current = config();
			if (!current.showJoinMessage || client.player == null) {
				return;
			}
			String mode = current.distantHorizonsMode()
				? "Distant Horizons owns the far terrain"
				: (current.lodActive()
					? "LOD " + current.lodCellSize + "-block cells"
					: "full-detail terrain");
			client.player.sendSystemMessage(Component.literal(
				"[ChunkView] " + mode + " | F6 = adaptive governor | /chunkview status"
			).withStyle(ChatFormatting.AQUA));
		});

		long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		LOGGER.info("[ChunkView] Loaded for Minecraft 26.2: {}", config.describe());
		LOGGER.info("[ChunkView] Heap {} MB, memory-safe render distance {} chunks; optional mods: {}",
			heapMb, memorySafeRenderDistance(), ChunkViewCompat.describe());
		if (ChunkViewCompat.VULKANMOD) {
			LOGGER.warn("[ChunkView] VulkanMod detected. ChunkView's terrain clamp currently supports the vanilla and Sodium renderers; "
				+ "the governor may not reduce VulkanMod's terrain work.");
		}
	}
}

package dev.chunkview;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
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
		if (Boolean.getBoolean("chunkview.ignoreMemoryLimit")) {
			return 64;
		}
		long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		if (heapMb >= 4096L) {
			return 64;
		}
		if (heapMb >= 3072L) {
			return 48;
		}
		if (heapMb >= 2048L) {
			return 32;
		}
		if (heapMb >= 1536L) {
			return 24;
		}
		return 16;
	}

	public static int fullDetailDistance() {
		return Math.min(config.fullDetailDistance, maxRenderDistance());
	}

	public static void reloadConfig() {
		config = ChunkViewConfig.load();
		LodWorld.reload();
		PerformanceGovernor.reset();
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

		long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		LOGGER.info("[ChunkView] Loaded for Minecraft 26.2: {}", config.describe());
		LOGGER.info("[ChunkView] Heap {} MB, memory-safe render distance {} chunks; optional mods: {}",
			heapMb, memorySafeRenderDistance(), ChunkViewCompat.describe());
	}
}

package dev.chunkview;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;

/**
 * Keeps the requested 64 chunks loaded, but limits how much of that ring is
 * submitted to the GPU when the frame rate falls. This is deliberately a
 * visible-radius governor: changing the real render distance would unload
 * chunks and cause a load hitch every time the FPS moved.
 *
 * <p>The implementation is original to ChunkView, but the general
 * keep-chunks-loaded / shrink-the-visible-ring strategy was informed by
 * FPSMaxx (CC0-1.0).</p>
 */
@Environment(EnvType.CLIENT)
public final class PerformanceGovernor {
	private static final long SAMPLE_INTERVAL_MS = 1000L;
	private static final long SHRINK_COOLDOWN_MS = 2500L;
	private static final long GROW_COOLDOWN_MS = 6000L;

	private static int visibleChunks = -1;
	private static int maxChunks = -1;
	private static long lastSampleMs;
	private static long settleUntilMs;
	private static long growAfterMs;
	private static int goodSamples;
	private static ClientLevel lastLevel;

	private PerformanceGovernor() {
	}

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			visibleChunks = -1;
			lastLevel = null;
			return;
		}

		ChunkViewConfig config = ChunkView.config();
		long now = Util.getMillis();

		if (level != lastLevel) {
			lastLevel = level;
			maxChunks = maxChunks(minecraft, config);
			visibleChunks = config.adaptiveEnabled
				? Math.min(maxChunks, Math.max(config.minVisibleDistance, config.initialVisibleDistance))
				: maxChunks;
			lastSampleMs = now;
			settleUntilMs = now + 3500L;
			growAfterMs = now + GROW_COOLDOWN_MS;
			goodSamples = 0;
			invalidateGraph(minecraft);
			return;
		}

		if (!config.adaptiveEnabled) {
			visibleChunks = maxChunks(minecraft, config);
			return;
		}

		int currentMax = maxChunks(minecraft, config);
		if (currentMax != maxChunks) {
			maxChunks = currentMax;
			visibleChunks = Math.min(visibleChunks, maxChunks);
			settleUntilMs = now + SHRINK_COOLDOWN_MS;
		}
		if (visibleChunks < 0) {
			visibleChunks = maxChunks;
		}

		// Menus and alt-tabs collapse the FPS counter; never shrink because of them.
		if (minecraft.isPaused()) {
			lastSampleMs = now;
			settleUntilMs = now + 1000L;
			return;
		}

		if (now - lastSampleMs < SAMPLE_INTERVAL_MS || now < settleUntilMs) {
			lastSampleMs = now;
			return;
		}
		lastSampleMs = now;

		int fps = minecraft.getFps();
		if (fps <= 0) {
			return;
		}

		int target = effectiveTarget(minecraft, config);
		int min = Math.min(config.minVisibleDistance, maxChunks);
		int next = visibleChunks;

		if (fps < target - 4) {
			// Drop quickly when the GPU is clearly behind, but leave the nearest
			// full-detail ring intact so the world never becomes an empty horizon.
			int drop = Math.max(2, visibleChunks / 12);
			if (fps < target / 2) {
				drop = Math.max(drop, visibleChunks / 3);
			}
			next = Math.max(min, visibleChunks - drop);
			settleUntilMs = now + SHRINK_COOLDOWN_MS;
			growAfterMs = now + GROW_COOLDOWN_MS;
			goodSamples = 0;
		} else if (now >= growAfterMs && visibleChunks < maxChunks) {
			if (fps > target + 6) {
				goodSamples++;
			} else {
				goodSamples = 0;
			}
			if (goodSamples >= 3) {
				int step = fps > target + 20 ? 2 : 1;
				next = Math.min(maxChunks, visibleChunks + step);
				settleUntilMs = now + SHRINK_COOLDOWN_MS;
				goodSamples = 0;
			}
		}

		if (next != visibleChunks) {
			ChunkView.LOGGER.info(
				"[ChunkView] Visible radius {} -> {} chunks (FPS {}, target {})",
				visibleChunks, next, fps, target
			);
			visibleChunks = next;
			invalidateGraph(minecraft);
		}
	}

	public static void reset() {
		visibleChunks = -1;
		maxChunks = -1;
		lastLevel = null;
		lastSampleMs = 0L;
		settleUntilMs = 0L;
		growAfterMs = 0L;
		goodSamples = 0;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			invalidateGraph(minecraft);
		}
	}

	/** Radius the LOD and fog should use, in chunks. */
	public static int visibleChunks() {
		Minecraft minecraft = Minecraft.getInstance();
		ChunkViewConfig config = ChunkView.config();
		if (visibleChunks > 0) {
			return Math.min(visibleChunks, maxChunks(minecraft, config));
		}
		return maxChunks(minecraft, config);
	}

	/**
	 * Vanilla full-detail radius. Normally the configured value; if the
	 * governor has dropped below it, the near ring follows the governor.
	 */
	public static int fullDetailChunks() {
		return Math.max(4, Math.min(ChunkView.fullDetailDistance(), visibleChunks()));
	}

	public static int entityCullDistance() {
		int configured = ChunkView.config().entityCullDistance;
		if (configured <= 0) {
			return 0;
		}
		return Math.min(configured, Math.max(48, visibleChunks() * 16));
	}

	public static int fogDistanceChunks(int vanillaDistance) {
		if (!ChunkView.config().adaptiveEnabled) {
			return vanillaDistance;
		}
		return Math.min(vanillaDistance, visibleChunks());
	}

	public static int effectiveTarget(Minecraft minecraft, ChunkViewConfig config) {
		int target = Math.max(30, config.targetFps);
		if (minecraft.options.enableVsync().get()) {
			int refresh = minecraft.getWindow().getRefreshRate();
			if (refresh <= 0) {
				refresh = 60;
			}
			target = Math.min(target, refresh);
		}
		return target;
	}

	private static int maxChunks(Minecraft minecraft, ChunkViewConfig config) {
		int slider = minecraft.options == null ? config.maxRenderDistance : minecraft.options.renderDistance().get();
		return Math.max(2, Math.min(slider, ChunkView.maxRenderDistance()));
	}

	private static void invalidateGraph(Minecraft minecraft) {
		if (minecraft.levelRenderer != null && minecraft.levelRenderer.sectionOcclusionGraph() != null) {
			minecraft.levelRenderer.sectionOcclusionGraph().invalidate();
		}
	}
}

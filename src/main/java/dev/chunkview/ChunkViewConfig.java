package dev.chunkview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON config. Kept deliberately small so it can be edited from the
 * Pojav file manager or a desktop text editor without a GUI dependency.
 */
public final class ChunkViewConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chunkview.json");

	/** Maximum value the vanilla render-distance slider is allowed to reach. */
	public int maxRenderDistance = 64;

	/**
	 * Chunks rendered by Minecraft's normal chunk renderer (full blocks, real
	 * lighting, entities). Everything past this is drawn by the heightmap LOD.
	 */
	public int fullDetailDistance = 24;

	public boolean lodEnabled = true;
	/** Let Distant Horizons own the far terrain instead of drawing two LODs. */
	public boolean disableLodWithDistantHorizons = true;
	/** Apply the governor and full-detail clamp to Sodium's terrain renderer. */
	public boolean sodiumIntegration = true;
	/** Extend Sodium's own render-distance slider to the ChunkView maximum. */
	public boolean sodiumConfigOverlay = true;
	/** Blocks per LOD cell. 4 = smoother, 8 = faster (recommended for phones). */
	public int lodCellSize = 2;
	public boolean lodSkirts = true;
	public boolean lodOverworldOnly = true;

	/** Keep chunks loaded but shrink the submitted/visible ring to hold FPS. */
	public boolean adaptiveEnabled = true;
	/** Aim slightly above 60 so dips do not fall under the requested minimum. */
	public int targetFps = 62;
	/** Never shrink the visible ring below this many chunks. */
	public int minVisibleDistance = 16;
	/** Starting visible radius when entering a world. */
	public int initialVisibleDistance = 48;
	/** Entities beyond this many blocks are skipped. 0 = off. */
	public int entityCullDistance = 192;
	/** Block entities beyond this many blocks are skipped. 0 = off. */
	public int blockEntityCullDistance = 128;
	/** Keep the fog edge at the visible ring when the governor shrinks it. */
	public boolean matchFogToVisibleRing = true;

	/**
	 * How many LOD regions may be built per client tick. 1 keeps tick times
	 * flat while the view fills in.
	 */
	public int regionBuildsPerFrame = 1;

	public boolean debug = false;

	/** Send a short chat summary when joining a world. */
	public boolean showJoinMessage = true;

	public static ChunkViewConfig load() {
		ChunkViewConfig config = new ChunkViewConfig();
		try {
			if (Files.exists(PATH)) {
				String json = Files.readString(PATH, StandardCharsets.UTF_8);
				ChunkViewConfig read = GSON.fromJson(json, ChunkViewConfig.class);
				if (read != null) {
					config = read;
				}
			}
		} catch (Exception e) {
			ChunkView.LOGGER.warn("[ChunkView] Could not read {}, using defaults", PATH, e);
		}
		config.sanitize();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ChunkView.LOGGER.warn("[ChunkView] Could not save {}", PATH, e);
		}
	}

	public void sanitize() {
		maxRenderDistance = clamp(maxRenderDistance, 8, 64);
		fullDetailDistance = clamp(fullDetailDistance, 2, 32);
		if (fullDetailDistance > maxRenderDistance - 2) {
			fullDetailDistance = Math.max(2, maxRenderDistance - 2);
		}
		lodCellSize = lodCellSize <= 2 ? 2 : (lodCellSize <= 4 ? 4 : 8);
		regionBuildsPerFrame = clamp(regionBuildsPerFrame, 1, 4);
		targetFps = clamp(targetFps, 30, 240);
		minVisibleDistance = clamp(minVisibleDistance, 4, maxRenderDistance);
		initialVisibleDistance = clamp(initialVisibleDistance, minVisibleDistance, maxRenderDistance);
		entityCullDistance = clamp(entityCullDistance, 0, 512);
		blockEntityCullDistance = clamp(blockEntityCullDistance, 0, 512);
	}

	public boolean lodActive() {
		return lodEnabled
			&& !(disableLodWithDistantHorizons && ChunkViewCompat.DISTANT_HORIZONS)
			&& fullDetailDistance + 1 < maxRenderDistance;
	}

	/** True when Distant Horizons is installed and owns the far LOD. */
	public boolean distantHorizonsMode() {
		return disableLodWithDistantHorizons && ChunkViewCompat.DISTANT_HORIZONS;
	}

	public String describe() {
		return "max=" + maxRenderDistance
			+ ", fullDetail=" + fullDetailDistance
			+ ", lod=" + (distantHorizonsMode()
				? "off (Distant Horizons)"
				: (lodEnabled ? lodCellSize + "b cells" : "off"))
			+ ", adaptive=" + (adaptiveEnabled ? "on/" + targetFps + "fps/min" + minVisibleDistance : "off")
			+ ", entityCull=" + entityCullDistance
			+ ", blockEntityCull=" + blockEntityCullDistance
			+ ", builds/tick=" + regionBuildsPerFrame;
	}

	public boolean applyPreset(String name) {
		switch (name.toLowerCase()) {
			case "phone", "low" -> {
				maxRenderDistance = 64;
				fullDetailDistance = 8;
				lodCellSize = 8;
				regionBuildsPerFrame = 1;
				lodEnabled = true;
				lodSkirts = true;
				adaptiveEnabled = true;
				targetFps = 60;
				minVisibleDistance = 8;
				initialVisibleDistance = 24;
				entityCullDistance = 48;
				blockEntityCullDistance = 48;
			}
			case "balanced" -> {
				maxRenderDistance = 64;
				fullDetailDistance = 12;
				lodCellSize = 4;
				regionBuildsPerFrame = 1;
				lodEnabled = true;
				lodSkirts = true;
				adaptiveEnabled = true;
				targetFps = 62;
				minVisibleDistance = 12;
				initialVisibleDistance = 64;
				entityCullDistance = 96;
				blockEntityCullDistance = 96;
			}
			case "quality", "pc" -> {
				maxRenderDistance = 64;
				fullDetailDistance = 24;
				lodCellSize = 2;
				regionBuildsPerFrame = 2;
				lodEnabled = true;
				lodSkirts = true;
				adaptiveEnabled = true;
				targetFps = 62;
				minVisibleDistance = 16;
				initialVisibleDistance = 64;
				entityCullDistance = 128;
				blockEntityCullDistance = 128;
			}
			default -> {
				return false;
			}
		}
		save();
		return true;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}

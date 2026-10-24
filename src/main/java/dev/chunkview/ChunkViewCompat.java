package dev.chunkview;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional-mod detection only. No optional classes are loaded unless the
 * corresponding mod is present, so ChunkView remains a standalone mod.
 */
public final class ChunkViewCompat {
	public static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
	public static final boolean C2ME = FabricLoader.getInstance().isModLoaded("c2me");
	public static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
	public static final boolean VULKANMOD = FabricLoader.getInstance().isModLoaded("vulkanmod");
	public static final boolean DISTANT_HORIZONS = FabricLoader.getInstance().isModLoaded("distanthorizons");
	public static final boolean ENTITY_CULLING = FabricLoader.getInstance().isModLoaded("entityculling");

	private ChunkViewCompat() {
	}

	public static String describe() {
		StringBuilder result = new StringBuilder();
		append(result, "Sodium", SODIUM);
		append(result, "C2ME", C2ME);
		append(result, "Iris", IRIS);
		append(result, "VulkanMod", VULKANMOD);
		append(result, "Distant Horizons", DISTANT_HORIZONS);
		append(result, "EntityCulling", ENTITY_CULLING);
		return result.length() == 0 ? "no known optimization mods" : result.toString();
	}

	private static void append(StringBuilder builder, String name, boolean present) {
		if (!present) {
			return;
		}
		if (builder.length() > 0) {
			builder.append(", ");
		}
		builder.append(name);
	}
}

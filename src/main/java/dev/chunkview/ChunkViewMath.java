package dev.chunkview;

/**
 * Pure render-distance and configuration math. Kept free of Minecraft classes
 * so it can be covered by fast unit tests.
 */
public final class ChunkViewMath {
	private ChunkViewMath() {
	}

	/** Heap-based cap used by the slider and the integrated server. */
	public static int memorySafeRenderDistance(long heapMb, boolean ignoreMemoryLimit) {
		if (ignoreMemoryLimit) {
			return 64;
		}
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

	/** Snap the requested LOD cell size to the supported 2/4/8-block steps. */
	public static int lodCellSize(int requested) {
		return requested <= 2 ? 2 : (requested <= 4 ? 4 : 8);
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}

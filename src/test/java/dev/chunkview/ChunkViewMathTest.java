package dev.chunkview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkViewMathTest {
	@Test
	void memoryGuardStepsFollowHeapSize() {
		assertEquals(16, ChunkViewMath.memorySafeRenderDistance(1024L, false));
		assertEquals(24, ChunkViewMath.memorySafeRenderDistance(1536L, false));
		assertEquals(32, ChunkViewMath.memorySafeRenderDistance(2048L, false));
		assertEquals(48, ChunkViewMath.memorySafeRenderDistance(3072L, false));
		assertEquals(64, ChunkViewMath.memorySafeRenderDistance(4096L, false));
		assertEquals(64, ChunkViewMath.memorySafeRenderDistance(8192L, false));
	}

	@Test
	void ignoreMemoryLimitAlwaysAllows64() {
		assertEquals(64, ChunkViewMath.memorySafeRenderDistance(512L, true));
	}

	@Test
	void lodCellSizeSnapsToSupportedSteps() {
		assertEquals(2, ChunkViewMath.lodCellSize(1));
		assertEquals(2, ChunkViewMath.lodCellSize(2));
		assertEquals(4, ChunkViewMath.lodCellSize(3));
		assertEquals(4, ChunkViewMath.lodCellSize(4));
		assertEquals(8, ChunkViewMath.lodCellSize(6));
		assertEquals(8, ChunkViewMath.lodCellSize(8));
	}

	@Test
	void clampKeepsValuesInsideRange() {
		assertEquals(0, ChunkViewMath.clamp(-1, 0, 10));
		assertEquals(5, ChunkViewMath.clamp(5, 0, 10));
		assertEquals(10, ChunkViewMath.clamp(11, 0, 10));
	}
}

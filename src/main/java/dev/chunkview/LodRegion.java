package dev.chunkview;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.world.phys.AABB;

/** One 16x16-chunk LOD mesh and its GPU state. */
final class LodRegion {
	static final int CHUNKS = 16;
	static final int BLOCKS = CHUNKS * 16;

	final int rx;
	final int rz;

	volatile boolean active = true;
	volatile boolean pending = false;
	volatile boolean dirty = true;
	volatile long lastBuildMs = 0L;

	volatile GpuBuffer buffer;
	volatile GpuBufferSlice slice;
	volatile int vertexCount;
	volatile AABB bounds;

	LodRegion(int rx, int rz) {
		this.rx = rx;
		this.rz = rz;
		this.bounds = new AABB(
			rx * BLOCKS, -64.0, rz * BLOCKS,
			rx * BLOCKS + BLOCKS, 320.0, rz * BLOCKS + BLOCKS
		);
	}

	long key() {
		return LodWorld.regionKey(rx, rz);
	}
}

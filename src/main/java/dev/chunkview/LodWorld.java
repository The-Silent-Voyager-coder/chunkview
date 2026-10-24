package dev.chunkview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.PrimitiveTopology;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side heightmap LOD cache. Chunk data is sampled on the client thread
 * (inside the client tick), never on the render thread. Finished CPU meshes are
 * handed to {@link LodRenderer}, which uploads them on the render thread.
 */
@Environment(EnvType.CLIENT)
public final class LodWorld {
	private static final int REGION_CHUNKS = LodRegion.CHUNKS;
	private static final int REGION_BLOCKS = LodRegion.BLOCKS;
	private static final int NO_HEIGHT = Integer.MIN_VALUE;

	private static final Long2ObjectOpenHashMap<LodChunk> CHUNKS = new Long2ObjectOpenHashMap<>();
	private static final Long2ObjectOpenHashMap<LodRegion> REGIONS = new Long2ObjectOpenHashMap<>();
	private static volatile List<LodRegion> renderRegions = List.of();

	private static ClientLevel lastLevel;
	private static volatile float dayLight = 1.0f;
	private static volatile int renderDistance;

	private LodWorld() {
	}

	public static void initialize() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			markRegionDirty(chunk.getPos().x(), chunk.getPos().z());
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			CHUNKS.remove(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
			markRegionDirty(chunk.getPos().x(), chunk.getPos().z());
		});
		ClientTickEvents.END_CLIENT_TICK.register(LodWorld::tick);
	}

	/** Config changed: throw away every cached mesh and rebuild on demand. */
	public static void reload() {
		clearRegions();
		CHUNKS.clear();
		lastLevel = null;
	}

	public static List<LodRegion> renderRegions() {
		return renderRegions;
	}

	public static float dayLight() {
		return dayLight;
	}

	/** Current slider distance, capped by the configured maximum. */
	public static int renderDistance() {
		return renderDistance;
	}

	public static void onBlockChanged(BlockPos pos) {
		CHUNKS.remove(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
		markRegionDirty(pos.getX() >> 4, pos.getZ() >> 4);
	}

	private static void markRegionDirty(int chunkX, int chunkZ) {
		LodRegion region = REGIONS.get(regionKey(chunkX >> 4, chunkZ >> 4));
		if (region != null) {
			region.dirty = true;
		}
	}

	static long regionKey(int rx, int rz) {
		return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			if (lastLevel != null) {
				clearRegions();
				CHUNKS.clear();
				lastLevel = null;
			}
			return;
		}

		if (level != lastLevel) {
			clearRegions();
			CHUNKS.clear();
			lastLevel = level;
		}

		ChunkViewConfig config = ChunkView.config();
		// Never build LOD for chunks the heap guard already refuses to hold.
		int distance = Math.min(minecraft.options.renderDistance().get(), ChunkView.maxRenderDistance());
		renderDistance = distance > ChunkView.fullDetailDistance() ? distance : 0;

		if (renderDistance == 0
			|| !config.lodActive()
			|| (config.lodOverworldOnly && level.dimension() != Level.OVERWORLD)
			|| minecraft.player == null) {
			if (!REGIONS.isEmpty()) {
				clearRegions();
			}
			return;
		}

		updateDaylight(level);

		int playerChunkX = minecraft.player.getBlockX() >> 4;
		int playerChunkZ = minecraft.player.getBlockZ() >> 4;
		int playerRegionX = playerChunkX >> 4;
		int playerRegionZ = playerChunkZ >> 4;
		int radius = Math.max(1, (renderDistance + 15) >> 4);

		List<LodRegion> candidates = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
		boolean listChanged = false;

		for (int rz = playerRegionZ - radius; rz <= playerRegionZ + radius; rz++) {
			for (int rx = playerRegionX - radius; rx <= playerRegionX + radius; rx++) {
				long key = regionKey(rx, rz);
				LodRegion region = REGIONS.get(key);
				if (region == null) {
					region = new LodRegion(rx, rz);
					REGIONS.put(key, region);
					listChanged = true;
				}
				candidates.add(region);
			}
		}

		// Drop regions that fell outside the radius.
		var iterator = REGIONS.long2ObjectEntrySet().fastIterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			LodRegion region = entry.getValue();
			if (Math.abs(region.rx - playerRegionX) > radius + 1
				|| Math.abs(region.rz - playerRegionZ) > radius + 1) {
				region.active = false;
				if (region.buffer != null) {
					LodRenderer.enqueueClose(region.buffer);
					region.buffer = null;
					region.slice = null;
				}
				iterator.remove();
				listChanged = true;
			}
		}

		if (listChanged) {
			renderRegions = List.copyOf(REGIONS.values());
		}

		candidates.sort(Comparator.comparingDouble(region -> {
			double cx = region.rx * (double) REGION_BLOCKS + REGION_BLOCKS * 0.5;
			double cz = region.rz * (double) REGION_BLOCKS + REGION_BLOCKS * 0.5;
			double dx = cx - minecraft.player.getX();
			double dz = cz - minecraft.player.getZ();
			return dx * dx + dz * dz;
		}));

		int budget = config.regionBuildsPerFrame;
		long now = System.currentTimeMillis();

		for (LodRegion region : candidates) {
			if (budget <= 0) {
				break;
			}
			if (region.pending || (!region.dirty && region.vertexCount > 0)) {
				continue;
			}
			long interval = region.vertexCount > 0 ? 1500L : 500L;
			if (region.lastBuildMs != 0L && now - region.lastBuildMs < interval) {
				continue;
			}

			region.dirty = false;
			region.pending = true;
			region.lastBuildMs = now;
			budget--;

			try {
				buildRegion(level, region);
			} catch (Throwable t) {
				region.pending = false;
				ChunkView.LOGGER.warn("[ChunkView] Failed to build LOD region {},{}", region.rx, region.rz, t);
			}
		}
	}

	private static void updateDaylight(ClientLevel level) {
		float darkness = level.getSkyDarken() / 11.0f;
		float light = 1.0f - darkness * 0.72f;
		if (level.getRainLevel(1.0f) > 0.0f) {
			light *= 0.88f;
		}
		if (!level.dimensionType().hasSkyLight()) {
			light = 0.85f;
		}
		dayLight = Math.max(0.22f, Math.min(1.0f, light));
	}

	private static void clearRegions() {
		for (LodRegion region : REGIONS.values()) {
			region.active = false;
			if (region.buffer != null) {
				LodRenderer.enqueueClose(region.buffer);
				region.buffer = null;
				region.slice = null;
			}
		}
		REGIONS.clear();
		renderRegions = List.of();
	}

	private static void buildRegion(ClientLevel level, LodRegion region) {
		ChunkViewConfig config = ChunkView.config();
		int cellSize = config.lodCellSize;
		int cellsPerChunk = 16 / cellSize;
		int cellsPerRegion = REGION_CHUNKS * cellsPerChunk;
		int gridWidth = cellsPerRegion + 2;
		int[] heights = new int[gridWidth * gridWidth];
		int[] colors = new int[gridWidth * gridWidth];
		byte[] flags = new byte[gridWidth * gridWidth];
		java.util.Arrays.fill(heights, NO_HEIGHT);

		int baseChunkX = region.rx * REGION_CHUNKS;
		int baseChunkZ = region.rz * REGION_CHUNKS;

		for (int cz = 0; cz < REGION_CHUNKS; cz++) {
			for (int cx = 0; cx < REGION_CHUNKS; cx++) {
				LodChunk data = getChunkData(level, baseChunkX + cx, baseChunkZ + cz, cellSize);
				if (data == null) {
					continue;
				}
				int offsetX = 1 + cx * cellsPerChunk;
				int offsetZ = 1 + cz * cellsPerChunk;
				for (int z = 0; z < cellsPerChunk; z++) {
					for (int x = 0; x < cellsPerChunk; x++) {
						int src = x + z * cellsPerChunk;
						int dst = offsetX + x + (offsetZ + z) * gridWidth;
						heights[dst] = data.heights[src];
						colors[dst] = data.colors[src];
						flags[dst] = data.flags[src];
					}
				}
			}
		}

		int originCellX = region.rx * REGION_CHUNKS * cellsPerChunk;
		int originCellZ = region.rz * REGION_CHUNKS * cellsPerChunk;

		// One-cell border gives correct skirts and normals at region seams.
		for (int gz = -1; gz <= cellsPerRegion; gz++) {
			for (int gx = -1; gx <= cellsPerRegion; gx++) {
				if (gx >= 0 && gx < cellsPerRegion && gz >= 0 && gz < cellsPerRegion) {
					continue;
				}
				heights[(gx + 1) + (gz + 1) * gridWidth] =
					sampleHeight(level, originCellX + gx, originCellZ + gz, cellSize);
			}
		}

		// Smooth the per-cell colours before building the mesh. A raw map-colour
		// sample per 4x4 cell produces a visible checkerboard at distance; a 3x3
		// average keeps the LOD reading as terrain rather than a quilt.
		int[] topColors = new int[gridWidth * gridWidth];
		for (int gz = 0; gz < cellsPerRegion; gz++) {
			for (int gx = 0; gx < cellsPerRegion; gx++) {
				int index = (gx + 1) + (gz + 1) * gridWidth;
				if (heights[index] == NO_HEIGHT) {
					continue;
				}
				boolean water = (flags[index] & 1) != 0;
				int own = colors[index];
				if (water) {
					topColors[index] = own;
					continue;
				}
				int r = 0;
				int g = 0;
				int b = 0;
				int n = 0;
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						int ni = index + dx + dz * gridWidth;
						if (heights[ni] == NO_HEIGHT || (flags[ni] & 1) != 0) {
							continue;
						}
						int c = colors[ni];
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
						n++;
					}
				}
				topColors[index] = n == 0 ? own : 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
			}
		}

		int initialCapacity = Math.max(16384, cellsPerRegion * cellsPerRegion * 4 * 16 * 2);
		ByteBufferBuilder allocator = new ByteBufferBuilder(initialCapacity);
		BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);

		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		int quads = 0;
		int dataCells = 0;

		for (int gz = 0; gz < cellsPerRegion; gz++) {
			for (int gx = 0; gx < cellsPerRegion; gx++) {
				int index = (gx + 1) + (gz + 1) * gridWidth;
				int height = heights[index];
				if (height == NO_HEIGHT) {
					continue;
				}

				int color = topColors[index];
				if ((color >>> 24) == 0) {
					color = 0xFF7F7F7F;
				}
				dataCells++;
				boolean water = (flags[index] & 1) != 0;
				boolean leaves = (flags[index] & 2) != 0;
				// Keep the coarse water plane clearly below vanilla's animated
				// surface so the two never z-fight at the detail boundary.
				float topY = height - (water ? 0.5f : 0.0f);

				int hNorth = heights[index - gridWidth];
				int hSouth = heights[index + gridWidth];
				int hWest = heights[index - 1];
				int hEast = heights[index + 1];

				float dhx = (hEast == NO_HEIGHT || hWest == NO_HEIGHT)
					? 0.0f : (hEast - hWest) / (2.0f * cellSize);
				float dhz = (hSouth == NO_HEIGHT || hNorth == NO_HEIGHT)
					? 0.0f : (hSouth - hNorth) / (2.0f * cellSize);

				float nx = -dhx;
				float ny = 1.0f;
				float nz = -dhz;
				float invLength = 1.0f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				float dot = Math.max(0.0f, (nx * 0.35f + ny * 0.85f + nz * 0.25f) * invLength);
				float shade = 0.70f + 0.30f * dot;
				int topColor = scale(color, shade);
				int sideColor = scale(color, shade * 0.60f);

				float x0 = (originCellX + gx) * (float) cellSize;
				float x1 = x0 + cellSize;
				float z0 = (originCellZ + gz) * (float) cellSize;
				float z1 = z0 + cellSize;

				// Top face (counter-clockwise seen from above).
				addQuad(builder, x0, topY, z1, x1, topY, z1, x1, topY, z0, x0, topY, z0, topColor);
				quads++;

				// Leaves are a canopy, not a cliff. Drawing skirts under every
				// leaf cell creates giant green walls in forests; skip them.
				if (config.lodSkirts && !water && !leaves) {
					float skirtFloor = topY - 16.0f;
					if (hEast != NO_HEIGHT && hEast < height) {
						float bottom = Math.max(skirtFloor, hEast - ((flags[index + 1] & 1) != 0 ? 0.125f : 0.0f));
						addQuad(builder, x1, bottom, z0, x1, bottom, z1, x1, topY, z1, x1, topY, z0, sideColor);
						minY = Math.min(minY, bottom);
						quads++;
					}
					if (hWest != NO_HEIGHT && hWest < height) {
						float bottom = Math.max(skirtFloor, hWest - ((flags[index - 1] & 1) != 0 ? 0.125f : 0.0f));
						addQuad(builder, x0, bottom, z1, x0, bottom, z0, x0, topY, z0, x0, topY, z1, sideColor);
						minY = Math.min(minY, bottom);
						quads++;
					}
					if (hSouth != NO_HEIGHT && hSouth < height) {
						float bottom = Math.max(skirtFloor, hSouth - ((flags[index + gridWidth] & 1) != 0 ? 0.125f : 0.0f));
						addQuad(builder, x0, bottom, z1, x1, bottom, z1, x1, topY, z1, x0, topY, z1, sideColor);
						minY = Math.min(minY, bottom);
						quads++;
					}
					if (hNorth != NO_HEIGHT && hNorth < height) {
						float bottom = Math.max(skirtFloor, hNorth - ((flags[index - gridWidth] & 1) != 0 ? 0.125f : 0.0f));
						addQuad(builder, x1, bottom, z0, x0, bottom, z0, x0, topY, z0, x1, topY, z0, sideColor);
						minY = Math.min(minY, bottom);
						quads++;
					}
				}

				minY = Math.min(minY, topY);
				maxY = Math.max(maxY, topY);
			}
		}

		if (quads == 0) {
			allocator.close();
			region.pending = false;
			region.vertexCount = 0;
			return;
		}

		MeshData mesh = builder.buildOrThrow();
		int vertexCount = mesh.drawState().vertexCount();
		if (vertexCount == 0) {
			mesh.close();
			allocator.close();
			region.pending = false;
			region.vertexCount = 0;
			return;
		}

		AABB bounds = new AABB(
			region.rx * (double) REGION_BLOCKS, minY - 1.0, region.rz * (double) REGION_BLOCKS,
			region.rx * (double) REGION_BLOCKS + REGION_BLOCKS, maxY + 2.0,
			region.rz * (double) REGION_BLOCKS + REGION_BLOCKS
		);

		if (config.debug) {
			ChunkView.LOGGER.info(
				"[ChunkView] LOD region {},{}: cells={}, quads={}, verts={}, y={}..{}",
				region.rx, region.rz, dataCells, quads, vertexCount, minY, maxY
			);
		}
		LodRenderer.enqueueUpload(region, mesh, allocator, vertexCount, bounds);
	}

	private static LodChunk getChunkData(ClientLevel level, int chunkX, int chunkZ, int cellSize) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		LodChunk cached = CHUNKS.get(key);
		if (cached != null && cached.cellSize == cellSize) {
			return cached;
		}
		if (cached != null) {
			CHUNKS.remove(key);
		}

		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return null;
		}

		int cells = 16 / cellSize;
		LodChunk data = new LodChunk(cellSize, cells);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Minecraft minecraft = Minecraft.getInstance();

		for (int z = 0; z < cells; z++) {
			for (int x = 0; x < cells; x++) {
				int blockX = chunkX * 16 + x * cellSize + cellSize / 2;
				int blockZ = chunkZ * 16 + z * cellSize + cellSize / 2;
				// ChunkAccess#getHeight is the first air Y, which is exactly the
				// top face of the highest non-air block.
				int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15);
				int index = x + z * cells;

				if (height <= level.getMinY()) {
					data.heights[index] = NO_HEIGHT;
					continue;
				}

				pos.set(blockX, height - 1, blockZ);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					data.heights[index] = NO_HEIGHT;
					continue;
				}

				boolean water = state.getFluidState().is(FluidTags.WATER);
				boolean leaves = state.is(BlockTags.LEAVES);
				Biome biome = level.getBiome(pos).value();
				int color = -1;
				try {
					BlockTintSource tintSource = minecraft.getBlockColors().getTintSource(state, 0);
					if (tintSource != null) {
						color = tintSource.colorInWorld(state, level, pos);
					}
				} catch (Throwable ignored) {
					color = -1;
				}
				if (water) {
					color = biome.getWaterColor();
				} else if (state.is(Blocks.GRASS_BLOCK)
					|| state.is(Blocks.SHORT_GRASS)
					|| state.is(Blocks.TALL_GRASS)
					|| state.is(Blocks.FERN)
					|| state.is(Blocks.LARGE_FERN)) {
					color = biome.getGrassColor(blockX, blockZ);
				} else if (state.is(BlockTags.LEAVES)) {
					color = biome.getFoliageColor();
				} else if (color == -1 || color == 0) {
					color = state.getMapColor(level, pos).col;
				}
				if (color == 0) {
					color = 0x7F7F7F;
				}

				data.heights[index] = height;
				data.colors[index] = color | 0xFF000000;
				data.flags[index] = (byte) ((water ? 1 : 0) | (leaves ? 2 : 0));
			}
		}

		CHUNKS.put(key, data);
		return data;
	}

	private static int sampleHeight(ClientLevel level, int cellX, int cellZ, int cellSize) {
		int blockX = cellX * cellSize + cellSize / 2;
		int blockZ = cellZ * cellSize + cellSize / 2;
		LevelChunk chunk = level.getChunkSource()
			.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false);
		if (chunk == null) {
			return NO_HEIGHT;
		}
		int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15);
		return height <= level.getMinY() ? NO_HEIGHT : height;
	}

	private static void addQuad(
		BufferBuilder builder,
		float x0, float y0, float z0,
		float x1, float y1, float z1,
		float x2, float y2, float z2,
		float x3, float y3, float z3,
		int argb
	) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int a = (argb >>> 24) & 0xFF;
		builder.addVertex(x0, y0, z0).setColor(r, g, b, a);
		builder.addVertex(x1, y1, z1).setColor(r, g, b, a);
		builder.addVertex(x2, y2, z2).setColor(r, g, b, a);
		builder.addVertex(x3, y3, z3).setColor(r, g, b, a);
	}

	private static int scale(int argb, float factor) {
		int r = Math.min(255, Math.max(0, (int) (((argb >> 16) & 0xFF) * factor)));
		int g = Math.min(255, Math.max(0, (int) (((argb >> 8) & 0xFF) * factor)));
		int b = Math.min(255, Math.max(0, (int) ((argb & 0xFF) * factor)));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static final class LodChunk {
		final int cellSize;
		final int[] heights;
		final int[] colors;
		final byte[] flags;

		LodChunk(int cellSize, int cells) {
			this.cellSize = cellSize;
			this.heights = new int[cells * cells];
			this.colors = new int[cells * cells];
			this.flags = new byte[cells * cells];
		}
	}
}

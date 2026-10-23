package dev.chunkview;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.PrimitiveTopology;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Draws the LOD meshes in one extra GPU pass immediately after vanilla's
 * opaque terrain. The pass uses the main colour/depth targets, so vanilla
 * translucent water and entities still composite correctly on top.
 */
@Environment(EnvType.CLIENT)
public final class LodRenderer {
	private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
	private static final List<RetiredBuffer> RETIRED = new ArrayList<>();
	private static final int RETIRE_FRAMES = 120;

	private static RenderPipeline pipeline;
	private static boolean initialized;
	private static boolean disabled;
	private static boolean errorLogged;
	private static long frame;

	private LodRenderer() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;

		pipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.FOG)
				.withLocation(Identifier.fromNamespaceAndPath(ChunkView.MOD_ID, "pipeline/lod_terrain"))
				.withVertexShader(Identifier.fromNamespaceAndPath(ChunkView.MOD_ID, "core/lod_terrain"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(ChunkView.MOD_ID, "core/lod_terrain"))
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, true))
				.withCull(false)
				.build()
		);

		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(LodRenderer::afterOpaqueTerrain);
	}

	public static void enqueueUpload(
		LodRegion region,
		MeshData mesh,
		ByteBufferBuilder allocator,
		int vertexCount,
		AABB bounds
	) {
		TASKS.add(() -> {
			GpuBuffer buffer = null;
			try {
				ByteBuffer data = mesh.vertexBuffer();
				data.position(0);
				buffer = RenderSystem.getDevice().createBuffer(
					() -> "ChunkView LOD region",
					GpuBuffer.USAGE_VERTEX,
					data
				);
			} catch (Throwable t) {
				ChunkView.LOGGER.warn("[ChunkView] LOD upload failed", t);
			} finally {
				try {
					mesh.close();
				} catch (Throwable ignored) {
				}
				try {
					allocator.close();
				} catch (Throwable ignored) {
				}
			}

			if (buffer == null) {
				region.pending = false;
				return;
			}
			if (!region.active) {
				buffer.close();
				region.pending = false;
				return;
			}

			GpuBuffer old = region.buffer;
			region.buffer = buffer;
			region.slice = buffer.slice();
			region.vertexCount = vertexCount;
			region.bounds = bounds;
			region.pending = false;

			if (old != null) {
				RETIRED.add(new RetiredBuffer(old, frame + RETIRE_FRAMES));
			}
		});
	}

	public static void enqueueClose(GpuBuffer buffer) {
		if (buffer != null) {
			TASKS.add(() -> RETIRED.add(new RetiredBuffer(buffer, frame + RETIRE_FRAMES)));
		}
	}

	private static void afterOpaqueTerrain(LevelTerrainRenderContext context) {
		runTasks();
		closeRetired();

		if (disabled) {
			return;
		}

		ChunkViewConfig config = ChunkView.config();
		int renderDistance = PerformanceGovernor.visibleChunks();
		int fullDetail = PerformanceGovernor.fullDetailChunks();
		if (!config.lodActive() || renderDistance <= fullDetail) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		if (!camera.initialized) {
			return;
		}

		List<LodRegion> source = LodWorld.renderRegions();
		if (source.isEmpty()) {
			return;
		}

		double maxDistance = renderDistance * 16.0 + 192.0;
		double maxDistanceSq = maxDistance * maxDistance;
		double cameraX = camera.pos.x;
		double cameraY = camera.pos.y;
		double cameraZ = camera.pos.z;
		Frustum frustum = camera.cullFrustum;

		List<LodRegion> visible = new ArrayList<>(source.size());
		for (LodRegion region : source) {
			if (!region.active || region.buffer == null || region.slice == null || region.vertexCount <= 0) {
				continue;
			}

			AABB bounds = region.bounds;
			double centerX = (bounds.minX + bounds.maxX) * 0.5;
			double centerY = (bounds.minY + bounds.maxY) * 0.5;
			double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
			double dx = centerX - cameraX;
			double dy = centerY - cameraY;
			double dz = centerZ - cameraZ;
			if (dx * dx + dy * dy + dz * dz > maxDistanceSq) {
				continue;
			}
			if (frustum != null && !frustum.isVisible(bounds)) {
				continue;
			}
			visible.add(region);
		}

		if (visible.isEmpty()) {
			return;
		}

		visible.sort(Comparator.comparingDouble(region -> {
			AABB bounds = region.bounds;
			double dx = (bounds.minX + bounds.maxX) * 0.5 - cameraX;
			double dz = (bounds.minZ + bounds.maxZ) * 0.5 - cameraZ;
			return dx * dx + dz * dz;
		}));

		RenderTarget target = context.gameRenderer().mainRenderTarget();
		Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix)
			.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ);
		float light = LodWorld.dayLight();
		// ModelOffset.x is repurposed as the full-detail radius for the shader's
		// near-discard test. It is not used for LOD vertex positioning.
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(light, light, light, 1.0f),
			new Vector3f((fullDetail + 1) * 16.0f, 0.0f, 0.0f),
			new Matrix4f()
		);

		try (RenderPass pass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				() -> "ChunkView LOD terrain",
				target.getColorTextureView(),
				Optional.empty(),
				target.getDepthTextureView(),
				OptionalDouble.empty()
			)) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);

			for (LodRegion region : visible) {
				pass.setVertexBuffer(0, region.slice);
				pass.draw(region.vertexCount, 1, 0, 0);
			}
		} catch (Throwable t) {
			disabled = true;
			if (!errorLogged) {
				errorLogged = true;
				ChunkView.LOGGER.error("[ChunkView] LOD draw failed; LOD rendering disabled for this session", t);
			}
		}
	}

	private static void runTasks() {
		int limit = 32;
		Runnable task;
		while (limit-- > 0 && (task = TASKS.poll()) != null) {
			try {
				task.run();
			} catch (Throwable t) {
				ChunkView.LOGGER.warn("[ChunkView] Render task failed", t);
			}
		}
	}

	private static void closeRetired() {
		frame++;
		RETIRED.removeIf(retired -> {
			if (frame >= retired.frame) {
				try {
					retired.buffer.close();
				} catch (Throwable ignored) {
				}
				return true;
			}
			return false;
		});
	}

	private record RetiredBuffer(GpuBuffer buffer, long frame) {
	}
}

package dev.chunkview;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ChunkViewCommands {
	private ChunkViewCommands() {
	}

	public static void initialize() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = LiteralArgumentBuilder
				.<FabricClientCommandSource>literal("chunkview")
				.executes(context -> status(context.getSource()))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
					.executes(context -> status(context.getSource())))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("adaptive")
					.then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument(
							"enabled", BoolArgumentType.bool())
						.executes(context -> {
							boolean enabled = BoolArgumentType.getBool(context, "enabled");
							ChunkView.config().adaptiveEnabled = enabled;
							ChunkView.config().save();
							PerformanceGovernor.reset();
							context.getSource().sendFeedback(Component.literal(
								"[ChunkView] Adaptive FPS " + (enabled ? "ON" : "OFF")
							));
							return 1;
						})))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("target")
					.then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
							"fps", IntegerArgumentType.integer(30, 240))
						.executes(context -> {
							int fps = IntegerArgumentType.getInteger(context, "fps");
							ChunkView.config().targetFps = fps;
							ChunkView.config().save();
							PerformanceGovernor.reset();
							context.getSource().sendFeedback(Component.literal(
								"[ChunkView] Target " + fps + " FPS. Turn VSync off to see more than the monitor refresh rate."
							));
							return 1;
						})))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("min")
					.then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
							"chunks", IntegerArgumentType.integer(4, 64))
						.executes(context -> {
							int chunks = IntegerArgumentType.getInteger(context, "chunks");
							ChunkView.config().minVisibleDistance = chunks;
							ChunkView.config().sanitize();
							ChunkView.config().save();
							PerformanceGovernor.reset();
							context.getSource().sendFeedback(Component.literal(
								"[ChunkView] Minimum visible radius: " + ChunkView.config().minVisibleDistance + " chunks"
							));
							return 1;
						})))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload")
					.executes(context -> {
						ChunkView.reloadConfig();
						context.getSource().sendFeedback(Component.literal(
							"[ChunkView] Reloaded: " + ChunkView.config().describe()
						));
						return 1;
					}))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("preset")
					.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
							"name", StringArgumentType.word())
						.executes(context -> {
							String name = StringArgumentType.getString(context, "name");
							if (!ChunkView.config().applyPreset(name)) {
								context.getSource().sendError(Component.literal(
									"[ChunkView] Unknown preset. Use phone, balanced, or quality."
								));
								return 0;
							}
							LodWorld.reload();
							PerformanceGovernor.reset();
							context.getSource().sendFeedback(Component.literal(
								"[ChunkView] Preset '" + name + "': " + ChunkView.config().describe()
							));
							return 1;
						})));

			dispatcher.register(root);
		});
	}

	private static int status(FabricClientCommandSource source) {
		ChunkViewConfig config = ChunkView.config();
		var minecraft = net.minecraft.client.Minecraft.getInstance();
		source.sendFeedback(Component.literal("[ChunkView] " + config.describe()));
		source.sendFeedback(Component.literal(
			"[ChunkView] visible=" + PerformanceGovernor.visibleChunks()
				+ " fullDetail=" + PerformanceGovernor.fullDetailChunks()
				+ " fps=" + minecraft.getFps()
				+ " target=" + PerformanceGovernor.effectiveTarget(minecraft, config)
				+ " loaded=" + (minecraft.level == null ? 0 : minecraft.level.getChunkSource().getLoadedChunksCount())
		));
		source.sendFeedback(Component.literal(
			"[ChunkView] LOD regions: " + LodWorld.renderRegions().size()
				+ ", heap-safe max: " + ChunkView.memorySafeRenderDistance() + " chunks"
		));
		return 1;
	}
}

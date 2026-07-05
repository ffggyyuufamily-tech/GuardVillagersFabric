package com.guardvillagers.client;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.GuardRole;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GuardDebugRenderer {
	private static final int CACHE_REFRESH_TICKS = 10;
	private static final int CIRCLE_SEGMENTS = 64;
	private static final double DETECTION_RANGE = 32.0D;
	private static final double CIRCLE_Y_OFFSET = 0.01D;
	private static final float LABEL_SCALE = 0.020F;
	private static final float LINE_HALF_WIDTH = 0.015F;
	private static final int PATH_TRAIL_KEEP_BEHIND = 3;
	// Path nodes snap to integer block Y. When a guard walks on partial blocks (slabs,
	// stairs, carpets) the actual ground surface is above pos.getY(), so the fill must
	// float high enough to stay above those surfaces. Height adds a thin slab body so the
	// marker is visible from any camera angle instead of collapsing to zero area edge-on.
	private static final double PATH_FILL_Y_BASE = 0.06D;
	private static final double PATH_FILL_HEIGHT = 0.09D;
	private static final double PATH_OUTLINE_Y_BASE = 0.055D;
	private static final double PATH_OUTLINE_HEIGHT = 0.1D;
	private static final double PATH_OUTLINE_THICKNESS = 0.075D;
	private static final float PATH_CURRENT_R = 0.42F;
	private static final float PATH_CURRENT_G = 1.00F;
	private static final float PATH_CURRENT_B = 0.34F;
	private static final float PATH_NODE_R = 0.95F;
	private static final float PATH_NODE_G = 0.95F;
	private static final float PATH_NODE_B = 0.20F;
	private static final float PATH_DESTINATION_R = 1.00F;
	private static final float PATH_DESTINATION_G = 0.38F;
	private static final float PATH_DESTINATION_B = 0.34F;
	private static final float PATH_FILL_ALPHA = 0.50F;
	private static final float PATH_OUTLINE_ALPHA = 0.82F;
	private static final float[][] CIRCLE_POINTS = buildCirclePoints();
	private static final List<GuardEntity> CACHED_GUARDS = new ArrayList<>();
	private static long lastCacheTick = Long.MIN_VALUE;
	private static double lastCacheRange = -1.0D;

	private GuardDebugRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(GuardDebugRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}
		if (!ClientDebugState.isEnabled()) {
			CACHED_GUARDS.clear();
			return;
		}

		double range = Math.max(1.0D, ClientDebugState.getRange());
		updateGuardCache(client, range);
		if (CACHED_GUARDS.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrices();
		VertexConsumerProvider vertexConsumers = context.consumers();
		Vec3d cameraPos = context.worldState().cameraRenderState.pos;
		double maxDistanceSq = range * range;
		ClientTacticsDataStore.WorldContext worldContext = ClientTacticsDataStore.resolveContext(client, client.world);

		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		for (GuardEntity guard : CACHED_GUARDS) {
			if (!guard.isAlive() || guard.squaredDistanceTo(client.player) > maxDistanceSq) {
				continue;
			}
			ClientGuardDebugData.GuardDebugSnapshot snapshot = ClientGuardDebugData.get(guard.getId());
			renderDetectionCircle(matrices, vertexConsumers, guard, cameraPos);
			renderPathHighlights(matrices, vertexConsumers, guard, snapshot);
			renderTargetLine(matrices, vertexConsumers, guard, snapshot, client, cameraPos);
			renderLabels(matrices, vertexConsumers, guard, client, worldContext);
		}

		matrices.pop();
		ClientGuardDebugData.pruneMissing(client.world);
	}

	private static void updateGuardCache(MinecraftClient client, double range) {
		if (client.world == null || client.player == null) {
			CACHED_GUARDS.clear();
			lastCacheTick = Long.MIN_VALUE;
			return;
		}
		long tick = client.world.getTime();
		if (lastCacheTick != Long.MIN_VALUE && tick - lastCacheTick < CACHE_REFRESH_TICKS && Math.abs(lastCacheRange - range) < 0.001D) {
			return;
		}

		lastCacheTick = tick;
		lastCacheRange = range;
		double rangeSq = range * range;
		CACHED_GUARDS.clear();
		CACHED_GUARDS.addAll(client.world.getEntitiesByClass(
			GuardEntity.class,
			client.player.getBoundingBox().expand(range),
			guard -> guard.isAlive()
				&& (guard.getOwnerUuid() == null || guard.isOwnedBy(client.player.getUuid()))
				&& guard.squaredDistanceTo(client.player) <= rangeSq
		));
	}

	private static void renderDetectionCircle(
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		GuardEntity guard,
		Vec3d cameraPos
	) {
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.debugFilledBox());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		double centerX = guard.getX();
		double centerY = guard.getY() + CIRCLE_Y_OFFSET;
		double centerZ = guard.getZ();

		for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
			double x1 = centerX + DETECTION_RANGE * CIRCLE_POINTS[i][0];
			double z1 = centerZ + DETECTION_RANGE * CIRCLE_POINTS[i][1];
			double x2 = centerX + DETECTION_RANGE * CIRCLE_POINTS[i + 1][0];
			double z2 = centerZ + DETECTION_RANGE * CIRCLE_POINTS[i + 1][1];

			renderLineSegment(
				consumer,
				matrix,
				cameraPos,
				x1,
				centerY,
				z1,
				x2,
				centerY,
				z2,
				0.0F,
				0.9F,
				0.25F,
				1.0F
			);
		}
	}

	private static void renderPathHighlights(
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		GuardEntity guard,
		ClientGuardDebugData.GuardDebugSnapshot snapshot
	) {
		if (snapshot == null || snapshot.pathNodes().isEmpty()) {
			return;
		}
		List<BlockPos> nodes = snapshot.pathNodes();
		int currentPathIndex = snapshot.currentPathIndex();
		if (currentPathIndex < 0 || currentPathIndex >= nodes.size()) {
			return;
		}

		int firstVisibleIndex = Math.max(0, currentPathIndex - PATH_TRAIL_KEEP_BEHIND);
		VertexConsumer filled = vertexConsumers.getBuffer(RenderLayers.debugFilledBox());
		for (int i = 0; i < nodes.size(); i++) {
			if (i < firstVisibleIndex) {
				continue;
			}
			BlockPos node = nodes.get(i);
			if (i == currentPathIndex) {
				drawPathMarkerCarpet(matrices, filled, node, PATH_CURRENT_R, PATH_CURRENT_G, PATH_CURRENT_B);
				continue;
			}
			if (i == nodes.size() - 1) {
				drawPathMarkerCarpet(
					matrices,
					filled,
					node,
					PATH_DESTINATION_R,
					PATH_DESTINATION_G,
					PATH_DESTINATION_B
				);
				continue;
			}
			drawPathMarkerCarpet(matrices, filled, node, PATH_NODE_R, PATH_NODE_G, PATH_NODE_B);
		}
	}

	private static void renderTargetLine(
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		GuardEntity guard,
		ClientGuardDebugData.GuardDebugSnapshot snapshot,
		MinecraftClient client,
		Vec3d cameraPos
	) {
		LivingEntity target = resolveTarget(guard, snapshot, client);
		if (target == null || !target.isAlive()) {
			return;
		}

		Vec3d origin = guard.getEyePos();
		Vec3d destination = target.getEyePos();
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.debugFilledBox());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		renderLineSegment(
			consumer,
			matrix,
			cameraPos,
			origin.x,
			origin.y,
			origin.z,
			destination.x,
			destination.y,
			destination.z,
			0.95F,
			0.95F,
			0.20F,
			1.0F
		);
	}

	private static LivingEntity resolveTarget(
		GuardEntity guard,
		ClientGuardDebugData.GuardDebugSnapshot snapshot,
		MinecraftClient client
	) {
		if (snapshot != null && snapshot.targetEntityId() >= 0 && client.world != null) {
			Entity synced = client.world.getEntityById(snapshot.targetEntityId());
			if (synced instanceof LivingEntity living) {
				return living;
			}
		}
		return guard.getTarget();
	}

	private static void renderLabels(
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		GuardEntity guard,
		MinecraftClient client,
		ClientTacticsDataStore.WorldContext worldContext
	) {
		TextRenderer textRenderer = client.textRenderer;
		List<DebugLabelLine> lines = buildLabelLines(guard, client, worldContext);
		if (lines.isEmpty()) {
			return;
		}

		matrices.push();
		matrices.translate(guard.getX(), guard.getY() + guard.getHeight() + 0.75D, guard.getZ());
		matrices.multiply(client.gameRenderer.getCamera().getRotation());
		matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		int background = (int) (client.options.getTextBackgroundOpacity(0.25F) * 255.0F) << 24;
		float lineY = -(lines.size() - 1) * (textRenderer.fontHeight + 2);
		for (DebugLabelLine line : lines) {
			float lineX = -textRenderer.getWidth(line.text()) / 2.0F;
			textRenderer.draw(
				line.text(),
				lineX,
				lineY,
				line.color(),
				false,
				matrix,
				vertexConsumers,
				TextRenderer.TextLayerType.NORMAL,
				background,
				LightmapTextureManager.MAX_LIGHT_COORDINATE
			);
			lineY += textRenderer.fontHeight + 2;
		}
		matrices.pop();
	}

	private static List<DebugLabelLine> buildLabelLines(GuardEntity guard, MinecraftClient client, ClientTacticsDataStore.WorldContext worldContext) {
		List<DebugLabelLine> lines = new ArrayList<>(9);
		int hp = Math.max(0, MathHelper.floor(guard.getHealth()));
		int maxHp = Math.max(1, MathHelper.floor(guard.getMaxHealth()));
		lines.add(line("HP: " + hp + "/" + maxHp, 0xFF5555));

		int level = guard.getLevel();
		lines.add(line("Level: " + level, 0x55FF55));

		int xp = guard.getExperience();
		String xpLine = level >= 10 ? "XP: " + xp + "/MAX" : "XP: " + xp + "/" + (level * 120);
		lines.add(line(xpLine, 0x55FFFF));

		lines.add(line("Role: " + titleCase(guard.getRole().name()), 0xFFFF55));

		LivingEntity target = guard.getTarget();
		String targetValue = target == null ? "None" : target.getName().getString();
		lines.add(line("Target: " + targetValue, 0xFF55FF));

		String typeValue = guard.getRole() == GuardRole.BOWMAN ? "Bowmen" : "Swordsmen";
		lines.add(line("Type: " + typeValue, 0xF5E6A9));

		UUID ownerUuid = guard.getOwnerUuid();
		String ownerValue = ownerUuid == null ? "Nil" : resolveOwnerName(ownerUuid, client);
		lines.add(line("Owned: " + ownerValue, ownerUuid == null ? 0xAAAAAA : 0xFFFFFF));

		RegionColor regionColor = ClientTacticsDataStore.getInstance().getRegionColor(worldContext, guard.getBlockX() >> 4, guard.getBlockZ() >> 4);
		String zoneValue = regionColor == RegionColor.NONE ? "Nil" : regionColor.label();
		lines.add(line("Zone: " + zoneValue, regionColor == RegionColor.NONE ? 0xAAAAAA : 0x5555FF));

		String groupValue = guard.getGroupIndex() < 0 ? "Nil" : guard.getGroupName();
		lines.add(line("Group: " + groupValue, guard.getGroupIndex() < 0 ? 0xAAAAAA : 0xFFAA00));
		return lines;
	}

	private static DebugLabelLine line(String text, int color) {
		return new DebugLabelLine(text, color);
	}

	private static String resolveOwnerName(UUID ownerUuid, MinecraftClient client) {
		if (client.getNetworkHandler() == null) {
			return shortUuid(ownerUuid);
		}
		PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(ownerUuid);
		if (entry != null && entry.getProfile() != null) {
			return entry.getProfile().name();
		}
		return shortUuid(ownerUuid);
	}

	private static String shortUuid(UUID uuid) {
		String raw = uuid.toString();
		return raw.substring(0, Math.min(8, raw.length())) + "...";
	}

	private static String titleCase(String value) {
		if (value == null || value.isBlank()) {
			return "Nil";
		}
		String[] parts = value.toLowerCase(Locale.ROOT).split("_");
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.isEmpty()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				builder.append(part.substring(1));
			}
		}
		return builder.length() == 0 ? "Nil" : builder.toString();
	}

	private static void drawPathMarkerCarpet(MatrixStack matrices, VertexConsumer consumer, BlockPos pos, float r, float g, float b) {
		double minX = pos.getX();
		double maxX = minX + 1.0D;
		double minZ = pos.getZ();
		double maxZ = minZ + 1.0D;

		double fillMinY = pos.getY() + PATH_FILL_Y_BASE;
		double fillMaxY = fillMinY + PATH_FILL_HEIGHT;
		drawFilledSlab(matrices, consumer, minX, fillMinY, minZ, maxX, fillMaxY, maxZ, r, g, b, PATH_FILL_ALPHA);

		float outlineR = brightenChannel(r);
		float outlineG = brightenChannel(g);
		float outlineB = brightenChannel(b);
		double outlineMinY = pos.getY() + PATH_OUTLINE_Y_BASE;
		double outlineMaxY = outlineMinY + PATH_OUTLINE_HEIGHT;

		drawFilledSlab(matrices, consumer, minX, outlineMinY, minZ, maxX, outlineMaxY, minZ + PATH_OUTLINE_THICKNESS,
			outlineR, outlineG, outlineB, PATH_OUTLINE_ALPHA);
		drawFilledSlab(matrices, consumer, minX, outlineMinY, maxZ - PATH_OUTLINE_THICKNESS, maxX, outlineMaxY, maxZ,
			outlineR, outlineG, outlineB, PATH_OUTLINE_ALPHA);
		drawFilledSlab(matrices, consumer, minX, outlineMinY, minZ + PATH_OUTLINE_THICKNESS,
			minX + PATH_OUTLINE_THICKNESS, outlineMaxY, maxZ - PATH_OUTLINE_THICKNESS,
			outlineR, outlineG, outlineB, PATH_OUTLINE_ALPHA);
		drawFilledSlab(matrices, consumer, maxX - PATH_OUTLINE_THICKNESS, outlineMinY, minZ + PATH_OUTLINE_THICKNESS,
			maxX, outlineMaxY, maxZ - PATH_OUTLINE_THICKNESS,
			outlineR, outlineG, outlineB, PATH_OUTLINE_ALPHA);
	}

	private static float brightenChannel(float channel) {
		return Math.min(1.0F, channel + 0.24F);
	}

	/**
	 * Draws a thin 3D box so debug markers stay visible from any camera angle. Flat quads
	 * collapse to zero screen area when the camera is level with the surface they sit on,
	 * and their Y can also fall inside partial blocks (slabs, carpets, stairs) that raise
	 * the walk surface above the PathNode's integer Y. Emitting all six faces with both
	 * windings guarantees the marker renders regardless of cull state or view direction.
	 */
	private static void drawFilledSlab(
		MatrixStack matrices,
		VertexConsumer consumer,
		double minX,
		double minY,
		double minZ,
		double maxX,
		double maxY,
		double maxZ,
		float r,
		float g,
		float b,
		float a
	) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		// Top (normal +Y)
		quad(consumer, matrix, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
		quad(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
		// Bottom (normal -Y)
		quad(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
		quad(consumer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);
		// North (-Z face)
		quad(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
		quad(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, a);
		// South (+Z face)
		quad(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a);
		quad(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
		// West (-X face)
		quad(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, a);
		quad(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a);
		// East (+X face)
		quad(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
		quad(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
	}

	private static void quad(
		VertexConsumer consumer,
		Matrix4f matrix,
		double x1,
		double y1,
		double z1,
		double x2,
		double y2,
		double z2,
		double x3,
		double y3,
		double z3,
		double x4,
		double y4,
		double z4,
		float r,
		float g,
		float b,
		float a
	) {
		quadVertex(consumer, matrix, x1, y1, z1, r, g, b, a);
		quadVertex(consumer, matrix, x2, y2, z2, r, g, b, a);
		quadVertex(consumer, matrix, x3, y3, z3, r, g, b, a);
		quadVertex(consumer, matrix, x4, y4, z4, r, g, b, a);
	}

	private static void quadVertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float r, float g, float b, float a) {
		consumer.vertex(matrix, (float) x, (float) y, (float) z).color(r, g, b, a);
	}

	/**
	 * Renders a line segment between two world-space points as a thin camera-facing quad.
	 */
	private static void renderLineSegment(
		VertexConsumer consumer,
		Matrix4f matrix,
		Vec3d cameraPos,
		double x1,
		double y1,
		double z1,
		double x2,
		double y2,
		double z2,
		float r,
		float g,
		float b,
		float a
	) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double dz = z2 - z1;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-6D) {
			return;
		}

		double midX = (x1 + x2) * 0.5D;
		double midY = (y1 + y2) * 0.5D;
		double midZ = (z1 + z2) * 0.5D;
		double camDx = midX - cameraPos.x;
		double camDy = midY - cameraPos.y;
		double camDz = midZ - cameraPos.z;

		double crossX = dy * camDz - dz * camDy;
		double crossY = dz * camDx - dx * camDz;
		double crossZ = dx * camDy - dy * camDx;
		double crossLen = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
		if (crossLen < 1.0E-6D) {
			crossX = -dz;
			crossY = 0.0D;
			crossZ = dx;
			crossLen = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
			if (crossLen < 1.0E-6D) {
				return;
			}
		}

		double scale = LINE_HALF_WIDTH / crossLen;
		double offX = crossX * scale;
		double offY = crossY * scale;
		double offZ = crossZ * scale;

		float ax = (float) (x1 + offX);
		float ay = (float) (y1 + offY);
		float az = (float) (z1 + offZ);

		float bx = (float) (x1 - offX);
		float by = (float) (y1 - offY);
		float bz = (float) (z1 - offZ);

		float cx = (float) (x2 - offX);
		float cy = (float) (y2 - offY);
		float cz = (float) (z2 - offZ);

		float ex = (float) (x2 + offX);
		float ey = (float) (y2 + offY);
		float ez = (float) (z2 + offZ);

		consumer.vertex(matrix, ax, ay, az).color(r, g, b, a);
		consumer.vertex(matrix, bx, by, bz).color(r, g, b, a);
		consumer.vertex(matrix, cx, cy, cz).color(r, g, b, a);
		consumer.vertex(matrix, ex, ey, ez).color(r, g, b, a);
	}

	private static float[][] buildCirclePoints() {
		float[][] points = new float[CIRCLE_SEGMENTS + 1][2];
		for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
			double angle = (2.0D * Math.PI * i) / CIRCLE_SEGMENTS;
			points[i][0] = (float) Math.cos(angle);
			points[i][1] = (float) Math.sin(angle);
		}
		return points;
	}

	private record DebugLabelLine(String text, int color) {
	}
}

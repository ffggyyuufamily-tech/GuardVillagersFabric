package com.guardvillagers.entity.ai;

import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public final class GuardMovementSlotResolver {
	private static final double GROUP_QUERY_RADIUS = 48.0D;
	private static final double GROUP_QUERY_HEIGHT = 24.0D;
	private static final int GROUND_SEARCH_VERTICAL_RANGE = 6;
	private static final int MAX_MOVEMENT_COHORT_SIZE = 12;
	private static final int FOLLOW_LANE_COUNT = 3;
	private static final double FOLLOW_MIN_TRAIL_DISTANCE = 2.5D;
	private static final double FOLLOW_ROW_SPACING = 1.5D;
	private static final double COHORT_ANCHOR_SPACING_MULTIPLIER = 2.5D;
	private static final Comparator<GuardEntity> SLOT_ORDER = Comparator
			.comparingInt(GuardEntity::getGroupIndex)
			.thenComparingInt(GuardEntity::getGroupColumn)
			.thenComparing(entity -> entity.getUuid());

	private GuardMovementSlotResolver() {
	}

	public static Vec3d resolveDynamicSlot(ServerWorld world, GuardEntity guard, Vec3d anchor, double spacing,
			boolean allowCenter) {
		int slotOrder = resolveSlotOrder(world, guard, anchor);
		double rotation = rotationForGuard(guard);
		int cohortIndex = slotOrder / MAX_MOVEMENT_COHORT_SIZE;
		int localSlotOrder = slotOrder % MAX_MOVEMENT_COHORT_SIZE;
		Vec3d offset = offsetForSlot(localSlotOrder, spacing, allowCenter, rotation);
		offset = offset.add(cohortAnchorOffset(cohortIndex, spacing, rotation));
		return anchor.add(offset);
	}

	public static BlockPos resolveGroundSlot(ServerWorld world, GuardEntity guard, BlockPos anchor, double spacing,
			boolean allowCenter) {
		Vec3d slot = resolveDynamicSlot(world, guard, Vec3d.ofBottomCenter(anchor), spacing, allowCenter);
		BlockPos candidateOrigin = BlockPos.ofFloored(slot.x, anchor.getY(), slot.z);
		return groundSlotNear(world, candidateOrigin, anchor);
	}

	public static BlockPos resolveFollowCatchUpSlot(ServerWorld world, GuardEntity guard, Vec3d anchor, double spacing) {
		int slotOrder = resolveSlotOrder(world, guard, anchor);
		int cohortIndex = slotOrder / MAX_MOVEMENT_COHORT_SIZE;
		int localSlotOrder = slotOrder % MAX_MOVEMENT_COHORT_SIZE;
		int laneIndex = Math.floorMod(localSlotOrder, FOLLOW_LANE_COUNT) - (FOLLOW_LANE_COUNT / 2);
		int rowIndex = localSlotOrder / FOLLOW_LANE_COUNT;
		double rotation = rotationForGuard(guard);

		Vec3d trailDirection = guard.getEntityPos().subtract(anchor);
		trailDirection = new Vec3d(trailDirection.x, 0.0D, trailDirection.z);
		if (trailDirection.lengthSquared() < 1.0E-4D) {
			trailDirection = new Vec3d(Math.cos(rotation), 0.0D, Math.sin(rotation));
		} else {
			trailDirection = trailDirection.normalize();
		}

		Vec3d right = new Vec3d(-trailDirection.z, 0.0D, trailDirection.x);
		double trailDistance = FOLLOW_MIN_TRAIL_DISTANCE + rowIndex * FOLLOW_ROW_SPACING;
		Vec3d slot = anchor.add(trailDirection.multiply(trailDistance))
				.add(right.multiply(laneIndex * spacing))
				.add(cohortAnchorOffset(cohortIndex, spacing, rotation));
		BlockPos anchorPos = BlockPos.ofFloored(anchor.x, anchor.y, anchor.z);
		BlockPos candidateOrigin = BlockPos.ofFloored(slot.x, anchor.y, slot.z);
		return groundSlotNear(world, candidateOrigin, anchorPos);
	}

	private static List<GuardEntity> collectNearbyGroup(ServerWorld world, GuardEntity guard, Vec3d anchor) {
		Box searchBox = Box.of(anchor, GROUP_QUERY_RADIUS * 2.0D, GROUP_QUERY_HEIGHT * 2.0D, GROUP_QUERY_RADIUS * 2.0D);
		List<GuardEntity> guards = world.getEntitiesByClass(
				GuardEntity.class,
				searchBox,
				entity -> entity.isAlive() && guard.isSameMovementGroup(entity));
		guards.sort(SLOT_ORDER);
		return guards;
	}

	private static int resolveSlotOrder(ServerWorld world, GuardEntity guard, Vec3d anchor) {
		List<GuardEntity> nearbyGroup = collectNearbyGroup(world, guard, anchor);
		int slotOrder = nearbyGroup.indexOf(guard);
		return slotOrder >= 0 ? slotOrder : Math.floorMod(guard.getUuid().hashCode(), MAX_MOVEMENT_COHORT_SIZE * 4);
	}

	private static Vec3d offsetForSlot(int slotOrder, double spacing, boolean allowCenter, double rotation) {
		if (allowCenter && slotOrder == 0) {
			return Vec3d.ZERO;
		}

		int remaining = allowCenter ? slotOrder - 1 : slotOrder;
		int ring = 1;
		int ringCapacity = 8;
		while (remaining >= ringCapacity) {
			remaining -= ringCapacity;
			ring++;
			ringCapacity = ring * 8;
		}

		double radius = Math.max(1.25D, ring * spacing);
		double angle = rotation + (Math.PI * 2.0D * remaining / ringCapacity);
		return new Vec3d(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
	}

	private static Vec3d cohortAnchorOffset(int cohortIndex, double spacing, double rotation) {
		if (cohortIndex <= 0) {
			return Vec3d.ZERO;
		}
		return offsetForSlot(
				cohortIndex - 1,
				Math.max(spacing * COHORT_ANCHOR_SPACING_MULTIPLIER, spacing + 1.0D),
				false,
				rotation + (Math.PI / 8.0D));
	}

	private static BlockPos groundSlotNear(ServerWorld world, BlockPos candidateOrigin, BlockPos fallbackAnchor) {
		BlockPos grounded = GuardVillagersMod.findNearbyGuardSpawnPos(world, candidateOrigin, GROUND_SEARCH_VERTICAL_RANGE);
		if (grounded != null) {
			return grounded.toImmutable();
		}
		BlockPos fallback = GuardVillagersMod.findNearbyGuardSpawnPos(world, fallbackAnchor, GROUND_SEARCH_VERTICAL_RANGE);
		return fallback != null ? fallback.toImmutable() : fallbackAnchor.toImmutable();
	}

	private static double rotationForGuard(GuardEntity guard) {
		int seed = Math.floorMod(guard.getGroupIndex() * 3 + guard.getGroupColumn(), 8);
		return seed * (Math.PI / 4.0D);
	}
}

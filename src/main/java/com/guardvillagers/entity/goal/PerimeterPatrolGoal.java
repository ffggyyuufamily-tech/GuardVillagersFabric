package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardBehaviorExecutor;
import com.guardvillagers.village.VillageDescriptor;
import com.guardvillagers.village.VillageManagerHandler;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class PerimeterPatrolGoal extends Goal {
	private static final int EDGE_STEP = 6;
	private static final int EDGE_JITTER = 2;
	private static final int INTERIOR_STEP = 8;
	private static final int INTERIOR_JITTER = 3;
	private static final int MIN_NEXT_DISTANCE_SQ = 36;
	private static final int RECALCULATE_INTERVAL = 120;
	private static final int MODE_SWITCH_BASE = 400;
	private static final double PATROL_SLOT_SPACING = 1.25D;

	private final GuardEntity guard;
	private final double speed;
	private final Random shuffleRandom = new Random();
	private VillageDescriptor village;
	private final List<BlockPos> edgePoints = new ArrayList<>();
	private final List<BlockPos> interiorPoints = new ArrayList<>();
	private boolean patrollingInterior;
	private int pointIndex;
	private int recalculateTicks;
	private int modeSwitchTicks;

	public PerimeterPatrolGoal(GuardEntity guard, double speed) {
		this.guard = guard;
		this.speed = speed;
		this.setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		return this.guard.isBehaviorExecutor(GuardBehaviorExecutor.PERIMETER)
			&& this.refreshVillageData();
	}

	@Override
	public boolean shouldContinue() {
		return this.guard.isBehaviorExecutor(GuardBehaviorExecutor.PERIMETER)
			&& (!this.edgePoints.isEmpty() || !this.interiorPoints.isEmpty());
	}

	@Override
	public void start() {
		this.pointIndex = 0;
		this.recalculateTicks = 0;
		this.modeSwitchTicks = MODE_SWITCH_BASE + this.guard.getRandom().nextInt(200);
		this.patrollingInterior = false;
		GuardDebugLogger.logPatrol(this.guard, "started patrol", "mode", "EDGE");
	}

	@Override
	public void tick() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}

		if (this.recalculateTicks-- <= 0) {
			this.refreshVillageData();
			this.recalculateTicks = RECALCULATE_INTERVAL;
		}

		if (this.modeSwitchTicks-- <= 0) {
			this.patrollingInterior = !this.patrollingInterior;
			this.pointIndex = 0;
			this.modeSwitchTicks = MODE_SWITCH_BASE + this.guard.getRandom().nextInt(200);
			List<BlockPos> active = this.activePoints();
			if (!active.isEmpty()) {
				this.shuffleRandom.setSeed(this.guard.getRandom().nextLong());
				Collections.shuffle(active, this.shuffleRandom);
			}
			GuardDebugLogger.logPatrol(this.guard, "patrol mode switched",
				"mode", this.patrollingInterior ? "INTERIOR" : "EDGE",
				"pointCount", String.valueOf(active.size()));
		}

		List<BlockPos> points = this.activePoints();
		if (points.isEmpty()) {
			this.patrollingInterior = !this.patrollingInterior;
			points = this.activePoints();
			if (points.isEmpty()) {
				return;
			}
		}

		BlockPos target = points.get(this.pointIndex % points.size());
		BlockPos groundedTarget = this.guard.resolveGroundMovementSlot(world, target, PATROL_SLOT_SPACING, true);
		double distanceSq = this.guard.squaredDistanceTo(
				groundedTarget.getX() + 0.5D,
				groundedTarget.getY(),
				groundedTarget.getZ() + 0.5D);
		if (distanceSq < 9.0D) {
			this.advancePoint(points);
			target = points.get(this.pointIndex % points.size());
			groundedTarget = this.guard.resolveGroundMovementSlot(world, target, PATROL_SLOT_SPACING, true);
		}

		this.guard.getGuardNavigation().startMovingToStatic(groundedTarget, this.speed);
	}

	private void advancePoint(List<BlockPos> points) {
		if (points.size() <= 1) {
			return;
		}
		BlockPos guardPos = this.guard.getBlockPos();
		int attempts = 0;
		do {
			int advance = 1 + this.guard.getRandom().nextInt(Math.min(4, points.size()));
			this.pointIndex = (this.pointIndex + advance) % points.size();
			attempts++;
		} while (attempts < 3 && guardPos.getSquaredDistance(points.get(this.pointIndex)) < MIN_NEXT_DISTANCE_SQ);
	}

	private List<BlockPos> activePoints() {
		return this.patrollingInterior ? this.interiorPoints : this.edgePoints;
	}

	private boolean refreshVillageData() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return false;
		}

		Optional<VillageDescriptor> descriptor = VillageManagerHandler.findVillageDescriptor(world, this.guard.getBlockPos());
		if (descriptor.isEmpty()) {
			return false;
		}
		this.village = descriptor.get();
		this.rebuildPatrolPoints();
		return !this.edgePoints.isEmpty() || !this.interiorPoints.isEmpty();
	}

	private void rebuildPatrolPoints() {
		this.edgePoints.clear();
		this.interiorPoints.clear();
		if (this.village == null) {
			return;
		}

		int minX = this.village.bounds().getMinX();
		int maxX = this.village.bounds().getMaxX();
		int minZ = this.village.bounds().getMinZ();
		int maxZ = this.village.bounds().getMaxZ();
		int y = this.village.center().getY();

		// Edge patrol points (perimeter boundary)
		for (int x = minX; x <= maxX; x += EDGE_STEP) {
			this.edgePoints.add(jittered(x, y, minZ, EDGE_JITTER));
			this.edgePoints.add(jittered(x, y, maxZ, EDGE_JITTER));
		}
		for (int z = minZ + EDGE_STEP; z < maxZ; z += EDGE_STEP) {
			this.edgePoints.add(jittered(minX, y, z, EDGE_JITTER));
			this.edgePoints.add(jittered(maxX, y, z, EDGE_JITTER));
		}

		// Interior patrol points (fill the zone area)
		for (int x = minX + INTERIOR_STEP; x < maxX; x += INTERIOR_STEP) {
			for (int z = minZ + INTERIOR_STEP; z < maxZ; z += INTERIOR_STEP) {
				this.interiorPoints.add(jittered(x, y, z, INTERIOR_JITTER));
			}
		}

		int guardSeed = this.guard.getId();
		if (!this.edgePoints.isEmpty()) {
			Collections.rotate(this.edgePoints, guardSeed % this.edgePoints.size());
		}
		if (!this.interiorPoints.isEmpty()) {
			Collections.rotate(this.interiorPoints, guardSeed % this.interiorPoints.size());
		}
	}

	private BlockPos jittered(int x, int y, int z, int jitter) {
		int offsetX = this.guard.getRandom().nextBetween(-jitter, jitter);
		int offsetZ = this.guard.getRandom().nextBetween(-jitter, jitter);
		return new BlockPos(x + offsetX, y, z + offsetZ);
	}
}

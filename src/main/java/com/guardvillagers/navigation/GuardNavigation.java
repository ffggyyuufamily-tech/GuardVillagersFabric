package com.guardvillagers.navigation;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;

public class GuardNavigation extends MobNavigation {
	private static final int STALL_THRESHOLD_TICKS = 40;
	private static final double MIN_STALL_PROGRESS_SQUARED = 0.25D;
	private static final int STATIC_REPATH_INTERVAL_TICKS = 12;
	private static final int DYNAMIC_REPATH_INTERVAL_TICKS = 4;
	private static final double STATIC_REPATH_DISTANCE_SQUARED = 0.25D;
	private static final double DYNAMIC_REPATH_DISTANCE_SQUARED = 1.0D;
	private static final int WATER_EXIT_SEARCH_RADIUS = 4;
	private static final int WATER_EXIT_VERTICAL_RANGE = 3;
	private static final int CROWD_RECOVERY_TICKS = 20;
	private static final double CROWD_RECOVERY_DISTANCE = 2.5D;
	private static final double CROWD_JAM_RADIUS = 1.35D;
	private static final int CROWD_RECOVERY_VERTICAL_RANGE = 6;

	private final GuardEntity guard;

	private long lastRecalculateTick;
	private BlockPos lastTargetPos;
	private Vec3d lastStallCheckPos = Vec3d.ZERO;
	private Vec3d lastRequestedTargetPos;
	private Vec3d lastIssuedTargetPos = Vec3d.ZERO;
	private RouteMode routeMode = RouteMode.STATIC;
	private RouteMode lastIssuedRouteMode = RouteMode.STATIC;
	private long lastMoveCommandTick = Long.MIN_VALUE;
	private double lastIssuedSpeed;
	private int stallTicks;
	private int crowdRecoveryTicks;

	public GuardNavigation(GuardEntity guard, World world) {
		super(guard, world);
		this.guard = guard;
	}

	@Override
	public Path findPathTo(BlockPos target, int distance) {
		if (target != null) {
			this.lastTargetPos = target.toImmutable();
		}

		if (this.guard.getAir() < 80 || this.routeMode != RouteMode.STATIC) {
			this.lastRecalculateTick = this.guard.getEntityWorld().getTime();
			Path path = super.findPathTo(target, distance);
			if (path != null) {
				GuardDebugLogger.logPath(this.guard, "path generated (underwater/dynamic)",
					"target", target.toShortString(), "length", path.getLength());
			} else {
				GuardDebugLogger.logNavFailure(this.guard, "path generation failed",
					"target", target != null ? target.toShortString() : "null", "reason", "UNREACHABLE");
			}
			return path;
		}

		long currentTick = this.guard.getEntityWorld().getTime();
		Path cached = SquadRouteCache.getSquadRoute(this.guard.getSquadId(), this.guard.getBlockPos(), target, currentTick);
		if (cached != null) {
			this.lastRecalculateTick = currentTick;
			GuardDebugLogger.logRouteCache(this.guard, "cache hit",
				"target", target.toShortString(), "length", cached.getLength());
			return cached;
		}

		Path newPath = super.findPathTo(target, distance);
		if (newPath != null) {
			SquadRouteCache.cacheSquadRoute(this.guard.getSquadId(), this.guard.getBlockPos(), target, newPath, currentTick);
			GuardDebugLogger.logPath(this.guard, "path generated and cached",
				"target", target.toShortString(), "length", newPath.getLength());
		} else {
			GuardDebugLogger.logNavFailure(this.guard, "path generation failed",
				"target", target.toShortString(), "reason", "UNREACHABLE");
		}
		this.lastRecalculateTick = currentTick;
		return newPath;
	}

	@Override
	public Path findPathTo(Entity entity, int distance) {
		if (entity == null) {
			return null;
		}
		this.lastTargetPos = entity.getBlockPos().toImmutable();
		this.lastRequestedTargetPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
		this.routeMode = RouteMode.DYNAMIC;
		this.lastRecalculateTick = this.guard.getEntityWorld().getTime();
		Path path = super.findPathTo(entity, distance);
		if (path != null) {
			GuardDebugLogger.logPath(this.guard, "dynamic path generated",
				"target", entity.getName().getString(), "length", path.getLength());
		} else {
			GuardDebugLogger.logNavFailure(this.guard, "dynamic path generation failed",
				"target", entity.getName().getString(), "reason", "UNREACHABLE");
		}
		return path;
	}

	@Override
	public boolean startMovingTo(Entity entity, double speed) {
		if (entity == null) {
			return false;
		}
		this.routeMode = RouteMode.DYNAMIC;
		this.lastTargetPos = entity.getBlockPos().toImmutable();
		this.lastRequestedTargetPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
		if (!this.shouldIssueMove(this.lastRequestedTargetPos, speed, RouteMode.DYNAMIC)) {
			return !this.isIdle();
		}
		return super.startMovingTo(entity, speed);
	}

	@Override
	public boolean startMovingTo(double x, double y, double z, double speed) {
		return this.startMovingToStatic(x, y, z, speed);
	}

	public boolean startMovingToStatic(BlockPos target, double speed) {
		if (target == null) {
			return false;
		}
		return this.startMovingToStatic(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
	}

	public boolean startMovingToStatic(double x, double y, double z, double speed) {
		Vec3d targetPos = new Vec3d(x, y, z);
		this.routeMode = RouteMode.STATIC;
		this.lastRequestedTargetPos = targetPos;
		this.lastTargetPos = BlockPos.ofFloored(x, y, z);
		if (!this.shouldIssueMove(targetPos, speed, RouteMode.STATIC)) {
			return !this.isIdle();
		}
		return super.startMovingTo(x, y, z, speed);
	}

	public boolean startMovingToDynamic(Vec3d targetPos, double speed) {
		if (targetPos == null) {
			return false;
		}
		this.routeMode = RouteMode.DYNAMIC;
		this.lastRequestedTargetPos = targetPos;
		this.lastTargetPos = BlockPos.ofFloored(targetPos.x, targetPos.y, targetPos.z);
		if (!this.shouldIssueMove(targetPos, speed, RouteMode.DYNAMIC)) {
			return !this.isIdle();
		}
		return super.startMovingTo(targetPos.x, targetPos.y, targetPos.z, speed);
	}

	public boolean isCrowdBlocked() {
		return this.crowdRecoveryTicks > 0 || this.hasNearbyAlliedCrowding();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.crowdRecoveryTicks > 0) {
			this.crowdRecoveryTicks--;
		}

		if (this.isIdle()) {
			this.stallTicks = 0;
			this.lastStallCheckPos = this.guard.getEntityPos();
			return;
		}

		Vec3d currentPos = this.guard.getEntityPos();
		if (this.lastStallCheckPos.squaredDistanceTo(currentPos) < MIN_STALL_PROGRESS_SQUARED) {
			this.stallTicks++;
			if (this.stallTicks >= STALL_THRESHOLD_TICKS) {
				this.recoverFromStall();
				this.stallTicks = 0;
				this.lastStallCheckPos = currentPos;
			}
			return;
		}

		this.lastStallCheckPos = currentPos;
		this.stallTicks = 0;
	}

	private void recoverFromStall() {
		BlockPos cachedTarget = this.lastTargetPos != null ? this.lastTargetPos.toImmutable() : this.getTargetPos();
		BlockPos recoveryTarget = this.resolveWaterRecoveryTarget();
		Vec3d crowdRecoveryTarget = this.resolveCrowdRecoveryTarget();
		this.stop();

		if (this.routeMode == RouteMode.STATIC && cachedTarget != null) {
			SquadRouteCache.invalidateSquadRoute(this.guard.getSquadId(), cachedTarget);
		}

		if (crowdRecoveryTarget != null) {
			this.crowdRecoveryTicks = CROWD_RECOVERY_TICKS;
			this.startMovingToDynamic(crowdRecoveryTarget, this.speed);
			return;
		}

		if (recoveryTarget != null) {
			this.startMovingToStatic(recoveryTarget, this.speed);
			return;
		}

		if (this.routeMode == RouteMode.DYNAMIC && this.lastRequestedTargetPos != null) {
			this.startMovingToDynamic(this.lastRequestedTargetPos, this.speed);
			return;
		}

		if (cachedTarget != null) {
			this.startMovingToStatic(cachedTarget, this.speed);
		}
	}

	private boolean shouldIssueMove(Vec3d targetPos, double speed, RouteMode mode) {
		long currentTick = this.guard.getEntityWorld().getTime();
		int minInterval = mode == RouteMode.DYNAMIC ? DYNAMIC_REPATH_INTERVAL_TICKS : STATIC_REPATH_INTERVAL_TICKS;
		double minDistanceSq = mode == RouteMode.DYNAMIC ? DYNAMIC_REPATH_DISTANCE_SQUARED : STATIC_REPATH_DISTANCE_SQUARED;

		boolean shouldIssue = this.isIdle()
				|| this.lastMoveCommandTick == Long.MIN_VALUE
				|| this.lastIssuedRouteMode != mode
				|| Math.abs(speed - this.lastIssuedSpeed) > 0.05D
				|| this.lastIssuedTargetPos.squaredDistanceTo(targetPos) > minDistanceSq
				|| currentTick - this.lastMoveCommandTick >= minInterval;

		if (shouldIssue) {
			this.lastMoveCommandTick = currentTick;
			this.lastIssuedTargetPos = targetPos;
			this.lastIssuedRouteMode = mode;
			this.lastIssuedSpeed = speed;
		}
		return shouldIssue;
	}

	private boolean hasNearbyAlliedCrowding() {
		Box crowdBox = this.guard.getBoundingBox().expand(CROWD_JAM_RADIUS);
		List<GuardEntity> nearby = this.world.getEntitiesByClass(
				GuardEntity.class,
				crowdBox,
				entity -> entity != this.guard && entity.isAlive() && this.guard.isSameMovementGroup(entity));
		return !nearby.isEmpty();
	}

	private Vec3d resolveCrowdRecoveryTarget() {
		if (!(this.world instanceof ServerWorld serverWorld) || !this.hasNearbyAlliedCrowding()) {
			return null;
		}

		Vec3d origin = this.guard.getEntityPos();
		double baseAngle = Math.toRadians(Math.floorMod(this.guard.getUuid().hashCode(), 360));
		for (int index = 0; index < 8; index++) {
			double angle = baseAngle + (Math.PI / 4.0D) * index;
			Vec3d probe = origin.add(Math.cos(angle) * CROWD_RECOVERY_DISTANCE, 0.0D,
					Math.sin(angle) * CROWD_RECOVERY_DISTANCE);
			BlockPos candidateOrigin = BlockPos.ofFloored(probe.x, this.guard.getBlockY(), probe.z);
			BlockPos grounded = GuardVillagersMod.findNearbyGuardSpawnPos(
					serverWorld,
					candidateOrigin,
					CROWD_RECOVERY_VERTICAL_RANGE);
			if (grounded == null) {
				continue;
			}
			return Vec3d.ofBottomCenter(grounded);
		}
		return null;
	}

	private BlockPos resolveWaterRecoveryTarget() {
		if (!(this.world instanceof ServerWorld serverWorld)) {
			return null;
		}

		if (!this.isInOrAgainstFlowingWater()) {
			return null;
		}

		BlockPos lastLand = this.guard.getLastLandPos();
		if (lastLand != null && GuardVillagersMod.canGuardSpawnAt(serverWorld, lastLand)) {
			return lastLand.toImmutable();
		}
		return this.findNearbyDryExit(serverWorld);
	}

	private boolean isInOrAgainstFlowingWater() {
		BlockPos origin = this.guard.getBlockPos();
		if (this.isFlowingWater(origin) || this.isFlowingWater(origin.up())) {
			return true;
		}
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (this.isFlowingWater(origin.offset(direction)) || this.isFlowingWater(origin.up().offset(direction))) {
				return true;
			}
		}
		return this.guard.isTouchingWater();
	}

	private boolean isFlowingWater(BlockPos pos) {
		var fluid = this.world.getFluidState(pos);
		return fluid.isIn(FluidTags.WATER) && !fluid.isStill();
	}

	private BlockPos findNearbyDryExit(ServerWorld serverWorld) {
		BlockPos origin = this.guard.getBlockPos();
		BlockPos best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for (int radius = 1; radius <= WATER_EXIT_SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					BlockPos topCandidate = this.world.getTopPosition(
							Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							origin.add(dx, 0, dz));
					best = this.pickBestDryExit(serverWorld, topCandidate, best, origin, bestDistanceSq);
					if (best != null) {
						bestDistanceSq = best.getSquaredDistance(origin);
					}

					for (int dy = -WATER_EXIT_VERTICAL_RANGE; dy <= WATER_EXIT_VERTICAL_RANGE; dy++) {
						BlockPos candidate = origin.add(dx, dy, dz);
						best = this.pickBestDryExit(serverWorld, candidate, best, origin, bestDistanceSq);
						if (best != null) {
							bestDistanceSq = best.getSquaredDistance(origin);
						}
					}
				}
			}
		}

		return best;
	}

	private BlockPos pickBestDryExit(ServerWorld serverWorld, BlockPos candidate, BlockPos currentBest, BlockPos origin, double bestDistanceSq) {
		if (!GuardVillagersMod.canGuardSpawnAt(serverWorld, candidate)) {
			return currentBest;
		}

		double distanceSq = candidate.getSquaredDistance(origin);
		if (distanceSq >= bestDistanceSq) {
			return currentBest;
		}
		return candidate.toImmutable();
	}

	private enum RouteMode {
		STATIC,
		DYNAMIC
	}
}

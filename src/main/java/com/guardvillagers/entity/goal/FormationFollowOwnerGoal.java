package com.guardvillagers.entity.goal;

import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public final class FormationFollowOwnerGoal extends Goal {
	private static final int CATCH_UP_REPATH_INTERVAL_TICKS = 2;
	private static final int STALLED_PROGRESS_WINDOW_TICKS = 20;
	private static final int EXIT_STABILITY_TICKS = 10;
	private static final double IMMEDIATE_CATCH_UP_DISTANCE_BLOCKS = 14.0D;
	private static final double STALLED_CATCH_UP_DISTANCE_BLOCKS = 8.0D;
	private static final double EXIT_DISTANCE_BLOCKS = 6.0D;
	private static final double MIN_PROGRESS_BLOCKS = 0.5D;
	private static final double MAX_EXIT_REGRESSION_BLOCKS = 0.25D;

	private final GuardEntity guard;
	private final double speed;
	private ServerPlayerEntity owner;
	private int updateCountdownTicks;
	private int progressWindowTicks;
	private int settledTicks;
	private double sampledDistanceBlocks = Double.MAX_VALUE;
	private double lastDistanceBlocks = Double.MAX_VALUE;
	private float oldWaterPathfindingPenalty;
	private Vec3d cachedFollowSlot;
	private double cachedDistanceBlocks;

	public FormationFollowOwnerGoal(GuardEntity guard, double speed) {
		this.guard = guard;
		this.speed = speed;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			this.resetProgressSampling();
			return false;
		}

		ServerPlayerEntity resolvedOwner = this.guard.resolveOwner(world);
		if (resolvedOwner == null || resolvedOwner.isSpectator()) {
			this.resetProgressSampling();
			return false;
		}
		if (!this.guard.canFollowOwnerFormation()) {
			this.resetProgressSampling();
			return false;
		}

		Vec3d followSlot = this.guard.resolveFollowSlot(world, resolvedOwner);
		double distanceSq = this.guard.squaredDistanceTo(followSlot);
		double distanceBlocks = Math.sqrt(distanceSq);
		if (!this.shouldEnterCatchUp(distanceBlocks)) {
			return false;
		}

		this.owner = resolvedOwner;
		this.cachedFollowSlot = followSlot;
		this.cachedDistanceBlocks = distanceBlocks;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (this.owner == null || !this.owner.isAlive()) {
			return false;
		}
		if (!this.guard.canFollowOwnerFormation()) {
			return false;
		}

		if (!(this.owner.getEntityWorld() instanceof ServerWorld world)) {
			return false;
		}
		Vec3d followSlot = this.guard.resolveFollowSlot(world, this.owner);
		double distanceBlocks = Math.sqrt(this.guard.squaredDistanceTo(followSlot));
		if (distanceBlocks > EXIT_DISTANCE_BLOCKS) {
			this.settledTicks = 0;
			this.lastDistanceBlocks = distanceBlocks;
			return true;
		}

		if (distanceBlocks <= this.lastDistanceBlocks + MAX_EXIT_REGRESSION_BLOCKS) {
			this.settledTicks++;
		} else {
			this.settledTicks = 0;
		}
		this.lastDistanceBlocks = distanceBlocks;
		return this.settledTicks < EXIT_STABILITY_TICKS;
	}

	@Override
	public void start() {
		this.updateCountdownTicks = 0;
		this.settledTicks = 0;
		this.lastDistanceBlocks = this.cachedDistanceBlocks != 0.0D ? this.cachedDistanceBlocks : Double.MAX_VALUE;
		this.oldWaterPathfindingPenalty = this.guard.getPathfindingPenalty(PathNodeType.WATER);
		this.guard.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
		this.guard.setCatchUpSpeedActive(true);
	}

	@Override
	public void stop() {
		this.owner = null;
		this.updateCountdownTicks = 0;
		this.settledTicks = 0;
		this.lastDistanceBlocks = Double.MAX_VALUE;
		this.cachedFollowSlot = null;
		this.cachedDistanceBlocks = 0.0D;
		this.guard.getNavigation().stop();
		this.guard.setPathfindingPenalty(PathNodeType.WATER, this.oldWaterPathfindingPenalty);
		this.guard.setCatchUpSpeedActive(false);
	}

	@Override
	public void tick() {
		if (this.owner == null) {
			return;
		}

		this.guard.getLookControl().lookAt(this.owner, 10.0F, this.guard.getMaxLookPitchChange());
		if (--this.updateCountdownTicks > 0) {
			return;
		}
		this.updateCountdownTicks = this.getTickCount(CATCH_UP_REPATH_INTERVAL_TICKS);

		if (this.guard.isLeashed() || this.guard.hasVehicle()) {
			return;
		}

		if (!(this.owner.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		Vec3d followSlot = this.guard.resolveFollowSlot(world, this.owner);
		this.guard.getGuardNavigation().startMovingToDynamic(followSlot, this.speed);
	}

	private boolean shouldEnterCatchUp(double distanceBlocks) {
		if (distanceBlocks <= EXIT_DISTANCE_BLOCKS) {
			this.resetProgressSampling(distanceBlocks);
			return false;
		}
		if (distanceBlocks > IMMEDIATE_CATCH_UP_DISTANCE_BLOCKS) {
			this.resetProgressSampling(distanceBlocks);
			return true;
		}
		if (distanceBlocks <= STALLED_CATCH_UP_DISTANCE_BLOCKS) {
			this.resetProgressSampling(distanceBlocks);
			return false;
		}
		if (this.progressWindowTicks == 0) {
			this.resetProgressSampling(distanceBlocks);
			return false;
		}

		this.progressWindowTicks++;
		if (this.progressWindowTicks < STALLED_PROGRESS_WINDOW_TICKS) {
			return false;
		}

		double progressBlocks = this.sampledDistanceBlocks - distanceBlocks;
		this.resetProgressSampling(distanceBlocks);
		return progressBlocks < MIN_PROGRESS_BLOCKS;
	}

	private void resetProgressSampling() {
		this.progressWindowTicks = 0;
		this.sampledDistanceBlocks = Double.MAX_VALUE;
	}

	private void resetProgressSampling(double distanceBlocks) {
		this.progressWindowTicks = 1;
		this.sampledDistanceBlocks = distanceBlocks;
	}
}

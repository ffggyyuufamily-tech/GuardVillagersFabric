package com.guardvillagers.entity.goal;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardBehaviorExecutor;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.EnumSet;
import java.util.Optional;

public final class CrowdControlGoal extends Goal {
	private final GuardEntity guard;
	private final double speed;
	private BlockPos anchor;
	private int recalculateTicks;

	public CrowdControlGoal(GuardEntity guard, double speed) {
		this.guard = guard;
		this.speed = speed;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		return this.guard.isBehaviorExecutor(GuardBehaviorExecutor.CROWD_CONTROL)
			&& this.resolveAnchor();
	}

	@Override
	public boolean shouldContinue() {
		return this.guard.isBehaviorExecutor(GuardBehaviorExecutor.CROWD_CONTROL)
			&& this.anchor != null;
	}

	@Override
	public void stop() {
		this.anchor = null;
		this.guard.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (this.anchor == null || !(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		if (this.recalculateTicks-- <= 0) {
			this.resolveAnchor();
			this.recalculateTicks = 100;
		}

		BlockPos slot = this.guard.resolveGroundMovementSlot(world, this.anchor, 1.75D, false);
		double distanceSq = this.guard.squaredDistanceTo(slot.getX() + 0.5D, slot.getY(), slot.getZ() + 0.5D);
		if (distanceSq > 2.25D) {
			this.guard.getGuardNavigation().startMovingToStatic(slot, this.speed);
		} else {
			this.guard.getNavigation().stop();
		}
	}

	private boolean resolveAnchor() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return false;
		}

		Optional<BlockPos> meetingPoint = world.getPointOfInterestStorage().getNearestPosition(
			entry -> entry.matchesKey(PointOfInterestTypes.MEETING),
			this.guard.getBlockPos(),
			64,
			PointOfInterestStorage.OccupationStatus.ANY
		);

		this.anchor = meetingPoint.orElse(this.guard.getHome().orElse(this.guard.getBlockPos()));
		return this.anchor != null;
	}
}

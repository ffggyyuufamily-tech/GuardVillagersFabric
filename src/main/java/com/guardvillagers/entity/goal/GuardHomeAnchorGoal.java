package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardBehaviorExecutor;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public final class GuardHomeAnchorGoal extends Goal {
	private final GuardEntity guard;
	private final double speed;

	public GuardHomeAnchorGoal(GuardEntity guard, double speed) {
		this.guard = guard;
		this.speed = speed;
		this.setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		return this.guard.isBehaviorExecutor(GuardBehaviorExecutor.HOME_ANCHOR)
				&& this.guard.getHome().isPresent();
	}

	@Override
	public boolean shouldContinue() {
		return this.guard.isBehaviorExecutor(GuardBehaviorExecutor.HOME_ANCHOR)
				&& this.guard.getHome().isPresent();
	}

	@Override
	public void stop() {
		this.guard.getNavigation().stop();
	}

	@Override
	public void tick() {
		BlockPos home = this.guard.getHome().orElse(null);
		if (home == null || !(this.guard.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
			return;
		}
		BlockPos slot = this.guard.resolveGroundMovementSlot(world, home, 1.75D, false);
		double distSq = this.guard.squaredDistanceTo(slot.getX() + 0.5D, slot.getY(), slot.getZ() + 0.5D);
		if (distSq <= 2.25D) {
			this.guard.getNavigation().stop();
			GuardDebugLogger.logHomeTether(this.guard, "arrived at home",
				"home", home.toShortString(),
				"distance", String.format("%.2f", Math.sqrt(distSq)));
			return;
		}
		this.guard.getGuardNavigation().startMovingToStatic(slot, this.speed);
	}
}

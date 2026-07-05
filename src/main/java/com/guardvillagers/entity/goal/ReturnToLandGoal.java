package com.guardvillagers.entity.goal;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiIntent;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * After a hunt ends (target lost/killed/aborted) while the guard is in water,
 * path back to the last known land position, then resume normal behavior.
 */
public final class ReturnToLandGoal extends Goal {
	private final GuardEntity guard;
	private final double speed;
	private float oldWaterPenalty;
	private int repathTicks;

	public ReturnToLandGoal(GuardEntity guard, double speed) {
		this.guard = guard;
		this.speed = speed;
		this.setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		return this.guard.isAiIntent(GuardAiIntent.RETURN_TO_LAND)
				&& this.guard.getLastLandPos() != null;
	}

	@Override
	public boolean shouldContinue() {
		// Don't keep trying to return to land if guard has active combat
		if (this.guard.hasActiveCombatTarget()) return false;
		return this.guard.isAiIntent(GuardAiIntent.RETURN_TO_LAND)
				&& this.guard.getLastLandPos() != null;
	}

	@Override
	public void start() {
		this.oldWaterPenalty = this.guard.getPathfindingPenalty(PathNodeType.WATER);
		this.guard.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
		this.repathTicks = 0;
	}

	@Override
	public void stop() {
		this.guard.getNavigation().stop();
		this.guard.setPathfindingPenalty(PathNodeType.WATER, this.oldWaterPenalty);
	}

	@Override
	public void tick() {
		if (--this.repathTicks > 0) {
			return;
		}
		this.repathTicks = 10;

		BlockPos landPos = this.guard.getLastLandPos();
		if (landPos == null) {
			return;
		}
		this.guard.getNavigation().startMovingTo(
			landPos.getX() + 0.5D, landPos.getY(), landPos.getZ() + 0.5D, this.speed
		);
	}
}

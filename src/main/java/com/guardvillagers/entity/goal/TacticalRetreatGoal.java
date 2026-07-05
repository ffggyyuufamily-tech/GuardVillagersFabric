package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiIntent;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public final class TacticalRetreatGoal extends Goal {
	private final GuardEntity guard;
	private BlockPos safePoint;
	private int recalculateTicks;

	public TacticalRetreatGoal(GuardEntity guard) {
		this.guard = guard;
		this.setControls(EnumSet.of(Control.MOVE, Control.TARGET));
	}

	@Override
	public boolean canStart() {
		return this.guard.isAiIntent(GuardAiIntent.RETREAT);
	}

	@Override
	public boolean shouldContinue() {
		return this.guard.isAiIntent(GuardAiIntent.RETREAT);
	}

	@Override
	public void start() {
		this.safePoint = null;
		this.recalculateTicks = 0;
	}

	@Override
	public void stop() {
		this.safePoint = null;
		this.guard.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		GuardVillagersConfig.Combat config = GuardVillagersConfig.get().combat;

		if (this.recalculateTicks-- <= 0 || this.safePoint == null) {
			this.safePoint = this.guard.findSafeRetreatPoint(world);
			this.recalculateTicks = config.retreatRecalculateTicks;
		}

		if (this.safePoint != null) {
			this.guard.getGuardNavigation().startMovingToStatic(this.safePoint, config.retreatSpeed);
		}
	}
}

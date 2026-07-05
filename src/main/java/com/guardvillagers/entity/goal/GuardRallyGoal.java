package com.guardvillagers.entity.goal;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiIntent;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public final class GuardRallyGoal extends Goal {
	private static final int REPATH_INTERVAL_TICKS = 10;

	private final GuardEntity guard;
	private final double speed;
	private int repathTicks;

	public GuardRallyGoal(GuardEntity guard, double speed) {
		this.guard = guard;
		this.speed = speed;
		this.setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		return this.guard.isAiIntent(GuardAiIntent.RALLY)
				&& this.guard.getRallyPoint().isPresent();
	}

	@Override
	public boolean shouldContinue() {
		return this.guard.isAiIntent(GuardAiIntent.RALLY)
				&& this.guard.getRallyPoint().isPresent();
	}

	@Override
	public void start() {
		this.repathTicks = 0;
	}

	@Override
	public void stop() {
		this.guard.getNavigation().stop();
		this.repathTicks = 0;
	}

	@Override
	public void tick() {
		if (this.repathTicks-- > 0) {
			return;
		}
		this.repathTicks = REPATH_INTERVAL_TICKS;
		BlockPos rallyPoint = this.guard.getRallyPoint().orElse(null);
		if (rallyPoint == null || !(this.guard.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
			return;
		}
		BlockPos slot = this.guard.resolveGroundMovementSlot(world, rallyPoint, 1.75D, false);
		this.guard.getGuardNavigation().startMovingToStatic(slot, this.speed);
	}
}

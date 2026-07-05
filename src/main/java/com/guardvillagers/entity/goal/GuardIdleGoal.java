package com.guardvillagers.entity.goal;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiIntent;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class GuardIdleGoal extends WanderAroundFarGoal {
	private static final double STAY_RADIUS_SQ = (double) GuardEntity.STAY_RADIUS * GuardEntity.STAY_RADIUS;

	private final GuardEntity guard;

	public GuardIdleGoal(GuardEntity guard, double speed) {
		super(guard, speed);
		this.guard = guard;
	}

	@Override
	public boolean canStart() {
		if (!this.guard.isAiIntent(GuardAiIntent.IDLE)) {
			return false;
		}
		if (this.guard.isStaying()) {
			BlockPos origin = this.guard.getStayOrigin();
			if (origin == null) {
				return false;
			}
			if (origin.getSquaredDistance(this.guard.getBlockPos()) > STAY_RADIUS_SQ) {
				this.targetX = origin.getX() + 0.5;
				this.targetY = origin.getY();
				this.targetZ = origin.getZ() + 0.5;
				return true;
			}
			return super.canStart();
		}
		return super.canStart();
	}

	@Override
	public boolean shouldContinue() {
		if (!this.guard.isAiIntent(GuardAiIntent.IDLE)) {
			return false;
		}
		if (this.guard.isStaying() && this.guard.getStayOrigin() == null) {
			return false;
		}
		return super.shouldContinue();
	}

	@Nullable
	@Override
	protected Vec3d getWanderTarget() {
		if (this.guard.isStaying()) {
			BlockPos origin = this.guard.getStayOrigin();
			if (origin == null) {
				return null;
			}
			Vec3d candidate = FuzzyTargeting.find(this.guard, GuardEntity.STAY_RADIUS, 2);
			if (candidate != null
					&& origin.getSquaredDistance(BlockPos.ofFloored(candidate.x, candidate.y, candidate.z)) <= STAY_RADIUS_SQ) {
				return candidate;
			}
			return null;
		}
		return super.getWanderTarget();
	}
}

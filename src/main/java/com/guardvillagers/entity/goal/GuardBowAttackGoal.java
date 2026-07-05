package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiIntent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

public final class GuardBowAttackGoal extends Goal {
	private final GuardEntity guard;
	private int targetVisibleTicks;
	private int minPullTicks;
	private int minVisibleTicks;

	public GuardBowAttackGoal(GuardEntity guard) {
		this.guard = guard;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		this.refreshConfig();
	}

	public void refreshConfig() {
		GuardVillagersConfig.Combat config = GuardVillagersConfig.get().combat;
		this.minPullTicks = Math.round(config.bowPullTicks);
		this.minVisibleTicks = Math.round(config.bowVisibleTicks);
	}

	@Override
	public boolean canStart() {
		return this.hasValidTarget();
	}

	@Override
	public boolean shouldContinue() {
		return this.hasValidTarget() || !this.guard.getNavigation().isIdle();
	}

	@Override
	public void start() {
		this.guard.setAttacking(true);
	}

	@Override
	public void stop() {
		this.guard.setAttacking(false);
		this.targetVisibleTicks = 0;
		this.guard.clearActiveItem();
		this.guard.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity target = this.guard.getTarget();
		if (target == null || !(this.guard.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
			return;
		}
		GuardVillagersConfig.Combat config = GuardVillagersConfig.get().combat;
		float range = config.rangedPreferredDistance;
		double squaredRange = range * range;

		double distanceSq = this.guard.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
		boolean canSeeTarget = this.guard.canSee(target);
		boolean sawTargetLastTick = this.targetVisibleTicks > 0;
		if (canSeeTarget != sawTargetLastTick) {
			this.targetVisibleTicks = 0;
		}
		if (canSeeTarget) {
			this.targetVisibleTicks++;
		} else {
			this.targetVisibleTicks--;
		}

		// If out of arrows, retreat to safe distance rather than standing still
		if (!this.guard.canShootBow()) {
			Vec3d away = this.guard.getEntityPos().subtract(target.getEntityPos()).normalize();
			Vec3d destination = this.guard.getEntityPos().add(away.multiply(8.0D));
			this.guard.getGuardNavigation().startMovingTo(destination.x, destination.y, destination.z, 1.2D);
			this.guard.getLookControl().lookAt(target, 30.0F, 30.0F);
			return;
		}

		if (distanceSq <= squaredRange && this.targetVisibleTicks >= this.minVisibleTicks) {
			this.guard.getNavigation().stop();
		} else {
			this.guard.getGuardNavigation().startMovingToDynamic(this.guard.resolveCombatApproachSlot(world, target),
					config.bowSpeed);
		}

		this.guard.getLookControl().lookAt(target, 30.0F, 30.0F);
		if (this.guard.isUsingItem()) {
			if (!canSeeTarget && this.targetVisibleTicks < -60) {
				this.guard.clearActiveItem();
			} else if (canSeeTarget) {
				int useTicks = this.guard.getItemUseTime();
				if (useTicks >= this.minPullTicks) {
					if (config.friendlyFireAvoidance > 0.0F
							&& (config.friendlyFireAvoidance >= 1.0F || this.guard.getRandom().nextFloat() < config.friendlyFireAvoidance)) {
						if (this.hasAllyInLineOfFire(target, world)) {
							this.guard.clearActiveItem();
							return;
						}
					}
					this.guard.clearActiveItem();
					this.guard.shootAt(target, BowItem.getPullProgress(useTicks));
				}
			}
		} else if (this.targetVisibleTicks >= -60 && this.guard.canShootBow()) {
			this.guard.setCurrentHand(Hand.MAIN_HAND);
		}
	}

	private boolean hasAllyInLineOfFire(LivingEntity target, net.minecraft.server.world.ServerWorld world) {
		Vec3d from = this.guard.getEyePos();
		Vec3d to = target.getEyePos();
		Vec3d direction = to.subtract(from).normalize();
		double distance = this.guard.distanceTo(target);

		List<LivingEntity> entitiesInPath = world.getEntitiesByClass(
			LivingEntity.class,
			this.guard.getBoundingBox().stretch(to.subtract(from)).expand(0.5D),
			entity -> entity != this.guard
				&& entity != target
				&& entity.isAlive()
				&& this.guard.isAlly(entity)
		);

		for (LivingEntity entity : entitiesInPath) {
			Vec3d toEntity = entity.getEyePos().subtract(from);
			double dot = toEntity.dotProduct(direction);
			if (dot <= 0 || dot > distance) continue;
			Vec3d closest = from.add(direction.multiply(dot));
			if (closest.distanceTo(entity.getEyePos()) < 0.8D) {
				return true;
			}
		}
		return false;
	}

	private boolean hasValidTarget() {
		LivingEntity target = this.guard.getTarget();
		return this.guard.isAiIntent(GuardAiIntent.ENGAGE_TARGET)
				&& target != null
				&& target.isAlive()
				&& this.guard.getMainHandStack().isOf(Items.BOW);
	}
}

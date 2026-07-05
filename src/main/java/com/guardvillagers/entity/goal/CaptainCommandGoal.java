package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardMoraleSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SpellcastingIllagerEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public final class CaptainCommandGoal extends Goal {
	private final GuardEntity captain;
	private int commandCooldown;

	public CaptainCommandGoal(GuardEntity captain) {
		this.captain = captain;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		GuardVillagersConfig.Captain config = GuardVillagersConfig.get().captain;
		return config.enabled
				&& this.captain.isAlive()
				&& this.captain.isSquadLeader()
				&& this.captain.getEntityWorld() instanceof ServerWorld;
	}

	@Override
	public boolean shouldContinue() {
		return this.canStart();
	}

	@Override
	public void tick() {
		if (this.commandCooldown-- > 0 || !(this.captain.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		GuardVillagersConfig.Captain config = GuardVillagersConfig.get().captain;
		this.commandCooldown = GuardMoraleSystem.commandIntervalTicks(this.captain);

		List<GuardEntity> squad = this.collectSquad(world, config);
		if (squad.isEmpty()) {
			return;
		}

		if (this.shouldOrderRetreat(world, squad, config)) {
			BlockPos safePoint = this.captain.findSafeRetreatPoint(world);
			for (GuardEntity guard : squad) {
				guard.rallyTo(safePoint, config.retreatRallyTicks);
			}
			return;
		}

		LivingEntity priorityTarget = this.findPriorityTarget(world, config);
		if (priorityTarget == null) {
			return;
		}

		long now = world.getTime();
		double rallyDistanceSq = config.rallyDistance * config.rallyDistance;
		for (GuardEntity guard : squad) {
			if (!guard.canTargetWithinZone(priorityTarget.getBlockPos())) {
				continue;
			}
			guard.setPriorityTarget(priorityTarget);
			if (guard.squaredDistanceTo(priorityTarget) > rallyDistanceSq) {
				guard.rallyTo(priorityTarget.getBlockPos(), config.rallyTicks);
			}
			guard.receiveAlliedAlert(priorityTarget, now);
		}
	}

	private List<GuardEntity> collectSquad(ServerWorld world, GuardVillagersConfig.Captain config) {
		return world.getEntitiesByClass(
				GuardEntity.class,
				this.captain.getBoundingBox().expand(config.commandRadius),
				guard -> guard.isAlive() && this.captain.isSameSquad(guard));
	}

	private boolean shouldOrderRetreat(ServerWorld world, List<GuardEntity> squad, GuardVillagersConfig.Captain config) {
		int lowHealth = 0;
		for (GuardEntity guard : squad) {
			if (guard.getHealth() <= guard.getMaxHealth() * config.retreatHealthThreshold) {
				lowHealth++;
			}
		}
		int minimumLow = Math.max(1, Math.round(squad.size() * config.retreatAllyRatio));
		if (lowHealth < minimumLow) {
			return false;
		}

		int nearbyEnemies = world.getEntitiesByClass(
				HostileEntity.class,
				this.captain.getBoundingBox().expand(config.retreatEnemyRadius),
				enemy -> enemy.isAlive() && !this.captain.isAlly(enemy)).size();
		return nearbyEnemies > squad.size();
	}

	private LivingEntity findPriorityTarget(ServerWorld world, GuardVillagersConfig.Captain config) {
		Box box = this.captain.getBoundingBox().expand(config.targetScanRadius);
		List<LivingEntity> targets = world.getEntitiesByClass(
				LivingEntity.class,
				box,
				entity -> entity.isAlive()
						&& !this.captain.isAlly(entity)
						&& this.captain.canTargetWithinZone(entity.getBlockPos())
						&& (entity instanceof HostileEntity || entity instanceof RaiderEntity));
		if (targets.isEmpty()) {
			return null;
		}
		targets.sort(Comparator
				.comparingInt(this::priorityScore)
				.reversed()
				.thenComparingDouble(this.captain::squaredDistanceTo)
				.thenComparing(LivingEntity::getUuid));
		return targets.getFirst();
	}

	private int priorityScore(LivingEntity target) {
		GuardVillagersConfig.Captain.FocusPriorityWeights weights = GuardVillagersConfig.get().captain.focusPriorityWeights;
		int score = this.captain.scoreTarget(target);
		if (target instanceof EvokerEntity || target instanceof SpellcastingIllagerEntity) {
			score += weights.evoker;
		}
		if (target instanceof WardenEntity || target instanceof WitherEntity) {
			score += weights.boss;
		}
		if (target instanceof RaiderEntity) {
			score += weights.raider;
		}
		if (target instanceof RangedAttackMob
				|| target.getMainHandStack().isOf(Items.BOW)
				|| target.getMainHandStack().isOf(Items.CROSSBOW)
				|| target.getMainHandStack().isOf(Items.TRIDENT)) {
			score += weights.ranged;
		}
		return score;
	}
}

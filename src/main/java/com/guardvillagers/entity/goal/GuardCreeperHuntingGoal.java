package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.GuardRole;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.UUID;

/**
 * Goal for guards to hunt creepers for gunpowder drops.
 * Bowmen stay at range (10-14 blocks), swordsmen kite and melee.
 * CRITICAL: Flee immediately if creeper ignites to avoid explosions.
 */
public final class GuardCreeperHuntingGoal extends Goal {
	private final GuardEntity guard;
	private int scanCooldown;
	private int tickTimeout;
	private int consecutiveMisses;
	private UUID targetCreeperUuid;

	public GuardCreeperHuntingGoal(GuardEntity guard) {
		this.guard = guard;
		this.scanCooldown = 0;
		this.tickTimeout = 0;
		this.consecutiveMisses = 0;
	}

	@Override
	public boolean canStart() {
		GuardVillagersConfig config = GuardVillagersConfig.get();
		if (!config.resourceGathering.enabled) {
			return false;
		}

		// Guard must be alive, not staying, not already in combat
		if (!this.guard.isAlive() || this.guard.isStaying() || this.guard.hasActiveCombatTarget()) {
			return false;
		}

		// Check if guard needs gunpowder
		int gunpowderHave = this.guard.getInventory().getCount(Items.GUNPOWDER);
		int gunpowderNeed = config.resourceGathering.gunpowderTargetAmount;
		if (gunpowderHave >= gunpowderNeed) {
			this.scanCooldown = 60;
			return false;
		}

		// Only scan periodically
		if (this.scanCooldown > 0) {
			this.scanCooldown--;
			return false;
		}

		// Scan for nearest creeper within follow range
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return false;
		}

		CreeperEntity creeper = this.findNearestCreeper(world);
		if (creeper != null) {
			this.targetCreeperUuid = creeper.getUuid();
			this.consecutiveMisses = 0;
			this.scanCooldown = 0;
			this.tickTimeout = 0;
			return true;
		}

		// Exponential backoff on repeated misses
		this.consecutiveMisses++;
		int backoffTicks = Math.min(1200, 60 * (1 << Math.min(this.consecutiveMisses, 5)));
		this.scanCooldown = backoffTicks;
		return false;
	}

	@Override
	public boolean shouldContinue() {
		// Guard must be alive and not staying
		if (!this.guard.isAlive() || this.guard.isStaying()) {
			return false;
		}

		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return false;
		}

		// Resolve target creeper
		CreeperEntity creeper = this.resolveTargetCreeper(world);
		if (creeper == null || creeper.isDead()) {
			return false;
		}

		// Check timeout (allow longer hunts, up to 600 ticks)
		this.tickTimeout++;
		if (this.tickTimeout > 600) {
			return false;
		}

		// Check if guard has enough gunpowder now
		int gunpowderHave = this.guard.getInventory().getCount(Items.GUNPOWDER);
		int gunpowderNeed = GuardVillagersConfig.get().resourceGathering.gunpowderTargetAmount;
		if (gunpowderHave >= gunpowderNeed) {
			return false;
		}

		// If creeper is ignited, continue to flee until safe
		if (creeper.isIgnited()) {
			double distance = this.guard.distanceTo(creeper);
			double safeRadius = GuardVillagersConfig.get().tnt.safeRadius + 4;
			if (distance > safeRadius) {
				return false; // Safe distance reached, stop pursuing
			}
			this.guard.clearCombatTarget(); // Stop fighting while fleeing
			return true; // Still too close, continue fleeing
		}

		return true;
	}

	@Override
	public void start() {
		this.targetCreeperUuid = null;
		this.tickTimeout = 0;
	}

	@Override
	public void stop() {
		this.targetCreeperUuid = null;
		this.tickTimeout = 0;
		this.consecutiveMisses = 0;
		this.guard.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}

		// Resolve target creeper
		CreeperEntity creeper = this.resolveTargetCreeper(world);
		if (creeper == null || creeper.isDead()) {
			this.targetCreeperUuid = null;
			return;
		}

		GuardVillagersConfig config = GuardVillagersConfig.get();

		// If creeper is ignited, flee immediately
		if (creeper.isIgnited()) {
			this.guard.clearCombatTarget(); // Stop fighting while fleeing
			this.fleeFromCreeper(creeper);
			return;
		}

		// Role-specific behavior
		GuardRole role = this.guard.getRole();
		if (role == GuardRole.BOWMAN) {
			this.tickBowman(creeper);
		} else {
			this.tickSwordsman(creeper);
		}

		// Always set as main target for combat system
		this.guard.setMainTarget(creeper);
	}

	/**
	 * Bowman positioning: maintain 10-14 block distance, let combat system handle shots.
	 */
	private void tickBowman(CreeperEntity creeper) {
		double distance = this.guard.distanceTo(creeper);
		double idealDistance = 12.0D; // middle of 10-14 range
		double minDistance = 10.0D;
		double maxDistance = 14.0D;

		// Only recalculate navigation when outside the acceptable range
		if (distance >= minDistance && distance <= maxDistance) {
			// Already in range — stop moving, let bow attack goal handle shooting
			if (!this.guard.getNavigation().isIdle()) {
				this.guard.getNavigation().stop();
			}
			return;
		}

		if (distance < minDistance) {
			// Too close — back away to ideal distance
			Vec3d away = this.guard.getEntityPos()
				.subtract(creeper.getEntityPos()).normalize();
			if (away.lengthSquared() < 1e-4) away = new Vec3d(1, 0, 0);
			Vec3d destination = creeper.getEntityPos().add(
				away.multiply(idealDistance));
			this.guard.getNavigation().startMovingTo(
				destination.x, destination.y, destination.z, 1.2D);
		} else {
			// Too far — move to ideal distance
			Vec3d toward = creeper.getEntityPos()
				.subtract(this.guard.getEntityPos()).normalize();
			Vec3d destination = creeper.getEntityPos()
				.subtract(toward.multiply(idealDistance));
			this.guard.getNavigation().startMovingTo(
				destination.x, destination.y, destination.z, 1.0D);
		}
	}

	/**
	 * Swordsman behavior: approach and melee, but ready to flee if ignited.
	 */
	private void tickSwordsman(CreeperEntity creeper) {
		double distance = this.guard.distanceTo(creeper);
		double engageDistance = 3.0D;

		if (distance > engageDistance) {
			// Move toward creeper
			this.guard.getNavigation().startMovingTo(
				creeper.getX(), creeper.getY(), creeper.getZ(), 1.4D
			);
		}
		// If within engagement distance, stop moving (melee goal will handle attacking)
	}

	/**
	 * Flee from an ignited creeper at high speed.
	 */
	private void fleeFromCreeper(CreeperEntity creeper) {
		GuardRole role = this.guard.getRole();
		double fleeSpeed = (role == GuardRole.SWORDSMAN) ? 1.5D : 1.4D;

		Vec3d away = this.guard.getEntityPos().subtract(creeper.getEntityPos()).normalize();
		if (away.lengthSquared() < 1e-4) {
			away = new Vec3d(1, 0, 0);
		}

		double safeRadius = GuardVillagersConfig.get().tnt.safeRadius + 4;
		Vec3d destination = this.guard.getEntityPos().add(away.multiply(safeRadius));
		this.guard.getNavigation().startMovingTo(
			destination.x, destination.y, destination.z, fleeSpeed
		);
	}

	/**
	 * Find the nearest alive, non-ignited creeper within follow range.
	 */
	private CreeperEntity findNearestCreeper(ServerWorld world) {
		double range = this.guard.getAttributeValue(EntityAttributes.FOLLOW_RANGE);

		List<CreeperEntity> creepers = world.getEntitiesByClass(
			CreeperEntity.class,
			this.guard.getBoundingBox().expand(range),
			creeper -> creeper.isAlive() && !creeper.isIgnited() && !creeper.isInvisible()
		);

		if (creepers.isEmpty()) {
			return null;
		}

		// Return closest creeper
		CreeperEntity closest = null;
		double closestDistSq = Double.MAX_VALUE;
		for (CreeperEntity creeper : creepers) {
			double distSq = this.guard.squaredDistanceTo(creeper);
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = creeper;
			}
		}
		return closest;
	}

	/**
	 * Resolve target creeper from UUID. Returns null if not found or dead.
	 */
	private CreeperEntity resolveTargetCreeper(ServerWorld world) {
		if (this.targetCreeperUuid == null) {
			return null;
		}

		var entity = world.getEntity(this.targetCreeperUuid);
		if (entity instanceof CreeperEntity creeper) {
			if (creeper.isAlive()) {
				return creeper;
			}
			// Confirmed dead — clear UUID
			this.targetCreeperUuid = null;
			return null;
		}
		// Entity not found (may be in unloaded chunk) — do NOT clear UUID yet
		return null;
	}
}

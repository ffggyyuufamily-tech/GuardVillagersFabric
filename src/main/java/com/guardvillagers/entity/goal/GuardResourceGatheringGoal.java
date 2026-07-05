package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Goal for guards to gather resources (like sand) for crafting.
 * 
 * Periodically scans for nearby resource blocks, navigates to them,
 * mines them, and adds them to the guard's inventory.
 */
public final class GuardResourceGatheringGoal extends Goal {
	private final GuardEntity guard;
	private int scanCooldown;
	private int tickTimeout;
	private int consecutiveMisses;
	private BlockPos targetPosition;

	public GuardResourceGatheringGoal(GuardEntity guard) {
		this.guard = guard;
		this.scanCooldown = 0;
		this.tickTimeout = 0;
		this.consecutiveMisses = 0;
	}

	@Override
	public boolean canStart() {
		// Check config is enabled
		GuardVillagersConfig config = GuardVillagersConfig.get();
		if (!config.resourceGathering.enabled) {
			return false;
		}

		// Guard must be alive and not in combat or staying
		if (!this.guard.isAlive() || this.guard.isStaying() || this.guard.hasActiveCombatTarget()) {
			return false;
		}

		// Only check need periodically
		if (this.scanCooldown > 0) {
			this.scanCooldown--;
			return false;
		}

		// Check if guard needs sand
		int sandNeeded = config.resourceGathering.sandTargetAmount;
		if (this.guard.getInventory().getCount(Items.SAND) >= sandNeeded) {
			this.scanCooldown = 60; // Check again in 60 ticks
			return false;
		}

		// Scan for sand
		this.targetPosition = this.findNearestSand();
		if (this.targetPosition != null) {
			this.consecutiveMisses = 0;
			this.scanCooldown = 0;
			this.tickTimeout = 0;
			return true;
		}

		// Exponential backoff on repeated misses
		this.consecutiveMisses++;
		// Cap at 1200 ticks (60 seconds) so guards don't spam expensive scans forever
		int backoffTicks = Math.min(1200, 60 * (1 << Math.min(this.consecutiveMisses, 5)));
		this.scanCooldown = backoffTicks;
		return false;
	}

	@Override
	public boolean shouldContinue() {
		GuardVillagersConfig config = GuardVillagersConfig.get();

		// Stop if guard dies, enters combat, or stays
		if (!this.guard.isAlive() || this.guard.isStaying() || this.guard.hasActiveCombatTarget()) {
			return false;
		}

		// Stop if no target or timeout reached
		if (this.targetPosition == null) {
			return false;
		}

		this.tickTimeout++;
		if (this.tickTimeout > 200) {
			return false;
		}

		// Stop if we got enough sand
		int sandNeeded = config.resourceGathering.sandTargetAmount;
		if (this.guard.getInventory().getCount(Items.SAND) >= sandNeeded) {
			return false;
		}

		return true;
	}

	@Override
	public void start() {
		this.targetPosition = null;
		this.tickTimeout = 0;
	}

	@Override
	public void stop() {
		this.targetPosition = null;
		this.tickTimeout = 0;
		this.consecutiveMisses = 0;
		this.guard.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}

		if (this.targetPosition == null) {
			return;
		}

		// Check horizontal distance + vertical tolerance separately
		BlockPos guardPos = this.guard.getBlockPos();
		double dx = guardPos.getX() - this.targetPosition.getX();
		double dz = guardPos.getZ() - this.targetPosition.getZ();
		double horizontalDistSq = dx * dx + dz * dz;
		double verticalDist = Math.abs(guardPos.getY() - this.targetPosition.getY());

		// If close enough horizontally and not too far vertically, mine the sand
		if (horizontalDistSq <= 4.0D && verticalDist <= 2.0D) {
			if (world.getBlockState(this.targetPosition).isOf(Blocks.SAND)) {
				world.setBlockState(this.targetPosition, Blocks.AIR.getDefaultState(), 3);
				this.guard.getInventory().addItem(Items.SAND, 1);
			}
			this.targetPosition = null;
			return;
		}

		// Navigate toward the sand
		this.guard.getNavigation().startMovingTo(
			this.targetPosition.getX() + 0.5D,
			this.targetPosition.getY() + 0.5D,
			this.targetPosition.getZ() + 0.5D,
			1.0D
		);
	}

	/**
	 * Scans for the nearest sand block within the configured radius.
	 * Uses manual nested loop with early returns for efficiency.
	 * Respects guard zone restrictions.
	 */
	private BlockPos findNearestSand() {
		GuardVillagersConfig config = GuardVillagersConfig.get();
		int radiusXZ = (int) config.resourceGathering.scanRadius;
		int radiusY = Math.min(radiusXZ, 8); // vertical search limited to 8 blocks max

		BlockPos guardPos = this.guard.getBlockPos();
		var world = this.guard.getEntityWorld();

		// Search in expanding distance layers from the guard
		// This provides efficiency by finding nearest sand quickly with early return
		for (int dist = 0; dist <= radiusXZ; dist++) {
			// Check a square layer at this distance
			for (int dx = -dist; dx <= dist; dx++) {
				for (int dz = -dist; dz <= dist; dz++) {
					// Only check the outer edge of this layer to avoid redundant checks
					if (Math.abs(dx) != dist && Math.abs(dz) != dist) {
						continue;
					}

					for (int dy = -radiusY; dy <= radiusY; dy++) {
						BlockPos pos = guardPos.add(dx, dy, dz);

						// Check zone restrictions
						if (!this.guard.canTargetWithinZone(pos)) {
							continue;
						}

						// First sand found at this distance = nearest
						if (world.getBlockState(pos).isOf(Blocks.SAND)) {
							return pos.toImmutable();
						}
					}
				}
			}
		}

		return null;
	}
}

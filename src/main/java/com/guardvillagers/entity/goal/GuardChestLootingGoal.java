package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Goal for guards to loot chests/barrels for sand and gunpowder.
 * Uses exponential backoff on repeated failed scans to avoid performance issues.
 */
public final class GuardChestLootingGoal extends Goal {
	private final GuardEntity guard;
	private int scanCooldown;
	private int tickTimeout;
	private int consecutiveMisses;
	private BlockPos targetPosition;

	public GuardChestLootingGoal(GuardEntity guard) {
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

		// Guard must be alive and not in combat or staying
		if (!this.guard.isAlive() || this.guard.isStaying() || this.guard.hasActiveCombatTarget()) {
			return false;
		}

		// Only check need periodically
		if (this.scanCooldown > 0) {
			this.scanCooldown--;
			return false;
		}

		// Check if guard needs sand or gunpowder
		int sandHave = this.guard.getInventory().getCount(Items.SAND);
		int sandNeed = config.resourceGathering.sandTargetAmount;
		int gunpowderHave = this.guard.getInventory().getCount(Items.GUNPOWDER);
		int gunpowderNeed = config.resourceGathering.gunpowderTargetAmount;

		if (sandHave >= sandNeed && gunpowderHave >= gunpowderNeed) {
			this.scanCooldown = 60; // Check again in 60 ticks
			return false;
		}

		// Scan for container
		this.targetPosition = this.findNearestContainer();
		if (this.targetPosition != null) {
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
		GuardVillagersConfig config = GuardVillagersConfig.get();

		// Stop if guard dies, enters combat, or stays
		if (!this.guard.isAlive() || this.guard.isStaying() || this.guard.hasActiveCombatTarget()) {
			return false;
		}

		// Stop if no target
		if (this.targetPosition == null) {
			return false;
		}

		this.tickTimeout++;
		if (this.tickTimeout > 200) {
			return false;
		}

		// Stop if we got enough of both resources
		int sandHave = this.guard.getInventory().getCount(Items.SAND);
		int sandNeed = config.resourceGathering.sandTargetAmount;
		int gunpowderHave = this.guard.getInventory().getCount(Items.GUNPOWDER);
		int gunpowderNeed = config.resourceGathering.gunpowderTargetAmount;

		if (sandHave >= sandNeed && gunpowderHave >= gunpowderNeed) {
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

		// If close enough, loot the container
		if (horizontalDistSq <= 4.0D && verticalDist <= 2.0D) {
			BlockEntity blockEntity = world.getBlockEntity(this.targetPosition);
			if (blockEntity instanceof Inventory inventory) {
				if (this.tryExtractFromInventory(inventory)) {
					this.targetPosition = null;
					return;
				}
			}
			this.targetPosition = null;
			return;
		}

		// Navigate toward the container
		this.guard.getNavigation().startMovingTo(
			this.targetPosition.getX() + 0.5D,
			this.targetPosition.getY() + 0.5D,
			this.targetPosition.getZ() + 0.5D,
			1.0D
		);
	}

	/**
	 * Searches for the nearest container (chest or barrel) within the configured radius.
	 * Respects guard zone restrictions.
	 */
	private BlockPos findNearestContainer() {
		GuardVillagersConfig config = GuardVillagersConfig.get();
		int radiusXZ = (int) config.resourceGathering.scanRadius;
		int radiusY = Math.min(radiusXZ, 8);

		BlockPos guardPos = this.guard.getBlockPos();
		var world = this.guard.getEntityWorld();

		// Search in expanding distance layers
		for (int dist = 0; dist <= radiusXZ; dist++) {
			for (int dx = -dist; dx <= dist; dx++) {
				for (int dz = -dist; dz <= dist; dz++) {
					// Only check outer edge of this layer
					if (Math.abs(dx) != dist && Math.abs(dz) != dist) {
						continue;
					}

					for (int dy = -radiusY; dy <= radiusY; dy++) {
						BlockPos pos = guardPos.add(dx, dy, dz);

						// Check zone restrictions
						if (!this.guard.canTargetWithinZone(pos)) {
							continue;
						}

						// Check if this position has a container
						BlockEntity blockEntity = world.getBlockEntity(pos);
						if (blockEntity instanceof ChestBlockEntity) {
							return pos.toImmutable();
						}
						// Also check for barrels
						if (blockEntity instanceof BarrelBlockEntity) {
							return pos.toImmutable();
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * Attempts to extract sand and gunpowder from an inventory (chest or barrel).
	 */
	private boolean tryExtractFromInventory(Inventory inventory) {
		GuardVillagersConfig config = GuardVillagersConfig.get();
		int sandHave = this.guard.getInventory().getCount(Items.SAND);
		int sandNeed = config.resourceGathering.sandTargetAmount;
		int gunpowderHave = this.guard.getInventory().getCount(Items.GUNPOWDER);
		int gunpowderNeed = config.resourceGathering.gunpowderTargetAmount;

		boolean extracted = false;

		for (int slot = 0; slot < inventory.size(); slot++) {
			var stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}

			// Extract sand if needed
			if (stack.isOf(Items.SAND) && sandHave < sandNeed) {
				int take = Math.min(stack.getCount(), sandNeed - sandHave);
				this.guard.getInventory().addItem(Items.SAND, take);
				stack.decrement(take);
				sandHave += take;
				extracted = true;
			}
			// Extract gunpowder if needed
			else if (stack.isOf(Items.GUNPOWDER) && gunpowderHave < gunpowderNeed) {
				int take = Math.min(stack.getCount(), gunpowderNeed - gunpowderHave);
				this.guard.getInventory().addItem(Items.GUNPOWDER, take);
				stack.decrement(take);
				gunpowderHave += take;
				extracted = true;
			}

			// Stop if we have enough of both
			if (sandHave >= sandNeed && gunpowderHave >= gunpowderNeed) {
				break;
			}
		}

		if (extracted) {
			inventory.markDirty();
		}

		return extracted;
	}
}

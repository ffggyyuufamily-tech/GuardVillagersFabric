package com.guardvillagers.entity.goal.engineering;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Handles pillar construction: building a vertical column of blocks beneath
 * the guard while jumping, so the guard can climb up to a higher target.
 */
public final class PillarBuilder {

	private PillarBuilder() {
	}

	/**
	 * Debug logging utility for pillar builder operations.
	 * Format matches project standard: [GuardDebug] [PillarBuilder] [T=<tick>] [Guard=<id>] [Pos=x,y,z] <message>
	 */
	private static void debugLog(GuardEntity guard, String message) {
		if (guard.isDebugActive()) {
			BlockPos pos = guard.getBlockPos();
			GuardVillagersMod.LOGGER.info("[GuardDebug] [PillarBuilder] [T={}] [Guard={}] [Pos={}] {}",
				guard.age,
				guard.getUuid().toString().substring(0, Math.min(8, guard.getUuid().toString().length())),
				pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				message);
		}
	}

	/**
	 * Analyzes terrain and creates a PILLAR task if the target is significantly
	 * above the guard and a direct path is blocked.
	 *
	 * Trigger conditions:
	 * - heightDiff > config.engineering.preferLadderAbove (target is high enough
	 *   to warrant pillar instead of stairs — reuse this existing config value)
	 * - Guard has no valid path to target OR path length > 20 nodes (mirrors ESM's noPath check)
	 * - Guard has building blocks available (guard.getBuildingBlockReserve() > 0)
	 * - Guard is not touching water or in a vehicle
	 */
	public static EngineeringTask analyzeAndCreateTask(
			GuardEntity guard, ServerWorld world,
			GuardVillagersConfig config, Vec3d destination, LivingEntity target) {

		double heightDiff = destination.y - guard.getY();
		debugLog(guard, "analyzeAndCreateTask called | target=" + (target != null ? "present,alive=" + target.isAlive() : "null")
			+ " | water=" + guard.isTouchingWater()
			+ " | vehicle=" + guard.hasVehicle()
			+ " | reserve=" + guard.getBuildingBlockReserve()
			+ " | heightDiff=" + String.format("%.1f", heightDiff));

		if (target == null || !target.isAlive()) {
			debugLog(guard, "Early return: target null or dead");
			return null;
		}
		if (guard.isTouchingWater() || guard.hasVehicle()) {
			debugLog(guard, "Early return: water=" + guard.isTouchingWater() + " vehicle=" + guard.hasVehicle());
			return null;
		}
		if (guard.getBuildingBlockReserve() <= 0) {
			debugLog(guard, "Early return: block reserve=" + guard.getBuildingBlockReserve() + " (<=0)");
			return null;
		}

		if (heightDiff <= config.engineering.preferLadderAbove) {
			debugLog(guard, "Height difference " + String.format("%.1f", heightDiff)
				+ " below pillar threshold " + config.engineering.preferLadderAbove);
			return null;
		}

		// Check if guard has no valid path to target or path is too long
		// (mirrors ESM's navigator.noPath() check)
		boolean noValidPath = guard.getNavigation().isIdle() && guard.squaredDistanceTo(target) > 4.0D;
		boolean pathTooLong = false;
		// If navigator has a path, estimate node count (rough heuristic: ~1 node per block)
		if (!guard.getNavigation().isIdle()) {
			double estimatedPathLength = guard.squaredDistanceTo(target);
			pathTooLong = estimatedPathLength > 20.0D * 20.0D; // > 20 blocks squared
		}

		debugLog(guard, "Path analysis: isIdle=" + guard.getNavigation().isIdle()
			+ " squaredDist=" + String.format("%.1f", guard.squaredDistanceTo(target))
			+ " noValidPath=" + noValidPath
			+ " pathTooLong=" + pathTooLong);

		if (!noValidPath && !pathTooLong) {
			debugLog(guard, "Direct path available, pillar not needed");
			return null;
		}

		// Verify there's actually a height obstacle (target is above, not just
		// on slightly higher ground reachable by normal walking)
		// Check if the block directly above the guard is obstructed for at
		// least 2 blocks vertically — if open sky, ladder is enough
		BlockPos guardPos = guard.getBlockPos();
		boolean hasVerticalObstacle = false;
		for (int dy = 1; dy <= Math.min((int) heightDiff, 3); dy++) {
			BlockState above = world.getBlockState(guardPos.up(dy));
			if (!above.isAir()) {
				hasVerticalObstacle = true;
				break;
			}
		}

		// Only pillar if there's something to climb past, or heightDiff is
		// large enough to justify (> preferLadderAbove * 1.5)
		if (!hasVerticalObstacle && heightDiff <= config.engineering.preferLadderAbove * 1.5) {
			debugLog(guard, "No vertical obstacle and height not extreme enough");
			return null;
		}

		int blocksNeeded = (int) Math.ceil(heightDiff) + 1;
		int maxBlocks = Math.min(blocksNeeded,
			Math.min(guard.getBuildingBlockReserve(), config.engineering.maxBlocksPlaced * 4));

		debugLog(guard, "Creating pillar task, blocks needed: " + blocksNeeded + ", max: " + maxBlocks);

		return new EngineeringTask(
			EngineeringTask.Type.PILLAR,
			null, // no fixed direction for pillar
			destination,
			maxBlocks,
			guardPos,
			guard.getBuildingBlockReserve());
	}

	/**
	 * Ticks the pillar task using teleport-then-place approach (ESM style).
	 * Per tick, if conditions are met:
	 * 1. Check guard is on ground
	 * 2. Verify space above (2 blocks must be replaceable/air for headroom)
	 * 3. Check below is solid
	 * 4. Place block at guard's current position
	 * 5. Teleport guard up by 1.0 block
	 * 6. Resume pathfinding to target
	 * Returns true if a block was placed this tick.
	 */
	public static boolean tickTask(GuardEntity guard, ServerWorld world,
	                                GuardVillagersConfig config, EngineeringTask task) {

		if (task.state() == EngineeringTask.State.SCANNING) {
			task.setState(EngineeringTask.State.BUILDING);
		}

		if (task.state() != EngineeringTask.State.BUILDING) {
			return false;
		}

		if (!task.canPlaceMore()) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		if (guard.getBuildingBlockReserve() <= 0) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("No building blocks");
			return false;
		}

		// Check if we've reached the target height
		double heightDiff = task.targetPos().y - guard.getBlockPos().getY();
		if (heightDiff <= 1.0D) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Apply 10-tick delay between each block placement
		if (guard.age % 10 != 0) {
			return false;
		}

		// Step 1: Guard must be on ground
		if (!guard.isOnGround()) {
			if (guard.age % 40 == 0) {
				debugLog(guard, "tickTask skip: not on ground, velY=" + String.format("%.2f", guard.getVelocity().y));
			}
			return false;
		}

		BlockPos guardPos = guard.getBlockPos();

		// Step 2: Check headroom — space above guard must be air (2 blocks)
		// We need 2 blocks of air for the guard to move up without suffocating
		BlockState aboveOne = world.getBlockState(guardPos.up(1));
		BlockState aboveTwo = world.getBlockState(guardPos.up(2));
		if (!aboveOne.isAir() || !aboveTwo.isAir()) {
			String blockingBlock = !aboveOne.isAir() ? "aboveOne=" + aboveOne.getBlock().getName().getString() : "aboveTwo=" + aboveTwo.getBlock().getName().getString();
			debugLog(guard, "Early return: no headroom - " + blockingBlock);
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("No headroom to climb");
			return false;
		}

		// Step 3: Check below is solid (guard has something to stand on)
		BlockState below = world.getBlockState(guardPos.down());
		if (below.isAir() || !below.isSolid()) {
			// Guard is floating, can't place pillar
			if (guard.age % 40 == 0) {
				debugLog(guard, "tickTask skip: floating, below=" + (below.isAir() ? "air" : below.getBlock().getName().getString()));
			}
			return false;
		}

		// Step 4: Place block at guard's current feet position
		BlockPos placePos = guardPos;
		BlockState placeState = config.engineeringBlockState();

		// Zone safety check
		if (!guard.canTargetWithinZone(placePos)) {
			debugLog(guard, "Early return: placePos outside zone, placePos=" + placePos.toShortString());
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Block placement outside zone");
			return false;
		}

		// Entity collision check
		Box targetBox = new Box(placePos);
		if (!world.isSpaceEmpty(guard, targetBox)) {
			if (guard.age % 40 == 0) {
				debugLog(guard, "Early return: entity collision at placePos=" + placePos.toShortString() + " (rate-limited)");
			}
			return false; // don't FAIL the task, just skip this tick
		}

		boolean placed = world.setBlockState(placePos, placeState, 3);
		if (!placed) {
			debugLog(guard, "Early return: world.setBlockState returned false for placePos=" + placePos.toShortString());
			return false;
		}

		task.incrementBlocksPlaced();
		task.consumeBlock();
		task.setLastPlacedPos(placePos);

		// Step 5: Teleport guard up by 1.0 block
		double newX = guard.getX();
		double newY = guard.getY() + 1.0D;
		double newZ = guard.getZ();
		guard.refreshPositionAndAngles(newX, newY, newZ, guard.getYaw(), guard.getPitch());

		// Step 6: Resume pathfinding to target
		guard.getNavigation().startMovingTo(
			task.targetPos().x, task.targetPos().y, task.targetPos().z, 1.0D);

		if (!task.canPlaceMore()) {
			task.setState(EngineeringTask.State.COMPLETED);
		}

		debugLog(guard, "Placed pillar block at " + placePos.toShortString()
			+ ", teleported to " + String.format("(%.1f, %.1f, %.1f)", newX, newY, newZ)
			+ ", total placed: " + task.blocksPlaced());
		return true;
	}
}

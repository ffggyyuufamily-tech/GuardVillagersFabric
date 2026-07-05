package com.guardvillagers.entity.goal.engineering;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Handles stair construction for moderate height differences (1-3 blocks).
 * Creates an ascending path of blocks that the guard can walk up.
 */
public final class StairBuilder {

	private StairBuilder() {
	}

	/**
	 * Debug logging utility for stair builder operations.
	 */
	private static void debugLog(GuardEntity guard, String message) {
		if (guard.isDebugActive()) {
			GuardVillagersMod.LOGGER.info("[{}] [StairBuilder] {}", 
				guard.getName().getString().substring(0, Math.min(8, guard.getName().getString().length())), 
				message);
		}
	}

	/**
	 * Creates a stair task if the destination is moderately higher (1-3 blocks) than the guard.
	 * This provides an alternative to bridges for small height differences.
	 */
	public static EngineeringTask analyzeAndCreateTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			Vec3d destination, Direction direction) {
		double heightDiff = destination.y - guard.getY();
		
		// Stairs are for small to moderate height differences
		if (heightDiff <= 0.5D || heightDiff > 3.5D) {
			return null;
		}

		// Check for minimum horizontal distance
		double horizontalDist = Math.sqrt(
			Math.pow(destination.x - guard.getX(), 2) + 
			Math.pow(destination.z - guard.getZ(), 2)
		);
		if (horizontalDist < 1.5D) {
			// Too close horizontally - ladder might be better
			return null;
		}

		// Calculate how many blocks we need
		int blocksNeeded = (int) Math.ceil(heightDiff) + 1;  // +1 for safe walking space
		// Stairs need more blocks than a simple barricade — use 4x the base limit
		int maxPlace = Math.min(
			config.engineering.maxBlocksPlaced * 4,
			guard.getBuildingBlockReserve()
		);
		if (maxPlace <= 0) {
			return null;
		}

		// Limit to what's actually needed
		maxPlace = Math.min(maxPlace, blocksNeeded);

		debugLog(guard, "Creating stair task, height=" + String.format("%.1f", heightDiff) 
			+ ", distance=" + String.format("%.1f", horizontalDist));

		return new EngineeringTask(
				EngineeringTask.Type.BRIDGE,  // Use BRIDGE type for stairs (same mechanics)
				direction,
				destination,
				maxPlace,
				guard.getBlockPos(),
				guard.getBuildingBlockReserve());
	}

	/**
	 * Ticks the stair task: places blocks in an ascending diagonal pattern.
	 */
	public static boolean tickTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			EngineeringTask task) {
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

		// Find next stair position
		BlockPos placePos = findNextStairBlock(world, guard, config, task);
		if (placePos == null) {
			// Check if guard has reached target height — if so, complete
			double heightRemaining = task.targetPos().y - guard.getBlockPos().getY();
			if (heightRemaining <= 1.0D) {
				task.setState(EngineeringTask.State.COMPLETED);
			}
			// Otherwise just wait — guard needs to walk forward before next block
			return false;
		}

		// Determine what block to place
		BlockState stateToPlace = config.engineeringBlockState();
		if (stateToPlace == null) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("No valid block state");
			return false;
		}

		// Validate placement
		if (!canPlaceAt(world, guard, config, placePos, stateToPlace)) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Place the block
		if (world.setBlockState(placePos, stateToPlace, 3)) {
			task.incrementBlocksPlaced();
			task.consumeBlock();
			task.setLastPlacedPos(placePos);

			if (!task.canPlaceMore()) {
				task.setState(EngineeringTask.State.COMPLETED);
			}

			debugLog(guard, "Placed stair block " + task.blocksPlaced() + "/" + task.maxBlocksToPlace() 
				+ " at " + placePos);
			return true;
		}

		task.setState(EngineeringTask.State.FAILED);
		task.setFailureReason("setBlockState returned false");
		return false;
	}

	/**
	 * Finds the next block position in the ascending stair pattern.
	 */
	private static BlockPos findNextStairBlock(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			EngineeringTask task) {
		Direction dir = task.direction();

		// Start from last placed position, or guard's position if nothing placed yet
		// This is the same pattern BridgeBuilder uses and prevents the scan
		// from jumping around as the guard moves
		BlockPos start = task.lastPlacedPos() != null
				? task.lastPlacedPos()
				: guard.getBlockPos();

		// Total height to climb
		double totalHeight = task.targetPos().y - task.startPos().getY();
		if (totalHeight <= 0) {
			task.setState(EngineeringTask.State.COMPLETED);
			return null;
		}

		// How many horizontal steps placed so far determines the current height step
		// Simple rule: every N horizontal steps, go up 1 block
		// N = max(1, floor(horizontalDist / totalHeight)) so the slope is gradual
		double horizontalDist = Math.sqrt(
			Math.pow(task.targetPos().x - task.startPos().getX(), 2) +
			Math.pow(task.targetPos().z - task.startPos().getZ(), 2)
		);
		// Steps per height level — how many horizontal blocks per 1 block of height
		int stepsPerLevel = Math.max(1, (int) Math.floor(horizontalDist / totalHeight));

		// Current expected height based on how many blocks placed
		int blocksPlaced = task.blocksPlaced();
		int expectedHeightOffset = blocksPlaced / stepsPerLevel;
		int expectedY = task.startPos().getY() + expectedHeightOffset;

		// Next candidate: 1 step forward from last placed position
		BlockPos candidate = start.offset(dir);
		// Adjust Y to match expected stair height
		candidate = new BlockPos(candidate.getX(), expectedY, candidate.getZ());

		// Check 3 Y positions around expected height (in case terrain is uneven)
		for (int dy = 0; dy <= 2; dy++) {
			BlockPos checkPos = new BlockPos(candidate.getX(),
					candidate.getY() + dy, candidate.getZ());
			BlockPos below = checkPos.down();

			// Valid stair position: air at checkPos and above, solid or air below
			// (we're placing the block at checkPos to create a step)
			boolean spaceOpen = BridgeBuilder.isSpaceOpen(world, checkPos)
					&& BridgeBuilder.isSpaceOpen(world, checkPos.up());

			// The block BELOW checkPos should be solid (so guard can walk on checkPos)
			// OR we place at the level where there's no ground (creating the step)
			boolean groundBelow = world.getBlockState(below)
					.isSideSolidFullSquare(world, below, Direction.UP);

			if (spaceOpen && !groundBelow) {
				return checkPos; // needs a block here to create a step
			}
			if (spaceOpen && groundBelow) {
				// Ground already here, skip forward — no block needed at this height
				// Try next horizontal step instead
				break;
			}
		}

		// If the candidate position already has ground (no block needed),
		// move forward without placing — let guard walk to next position
		// Return null to signal no block needed this tick (task continues next tick)
		return null;
	}

	private static boolean canPlaceAt(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			BlockPos pos, BlockState state) {
		// Zone and height checks
		if (!guard.canTargetWithinZone(pos)) {
			return false;
		}
		if (pos.getY() <= world.getBottomY() || pos.getY() >= world.getTopYInclusive()) {
			return false;
		}
		// Space and fluid checks
		if (!BridgeBuilder.isSpaceOpen(world, pos) || !world.getFluidState(pos).isEmpty()) {
			return false;
		}
		// Block placement rules
		if (!state.canPlaceAt(world, pos)) {
			return false;
		}
		// Safety: don't place within 1 block of guard for stairs
		if (EngineeringSafety.isTooCloseToGuard(pos, guard, EngineeringSafety.ProximityCheck.BRIDGE)) {
			return false;
		}
		// Safety: ensure guard has an escape route after placement
		if (!EngineeringSafety.hasEscapeRoute(world, pos, guard)) {
			return false;
		}
		// Entity collision check — pass guard so it excludes itself
		net.minecraft.util.math.Box targetBox = new net.minecraft.util.math.Box(pos);
		if (!world.isSpaceEmpty(guard, targetBox)) {
			return false;
		}
		// Extra check: never bury any living entity
		if (!world.getEntitiesByClass(
				LivingEntity.class,
				targetBox.expand(0.1D),
				entity -> entity.isAlive() && entity != guard).isEmpty()) {
			return false;
		}
		return true;
	}
}

package com.guardvillagers.entity.goal.engineering;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Handles ladder construction: building a vertical column with ladders
 * so the guard can climb to a higher target.
 */
public final class LadderBuilder {

	private LadderBuilder() {
	}

	/**
	 * Debug logging utility for ladder builder operations.
	 */
	private static void debugLog(GuardEntity guard, String message) {
		if (guard.isDebugActive()) {
			GuardVillagersMod.LOGGER.info("[{}] [LadderBuilder] {}", 
				guard.getName().getString().substring(0, Math.min(8, guard.getName().getString().length())), 
				message);
		}
	}

	/**
	 * Creates a ladder task if the destination is significantly higher than the guard.
	 * The task will place a column of blocks with ladders attached.
	 * Uses terrain classification to validate that ladder is appropriate.
	 */
	public static EngineeringTask analyzeAndCreateTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			Vec3d destination, Direction direction) {
		return analyzeAndCreateTask(guard, world, config, destination, direction, null);
	}

	/**
	 * Creates a ladder task with terrain awareness.
	 * 
	 * @param guard the guard entity
	 * @param world the server world
	 * @param config the mod configuration
	 * @param destination the destination position
	 * @param direction the direction to build
	 * @param target the target entity (optional, for terrain classification)
	 * @return the engineering task, or null if ladder is not appropriate
	 */
	public static EngineeringTask analyzeAndCreateTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			Vec3d destination, Direction direction, LivingEntity target) {
		double heightDiff = destination.y - guard.getY();
		
		// Use config value for ladder threshold instead of hardcoded 2.5
		if (heightDiff <= config.engineering.preferLadderAbove) {
			debugLog(guard, "Height difference " + String.format("%.1f", heightDiff) 
				+ " below ladder threshold " + config.engineering.preferLadderAbove);
			return null;
		}

		// Check if there's a solid block face nearby to attach a ladder to
		// Ladder needs a solid block on at least one horizontal side
		BlockPos guardPos = guard.getBlockPos();
		boolean hasWallForLadder = false;
		for (Direction horizontal : new Direction[]{
				Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			// Check at guard height and 1-2 above
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos checkPos = guardPos.offset(horizontal).up(dy);
				if (!world.getBlockState(checkPos).isAir()
						&& world.getBlockState(checkPos).isSideSolidFullSquare(
								world, checkPos, horizontal.getOpposite())) {
					hasWallForLadder = true;
					break;
				}
			}
			if (hasWallForLadder) break;
		}
		if (!hasWallForLadder) {
			debugLog(guard, "No wall found for ladder placement, skipping");
			return null;
		}

		// Validate terrain is suitable for climbing
		if (target != null) {
			TerrainClassifier.TerrainType terrain = TerrainClassifier.classifyTerrain(guard, target, world, config);
			debugLog(guard, "Terrain classification for ladder: " + terrain.name());
			
			// Don't build ladder for horizontal gaps - bridge is better
			if (terrain == TerrainClassifier.TerrainType.GAP) {
				debugLog(guard, "Bridge is better for GAP terrain");
				return null;
			}
			
			// Don't build ladder if path is normal
			if (terrain == TerrainClassifier.TerrainType.NORMAL_PATH) {
				debugLog(guard, "No engineering needed - normal path");
				return null;
			}
			
			// Ladder is good for PILLAR, CLIFF, and ELEVATED_PLATFORM
			debugLog(guard, "Creating ladder task for terrain: " + terrain.name() + ", height=" + String.format("%.1f", heightDiff));
		}

		// Calculate how many blocks we need to climb
		int blocksNeeded = (int) Math.ceil(heightDiff);
		int maxPlace = Math.min(config.engineering.maxBlocksPlaced, guard.getBuildingBlockReserve());
		if (maxPlace <= 0) {
			return null;
		}

		// Limit to what's actually needed
		maxPlace = Math.min(maxPlace, blocksNeeded);

		// Resolve destination if target is available
		Vec3d resolvedDestination = destination;
		if (target != null) {
			try {
				TerrainClassifier.TerrainType terrain = TerrainClassifier.classifyTerrain(guard, target, world, config);
				resolvedDestination = TerrainAnalyzer.resolveReachableDestination(guard, target, world, config, terrain);
				debugLog(guard, "Resolved destination from terrain analysis");
			} catch (Exception e) {
				// Fall back to original destination if analysis fails
				debugLog(guard, "Terrain analysis failed, using original destination");
			}
		}

		return new EngineeringTask(
				EngineeringTask.Type.LADDER,
				direction,
				resolvedDestination,
				maxPlace,
				guard.getBlockPos(),
				guard.getBuildingBlockReserve());
	}

	/**
	 * Ticks the ladder task: places one ladder block per tick, building upward.
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

		// Find the next position to place a ladder
		BlockPos placePos = findNextLadderPosition(world, guard, config, task);
		if (placePos == null) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Check for support below
		BlockPos support = placePos.down();
		if (!world.getBlockState(support).isSideSolidFullSquare(world, support, Direction.UP)) {
			// Need a support block first
			if (!guard.canTargetWithinZone(support)) {
				task.setState(EngineeringTask.State.FAILED);
				task.setFailureReason("Cannot place support block outside zone");
				return false;
			}
			if (!BridgeBuilder.isSpaceOpen(world, support) || !world.getFluidState(support).isEmpty()) {
				task.setState(EngineeringTask.State.FAILED);
				task.setFailureReason("Cannot place support block — space occupied");
				return false;
			}
			BlockState supportState = config.engineeringBlockState();
			// Safety: support block proximity check (allow closer placement for ladder support)
			if (EngineeringSafety.isTooCloseToGuard(support, guard, EngineeringSafety.ProximityCheck.LADDER_SUPPORT)) {
				task.setState(EngineeringTask.State.FAILED);
				task.setFailureReason("Support block too close to guard");
				return false;
			}
			if (!supportState.canPlaceAt(world, support)) {
				task.setState(EngineeringTask.State.FAILED);
				task.setFailureReason("Support block cannot be placed here");
				return false;
			}
			if (!world.setBlockState(support, supportState, 3)) {
				task.setState(EngineeringTask.State.FAILED);
				task.setFailureReason("Failed to place support block");
				return false;
			}
			task.incrementBlocksPlaced();
			task.consumeBlock();
			task.setLastPlacedPos(support);

			if (!task.canPlaceMore()) {
				task.setState(EngineeringTask.State.COMPLETED);
			}
			return true;
		}

		// Place the ladder
		Direction ladderFacing = task.direction().getOpposite();
		BlockState ladderState = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, ladderFacing);

		if (!guard.canTargetWithinZone(placePos)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Ladder position outside zone");
			return false;
		}
		if (!BridgeBuilder.isSpaceOpen(world, placePos)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Ladder position not empty");
			return false;
		}
		if (!ladderState.canPlaceAt(world, placePos)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Ladder cannot be placed here");
			return false;
		}
		// Safety: ladder placement check
		if (EngineeringSafety.isTooCloseToGuard(placePos, guard, EngineeringSafety.ProximityCheck.LADDER)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Ladder too close to guard");
			return false;
		}
		if (!EngineeringSafety.hasEscapeRoute(world, placePos, guard)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("No escape route after ladder placement");
			return false;
		}
		// Entity collision check
		Box targetBox = new Box(placePos);
		if (!world.isSpaceEmpty(null, targetBox)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Ladder position occupied");
			return false;
		}
		if (EngineeringSafety.hasEntityCollision(world, targetBox)) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Entity collision on ladder position");
			return false;
		}

		if (world.setBlockState(placePos, ladderState, 3)) {
			task.incrementBlocksPlaced();
			task.consumeBlock();
			task.setLastPlacedPos(placePos);

			if (!task.canPlaceMore()) {
				task.setState(EngineeringTask.State.COMPLETED);
			}
			return true;
		}

		task.setState(EngineeringTask.State.FAILED);
		task.setFailureReason("Failed to place ladder");
		return false;
	}

	private static BlockPos findNextLadderPosition(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			EngineeringTask task) {
		Direction dir = task.direction();
		// Continue from the last placed block if available
		BlockPos base = task.lastPlacedPos() != null ? task.lastPlacedPos().offset(dir) : guard.getBlockPos().offset(dir);

		// Start from one block above the guard's feet and go up
		int startY = guard.getBlockY() + 1;
		int maxY = guard.getBlockY() + task.maxBlocksToPlace() + 2;

		for (int y = startY; y <= maxY; y++) {
			BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
			BlockPos below = pos.down();

			// Check if there's already a ladder here
			if (world.getBlockState(pos).isOf(Blocks.LADDER)) {
				continue;
			}

			// Check if there's support below
			if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) {
				// Need a support block
				return pos;
			}

			// There's support but no ladder — place ladder here
			if (BridgeBuilder.isSpaceOpen(world, pos)) {
				return pos;
			}
		}

		return null;
	}
}

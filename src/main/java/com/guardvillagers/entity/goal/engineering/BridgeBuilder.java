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
 * Handles bridge construction: scanning gaps, placing support blocks under the guard's path,
 * building stairs upward, and placing ladders when the target is much higher.
 */
public final class BridgeBuilder {

	private BridgeBuilder() {
	}

	/**
	 * Debug logging utility for bridge builder operations.
	 */
	private static void debugLog(GuardEntity guard, String message) {
		if (guard.isDebugActive()) {
			GuardVillagersMod.LOGGER.info("[{}] [BridgeBuilder] {}", 
				guard.getName().getString().substring(0, Math.min(8, guard.getName().getString().length())), 
				message);
		}
	}

	/**
	 * Analyzes the terrain ahead and creates a bridge task if a gap is detected.
	 * Scans up to config.engineering.bridgeLength blocks forward to find empty space
	 * that needs filling. Uses terrain classification to determine if bridging is appropriate.
	 */
	public static EngineeringTask analyzeAndCreateTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			Vec3d destination, Direction direction) {
		return analyzeAndCreateTask(guard, world, config, destination, direction, null);
	}

	/**
	 * Analyzes the terrain ahead and creates a bridge task if a gap is detected.
	 * Scans up to config.engineering.bridgeLength blocks forward to find empty space
	 * that needs filling. Uses terrain classification to determine if bridging is appropriate.
	 * 
	 * @param guard the guard entity
	 * @param world the server world
	 * @param config the mod configuration
	 * @param destination the destination position
	 * @param direction the direction to build
	 * @param target the target entity (optional, for terrain classification)
	 * @return the engineering task, or null if no bridge is needed
	 */
	public static EngineeringTask analyzeAndCreateTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			Vec3d destination, Direction direction, LivingEntity target) {
		BlockPos feet = guard.getBlockPos();
		BlockPos frontFeet = feet.offset(direction);

		// Perform terrain classification if target is available
		if (target != null) {
			TerrainClassifier.TerrainType terrain = TerrainClassifier.classifyTerrain(guard, target, world, config);
			debugLog(guard, "Terrain classification: " + terrain.name());
			
			// Don't bridge to vertical structures or when path is normal
			if (terrain == TerrainClassifier.TerrainType.PILLAR || terrain == TerrainClassifier.TerrainType.CLIFF) {
				debugLog(guard, "Cannot bridge to " + terrain.name());
				return null;
			}
			
			if (terrain == TerrainClassifier.TerrainType.NORMAL_PATH) {
				debugLog(guard, "No bridge needed - normal path");
				return null;
			}
			
			// Only bridge GAP or ELEVATED_PLATFORM terrain
			if (terrain != TerrainClassifier.TerrainType.GAP && terrain != TerrainClassifier.TerrainType.ELEVATED_PLATFORM) {
				return null;
			}
		}

		// Check if there's actually a gap to bridge
		int gapLength = scanGapLength(world, frontFeet, direction, config.engineering.bridgeLength);
		if (gapLength <= 0) {
			return null;
		}

		int maxPlace = Math.min(config.engineering.maxBlocksPlaced, guard.getBuildingBlockReserve());
		if (maxPlace <= 0) {
			return null;
		}

		// Resolve destination using TerrainAnalyzer if target is available
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
				EngineeringTask.Type.BRIDGE,
				direction,
				resolvedDestination,
				maxPlace,
				feet,
				guard.getBuildingBlockReserve());
	}

	/**
	 * Scans forward from the given position to count consecutive empty-air blocks
	 * (no solid ground). Returns the number of empty spaces before hitting solid ground
	 * or the max scan distance.
	 */
	private static int scanGapLength(ServerWorld world, BlockPos start, Direction direction, double maxDistance) {
		int gap = 0;
		int maxScan = (int) Math.ceil(maxDistance);
		BlockPos current = start;

		for (int i = 0; i < maxScan; i++) {
			// Check if this position and the one above are open (air/space we could walk through)
			boolean feetOpen = isSpaceOpen(world, current);
			boolean headOpen = isSpaceOpen(world, current.up());

			if (!feetOpen || !headOpen) {
				// Hit solid ground or an obstacle — stop scanning
				break;
			}

			// Check if there's ground below (if so, we don't need to bridge here)
			BlockPos below = current.down();
			if (world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) {
				// There's solid ground below, no need to bridge — skip this column
				current = current.offset(direction);
				continue;
			}

			gap++;
			current = current.offset(direction);
		}

		return gap;
	}

	/**
	 * Ticks the bridge task: places one block per call (or more depending on config),
	 * advancing along the direction. Returns true if a block was placed this tick.
	 * Includes termination checks for maximum bridge distance and vertical mismatches.
	 */
	public static boolean tickTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config,
			EngineeringTask task) {
		if (task.state() == EngineeringTask.State.SCANNING) {
			// Transition to building on first tick
			task.setState(EngineeringTask.State.BUILDING);
		}

		if (task.state() != EngineeringTask.State.BUILDING) {
			return false;
		}

		if (!task.canPlaceMore()) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Calculate the next position to place a block
		BlockPos placePos = findNextBridgeBlock(world, guard, config, task);
		if (placePos == null) {
			// No valid position found — bridge is done or blocked
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Check termination conditions based on distance and vertical mismatch
		double distFromStart = Math.sqrt(placePos.getSquaredDistance(task.startPos()));
		if (distFromStart > config.engineering.maxBridgeDistance) {
			debugLog(guard, "Bridge terminating: max distance " + String.format("%.1f", distFromStart) 
				+ " > " + config.engineering.maxBridgeDistance);
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Check vertical mismatch
		double verticalMismatch = Math.abs(placePos.getY() - task.startPos().getY());
		if (verticalMismatch > config.engineering.maxVerticalDiff) {
			debugLog(guard, "Bridge terminating: vertical mismatch " + String.format("%.1f", verticalMismatch)
				+ " > " + config.engineering.maxVerticalDiff);
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("Vertical mismatch: " + String.format("%.1f", verticalMismatch));
			return false;
		}

		// Determine what type of block to place
		BlockState stateToPlace = determineBlockState(world, guard, config, placePos, task);
		if (stateToPlace == null) {
			task.setState(EngineeringTask.State.FAILED);
			task.setFailureReason("No valid block state for position");
			return false;
		}

		// Validate placement
		if (!canPlaceAt(world, guard, config, placePos, stateToPlace)) {
			// Skip this position and try the next one
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
			debugLog(guard, "Block placed at distance " + String.format("%.1f", distFromStart));
			return true;
		}

		task.setState(EngineeringTask.State.FAILED);
		task.setFailureReason("setBlockState returned false");
		return false;
	}

	/**
	 * Finds the next block position along the bridge direction that needs a block placed.
	 */
	private static BlockPos findNextBridgeBlock(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			EngineeringTask task) {
		Direction dir = task.direction();
		// Continue from the last placed block if available, otherwise start from guard's position
		BlockPos start = task.lastPlacedPos() != null ? task.lastPlacedPos() : guard.getBlockPos();
		BlockPos current = start.offset(dir);
		int maxScan = (int) Math.ceil(config.engineering.bridgeLength);
		for (int i = 0; i < maxScan; i++) {
			BlockPos below = current.down();
			boolean hasGroundBelow = world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
			boolean spaceOpen = isSpaceOpen(world, current) && isSpaceOpen(world, current.up());
			if (spaceOpen && !hasGroundBelow) {
				return below;
			}
			current = current.offset(dir);
		}
		return null;
	}

	/**
	 * Determines what block state to place at the given position.
	 * Could be a stair, ladder, or plain block depending on the situation.
	 */
	private static BlockState determineBlockState(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			BlockPos placePos, EngineeringTask task) {
		// If the target is much higher, use ladders
		double heightDiff = task.targetPos().y - guard.getY();
		if (config.engineering.ladderBuilding && heightDiff > 2.5D) {
			Direction ladderFacing = task.direction().getOpposite();
			BlockPos ladderPos = placePos.up();
			BlockState ladderState = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, ladderFacing);
			if (ladderState.canPlaceAt(world, ladderPos)) {
				return ladderState;
			}
		}

		// Default: use the configured engineering block
		return config.engineeringBlockState();
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
		if (!isSpaceOpen(world, pos) || !world.getFluidState(pos).isEmpty()) {
			return false;
		}
		// Block placement rules
		if (!state.canPlaceAt(world, pos)) {
			return false;
		}
		// Safety: don't place within 1 block of guard for bridges
		if (EngineeringSafety.isTooCloseToGuard(pos, guard, EngineeringSafety.ProximityCheck.BRIDGE)) {
			return false;
		}
		// Safety: ensure guard has an escape route after placement
		if (!EngineeringSafety.hasEscapeRoute(world, pos, guard)) {
			return false;
		}
		// Entity collision check — pass guard so it excludes itself
		Box targetBox = new Box(pos);
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

	static boolean isSpaceOpen(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}
}

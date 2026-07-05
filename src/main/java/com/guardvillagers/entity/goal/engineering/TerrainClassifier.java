package com.guardvillagers.entity.goal.engineering;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Classifies terrain types to help guards choose appropriate engineering tasks.
 * Analyzes height differences and horizontal distances to determine if a terrain gap
 * is safe to bridge, if it's a dangerous cliff, or if it's on an elevated structure.
 */
public final class TerrainClassifier {

	/**
	 * Enum representing different terrain classifications.
	 */
	public enum TerrainType {
		GAP,                    // Horizontal void, safe to bridge
		CLIFF,                  // Vertical drop, dangerous
		PILLAR,                 // Enemy on elevated structure (nearly vertical climb)
		ELEVATED_PLATFORM,      // Large platform above (sloped/accessible)
		NORMAL_PATH             // No engineering needed
	}

	private TerrainClassifier() {
	}

	/**
	 * Classifies the terrain between a guard and a target to determine the engineering challenge.
	 *
	 * @param guard the guard analyzing the terrain
	 * @param target the target entity to reach
	 * @param world the server world
	 * @param config the mod configuration
	 * @return the classified terrain type
	 */
	public static TerrainType classifyTerrain(GuardEntity guard, LivingEntity target, ServerWorld world, GuardVillagersConfig config) {
		double guardY = guard.getY();
		double targetY = target.getY();
		double heightDiff = targetY - guardY;

		// Calculate horizontal distance only
		double guardX = guard.getX();
		double guardZ = guard.getZ();
		double targetX = target.getX();
		double targetZ = target.getZ();
		double horizontalDistance = Math.sqrt((targetX - guardX) * (targetX - guardX) + (targetZ - guardZ) * (targetZ - guardZ));

		double maxVerticalDiff = config.engineering.maxVerticalDiff;
		double preferLadderAbove = config.engineering.preferLadderAbove;
		double bridgeLength = config.engineering.bridgeLength;

		// Target is in normal height range
		if (heightDiff >= -1.0D && heightDiff <= 1.0D) {
			if (horizontalDistance < 1.0D) {
				return TerrainType.NORMAL_PATH;
			} else if (horizontalDistance > bridgeLength) {
				return TerrainType.NORMAL_PATH;  // Too far to bridge
			} else {
				return TerrainType.GAP;  // Safe horizontal gap
			}
		}

		// Target much lower - dangerous drop
		if (heightDiff < -2.0D) {
			return TerrainType.CLIFF;
		}

		// Target significantly higher
		if (heightDiff > preferLadderAbove) {
			if (horizontalDistance < maxVerticalDiff) {
				return TerrainType.PILLAR;  // Nearly vertical, hard to climb
			} else {
				return TerrainType.ELEVATED_PLATFORM;  // Sloped platform
			}
		}

		// Target slightly higher but within reasonable range
		if (heightDiff > 0.0D && heightDiff <= maxVerticalDiff) {
			return TerrainType.GAP;  // Slight elevation is still a bridgeable gap
		}

		// Default fallback
		return TerrainType.NORMAL_PATH;
	}

	/**
	 * Finds a reachable ground level near the target by scanning downward.
	 * Returns the BlockPos of a solid standing position, or null if the target is hovering.
	 *
	 * @param target the target entity
	 * @param world the server world
	 * @param searchRadius the radius to search downward
	 * @return BlockPos of ground level, or null if no ground found
	 */
	public static BlockPos findReachableGroundNear(LivingEntity target, ServerWorld world, double searchRadius) {
		BlockPos targetPos = target.getBlockPos();
		int searchDistance = (int) Math.ceil(searchRadius);

		// Scan downward from target position
		for (int y = targetPos.getY(); y >= targetPos.getY() - searchDistance; y--) {
			BlockPos checkPos = new BlockPos(targetPos.getX(), y, targetPos.getZ());
			BlockState blockState = world.getBlockState(checkPos);

			// Check if this is solid ground
			if (blockState.isSideSolidFullSquare(world, checkPos, Direction.UP)) {
				// Check if there's air above for standing
				BlockPos abovePos = checkPos.up();
				BlockState aboveState = world.getBlockState(abovePos);
				if (aboveState.getCollisionShape(world, abovePos).isEmpty()) {
					return abovePos;  // Return the standing position above the solid block
				}
			}
		}

		return null;  // No ground found, target is hovering
	}

	/**
	 * Detects if a target is standing on an elevated structure.
	 * Returns true if there is significant space below the target's feet.
	 *
	 * @param target the target entity
	 * @param world the server world
	 * @return true if target is at least 2 blocks above ground
	 */
	public static boolean isOnElevatedStructure(LivingEntity target, ServerWorld world) {
		BlockPos targetPos = target.getBlockPos();
		int groundLevel = findGroundLevel(targetPos, world);

		// Check if there's at least 2 blocks of space between target and ground
		return (targetPos.getY() - groundLevel) >= 2;
	}

	/**
	 * Helper method to find the ground level directly below a position.
	 *
	 * @param pos the position to scan from
	 * @param world the server world
	 * @return the Y coordinate of the highest solid block below the position
	 */
	private static int findGroundLevel(BlockPos pos, ServerWorld world) {
		int y = pos.getY() - 1;

		// Scan downward up to 64 blocks
		while (y >= pos.getY() - 64) {
			BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
			BlockState blockState = world.getBlockState(checkPos);

			if (blockState.isSideSolidFullSquare(world, checkPos, Direction.UP)) {
				return y;
			}

			y--;
		}

		// Return world minimum if no ground found
		return world.getBottomY();
	}
}

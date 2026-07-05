package com.guardvillagers.entity.goal.engineering;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.GuardRole;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Handles defensive barricade construction: placing walls of blocks between
 * the guard (archer) and an approaching enemy.
 */
public final class BarricadeBuilder {

	private BarricadeBuilder() {
	}

	/**
	 * Creates a barricade task if the guard is a bowman and has a target within range.
	 * The barricade is built in front of the guard, perpendicular to the direction
	 * of the incoming threat.
	 */
	public static EngineeringTask analyzeAndCreateTask(GuardEntity guard, ServerWorld world, GuardVillagersConfig config) {
		LivingEntity target = guard.getTarget();
		if (target == null || !target.isAlive()) {
			return null;
		}
		if (guard.getRole() != GuardRole.BOWMAN) {
			return null;
		}
		if (!config.engineering.defensiveBarricades) {
			return null;
		}

		double barricadeRangeSq = config.engineering.defensiveBarricadeRange * config.engineering.defensiveBarricadeRange;
		if (guard.squaredDistanceTo(target) > barricadeRangeSq) {
			return null;
		}

		int maxPlace = Math.min(config.engineering.maxBlocksPlaced, guard.getBuildingBlockReserve());
		if (maxPlace <= 0) {
			return null;
		}

		Direction dirToTarget = horizontalDirectionTo(guard, target.getEntityPos());

		return new EngineeringTask(
				EngineeringTask.Type.BARRICADE,
				dirToTarget,
				target.getEntityPos(),
				maxPlace,
				guard.getBlockPos(),
				guard.getBuildingBlockReserve());
	}

	/**
	 * Ticks the barricade task: places blocks in a line perpendicular to the
	 * direction of the threat, creating a wall.
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

		// Find the next barricade position
		BlockPos placePos = findNextBarricadePosition(world, guard, config, task);
		if (placePos == null) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		// Validate and place
		if (!canPlaceBarricadeBlock(world, guard, config, placePos)) {
			task.setState(EngineeringTask.State.COMPLETED);
			return false;
		}

		BlockState state = config.engineeringBlockState();
		if (world.setBlockState(placePos, state, 3)) {
			task.incrementBlocksPlaced();
			task.consumeBlock();
			task.setLastPlacedPos(placePos);

			if (!task.canPlaceMore()) {
				task.setState(EngineeringTask.State.COMPLETED);
			}
			return true;
		}

		task.setState(EngineeringTask.State.FAILED);
		task.setFailureReason("Failed to place barricade block");
		return false;
	}

	/**
	 * Finds the next position for a barricade block.
	 * Builds a line of blocks perpendicular to the threat direction.
	 */
	private static BlockPos findNextBarricadePosition(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			EngineeringTask task) {
		Direction dirToTarget = task.direction();
		Direction perpendicular = dirToTarget.rotateYClockwise();
		BlockPos feet = guard.getBlockPos();

		// First try the block 2 blocks ahead of the guard (to avoid self-entrapment)
		BlockPos front = feet.offset(dirToTarget, 2);
		int placed = task.blocksPlaced();

		if (placed == 0) {
			// First block must be at least 2 blocks away
			if (BridgeBuilder.isSpaceOpen(world, front) && world.getFluidState(front).isEmpty()) {
				return front;
			}
			// Try perpendicular offsets from the 2-block position
			BlockPos candidate = front.offset(perpendicular, 1);
			if (BridgeBuilder.isSpaceOpen(world, candidate) && world.getFluidState(candidate).isEmpty()) {
				return candidate;
			}
			candidate = front.offset(perpendicular, -1);
			if (BridgeBuilder.isSpaceOpen(world, candidate) && world.getFluidState(candidate).isEmpty()) {
				return candidate;
			}
			return null;
		}

		// Use last placed position as starting point
		BlockPos start = task.lastPlacedPos() != null ? task.lastPlacedPos() : front;

		// Then try positions perpendicular to the direction (forming a wall)
		// Alternate: right, left, further right, further left, etc.
		int perpStep = (placed + 1) / 2;
		int sign = (placed % 2 == 1) ? 1 : -1;

		BlockPos candidate = start.offset(perpendicular, perpStep * sign);
		if (BridgeBuilder.isSpaceOpen(world, candidate) && world.getFluidState(candidate).isEmpty()) {
			return candidate;
		}

		// Try the other side
		candidate = start.offset(perpendicular, perpStep * -sign);
		if (BridgeBuilder.isSpaceOpen(world, candidate) && world.getFluidState(candidate).isEmpty()) {
			return candidate;
		}

		// Try further forward
		BlockPos front2 = start.offset(dirToTarget);
		if (BridgeBuilder.isSpaceOpen(world, front2) && world.getFluidState(front2).isEmpty()) {
			return front2;
		}

		return null;
	}

	private static boolean canPlaceBarricadeBlock(ServerWorld world, GuardEntity guard, GuardVillagersConfig config,
			BlockPos pos) {
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
		BlockState state = config.engineeringBlockState();
		if (!state.canPlaceAt(world, pos)) {
			return false;
		}
		// Safety: don't place within 1 block of guard for barricades
		if (EngineeringSafety.isTooCloseToGuard(pos, guard, EngineeringSafety.ProximityCheck.BARRICADE)) {
			return false;
		}
		// Safety: ensure guard has an escape route after placement
		if (!EngineeringSafety.hasEscapeRoute(world, pos, guard)) {
			return false;
		}
		// Entity collision check — must not bury any living entity, especially allies
		Box targetBox = new Box(pos);
		if (!world.isSpaceEmpty(guard, targetBox)) {
			return false;
		}
		// Extra check: never place on top of or inside any living entity
		if (!world.getEntitiesByClass(
				LivingEntity.class,
				targetBox.expand(0.1D),
				entity -> entity.isAlive() && entity != guard).isEmpty()) {
			return false;
		}
		return true;
	}

	static Direction horizontalDirectionTo(GuardEntity guard, Vec3d destination) {
		double dx = destination.x - guard.getX();
		double dz = destination.z - guard.getZ();
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx >= 0.0D ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
	}
}

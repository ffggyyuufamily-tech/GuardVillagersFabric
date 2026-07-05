package com.guardvillagers.entity.goal.engineering;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import com.guardvillagers.entity.GuardEntity;

/**
 * Utility safety checks for engineering tasks.
 */
public final class EngineeringSafety {
    private EngineeringSafety() {}

    /**
     * Check types for proximity validation.
     */
    public enum ProximityCheck {
        BRIDGE(1),
        BARRICADE(1),
        LADDER_SUPPORT(0),
        LADDER(0);

        public final int minDistance;

        ProximityCheck(int minDistance) {
            this.minDistance = minDistance;
        }
    }

    /**
     * Returns true if the target position is within the exclusion zone around the guard.
     * The exclusion distance depends on the task type.
     */
    public static boolean isTooCloseToGuard(BlockPos pos, GuardEntity guard, ProximityCheck check) {
        BlockPos guardPos = guard.getBlockPos();
        int minDist = check.minDistance;
        return Math.abs(pos.getX() - guardPos.getX()) <= minDist &&
               Math.abs(pos.getY() - guardPos.getY()) <= minDist &&
               Math.abs(pos.getZ() - guardPos.getZ()) <= minDist;
    }

    /**
     * Checks whether after placing a block at {@code pos} the guard would still have at least one
     * horizontal neighboring walkable block (north, south, east, or west).
     * This simulates the block being placed as a solid obstruction.
     */
    public static boolean hasEscapeRoute(ServerWorld world, BlockPos pos, GuardEntity guard) {
        BlockPos guardPos = guard.getBlockPos();
        Box guardBox = guard.getBoundingBox();

        // Check each horizontal direction around the guard's feet position
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = guardPos.offset(dir);
            // Skip if this is the position we're about to place (simulate it as blocked)
            if (neighbor.equals(pos)) {
                continue;
            }
            // Check if the neighbor is walkable
            if (isWalkable(world, neighbor, guardBox, guard)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a position is walkable (no solid block, no fluid, no entity collision).
     */
    private static boolean isWalkable(ServerWorld world, BlockPos pos, Box guardBox, GuardEntity guard) {
        // Check if the block itself is walkable
        BlockState state = world.getBlockState(pos);
        if (!state.getCollisionShape(world, pos).isEmpty()) {
            return false;
        }
        // Check fluid
        if (!world.getFluidState(pos).isEmpty()) {
            return false;
        }
        // Check entity collisions — pass guard so it excludes itself
        Box posBox = new Box(pos);
        if (!world.isSpaceEmpty(guard, posBox)) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the target position intersects with any entity's bounding box.
     */
    public static boolean hasEntityCollision(ServerWorld world, Box targetBox) {
        for (Entity entity : world.getEntitiesByClass(Entity.class, targetBox.expand(0.5), e -> true)) {
            if (entity.getBoundingBox().intersects(targetBox)) {
                return true;
            }
        }
        return false;
    }
}

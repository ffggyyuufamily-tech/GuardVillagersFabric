package com.guardvillagers.entity.goal;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiIntent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * When a guard is submerged and running low on air, navigate upward to breathe.
 * Suspends combat targeting while surfacing and resumes after air is
 * replenished.
 */
public final class SeekAirGoal extends Goal {
    private static final int AIR_CRITICAL_THRESHOLD = 60;
    private static final int MAX_VERTICAL_SCAN = 24;

    private final GuardEntity guard;
    private final double speed;
    private float oldWaterPenalty;
    private int repathTicks;
    private BlockPos targetAirPos;

    public SeekAirGoal(GuardEntity guard, double speed) {
        this.guard = guard;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        return this.guard.isAiIntent(GuardAiIntent.SEEK_AIR);
    }

    @Override
    public boolean shouldContinue() {
        return this.guard.isAiIntent(GuardAiIntent.SEEK_AIR);
    }

    @Override
    public void start() {
        this.oldWaterPenalty = this.guard.getPathfindingPenalty(PathNodeType.WATER);
        this.guard.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        this.repathTicks = 0;
        this.targetAirPos = null;
    }

    @Override
    public void stop() {
        this.guard.getNavigation().stop();
        this.guard.setPathfindingPenalty(PathNodeType.WATER, this.oldWaterPenalty);
        this.targetAirPos = null;
    }

    @Override
    public void tick() {
        if (--this.repathTicks > 0) {
            return;
        }
        this.repathTicks = 10;

        if (this.targetAirPos == null || !isBreathable(this.guard.getEntityWorld(), this.targetAirPos)) {
            this.targetAirPos = findAirAbove();
        }

        if (this.targetAirPos != null) {
            this.guard.getNavigation().startMovingTo(
                    this.targetAirPos.getX() + 0.5D,
                    this.targetAirPos.getY(),
                    this.targetAirPos.getZ() + 0.5D,
                    this.speed);
        } else {
            // No air found above — swim upward as best we can
            this.guard.setVelocity(
                    this.guard.getVelocity().x,
                    Math.max(this.guard.getVelocity().y, 0.04D),
                    this.guard.getVelocity().z);
        }
    }

    private BlockPos findAirAbove() {
        BlockPos guardPos = this.guard.getBlockPos();
        World world = this.guard.getEntityWorld();
        for (int dy = 1; dy <= MAX_VERTICAL_SCAN; dy++) {
            BlockPos checkPos = guardPos.up(dy);
            if (isBreathable(world, checkPos)) {
                // Return the block below the air so the guard stands there
                return checkPos.down();
            }
        }
        return null;
    }

    private static boolean isBreathable(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isSolidBlock(world, pos) && !state.getFluidState().isStill();
    }
}

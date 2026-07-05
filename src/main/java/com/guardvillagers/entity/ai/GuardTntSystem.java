package com.guardvillagers.entity.ai;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Handles TNT deployment logic for guards.
 * Moved from GuardEngineeringGoal to decouple TNT from building goal control.
 */
public final class GuardTntSystem {

    private GuardTntSystem() {}

    /**
     * Attempt to deploy TNT against the guard's current target.
     * Returns true if TNT was successfully placed, false otherwise.
     */
    public static boolean tryUseTnt(GuardEntity guard, ServerWorld world, GuardVillagersConfig config) {
        LivingEntity target = guard.getTarget();
        if (target == null || !target.isAlive()
                || guard.getHealth() < guard.getMaxHealth() * config.tnt.minHealthRatio) {
            return false;
        }

        if (!guard.canSee(target)) {
            return false;
        }

        double distanceSq = guard.squaredDistanceTo(target);
        double minDistanceSq = config.tnt.minDistance * config.tnt.minDistance;
        double maxDistanceSq = config.tnt.maxDistance * config.tnt.maxDistance;
        if (distanceSq < minDistanceSq || distanceSq > maxDistanceSq) {
            return false;
        }

        BlockPos blastPos = resolveBlastPosition(guard, world, target);
        if (blastPos == null) {
            return false;
        }
        if (!guard.canTargetWithinZone(blastPos) || !isSafeBlastPosition(guard, world, blastPos, config)) {
            return false;
        }

        int clusteredTargets = countClusteredEnemies(guard, world, target, config);
        if (clusteredTargets < config.tnt.minimumEnemyCount) {
            return false;
        }

        // Consume 1 TNT from guard's inventory if logistics is enabled
        if (config.logistics.enabled) {
            if (!guard.getInventory().hasAtLeast(Items.TNT, 1)) {
                return false;
            }
            guard.getInventory().removeItem(Items.TNT, 1);
        }

        TntEntity tnt = new TntEntity(world, blastPos.getX() + 0.5D, blastPos.getY(), blastPos.getZ() + 0.5D, guard);
        tnt.setFuse(config.tnt.fuseTicks);
        boolean spawned = world.spawnEntity(tnt);

        if (!spawned && config.logistics.enabled) {
            guard.getInventory().addItem(Items.TNT, 1);
        }

        if (spawned) {
            GuardDebugLogger.logTNT(guard, "placed TNT",
                "position", blastPos.toShortString(),
                "targetCount", String.valueOf(clusteredTargets),
                "distance", String.format("%.1f", Math.sqrt(distanceSq)),
                "reason", "ENEMY_CLUSTER");

            // Retreat to safety before TNT detonates
            LivingEntity currentTarget = guard.getTarget();
            if (currentTarget != null) {
                // Use HORIZONTAL-only away vector to avoid retreating underground
                double dx = guard.getX() - currentTarget.getX();
                double dz = guard.getZ() - currentTarget.getZ();
                double hLen = Math.sqrt(dx * dx + dz * dz);
                Vec3d away;
                if (hLen < 1e-4) {
                    away = new Vec3d(1, 0, 0);
                } else {
                    away = new Vec3d(dx / hLen, 0, dz / hLen);
                }
                double retreatDist = config.tnt.safeRadius + 3;
                Vec3d dest = guard.getEntityPos().add(away.multiply(retreatDist));
                guard.getNavigation().startMovingTo(
                    dest.x, guard.getY(), dest.z, 1.5D);
                guard.startTntRetreat(config.tnt.fuseTicks);
            }
        }
        return spawned;
    }

    private static int countClusteredEnemies(GuardEntity guard, ServerWorld world, LivingEntity center, GuardVillagersConfig config) {
        Box box = center.getBoundingBox().expand(config.tnt.clusterRadius);
        List<LivingEntity> enemies = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                entity -> entity.isAlive()
                        && !guard.isAlly(entity)
                        && ((config.tnt.countAllHostileMobs && entity instanceof HostileEntity)
                                || entity instanceof RaiderEntity || guard.getTarget() == entity)
                        && entity.squaredDistanceTo(center) <= config.tnt.clusterRadius * config.tnt.clusterRadius);
        return enemies.size();
    }

    /**
     * Finds the best TNT placement position between the guard and the target.
     * If there's a wall in the way, places TNT just outside the wall surface
     * (on the guard's side) rather than at the target's position.
     * Returns null if no valid position can be found.
     */
    private static BlockPos resolveBlastPosition(GuardEntity guard, ServerWorld world, LivingEntity target) {
        Vec3d guardEye = guard.getEyePos();
        Vec3d targetCenter = target.getEyePos();
        Vec3d direction = targetCenter.subtract(guardEye).normalize();
        double totalDist = guardEye.distanceTo(targetCenter);

        double stepSize = 0.5D;
        int steps = (int) Math.ceil(totalDist / stepSize);

        BlockPos lastAirPos = null;

        for (int i = 1; i <= steps; i++) {
            double t = i * stepSize;
            Vec3d point = guardEye.add(direction.multiply(t));
            BlockPos pos = BlockPos.ofFloored(point);
            BlockState state = world.getBlockState(pos);

            if (state.isAir() || !state.blocksMovement()) {
                BlockPos below = pos.down();
                BlockState belowState = world.getBlockState(below);
                if (belowState.isSolid() || !state.isAir()) {
                    lastAirPos = pos;
                } else {
                    lastAirPos = pos;
                }
            } else if (state.blocksMovement()) {
                if (lastAirPos != null) {
                    return lastAirPos;
                }
                return target.getBlockPos();
            }
        }

        return target.getBlockPos();
    }

    private static boolean isSafeBlastPosition(GuardEntity guard, ServerWorld world, BlockPos blastPos, GuardVillagersConfig config) {
        Vec3d center = Vec3d.ofCenter(blastPos);
        double safeRadiusSq = config.tnt.safeRadius * config.tnt.safeRadius;
        double villagerRadiusSq = config.tnt.villagerProtectionRadius * config.tnt.villagerProtectionRadius;
        double friendlyRadiusSq = config.tnt.friendlyProtectionRadius * config.tnt.friendlyProtectionRadius;

        List<LivingEntity> nearby = world.getEntitiesByClass(
                LivingEntity.class,
                new Box(blastPos).expand(Math.max(config.tnt.safeRadius, config.tnt.villagerProtectionRadius)),
                LivingEntity::isAlive);
        for (LivingEntity entity : nearby) {
            double distanceSq = entity.squaredDistanceTo(center);
            if (entity instanceof VillagerEntity && distanceSq <= villagerRadiusSq) {
                return false;
            }
            if ((guard.isAlly(entity) || entity instanceof PlayerEntity) && distanceSq <= friendlyRadiusSq) {
                return false;
            }
            if (distanceSq <= safeRadiusSq && guard.isAlly(entity)) {
                return false;
            }
        }

        double guardDistSq = guard.squaredDistanceTo(center);
        double guardSafeRadiusSq = config.tnt.safeRadius * config.tnt.safeRadius;
        if (guardDistSq <= guardSafeRadiusSq) {
            return false;
        }

        return true;
    }
}

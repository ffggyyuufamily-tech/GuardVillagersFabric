package com.guardvillagers.entity.goal.engineering;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.goal.engineering.TerrainClassifier.TerrainType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class TerrainAnalyzer {
    private static final int MAX_SCAN_DOWN = 10;
    private static final int PLATFORM_SEARCH_RADIUS = 5;
    private static final double STANDING_OFFSET = 0.5;
    
    private static final Map<Long, CachedResult> resultCache = new HashMap<>();
    private static final int CACHE_TTL_TICKS = 5;
    
    private static class CachedResult {
        Vec3d position;
        int tickTime;
        
        CachedResult(Vec3d position, int tickTime) {
            this.position = position;
            this.tickTime = tickTime;
        }
        
        boolean isExpired(int currentTick) {
            return currentTick - tickTime > CACHE_TTL_TICKS;
        }
    }
    
    public static Vec3d resolveReachableDestination(
            GuardEntity guard,
            LivingEntity target,
            ServerWorld world,
            GuardVillagersConfig config,
            TerrainType terrainType) {
        
        if (target == null || world == null) {
            return guard.getEntityPos();
        }
        
        long cacheKey = getCacheKey(guard, target);
        CachedResult cached = resultCache.get(cacheKey);
        if (cached != null && !cached.isExpired((int) world.getTime())) {
            return cached.position;
        }
        
        Vec3d result;
        Vec3d targetPos = target.getEntityPos();
        
        switch (terrainType) {
            case GAP:
                result = resolveGapTerrain(targetPos);
                break;
            
            case CLIFF:
                result = resolveCliffTerrain(targetPos, world);
                break;
            
            case PILLAR:
                result = resolvePillarTerrain(targetPos, world);
                break;
            
            case ELEVATED_PLATFORM:
                result = resolveElevatedPlatform(targetPos, world);
                break;
            
            case NORMAL_PATH:
            default:
                result = resolveNormalPath(targetPos);
                break;
        }
        
        if (result == null) {
            result = targetPos;
        }
        
        resultCache.put(cacheKey, new CachedResult(result, (int) world.getTime()));
        cleanupExpiredCache(world.getTime());
        
        return result;
    }
    
    private static Vec3d resolveGapTerrain(Vec3d targetPos) {
        return targetPos;
    }
    
    private static Vec3d resolveCliffTerrain(Vec3d targetPos, ServerWorld world) {
        BlockPos groundLevel = findGroundLevel(targetPos, world, MAX_SCAN_DOWN);
        
        if (groundLevel != null) {
            return Vec3d.ofBottomCenter(groundLevel.up());
        }
        
        return targetPos;
    }
    
    private static Vec3d resolvePillarTerrain(Vec3d targetPos, ServerWorld world) {
        BlockPos pillarBase = findPillarBase(targetPos, world);
        
        if (pillarBase != null) {
            Vec3d basePosition = Vec3d.ofBottomCenter(pillarBase);
            return findNearestGroundPoint(basePosition, world, STANDING_OFFSET);
        }
        
        return targetPos;
    }
    
    private static Vec3d resolveElevatedPlatform(Vec3d targetPos, ServerWorld world) {
        BlockPos platformEdge = findPlatformEdge(targetPos, world, PLATFORM_SEARCH_RADIUS);
        
        if (platformEdge != null) {
            return Vec3d.ofBottomCenter(platformEdge.up());
        }
        
        return targetPos;
    }
    
    private static Vec3d resolveNormalPath(Vec3d targetPos) {
        return targetPos;
    }
    
    private static BlockPos findGroundLevel(Vec3d startPos, ServerWorld world, int maxScanDown) {
        BlockPos startBlock = BlockPos.ofFloored(startPos);
        
        for (int i = 0; i < maxScanDown; i++) {
            BlockPos checkPos = startBlock.down(i);
            BlockState state = world.getBlockState(checkPos);
            
            if (!BridgeBuilder.isSpaceOpen(world, checkPos)) {
                return checkPos;
            }
        }
        
        return null;
    }
    
    private static BlockPos findPillarBase(Vec3d targetPos, ServerWorld world) {
        BlockPos startBlock = BlockPos.ofFloored(targetPos);
        boolean foundSolid = false;
        
        for (int i = 0; i < MAX_SCAN_DOWN * 2; i++) {
            BlockPos checkPos = startBlock.down(i);
            BlockState state = world.getBlockState(checkPos);
            
            if (!BridgeBuilder.isSpaceOpen(world, checkPos) && !foundSolid) {
                foundSolid = true;
            } else if (BridgeBuilder.isSpaceOpen(world, checkPos) && foundSolid) {
                return checkPos.up();
            }
        }
        
        return startBlock;
    }
    
    private static BlockPos findPlatformEdge(Vec3d targetPos, ServerWorld world, int searchRadius) {
        BlockPos centerBlock = BlockPos.ofFloored(targetPos);
        int scanRadius = Math.min(searchRadius, 8);
        
        for (int radius = 1; radius <= scanRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    
                    BlockPos checkPos = centerBlock.add(x, 0, z);
                    BlockPos groundLevel = findGroundLevel(Vec3d.ofBottomCenter(checkPos), world, MAX_SCAN_DOWN);
                    
                    if (groundLevel != null) {
                        return groundLevel;
                    }
                }
            }
        }
        
        return null;
    }
    
    private static Vec3d findNearestGroundPoint(Vec3d targetPos, ServerWorld world, double searchOffset) {
        BlockPos centerBlock = BlockPos.ofFloored(targetPos);
        int scanRadius = 3;
        double closestDistance = Double.MAX_VALUE;
        BlockPos closestGround = null;
        
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                BlockPos checkPos = centerBlock.add(x, 0, z);
                BlockPos groundLevel = findGroundLevel(Vec3d.ofBottomCenter(checkPos), world, MAX_SCAN_DOWN);
                
                if (groundLevel != null) {
                    double distance = targetPos.squaredDistanceTo(Vec3d.ofBottomCenter(groundLevel));
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestGround = groundLevel;
                    }
                }
            }
        }
        
        if (closestGround != null) {
            Vec3d groundPos = Vec3d.ofBottomCenter(closestGround.up());
            
            int dx = closestGround.getX() - centerBlock.getX();
            int dz = closestGround.getZ() - centerBlock.getZ();
            double angle = Math.atan2(dz, dx);
            
            double offsetX = Math.cos(angle) * searchOffset;
            double offsetZ = Math.sin(angle) * searchOffset;
            
            return groundPos.add(offsetX, 0, offsetZ);
        }
        
        return targetPos;
    }
    
    private static long getCacheKey(GuardEntity guard, LivingEntity target) {
        return ((long) guard.getId() << 32) | (target.getId() & 0xFFFFFFFFL);
    }
    
    private static void cleanupExpiredCache(long currentTick) {
        if (resultCache.size() > 1000) {
            resultCache.values().removeIf(result -> result.isExpired((int) currentTick));
        }
    }
}

package com.guardvillagers.navigation;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SquadRouteCache {
    /**
     * Accessed from pathfinder callbacks on each world's tick thread, so multiple
     * loaded dimensions can write here concurrently. ConcurrentHashMap prevents
     * the HashMap corruption seen under that pattern while keeping reads lock-free.
     */
    private static final Map<CacheKey, CachedRoute> SQUAD_ROUTES = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_AGE_TICKS = 40; // 2 seconds
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final double MAX_TARGET_DRIFT_SQR = 0.0; // exact static target only
    private static final double MAX_ORIGIN_DRIFT_SQR = 144.0; // 12 blocks

    /**
     * Tries to find a valid cached route for a squad/owner given a target position.
     * Starts from the guard's current position and ensures validity.
     * Returns a DEFENSIVE COPY so consumers don't mutate each other's Path state.
     */
    public static Path getSquadRoute(UUID groupId, BlockPos origin, BlockPos target, long currentTick) {
        if (groupId == null || target == null) {
            return null;
        }

        CacheKey key = new CacheKey(groupId, quantize(target));
        CachedRoute entry = SQUAD_ROUTES.get(key);

        if (entry == null) {
            return null;
        }

        // Validity checks
        if (currentTick - entry.computeTick > MAX_CACHE_AGE_TICKS) {
            SQUAD_ROUTES.remove(key);
            return null;
        }

        if (entry.targetPos.getSquaredDistance(target) > MAX_TARGET_DRIFT_SQR) {
            SQUAD_ROUTES.remove(key);
            return null;
        }

        if (origin.getSquaredDistance(entry.originPos) > MAX_ORIGIN_DRIFT_SQR) {
            // Not invalidated globally, but invalid for this specific guard's distance
            return null;
        }

        // Defensive copy to prevent concurrent index mutation
        return copyPath(entry.path);
    }

    /**
     * Caches a successfully computed route for a squad.
     */
    public static void cacheSquadRoute(UUID groupId, BlockPos origin, BlockPos target, Path path, long currentTick) {
        if (groupId == null || target == null || path == null) {
            return;
        }

        CacheKey key = new CacheKey(groupId, quantize(target));
        // Path is only read from cache via copyPath() on retrieval, so we can store
        // the original directly and avoid a redundant copy on insert.
        SQUAD_ROUTES.put(key, new CachedRoute(path, currentTick, origin, target));

        // Bounded eviction: if the cache has grown past the soft cap (which can
        // happen on long-running servers with many distinct squad/target pairs),
        // drop any entry older than MAX_CACHE_AGE_TICKS. This prevents an
        // unbounded retention loop without adding a dedicated tick subscriber.
        if (SQUAD_ROUTES.size() > MAX_CACHE_ENTRIES) {
            SQUAD_ROUTES.values().removeIf(route -> currentTick - route.computeTick > MAX_CACHE_AGE_TICKS);
        }
    }

    public static void invalidateSquadRoute(UUID groupId, BlockPos target) {
        if (groupId == null || target == null) {
            return;
        }
        SQUAD_ROUTES.remove(new CacheKey(groupId, quantize(target)));
    }

    /**
     * Creates a safe defensive copy of a path. PathNode instances are read-only
     * structurally so sharing them is safe; only the Path wrapper (with its
     * mutable currentNodeIndex) needs to be fresh.
     */
    private static Path copyPath(Path original) {
        if (original == null) {
            return null;
        }

        int length = original.getLength();
        List<PathNode> nodes = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            nodes.add(original.getNode(i));
        }
        return new Path(nodes, original.getTarget(), original.reachesTarget());
    }

    private static BlockPos quantize(BlockPos pos) {
        return pos.toImmutable();
    }

    private record CacheKey(UUID groupId, BlockPos quantizedTarget) {
    }

    private record CachedRoute(Path path, long computeTick, BlockPos originPos, BlockPos targetPos) {
    }
}

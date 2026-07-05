package com.guardvillagers;

import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Centralized debug logging utility for the Guard Villagers mod.
 * All logging respects GuardDebugConfig flags and formats output consistently.
 * 
 * Usage:
 *   GuardDebugLogger.logAI(guard, "decided RETREAT", "NO_AMMO", "health", String.valueOf(guard.getHealth()));
 *   GuardDebugLogger.logPath(guard, "path generation failed", "reason", "unreachable destination");
 */
public final class GuardDebugLogger {
    private GuardDebugLogger() {}

    // ==================== AI Decision Logging ====================
    public static void logAI(GuardEntity guard, String decision, Object... details) {
        if (!GuardDebugConfig.DEBUG_AI) return;
        logMessage("[AI]", guard, decision, details);
    }

    // ==================== Combat Logging ====================
    public static void logCombat(GuardEntity guard, String action, Object... details) {
        if (!GuardDebugConfig.DEBUG_COMBAT) return;
        logMessage("[COMBAT]", guard, action, details);
    }

    public static void logTargetAcquisition(GuardEntity guard, String action, Object... details) {
        if (!GuardDebugConfig.DEBUG_TARGET_ACQUISITION) return;
        logMessage("[COMBAT:TARGET]", guard, action, details);
    }

    // ==================== Pathfinding Logging ====================
    public static void logPath(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_PATH) return;
        logMessage("[PATH]", guard, event, details);
    }

    public static void logNavFailure(GuardEntity guard, String failure, Object... details) {
        if (!GuardDebugConfig.DEBUG_NAV_FAILURES) return;
        logMessage("[PATH:FAIL]", guard, failure, details);
    }

    public static void logRouteCache(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_CACHE) return;
        logMessage("[CACHE]", guard, event, details);
    }

    // ==================== Building & Engineering Logging ====================
    public static void logBuild(GuardEntity guard, String action, Object... details) {
        if (!GuardDebugConfig.DEBUG_BUILD) return;
        logMessage("[BUILD]", guard, action, details);
    }

    public static void logBridge(GuardEntity guard, String action, Object... details) {
        if (!GuardDebugConfig.DEBUG_BRIDGE) return;
        logMessage("[BUILD:BRIDGE]", guard, action, details);
    }

    public static void logWall(GuardEntity guard, String action, Object... details) {
        if (!GuardDebugConfig.DEBUG_WALL) return;
        logMessage("[BUILD:WALL]", guard, action, details);
    }

    public static void logEngineeringTask(GuardEntity guard, String state, Object... details) {
        if (!GuardDebugConfig.DEBUG_ENGINEERING_TASK) return;
        logMessage("[BUILD:TASK]", guard, state, details);
    }

    // ==================== TNT Logging ====================
    public static void logTNT(GuardEntity guard, String action, Object... details) {
        if (!GuardDebugConfig.DEBUG_TNT) return;
        logMessage("[TNT]", guard, action, details);
    }

    public static void logTNTDetonation(GuardEntity placer, String result, Object... details) {
        if (!GuardDebugConfig.DEBUG_TNT_DETONATION) return;
        logMessage("[TNT:DETONATE]", placer, result, details);
    }

    // ==================== Squad & Formation Logging ====================
    public static void logSquad(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_SQUAD) return;
        logMessage("[SQUAD]", guard, event, details);
    }

    public static void logFormation(GuardEntity guard, String change, Object... details) {
        if (!GuardDebugConfig.DEBUG_FORMATION) return;
        logMessage("[FORMATION]", guard, change, details);
    }

    // ==================== Reputation Logging ====================
    public static void logReputation(String context, Object... details) {
        if (!GuardDebugConfig.DEBUG_REPUTATION) return;
        StringBuilder msg = new StringBuilder(GuardDebugConfig.LOG_PREFIX + " [REPUTATION] " + context);
        for (int i = 0; i < details.length; i += 2) {
            if (i + 1 < details.length) {
                msg.append(" | ").append(details[i]).append("=").append(details[i + 1]);
            }
        }
        GuardVillagersMod.LOGGER.info(msg.toString());
    }

    public static void logMorale(GuardEntity guard, String change, Object... details) {
        if (!GuardDebugConfig.DEBUG_MORALE) return;
        logMessage("[MORALE]", guard, change, details);
    }

    // ==================== Spawn/Despawn/Death Logging ====================
    public static void logSpawn(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_SPAWN) return;
        logMessage("[SPAWN]", guard, event, details);
    }

    public static void logDespawn(GuardEntity guard, String reason, Object... details) {
        if (!GuardDebugConfig.DEBUG_DESPAWN) return;
        logMessage("[DESPAWN]", guard, reason, details);
    }

    public static void logDeath(GuardEntity guard, String cause, Object... details) {
        if (!GuardDebugConfig.DEBUG_SPAWN) return;
        logMessage("[DEATH]", guard, cause, details);
    }

    // ==================== Patrol & Home Logging ====================
    public static void logPatrol(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_PATROL) return;
        logMessage("[PATROL]", guard, event, details);
    }

    public static void logHomeTether(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_HOME_TETHER) return;
        logMessage("[HOME]", guard, event, details);
    }

    public static void logZoneAssignment(GuardEntity guard, String event, Object... details) {
        if (!GuardDebugConfig.DEBUG_ZONE_ASSIGNMENT) return;
        logMessage("[ZONE]", guard, event, details);
    }

    // ==================== Generic Debug Logging ====================
    public static void logDebug(GuardEntity guard, String message, Object... details) {
        if (!GuardDebugConfig.DEBUG_DEBUG) return;
        logMessage("[DEBUG]", guard, message, details);
    }

    // ==================== Core Logging Implementation ====================
    private static void logMessage(String category, GuardEntity guard, String message, Object[] details) {
        StringBuilder sb = new StringBuilder();
        sb.append(GuardDebugConfig.LOG_PREFIX);
        sb.append(" ").append(category);
        
        if (GuardDebugConfig.INCLUDE_TIMESTAMP) {
            sb.append(" [T=").append(System.currentTimeMillis() % 10000).append("]");
        }
        
        if (GuardDebugConfig.INCLUDE_GUARD_UUID && guard != null) {
            String uuid = guard.getUuid().toString();
            sb.append(" [Guard=").append(uuid.substring(0, Math.min(8, uuid.length()))).append("]");
        }
        
        if (GuardDebugConfig.INCLUDE_TICK && guard != null) {
            sb.append(" [Tick=").append(guard.age).append("]");
        }
        
        if (guard != null) {
            sb.append(" [Pos=").append(formatPos(guard.getBlockPos())).append("]");
        }
        
        sb.append(" ").append(message);
        
        // Format details as key=value pairs
        for (int i = 0; i < details.length; i += 2) {
            if (i + 1 < details.length) {
                sb.append(" | ").append(details[i]).append("=").append(formatDetail(details[i + 1]));
            }
        }
        
        GuardVillagersMod.LOGGER.info(sb.toString());
    }

    // ==================== Formatting Utilities ====================
    private static String formatPos(BlockPos pos) {
        if (pos == null) return "null";
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatVec3d(Vec3d vec) {
        if (vec == null) return "null";
        return String.format("%.2f,%.2f,%.2f", vec.x, vec.y, vec.z);
    }

    private static String formatEntity(LivingEntity entity) {
        if (entity == null) return "null";
        return entity.getName().getString() + "(" + entity.getUuid().toString().substring(0, 8) + ")";
    }

    private static String formatDetail(Object detail) {
        if (detail == null) {
            return "null";
        }
        if (detail instanceof BlockPos pos) {
            return formatPos(pos);
        }
        if (detail instanceof Vec3d vec) {
            return formatVec3d(vec);
        }
        if (detail instanceof LivingEntity entity) {
            return formatEntity(entity);
        }
        if (detail instanceof Double d) {
            return String.format("%.2f", d);
        }
        if (detail instanceof Float f) {
            return String.format("%.2f", f);
        }
        return detail.toString();
    }

    // ==================== Helper Methods ====================
    public static String shortUUID(GuardEntity guard) {
        if (guard == null) return "null";
        return guard.getUuid().toString().substring(0, 8);
    }

    public static void logBlockPlacement(GuardEntity guard, BlockPos pos, String blockType, 
                                        String reason, LivingEntity targetEnemy, String aiState) {
        if (!GuardDebugConfig.DEBUG_BUILD) return;
        logMessage("[BUILD]", guard, "placed block", new Object[]{
            "block", blockType,
            "pos", pos,
            "reason", reason,
            "target", targetEnemy != null ? formatEntity(targetEnemy) : "none",
            "state", aiState,
            "role", guard.getRole().name()
        });
    }

    public static void logTNTPlacement(GuardEntity placer, BlockPos placementPos, BlockPos targetPos,
                                       LivingEntity targetEntity, double distance, String expectedPurpose) {
        if (!GuardDebugConfig.DEBUG_TNT) return;
        logMessage("[TNT]", placer, "placed TNT", new Object[]{
            "placement", placementPos,
            "target_pos", targetPos,
            "target_entity", targetEntity != null ? formatEntity(targetEntity) : "none",
            "distance", String.format("%.1f", distance),
            "purpose", expectedPurpose
        });
    }

    public static void logPathGeneration(GuardEntity guard, BlockPos start, BlockPos destination, 
                                        String status, Object... details) {
        if (!GuardDebugConfig.DEBUG_PATH) return;
        Object[] fullDetails = new Object[details.length + 4];
        fullDetails[0] = "start";
        fullDetails[1] = start;
        fullDetails[2] = "destination";
        fullDetails[3] = destination;
        System.arraycopy(details, 0, fullDetails, 4, details.length);
        logMessage("[PATH]", guard, status, fullDetails);
    }
}

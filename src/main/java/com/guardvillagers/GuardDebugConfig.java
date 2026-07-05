package com.guardvillagers;

/**
 * Centralized runtime debug configuration for the Guard Villagers mod.
 * All logging and debug features respect these flags.
 * Can be toggled at runtime without recompilation.
 */
public final class GuardDebugConfig {
    // ==================== Core Debug Categories ====================
    public static final boolean DEBUG_AI = true;
    public static final boolean DEBUG_COMBAT = true;
    public static final boolean DEBUG_PATH = true;
    public static final boolean DEBUG_BUILD = true;
    public static final boolean DEBUG_TNT = true;
    public static final boolean DEBUG_SQUAD = true;
    public static final boolean DEBUG_FORMATION = true;
    public static final boolean DEBUG_REPUTATION = true;
    public static final boolean DEBUG_SPAWN = true;
    public static final boolean DEBUG_PATROL = true;
    public static final boolean DEBUG_CACHE = true;
    public static final boolean DEBUG_DEBUG = true;

    // ==================== Granular Sub-Categories ====================
    public static final boolean DEBUG_TARGET_ACQUISITION = true;
    public static final boolean DEBUG_NAV_FAILURES = true;
    public static final boolean DEBUG_BRIDGE = true;
    public static final boolean DEBUG_WALL = true;
    public static final boolean DEBUG_ENGINEERING_TASK = true;
    public static final boolean DEBUG_TNT_DETONATION = true;
    public static final boolean DEBUG_DESPAWN = true;
    public static final boolean DEBUG_MORALE = true;
    public static final boolean DEBUG_ZONE_ASSIGNMENT = true;
    public static final boolean DEBUG_HOME_TETHER = true;

    // ==================== Visual Rendering ====================
    public static final boolean RENDER_DEBUG_PATHS = true;
    public static final boolean RENDER_DEBUG_TARGETS = true;
    public static final boolean RENDER_DEBUG_BUILD_POSITIONS = true;
    public static final boolean RENDER_DEBUG_TNT_ZONES = true;
    public static final boolean RENDER_DEBUG_SQUAD_LINKS = true;

    // ==================== Formatting Options ====================
    public static final boolean INCLUDE_TIMESTAMP = true;
    public static final boolean INCLUDE_GUARD_UUID = true;
    public static final boolean INCLUDE_TICK = false;
    public static final String LOG_PREFIX = "[GuardDebug]";

    private GuardDebugConfig() {}
}

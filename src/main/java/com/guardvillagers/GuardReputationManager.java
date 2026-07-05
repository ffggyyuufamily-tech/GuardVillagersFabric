package com.guardvillagers;

import com.guardvillagers.data.GuardReputationState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardReputationManager {
	private static final double TRUST_THRESHOLD = 0.50D;
	private static final double HOSTILE_THRESHOLD = 0.50D;
	private static final int LEGACY_RANGE_SPAN = 400;
	private static final int TRADE_COOLDOWN_TICKS = 200;
	private static final int COOLDOWN_RETENTION_TICKS = 20 * 60 * 10;
	private static final int COOLDOWN_CLEANUP_INTERVAL_TICKS = 20 * 60;
	public static final int DECAY_INTERVAL_TICKS = 20 * 36;
	public static final double DECAY_STEP = 0.01D;
	private static final double TRADE_DELTA = legacyDeltaToNormalized(1);
	private static final double VILLAGER_HARM_DELTA = legacyDeltaToNormalized(-8);
	private static final double GUARD_HARM_DELTA = legacyDeltaToNormalized(-6);
	private static final double RAID_DEFENSE_DELTA = legacyDeltaToNormalized(4);
	private static final double HOSTILE_KILL_DELTA = legacyDeltaToNormalized(2);
	private static final double RAIDER_KILL_DELTA = legacyDeltaToNormalized(4);
	private static final Map<UUID, Long> TRADE_REPUTATION_COOLDOWN = new ConcurrentHashMap<>();
	private static volatile long lastCooldownCleanupTick = Long.MIN_VALUE;

	private GuardReputationManager() {
	}

	public static GuardReputationState getState(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(GuardReputationState.TYPE);
	}

	public static double getEffectiveReputation(ServerWorld world, UUID playerUuid, BlockPos reference, int radius) {
		return getState(world.getServer()).get(playerUuid);
	}

	public static double getEffectiveReputation(ServerPlayerEntity player) {
		return getEffectiveReputation(player.getEntityWorld(), player.getUuid(), player.getBlockPos(), 64);
	}

	public static boolean isTrustedByGuards(ServerWorld world, UUID playerUuid, BlockPos reference) {
		return getEffectiveReputation(world, playerUuid, reference, 64) >= TRUST_THRESHOLD;
	}

	public static boolean shouldGuardsTurnHostile(ServerWorld world, UUID playerUuid, BlockPos reference) {
		return getEffectiveReputation(world, playerUuid, reference, 64) < HOSTILE_THRESHOLD;
	}

	public static double getStoredReputation(ServerWorld world, UUID playerUuid) {
		return getState(world.getServer()).get(playerUuid);
	}

	public static void setReputation(ServerWorld world, UUID playerUuid, double value) {
		getState(world.getServer()).set(playerUuid, value);
	}

	public static void resetReputation(ServerWorld world, UUID playerUuid) {
		setReputation(world, playerUuid, 0.0D);
	}

	public static double applyReputationDelta(ServerWorld world, UUID playerUuid, int legacyDelta) {
		return applyNormalizedReputationDelta(world, playerUuid, legacyDeltaToNormalized(legacyDelta));
	}

	public static double applyNormalizedReputationDelta(ServerWorld world, UUID playerUuid, double delta) {
		double newValue = getState(world.getServer()).add(playerUuid, delta);
		GuardDebugLogger.logReputation("reputation changed",
			"player", playerUuid.toString().substring(0, 8),
			"delta", String.format("%.3f", delta),
			"newValue", String.format("%.3f", newValue));
		return newValue;
	}

	public static int getAdjustedGuardCost(ServerPlayerEntity player, int baseCost) {
		return Math.max(1, baseCost);
	}

	public static void recordTradeInteraction(ServerPlayerEntity player, VillagerEntity villager) {
		long now = player.getEntityWorld().getTime();
		cleanupTradeCooldown(now);
		UUID key = mix(player.getUuid(), villager.getUuid());
		long previous = TRADE_REPUTATION_COOLDOWN.getOrDefault(key, Long.MIN_VALUE);
		if (now - previous < TRADE_COOLDOWN_TICKS) {
			return;
		}

		TRADE_REPUTATION_COOLDOWN.put(key, now);
		applyNormalizedReputationDelta(player.getEntityWorld(), player.getUuid(), TRADE_DELTA);
	}

	public static void recordVillagerHarm(ServerWorld world, UUID playerUuid) {
		applyNormalizedReputationDelta(world, playerUuid, VILLAGER_HARM_DELTA);
	}

	public static void recordGuardHarm(ServerWorld world, UUID playerUuid) {
		applyNormalizedReputationDelta(world, playerUuid, GUARD_HARM_DELTA);
	}

	public static void recordRaidDefense(ServerWorld world, UUID playerUuid) {
		applyNormalizedReputationDelta(world, playerUuid, RAID_DEFENSE_DELTA);
	}

	public static void recordHostileKill(ServerWorld world, UUID playerUuid, LivingEntity target) {
		if (target == null) {
			return;
		}
		double delta = target instanceof RaiderEntity ? RAIDER_KILL_DELTA : HOSTILE_KILL_DELTA;
		applyNormalizedReputationDelta(world, playerUuid, delta);
	}

	public static void tickDecay(ServerWorld world) {
		if (world.getTime() % DECAY_INTERVAL_TICKS != 0) {
			return;
		}
		getState(world.getServer()).decayAll(DECAY_STEP);
	}

	private static void cleanupTradeCooldown(long worldTime) {
		if (lastCooldownCleanupTick != Long.MIN_VALUE && worldTime - lastCooldownCleanupTick < COOLDOWN_CLEANUP_INTERVAL_TICKS) {
			return;
		}
		lastCooldownCleanupTick = worldTime;
		long cutoff = worldTime - COOLDOWN_RETENTION_TICKS;
		TRADE_REPUTATION_COOLDOWN.entrySet().removeIf(entry -> entry.getValue() < cutoff);
	}

	private static UUID mix(UUID a, UUID b) {
		long msb = a.getMostSignificantBits() ^ b.getMostSignificantBits();
		long lsb = a.getLeastSignificantBits() ^ b.getLeastSignificantBits();
		return new UUID(msb, lsb);
	}

	private static double legacyDeltaToNormalized(int legacyDelta) {
		return legacyDelta / (double) LEGACY_RANGE_SPAN;
	}
}

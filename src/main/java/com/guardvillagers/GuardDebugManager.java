package com.guardvillagers;

import com.guardvillagers.data.GuardDebugState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.UUID;

public final class GuardDebugManager {
	private GuardDebugManager() {
	}

	public static GuardDebugState getState(MinecraftServer server) {
		MinecraftServer checkedServer = Objects.requireNonNull(server, "server");
		if (checkedServer.getOverworld() == null) {
			throw new IllegalStateException("Cannot access guard debug state before overworld is available");
		}
		return checkedServer.getOverworld().getPersistentStateManager().getOrCreate(GuardDebugState.TYPE);
	}

	public static boolean isEnabled(MinecraftServer server, UUID playerId) {
		return getState(server).isEnabled(playerId);
	}

	public static void setEnabled(MinecraftServer server, UUID playerId, boolean enabled) {
		getState(server).setEnabled(playerId, enabled);
	}

	public static void toggle(MinecraftServer server, UUID playerId) {
		getState(server).toggle(playerId);
	}

	public static void setRange(MinecraftServer server, UUID playerId, double range) {
		getState(server).setRange(playerId, range);
	}

	public static double getRange(MinecraftServer server, UUID playerId) {
		return getState(server).getRange(playerId);
	}

	public static int getRangeCapBlocks(ServerPlayerEntity player) {
		int viewDistanceChunks = Math.max(2, player.getViewDistance());
		return Math.max(1, (viewDistanceChunks * 16) / 2);
	}

	public static double getEffectiveRange(ServerPlayerEntity player) {
		MinecraftServer server = player.getCommandSource().getServer();
		if (server == null) {
			return getRangeCapBlocks(player);
		}
		double configuredRange = getRange(server, player.getUuid());
		int cap = getRangeCapBlocks(player);
		if (configuredRange < 0.0D) {
			return cap;
		}
		return Math.max(1.0D, Math.min(configuredRange, cap));
	}
}

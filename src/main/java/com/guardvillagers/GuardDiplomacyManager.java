package com.guardvillagers;

import com.guardvillagers.data.GuardDiplomacyState;
import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.UUID;

public final class GuardDiplomacyManager {
	private GuardDiplomacyManager() {
	}

	public static GuardDiplomacyState getState(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(GuardDiplomacyState.TYPE);
	}

	public static boolean toggleWhitelist(MinecraftServer server, UUID owner, UUID target) {
		return getState(server).toggleWhitelist(owner, target);
	}

	public static boolean toggleBlacklist(MinecraftServer server, UUID owner, UUID target) {
		return getState(server).toggleBlacklist(owner, target);
	}

	public static boolean isBlacklisted(MinecraftServer server, UUID owner, UUID target) {
		return getState(server).isBlacklisted(owner, target);
	}

	public static boolean isWhitelisted(MinecraftServer server, UUID owner, UUID target) {
		return getState(server).isWhitelisted(owner, target);
	}

	public static Set<UUID> getBlacklist(MinecraftServer server, UUID owner) {
		return getState(server).getBlacklist(owner);
	}

	public static Set<UUID> getWhitelist(MinecraftServer server, UUID owner) {
		return getState(server).getWhitelist(owner);
	}

	public static boolean canInteract(MinecraftServer server, UUID owner, UUID target) {
		GuardDiplomacyState state = getState(server);
		if (state.isBlacklisted(owner, target)) {
			return false;
		}
		Set<UUID> whitelist = state.getWhitelist(owner);
		if (!whitelist.isEmpty()) {
			return whitelist.contains(target);
		}
		return true;
	}
}

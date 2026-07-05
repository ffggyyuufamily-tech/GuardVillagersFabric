package com.guardvillagers;

import com.guardvillagers.data.GuardTacticsState;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class GuardTacticsManager {
	private GuardTacticsManager() {
	}

	public static GuardTacticsState getState(MinecraftServer server) {
		MinecraftServer checkedServer = Objects.requireNonNull(server, "server");
		if (checkedServer.getOverworld() == null) {
			throw new IllegalStateException("Cannot access guard tactics state before overworld is available");
		}
		return checkedServer.getOverworld().getPersistentStateManager().getOrCreate(GuardTacticsState.TYPE);
	}
}

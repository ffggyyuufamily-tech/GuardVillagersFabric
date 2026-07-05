package com.guardvillagers.client;

import com.guardvillagers.network.GuardDebugDataPayload;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientGuardDebugData {
	private static final Map<Integer, GuardDebugSnapshot> SNAPSHOTS = new HashMap<>();

	private ClientGuardDebugData() {
	}

	public static void applyPayload(GuardDebugDataPayload payload) {
		for (GuardDebugDataPayload.GuardDebugEntry entry : payload.entries()) {
			if (entry.pathNodes().isEmpty() && entry.targetEntityId() < 0) {
				SNAPSHOTS.remove(entry.entityId());
				continue;
			}
			SNAPSHOTS.put(entry.entityId(), new GuardDebugSnapshot(entry.pathNodes(), entry.currentPathIndex(), entry.targetEntityId()));
		}
	}

	public static GuardDebugSnapshot get(int entityId) {
		return SNAPSHOTS.get(entityId);
	}

	public static void pruneMissing(ClientWorld world) {
		if (world == null) {
			SNAPSHOTS.clear();
			return;
		}
		SNAPSHOTS.keySet().removeIf(entityId -> world.getEntityById(entityId) == null);
	}

	public static void clear() {
		SNAPSHOTS.clear();
	}

	public record GuardDebugSnapshot(List<BlockPos> pathNodes, int currentPathIndex, int targetEntityId) {
		public GuardDebugSnapshot {
			pathNodes = List.copyOf(pathNodes);
		}
	}
}

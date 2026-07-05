package com.guardvillagers;

import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardOwnershipIndex {
	private static final int CLEANUP_INTERVAL_TICKS = 200;
	private static volatile long lastCleanupTick = Long.MIN_VALUE;

	private static final ConcurrentHashMap<UUID, Set<UUID>> OWNER_TO_GUARDS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, UUID> GUARD_TO_OWNER = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, WeakReference<GuardEntity>> GUARD_REFERENCES = new ConcurrentHashMap<>();

	private GuardOwnershipIndex() {
	}

	public static void track(GuardEntity guard) {
		if (guard == null) {
			return;
		}

		UUID guardId = guard.getUuid();
		if (guard.isRemoved()) {
			removeGuard(guardId);
			return;
		}

		UUID ownerUuid = guard.getOwnerUuid();
		if (ownerUuid == null) {
			removeGuard(guardId);
			return;
		}

		UUID previousOwner = GUARD_TO_OWNER.put(guardId, ownerUuid);
		if (previousOwner != null && !previousOwner.equals(ownerUuid)) {
			removeFromOwner(previousOwner, guardId);
		}

		OWNER_TO_GUARDS.computeIfAbsent(ownerUuid, ignored -> ConcurrentHashMap.newKeySet()).add(guardId);
		GUARD_REFERENCES.put(guardId, new WeakReference<>(guard));
		maybeCleanup(guard.getEntityWorld().getTime());
	}

	public static void untrack(GuardEntity guard) {
		if (guard == null) {
			return;
		}
		removeGuard(guard.getUuid());
	}

	public static List<GuardEntity> getOwnedGuards(MinecraftServer server, UUID ownerUuid) {
		if (server == null || ownerUuid == null) {
			return List.of();
		}

		return resolveIndexedGuards(server, ownerUuid);
	}

	public static int countOwnedGuards(MinecraftServer server, UUID ownerUuid) {
		if (server == null || ownerUuid == null) {
			return 0;
		}

		Set<UUID> indexedIds = OWNER_TO_GUARDS.get(ownerUuid);
		return indexedIds == null ? 0 : indexedIds.size();
	}

	private static List<GuardEntity> resolveIndexedGuards(MinecraftServer server, UUID ownerUuid) {
		Set<UUID> indexedIds = OWNER_TO_GUARDS.get(ownerUuid);
		if (indexedIds == null || indexedIds.isEmpty()) {
			return List.of();
		}

		List<GuardEntity> resolved = new ArrayList<>(indexedIds.size());
		List<UUID> staleIds = new ArrayList<>();
		for (UUID guardId : List.copyOf(indexedIds)) {
			GuardEntity guard = resolveGuard(server, guardId);
			if (guard == null) {
				continue;
			}
			if (guard.isRemoved() || !ownerUuid.equals(guard.getOwnerUuid())) {
				staleIds.add(guardId);
				continue;
			}
			resolved.add(guard);
		}

		for (UUID staleId : staleIds) {
			removeGuard(staleId);
		}
		return resolved;
	}

	private static GuardEntity resolveGuard(MinecraftServer server, UUID guardId) {
		WeakReference<GuardEntity> reference = GUARD_REFERENCES.get(guardId);
		GuardEntity cached = reference == null ? null : reference.get();
		if (cached != null && !cached.isRemoved()) {
			return cached;
		}

		for (ServerWorld world : server.getWorlds()) {
			Entity entity = world.getEntity(guardId);
			if (entity instanceof GuardEntity guard && !guard.isRemoved()) {
				GUARD_REFERENCES.put(guardId, new WeakReference<>(guard));
				return guard;
			}
		}
		return null;
	}

	private static void maybeCleanup(long worldTime) {
		if (lastCleanupTick != Long.MIN_VALUE && worldTime - lastCleanupTick < CLEANUP_INTERVAL_TICKS) {
			return;
		}
		lastCleanupTick = worldTime;

		for (UUID guardId : List.copyOf(GUARD_TO_OWNER.keySet())) {
			WeakReference<GuardEntity> reference = GUARD_REFERENCES.get(guardId);
			GuardEntity guard = reference == null ? null : reference.get();
			if (guard == null || guard.isRemoved()) {
				continue;
			}
			UUID indexedOwner = GUARD_TO_OWNER.get(guardId);
			if (guard.getOwnerUuid() == null || !guard.getOwnerUuid().equals(indexedOwner)) {
				removeGuard(guardId);
			}
		}
	}

	private static void removeGuard(UUID guardId) {
		UUID ownerUuid = GUARD_TO_OWNER.remove(guardId);
		if (ownerUuid != null) {
			removeFromOwner(ownerUuid, guardId);
		}
		GUARD_REFERENCES.remove(guardId);
	}

	private static void removeFromOwner(UUID ownerUuid, UUID guardId) {
		Set<UUID> ownerSet = OWNER_TO_GUARDS.get(ownerUuid);
		if (ownerSet == null) {
			return;
		}
		ownerSet.remove(guardId);
		if (ownerSet.isEmpty()) {
			OWNER_TO_GUARDS.remove(ownerUuid, ownerSet);
		}
	}
}

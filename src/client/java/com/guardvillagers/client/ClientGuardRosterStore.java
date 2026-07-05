package com.guardvillagers.client;

import com.guardvillagers.network.GuardRosterSyncPayload;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientGuardRosterStore {
	private static final ClientGuardRosterStore INSTANCE = new ClientGuardRosterStore();

	private final Map<String, List<GuardRosterEntry>> rostersByWorld = new HashMap<>();

	private ClientGuardRosterStore() {
	}

	public static ClientGuardRosterStore getInstance() {
		return INSTANCE;
	}

	public void applyPayload(ClientTacticsDataStore.WorldContext context, GuardRosterSyncPayload payload) {
		if (context == null || payload == null) {
			return;
		}

		List<GuardRosterEntry> entries = payload.guards().stream()
				.map(ClientGuardRosterStore::toEntry)
				.toList();
		this.rostersByWorld.put(this.key(context), entries);
	}

	public boolean hasRoster(ClientTacticsDataStore.WorldContext context) {
		return context != null && this.rostersByWorld.containsKey(this.key(context));
	}

	public List<GuardRosterEntry> roster(ClientTacticsDataStore.WorldContext context) {
		if (context == null) {
			return List.of();
		}
		return this.rostersByWorld.getOrDefault(this.key(context), List.of());
	}

	public void clear() {
		this.rostersByWorld.clear();
	}

	private static GuardRosterEntry toEntry(GuardRosterSyncPayload.GuardSummary summary) {
		return new GuardRosterEntry(
				summary.guardUuid(),
				summary.displayName(),
				summary.level(),
				summary.groupIndex(),
				summary.groupName(),
				summary.mainHand(),
				summary.helmet(),
				summary.chest(),
				summary.legs(),
				summary.boots());
	}

	private String key(ClientTacticsDataStore.WorldContext context) {
		return context.worldId() + "|" + context.dimensionId();
	}

	public record GuardRosterEntry(
			UUID guardUuid,
			String displayName,
			int level,
			int groupIndex,
			String groupName,
			ItemStack mainHand,
			ItemStack helmet,
			ItemStack chest,
			ItemStack legs,
			ItemStack boots) {
		public GuardRosterEntry {
			displayName = displayName == null ? "" : displayName;
			groupName = groupName == null ? "" : groupName;
			mainHand = mainHand.copy();
			helmet = helmet.copy();
			chest = chest.copy();
			legs = legs.copy();
			boots = boots.copy();
		}
	}
}

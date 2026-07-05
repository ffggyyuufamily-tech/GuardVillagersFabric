package com.guardvillagers.network;

import com.guardvillagers.GuardVillagersMod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GuardRosterSyncPayload(List<String> groupNames, List<GuardSummary> guards) implements CustomPayload {
	private static final int MAX_GROUPS = 256;
	private static final int MAX_GUARDS = 1_024;
	private static final int MAX_GROUP_NAME_LENGTH = 24;
	private static final int MAX_GUARD_NAME_LENGTH = 64;

	public static final Id<GuardRosterSyncPayload> ID = new Id<>(GuardVillagersMod.id("guard_roster_sync"));
	public static final PacketCodec<RegistryByteBuf, GuardRosterSyncPayload> CODEC = CustomPayload.codecOf(
			GuardRosterSyncPayload::write,
			GuardRosterSyncPayload::new);

	public GuardRosterSyncPayload {
		groupNames = List.copyOf(groupNames);
		guards = List.copyOf(guards);
	}

	private GuardRosterSyncPayload(RegistryByteBuf buf) {
		this(readGroupNames(buf), readGuardSummaries(buf));
	}

	private void write(RegistryByteBuf buf) {
		int groupCount = Math.min(MAX_GROUPS, this.groupNames.size());
		buf.writeInt(groupCount);
		for (int i = 0; i < groupCount; i++) {
			buf.writeString(this.groupNames.get(i), MAX_GROUP_NAME_LENGTH);
		}

		int guardCount = Math.min(MAX_GUARDS, this.guards.size());
		buf.writeInt(guardCount);
		for (int i = 0; i < guardCount; i++) {
			this.guards.get(i).write(buf);
		}
	}

	private static List<String> readGroupNames(RegistryByteBuf buf) {
		int groupCount = Math.max(0, Math.min(MAX_GROUPS, buf.readInt()));
		List<String> groupNames = new ArrayList<>(groupCount);
		for (int i = 0; i < groupCount; i++) {
			groupNames.add(buf.readString(MAX_GROUP_NAME_LENGTH));
		}
		return groupNames;
	}

	private static List<GuardSummary> readGuardSummaries(RegistryByteBuf buf) {
		int guardCount = Math.max(0, Math.min(MAX_GUARDS, buf.readInt()));
		List<GuardSummary> guards = new ArrayList<>(guardCount);
		for (int i = 0; i < guardCount; i++) {
			guards.add(GuardSummary.read(buf));
		}
		return guards;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record GuardSummary(
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
		public GuardSummary {
			displayName = displayName == null ? "" : displayName;
			groupName = groupName == null ? "" : groupName;
			mainHand = mainHand.copy();
			helmet = helmet.copy();
			chest = chest.copy();
			legs = legs.copy();
			boots = boots.copy();
		}

		private void write(RegistryByteBuf buf) {
			buf.writeUuid(this.guardUuid);
			buf.writeString(this.displayName, MAX_GUARD_NAME_LENGTH);
			buf.writeInt(this.level);
			buf.writeInt(this.groupIndex);
			buf.writeString(this.groupName, MAX_GROUP_NAME_LENGTH);
			ItemStack.PACKET_CODEC.encode(buf, this.mainHand);
			ItemStack.PACKET_CODEC.encode(buf, this.helmet);
			ItemStack.PACKET_CODEC.encode(buf, this.chest);
			ItemStack.PACKET_CODEC.encode(buf, this.legs);
			ItemStack.PACKET_CODEC.encode(buf, this.boots);
		}

		private static GuardSummary read(RegistryByteBuf buf) {
			return new GuardSummary(
					buf.readUuid(),
					buf.readString(MAX_GUARD_NAME_LENGTH),
					buf.readInt(),
					buf.readInt(),
					buf.readString(MAX_GROUP_NAME_LENGTH),
					ItemStack.PACKET_CODEC.decode(buf),
					ItemStack.PACKET_CODEC.decode(buf),
					ItemStack.PACKET_CODEC.decode(buf),
					ItemStack.PACKET_CODEC.decode(buf),
					ItemStack.PACKET_CODEC.decode(buf));
		}
	}
}

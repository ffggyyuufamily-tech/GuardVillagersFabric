package com.guardvillagers.network;

import com.guardvillagers.GuardVillagersMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record GuardDebugDataPayload(List<GuardDebugEntry> entries) implements CustomPayload {
	private static final int MAX_GUARDS_PER_PACKET = 256;
	private static final int MAX_PATH_NODES = 64;
	public static final Id<GuardDebugDataPayload> ID = new Id<>(GuardVillagersMod.id("debug_data"));
	public static final PacketCodec<RegistryByteBuf, GuardDebugDataPayload> CODEC = CustomPayload.codecOf(
		GuardDebugDataPayload::write,
		GuardDebugDataPayload::new
	);

	public GuardDebugDataPayload {
		entries = List.copyOf(entries);
	}

	private GuardDebugDataPayload(RegistryByteBuf buf) {
		this(readEntries(buf));
	}

	private void write(RegistryByteBuf buf) {
		int guardCount = Math.min(MAX_GUARDS_PER_PACKET, this.entries.size());
		buf.writeInt(guardCount);
		for (int i = 0; i < guardCount; i++) {
			this.entries.get(i).write(buf);
		}
	}

	private static List<GuardDebugEntry> readEntries(RegistryByteBuf buf) {
		int guardCount = Math.max(0, Math.min(MAX_GUARDS_PER_PACKET, buf.readInt()));
		List<GuardDebugEntry> entries = new ArrayList<>(guardCount);
		for (int i = 0; i < guardCount; i++) {
			entries.add(GuardDebugEntry.read(buf));
		}
		return entries;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record GuardDebugEntry(int entityId, List<BlockPos> pathNodes, int currentPathIndex, int targetEntityId) {
		public GuardDebugEntry {
			pathNodes = List.copyOf(pathNodes);
		}

		private void write(RegistryByteBuf buf) {
			buf.writeInt(this.entityId);
			int nodeCount = Math.min(MAX_PATH_NODES, this.pathNodes.size());
			buf.writeInt(nodeCount);
			for (int i = 0; i < nodeCount; i++) {
				buf.writeBlockPos(this.pathNodes.get(i));
			}
			buf.writeInt(this.currentPathIndex);
			buf.writeInt(this.targetEntityId);
		}

		private static GuardDebugEntry read(RegistryByteBuf buf) {
			int entityId = buf.readInt();
			int nodeCount = Math.max(0, Math.min(MAX_PATH_NODES, buf.readInt()));
			List<BlockPos> nodes = new ArrayList<>(nodeCount);
			for (int i = 0; i < nodeCount; i++) {
				nodes.add(buf.readBlockPos());
			}
			int currentPathIndex = buf.readInt();
			int targetEntityId = buf.readInt();
			return new GuardDebugEntry(entityId, nodes, currentPathIndex, targetEntityId);
		}
	}
}

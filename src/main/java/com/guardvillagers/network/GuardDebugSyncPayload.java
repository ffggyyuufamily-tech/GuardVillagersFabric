package com.guardvillagers.network;

import com.guardvillagers.GuardVillagersMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record GuardDebugSyncPayload(boolean enabled, double range) implements CustomPayload {
	public static final Id<GuardDebugSyncPayload> ID = new Id<>(GuardVillagersMod.id("debug_sync"));
	public static final PacketCodec<RegistryByteBuf, GuardDebugSyncPayload> CODEC = CustomPayload.codecOf(
		GuardDebugSyncPayload::write,
		GuardDebugSyncPayload::new
	);

	private GuardDebugSyncPayload(RegistryByteBuf buf) {
		this(buf.readBoolean(), buf.readDouble());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeBoolean(this.enabled);
		buf.writeDouble(this.range);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

package com.guardvillagers.data;

import com.guardvillagers.GuardPlayerUpgrades;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuardUpgradeState extends PersistentState {
	private static final Codec<Map<UUID, GuardPlayerUpgrades>> UPGRADE_MAP_CODEC = Codec.unboundedMap(Uuids.STRING_CODEC, GuardPlayerUpgrades.CODEC);

	public static final Codec<GuardUpgradeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UPGRADE_MAP_CODEC.optionalFieldOf("upgrades", Map.of()).forGetter(GuardUpgradeState::upgradesForCodec)
	).apply(instance, GuardUpgradeState::new));

	public static final PersistentStateType<GuardUpgradeState> TYPE = new PersistentStateType<>(
		"guardvillagers_upgrades",
		GuardUpgradeState::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private final Map<UUID, GuardPlayerUpgrades> upgrades;

	public GuardUpgradeState() {
		this(Map.of());
	}

	private GuardUpgradeState(Map<UUID, GuardPlayerUpgrades> upgrades) {
		this.upgrades = new HashMap<>();
		for (Map.Entry<UUID, GuardPlayerUpgrades> entry : upgrades.entrySet()) {
			this.upgrades.put(entry.getKey(), this.trackUpgrade(entry.getValue().copy()));
		}
	}

	public GuardPlayerUpgrades getOrCreate(UUID playerUuid) {
		return this.upgrades.computeIfAbsent(playerUuid, ignored -> {
			this.markDirty();
			return this.trackUpgrade(new GuardPlayerUpgrades());
		});
	}

	public GuardPlayerUpgrades getOrDefault(UUID playerUuid) {
		GuardPlayerUpgrades upgrades = this.upgrades.get(playerUuid);
		if (upgrades == null) {
			return new GuardPlayerUpgrades();
		}
		return upgrades;
	}

	private GuardPlayerUpgrades trackUpgrade(GuardPlayerUpgrades upgrades) {
		upgrades.setDirtyCallback(this::markDirty);
		return upgrades;
	}

	private Map<UUID, GuardPlayerUpgrades> upgradesForCodec() {
		return Collections.unmodifiableMap(this.upgrades);
	}
}

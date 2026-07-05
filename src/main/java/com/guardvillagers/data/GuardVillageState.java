package com.guardvillagers.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GuardVillageState extends PersistentState {
	private static final Codec<Map<String, VillageData>> VILLAGE_MAP_CODEC = Codec.unboundedMap(Codec.STRING, VillageData.CODEC);
	public static final Codec<GuardVillageState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		VILLAGE_MAP_CODEC.optionalFieldOf("villages", Map.of()).forGetter(GuardVillageState::villagesForCodec)
	).apply(instance, GuardVillageState::new));

	public static final PersistentStateType<GuardVillageState> TYPE = new PersistentStateType<>(
		"guardvillagers_village_state",
		GuardVillageState::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private final Map<String, VillageData> villages;

	public GuardVillageState() {
		this(Map.of());
	}

	private GuardVillageState(Map<String, VillageData> villages) {
		this.villages = new HashMap<>(villages);
	}

	public VillageData getOrCreate(String villageId, int initialSpawnCount) {
		int clampedInitial = Math.max(1, initialSpawnCount);
		VillageData existing = this.villages.get(villageId);
		if (existing != null) {
			return existing;
		}

		VillageData created = new VillageData(clampedInitial, Long.MIN_VALUE, clampedInitial);
		this.villages.put(villageId, created);
		this.markDirty();
		return created;
	}

	public void updateInitial(String villageId, int newInitial) {
		if (newInitial <= 0) {
			return;
		}
		VillageData current = this.villages.get(villageId);
		if (current == null || newInitial > current.initialSpawnCount()) {
			long lastSpawnTick = current == null ? Long.MIN_VALUE : current.lastSpawnTick();
			int maxGuards = current == null ? newInitial : Math.max(newInitial, current.maxGuardCount());
			this.villages.put(villageId, new VillageData(newInitial, lastSpawnTick, maxGuards));
			this.markDirty();
		}
	}

	public void updateMaxGuardCount(String villageId, int newMax) {
		if (newMax <= 0) {
			return;
		}
		VillageData current = this.villages.get(villageId);
		if (current == null) {
			return;
		}
		if (newMax > current.maxGuardCount()) {
			this.villages.put(villageId, new VillageData(current.initialSpawnCount(), current.lastSpawnTick(), newMax));
			this.markDirty();
		}
	}

	public int getMaxGuardCount(String villageId) {
		VillageData data = this.villages.get(villageId);
		return data == null ? 0 : data.maxGuardCount();
	}

	public long getLastSpawnTick(String villageId) {
		VillageData data = this.villages.get(villageId);
		return data == null ? Long.MIN_VALUE : data.lastSpawnTick();
	}

	public void setLastSpawnTick(String villageId, long tick) {
		VillageData data = this.villages.get(villageId);
		if (data == null || data.lastSpawnTick() == tick) {
			return;
		}
		this.villages.put(villageId, new VillageData(data.initialSpawnCount(), tick, data.maxGuardCount()));
		this.markDirty();
	}

	private Map<String, VillageData> villagesForCodec() {
		return Collections.unmodifiableMap(this.villages);
	}

	public record VillageData(int initialSpawnCount, long lastSpawnTick, int maxGuardCount) {
		public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(1, 10_000).fieldOf("initial_spawn_count").forGetter(VillageData::initialSpawnCount),
			Codec.LONG.optionalFieldOf("last_spawn_tick", Long.MIN_VALUE).forGetter(VillageData::lastSpawnTick),
			Codec.intRange(1, 10_000).optionalFieldOf("max_guard_count", 1).forGetter(VillageData::maxGuardCount)
		).apply(instance, VillageData::new));
	}
}

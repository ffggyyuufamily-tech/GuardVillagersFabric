package com.guardvillagers.data;

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

public final class GuardReputationState extends PersistentState {
	private static final double MIN_REPUTATION = 0.0D;
	private static final double MAX_REPUTATION = 1.0D;
	private static final int LEGACY_SCHEMA_VERSION = 1;
	private static final int CURRENT_SCHEMA_VERSION = 2;
	private static final int LEGACY_MIN_REPUTATION = -200;
	private static final int LEGACY_MAX_REPUTATION = 200;
	private static final double LEGACY_REPUTATION_SPAN = LEGACY_MAX_REPUTATION - LEGACY_MIN_REPUTATION;
	public static final double DEFAULT_REPUTATION = 0.50D;

	private static final Codec<Map<UUID, Double>> NORMALIZED_REPUTATION_CODEC = Codec.unboundedMap(Uuids.STRING_CODEC, Codec.DOUBLE);
	public static final Codec<GuardReputationState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("schema_version", LEGACY_SCHEMA_VERSION).forGetter(GuardReputationState::schemaVersionForCodec),
		NORMALIZED_REPUTATION_CODEC.optionalFieldOf("reputation", Map.of()).forGetter(GuardReputationState::reputationForCodec)
	).apply(instance, GuardReputationState::fromCodecData));

	public static final PersistentStateType<GuardReputationState> TYPE = new PersistentStateType<>(
		"guardvillagers_reputation",
		GuardReputationState::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private final Map<UUID, Double> reputation;

	public GuardReputationState() {
		this(CURRENT_SCHEMA_VERSION, Map.of());
	}

	private GuardReputationState(int schemaVersion, Map<UUID, Double> reputation) {
		this.reputation = new HashMap<>(reputation);
	}

	public double get(UUID playerId) {
		return this.reputation.getOrDefault(playerId, DEFAULT_REPUTATION);
	}

	public double ensureTracked(UUID playerId) {
		Double existing = this.reputation.get(playerId);
		if (existing != null) {
			return existing;
		}
		this.reputation.put(playerId, DEFAULT_REPUTATION);
		this.markDirty();
		return DEFAULT_REPUTATION;
	}

	public double add(UUID playerId, double delta) {
		double updated = clamp(this.ensureTracked(playerId) + delta);
		this.reputation.put(playerId, updated);
		this.markDirty();
		return updated;
	}

	public void set(UUID playerId, double value) {
		double clamped = clamp(value);
		this.reputation.put(playerId, clamped);
		this.markDirty();
	}

	public void decayAll(double amount) {
		if (amount <= 0.0D || this.reputation.isEmpty()) {
			return;
		}
		boolean changed = false;
		for (Map.Entry<UUID, Double> entry : this.reputation.entrySet()) {
			double current = entry.getValue();
			double decayed = clamp(current - amount);
			if (Double.compare(current, decayed) != 0) {
				entry.setValue(decayed);
				changed = true;
			}
		}
		if (changed) {
			this.markDirty();
		}
	}

	private Map<UUID, Double> reputationForCodec() {
		return Collections.unmodifiableMap(this.reputation);
	}

	private int schemaVersionForCodec() {
		return CURRENT_SCHEMA_VERSION;
	}

	private static GuardReputationState fromCodecData(int schemaVersion, Map<UUID, Double> rawValues) {
		if (schemaVersion < CURRENT_SCHEMA_VERSION) {
			GuardReputationState migrated = new GuardReputationState(CURRENT_SCHEMA_VERSION, migrateLegacyValues(rawValues));
			migrated.markDirty();
			return migrated;
		}
		return new GuardReputationState(CURRENT_SCHEMA_VERSION, sanitizeNormalizedValues(rawValues));
	}

	private static Map<UUID, Double> sanitizeNormalizedValues(Map<UUID, Double> rawValues) {
		Map<UUID, Double> normalizedValues = new HashMap<>();
		for (Map.Entry<UUID, Double> entry : rawValues.entrySet()) {
			Double value = entry.getValue();
			if (value == null || value.isNaN() || value.isInfinite()) {
				continue;
			}
			normalizedValues.put(entry.getKey(), clamp(value));
		}
		return normalizedValues;
	}

	private static Map<UUID, Double> migrateLegacyValues(Map<UUID, Double> legacyValues) {
		Map<UUID, Double> normalizedValues = new HashMap<>();
		for (Map.Entry<UUID, Double> entry : legacyValues.entrySet()) {
			Double legacyRaw = entry.getValue();
			if (legacyRaw == null || legacyRaw.isNaN() || legacyRaw.isInfinite()) {
				continue;
			}
			int legacy = (int) Math.round(legacyRaw);
			double normalized = (legacy - LEGACY_MIN_REPUTATION) / LEGACY_REPUTATION_SPAN;
			normalizedValues.put(entry.getKey(), clamp(normalized));
		}
		return normalizedValues;
	}

	private static double clamp(double value) {
		return Math.max(MIN_REPUTATION, Math.min(MAX_REPUTATION, value));
	}
}

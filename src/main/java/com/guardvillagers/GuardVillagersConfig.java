package com.guardvillagers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GuardVillagersConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String CONFIG_FILE_NAME = GuardVillagersMod.MOD_ID + ".json";
	private static GuardVillagersConfig INSTANCE = new GuardVillagersConfig();

	public Engineering engineering = new Engineering();
	public Tnt tnt = new Tnt();
	public Captain captain = new Captain();
	public Formations formations = new Formations();
	public Combat combat = new Combat();
	public Survival survival = new Survival();
	public Morale morale = new Morale();
	public Logistics logistics = new Logistics();
	public ResourceGathering resourceGathering = new ResourceGathering();

	public static GuardVillagersConfig get() {
		return INSTANCE;
	}

	public static void load() {
		Path path = configPath();
		try {
			if (Files.notExists(path)) {
				INSTANCE = new GuardVillagersConfig();
				save();
				return;
			}
			try (Reader reader = Files.newBufferedReader(path)) {
				GuardVillagersConfig loaded = GSON.fromJson(reader, GuardVillagersConfig.class);
				INSTANCE = loaded == null ? new GuardVillagersConfig() : loaded.sanitized();
			}
			save();
		} catch (RuntimeException | IOException exception) {
			GuardVillagersMod.LOGGER.warn("Failed to load {}; using defaults", path, exception);
			INSTANCE = new GuardVillagersConfig();
		}
	}

	public static void reload() {
		load();
	}

	public static void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(INSTANCE.sanitized(), writer);
			}
		} catch (IOException exception) {
			GuardVillagersMod.LOGGER.warn("Failed to save {}", path, exception);
		}
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
	}

	public BlockState engineeringBlockState() {
		Identifier id = Identifier.tryParse(this.engineering.blockId);
		if (id == null) {
			return Blocks.COBBLESTONE.getDefaultState();
		}
		Block block = Registries.BLOCK.get(id);
		if (block == Blocks.AIR) {
			return Blocks.COBBLESTONE.getDefaultState();
		}
		return block.getDefaultState();
	}

	private GuardVillagersConfig sanitized() {
		if (this.engineering == null) {
			this.engineering = new Engineering();
		}
		if (this.tnt == null) {
			this.tnt = new Tnt();
		}
		if (this.captain == null) {
			this.captain = new Captain();
		}
		if (this.formations == null) {
			this.formations = new Formations();
		}
		if (this.combat == null) {
			this.combat = new Combat();
		}
		if (this.survival == null) {
			this.survival = new Survival();
		}
		if (this.morale == null) {
			this.morale = new Morale();
		}
		if (this.logistics == null) {
			this.logistics = new Logistics();
		}
		if (this.resourceGathering == null) {
			this.resourceGathering = new ResourceGathering();
		}
		this.engineering.sanitize();
		this.tnt.sanitize();
		this.captain.sanitize();
		this.formations.sanitize();
		this.combat.sanitize();
		this.survival.sanitize();
		this.morale.sanitize();
		this.logistics.sanitize();
		this.resourceGathering.sanitize();
		return this;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public static final class Engineering {
		public boolean enabled = true;
		public double bridgeLength = 12.0D;
		public int maxBlocksPlaced = 2;
		public boolean stairBuilding = true;
		public boolean ladderBuilding = false;
		public boolean lavaFill = false;
		public boolean defensiveBarricades = true;
		public boolean requireMobGriefing = true;
		public String blockId = "minecraft:cobblestone";
		public int buildIntervalTicks = 10;
		public int buildCooldownTicks = 35;
		public double defensiveBarricadeRange = 5.0D;
		public double archerRetreatDistance = 4.0D;
		public double archerRetreatSpeed = 1.25D;
		public double maxVerticalDiff = 2.0D;
		public double maxBridgeDistance = 32.0D;
		public double preferLadderAbove = 3.0D;
		public double cliffDetectionThreshold = 2.0D;
		public double pillarDetectionHeight = 3.0D;
		public double terrainScanRadius = 16.0D;
		public boolean terrainAnalysisEnabled = true;

		private void sanitize() {
			if (this.blockId == null || this.blockId.isBlank()) {
				this.blockId = "minecraft:cobblestone";
			}
			this.bridgeLength = clamp(this.bridgeLength, 1.0D, 64.0D);
			this.maxBlocksPlaced = clamp(this.maxBlocksPlaced, 1, 8);
			this.buildIntervalTicks = clamp(this.buildIntervalTicks, 1, 200);
			this.buildCooldownTicks = clamp(this.buildCooldownTicks, 1, 20 * 60);
			this.defensiveBarricadeRange = clamp(this.defensiveBarricadeRange, 1.0D, 24.0D);
			this.archerRetreatDistance = clamp(this.archerRetreatDistance, 0.0D, 16.0D);
			this.archerRetreatSpeed = clamp(this.archerRetreatSpeed, 0.1D, 3.0D);
			this.maxVerticalDiff = clamp(this.maxVerticalDiff, 0.5D, 8.0D);
			this.maxBridgeDistance = clamp(this.maxBridgeDistance, 4.0D, 64.0D);
			this.preferLadderAbove = clamp(this.preferLadderAbove, 1.0D, 16.0D);
			this.cliffDetectionThreshold = clamp(this.cliffDetectionThreshold, 0.5D, 8.0D);
			this.pillarDetectionHeight = clamp(this.pillarDetectionHeight, 1.0D, 16.0D);
			this.terrainScanRadius = clamp(this.terrainScanRadius, 4.0D, 64.0D);
		}
	}

	public static final class Tnt {
		public boolean enabled = true;
		public int cooldownTicks = 160;
		public int fuseTicks = 60;
		public int minimumEnemyCount = 3;
		public double safeRadius = 7.0D;
		public double villagerProtectionRadius = 7.0D;
		public double friendlyProtectionRadius = 5.0D;
		public double minDistance = 8.0D;
		public double maxDistance = 24.0D;
		public double clusterRadius = 4.5D;
		public float minHealthRatio = 0.45F;
		public boolean countAllHostileMobs = true;

		private void sanitize() {
			this.cooldownTicks = clamp(this.cooldownTicks, 20, 20 * 60 * 10);
			this.fuseTicks = clamp(this.fuseTicks, 20, 20 * 15);
			this.minimumEnemyCount = clamp(this.minimumEnemyCount, 1, 32);
			this.safeRadius = clamp(this.safeRadius, 1.0D, 32.0D);
			this.villagerProtectionRadius = clamp(this.villagerProtectionRadius, 1.0D, 32.0D);
			this.friendlyProtectionRadius = clamp(this.friendlyProtectionRadius, 1.0D, 32.0D);
			this.minDistance = clamp(this.minDistance, 0.0D, 128.0D);
			this.maxDistance = clamp(Math.max(this.maxDistance, this.minDistance), 1.0D, 128.0D);
			this.clusterRadius = clamp(this.clusterRadius, 1.0D, 32.0D);
			this.minHealthRatio = clamp(this.minHealthRatio, 0.0F, 1.0F);
		}
	}

	public static final class Captain {
		public boolean enabled = true;
		public int targetRefreshTicks = 20;
		public float retreatHealthThreshold = 0.35F;
		public float retreatAllyRatio = 0.5F;
		public FocusPriorityWeights focusPriorityWeights = new FocusPriorityWeights();
		public double commandRadius = 48.0D;
		public double targetScanRadius = 40.0D;
		public double rallyDistance = 18.0D;
		public int rallyTicks = 60;
		public int retreatRallyTicks = 120;
		public double retreatEnemyRadius = 14.0D;

		private void sanitize() {
			if (this.focusPriorityWeights == null) {
				this.focusPriorityWeights = new FocusPriorityWeights();
			}
			this.focusPriorityWeights.sanitize();
			this.targetRefreshTicks = clamp(this.targetRefreshTicks, 1, 20 * 60);
			this.commandRadius = clamp(this.commandRadius, 4.0D, 128.0D);
			this.targetScanRadius = clamp(this.targetScanRadius, 4.0D, 128.0D);
			this.rallyDistance = clamp(this.rallyDistance, 1.0D, 128.0D);
			this.rallyTicks = clamp(this.rallyTicks, 1, 20 * 60);
			this.retreatRallyTicks = clamp(this.retreatRallyTicks, 1, 20 * 60);
			this.retreatEnemyRadius = clamp(this.retreatEnemyRadius, 1.0D, 64.0D);
			this.retreatHealthThreshold = clamp(this.retreatHealthThreshold, 0.0F, 1.0F);
			this.retreatAllyRatio = clamp(this.retreatAllyRatio, 0.0F, 1.0F);
		}

		public static final class FocusPriorityWeights {
			public int evoker = 90;
			public int boss = 80;
			public int raider = 45;
			public int ranged = 25;

			private void sanitize() {
				this.evoker = clamp(this.evoker, 0, 1000);
				this.boss = clamp(this.boss, 0, 1000);
				this.raider = clamp(this.raider, 0, 1000);
				this.ranged = clamp(this.ranged, 0, 1000);
			}
		}
	}

	public static final class Formations {
		public boolean enabled = true;
		public double spacing = 2.5D;
		public double shieldWallSpacing = 0.75D;
		public double wedgeSpacing = 0.35D;
		public double circleRadius = 1.5D;
		public double phalanxDepth = 1.4D;
		public double bowmanExtraDistance = 4.5D;
		public double bowmanFlankWeight = 2.2D;
		public double meleeFlankWeight = 1.15D;
		public double shieldWallBowmanExtraDistance = 6.0D;
		public double phalanxColumnSpacing = 0.9D;
		public double wedgeBaseColumnSpacing = 1.2D;
		public double wedgeColumnGrowth = 0.55D;
		public double circleBowmanExtraDistance = 5.0D;
		public double slotSpacing = 1.75D;

		private void sanitize() {
			this.spacing = clamp(this.spacing, 0.5D, 16.0D);
			this.shieldWallSpacing = clamp(this.shieldWallSpacing, 0.0D, 16.0D);
			this.wedgeSpacing = clamp(this.wedgeSpacing, 0.0D, 4.0D);
			this.circleRadius = clamp(this.circleRadius, 0.0D, 24.0D);
			this.phalanxDepth = clamp(this.phalanxDepth, 0.0D, 8.0D);
			this.bowmanExtraDistance = clamp(this.bowmanExtraDistance, 0.0D, 24.0D);
			this.bowmanFlankWeight = clamp(this.bowmanFlankWeight, 0.0D, 16.0D);
			this.meleeFlankWeight = clamp(this.meleeFlankWeight, 0.0D, 16.0D);
			this.shieldWallBowmanExtraDistance = clamp(this.shieldWallBowmanExtraDistance, 0.0D, 24.0D);
			this.phalanxColumnSpacing = clamp(this.phalanxColumnSpacing, 0.0D, 8.0D);
			this.wedgeBaseColumnSpacing = clamp(this.wedgeBaseColumnSpacing, 0.0D, 8.0D);
			this.wedgeColumnGrowth = clamp(this.wedgeColumnGrowth, 0.0D, 8.0D);
			this.circleBowmanExtraDistance = clamp(this.circleBowmanExtraDistance, 0.0D, 24.0D);
			this.slotSpacing = clamp(this.slotSpacing, 0.5D, 8.0D);
		}
	}

	public static final class Combat {
		public float flankChance = 1.0F;
		public float dodgeChance = 0.0F;
		public float friendlyFireAvoidance = 1.0F;
		public float rangedPreferredDistance = 15.0F;
		public float retreatEnterHealthRatio = 0.25F;
		public float retreatExitHealthRatio = 0.40F;
		public float alertRejectionHealthRatio = 0.30F;
		public double retreatSpeed = 1.35D;
		public int retreatRecalculateTicks = 20;
		public double retreatFleeDistance = 12.0D;
		public double retreatSearchRadius = 48.0D;
		public int hostileScanRange = 20;
		public int raidScanRange = 48;
		public int targetLosGraceTicks = 40;
		public int targetStuckGraceTicks = 60;
		public int targetSuppressionTicks = 40;
		public int combatCooldownTicks = 80;
		public int raiderThreatBonus = 20;
		public int rangedThreatBonus = 5;
		public int peelCapPercent = 30;
		public int peelCapMax = 10;
		public int minOwnerAlertRange = 8;
		public float bowPullTicks = 20.0F;
		public float bowVisibleTicks = 20.0F;
		public double bowSpeed = 1.0D;
		public double bowTooCloseDistance = 10.0D;
		public double bowTooFarDistance = 15.0D;
		public double bowRetreatDistance = 6.0D;
		public double bowApproachSpeed = 1.0D;
		public double bowRetreatSpeed = 1.15D;
		public int baseScoreOwnerAttacker = 100;
		public int baseScorePlayer = 80;
		public int baseScoreHostile = 40;
		public int baseScoreOther = 20;

		private void sanitize() {
			this.flankChance = clamp(this.flankChance, 0.0F, 2.0F);
			this.dodgeChance = clamp(this.dodgeChance, 0.0F, 1.0F);
			this.friendlyFireAvoidance = clamp(this.friendlyFireAvoidance, 0.0F, 1.0F);
			this.rangedPreferredDistance = clamp(this.rangedPreferredDistance, 4.0F, 48.0F);
			this.retreatEnterHealthRatio = clamp(this.retreatEnterHealthRatio, 0.0F, 1.0F);
			this.retreatExitHealthRatio = clamp(this.retreatExitHealthRatio, 0.0F, 1.0F);
			this.alertRejectionHealthRatio = clamp(this.alertRejectionHealthRatio, 0.0F, 1.0F);
			this.retreatSpeed = clamp(this.retreatSpeed, 0.1D, 3.0D);
			this.retreatRecalculateTicks = clamp(this.retreatRecalculateTicks, 1, 200);
			this.retreatFleeDistance = clamp(this.retreatFleeDistance, 1.0D, 64.0D);
			this.retreatSearchRadius = clamp(this.retreatSearchRadius, 4.0D, 128.0D);
			this.hostileScanRange = clamp(this.hostileScanRange, 4, 128);
			this.raidScanRange = clamp(this.raidScanRange, 4, 128);
			this.targetLosGraceTicks = clamp(this.targetLosGraceTicks, 1, 600);
			this.targetStuckGraceTicks = clamp(this.targetStuckGraceTicks, 1, 600);
			this.targetSuppressionTicks = clamp(this.targetSuppressionTicks, 1, 600);
			this.combatCooldownTicks = clamp(this.combatCooldownTicks, 1, 600);
			this.raiderThreatBonus = clamp(this.raiderThreatBonus, 0, 1000);
			this.rangedThreatBonus = clamp(this.rangedThreatBonus, 0, 1000);
			this.peelCapPercent = clamp(this.peelCapPercent, 1, 100);
			this.peelCapMax = clamp(this.peelCapMax, 1, 64);
			this.minOwnerAlertRange = clamp(this.minOwnerAlertRange, 1, 128);
			this.bowPullTicks = clamp(this.bowPullTicks, 1.0F, 100.0F);
			this.bowVisibleTicks = clamp(this.bowVisibleTicks, 1.0F, 100.0F);
			this.bowSpeed = clamp(this.bowSpeed, 0.1D, 3.0D);
			this.bowTooCloseDistance = clamp(this.bowTooCloseDistance, 1.0D, 32.0D);
			this.bowTooFarDistance = clamp(this.bowTooFarDistance, 1.0D, 64.0D);
			this.bowRetreatDistance = clamp(this.bowRetreatDistance, 0.0D, 32.0D);
			this.bowApproachSpeed = clamp(this.bowApproachSpeed, 0.1D, 3.0D);
			this.bowRetreatSpeed = clamp(this.bowRetreatSpeed, 0.1D, 3.0D);
		}
	}

	public static final class Survival {
		public boolean enabled = true;
		public float fallDamageMultiplier = 0.25F;
		public int safeFallDistance = 16;
		public float panicThreshold = 0.25F;
		public int safeFallDistancePerLevel = 1;
		public int damageReductionPerTwoLevels = 1;

		private void sanitize() {
			this.fallDamageMultiplier = clamp(this.fallDamageMultiplier, 0.0F, 1.0F);
			this.safeFallDistance = clamp(this.safeFallDistance, 3, 256);
			this.panicThreshold = clamp(this.panicThreshold, 0.0F, 1.0F);
			this.safeFallDistancePerLevel = clamp(this.safeFallDistancePerLevel, 0, 32);
			this.damageReductionPerTwoLevels = clamp(this.damageReductionPerTwoLevels, 0, 20);
		}
	}

	public static final class Morale {
		public boolean enabled = true;
		public int allyDeathPenalty = 15;
		public int captainBonus = 10;
		public int outnumberedPenalty = 8;
		public int victoryBonus = 12;
		public int lowHealthPenalty = 5;
		public int recoveryPerTick = 1;
		public int recoveryIntervalTicks = 100;
		public int defaultMorale = 70;
		public float highMoraleThreshold = 70.0F;
		public float lowMoraleThreshold = 30.0F;
		public float highMoraleRetreatResistance = 0.15F;
		public float lowMoraleRetreatSensitivity = 0.15F;
		public float highMoraleFormationTightness = 0.85F;
		public float lowMoraleFormationScatter = 1.5F;
		public float reactionSpeedBonus = 0.25F;

		private void sanitize() {
			this.allyDeathPenalty = clamp(this.allyDeathPenalty, 0, 50);
			this.captainBonus = clamp(this.captainBonus, 0, 50);
			this.outnumberedPenalty = clamp(this.outnumberedPenalty, 0, 50);
			this.victoryBonus = clamp(this.victoryBonus, 0, 50);
			this.lowHealthPenalty = clamp(this.lowHealthPenalty, 0, 50);
			this.recoveryPerTick = clamp(this.recoveryPerTick, 0, 10);
			this.recoveryIntervalTicks = clamp(this.recoveryIntervalTicks, 20, 20 * 60);
			this.defaultMorale = clamp(this.defaultMorale, 0, 100);
			this.highMoraleThreshold = clamp(this.highMoraleThreshold, 0.0F, 100.0F);
			this.lowMoraleThreshold = clamp(this.lowMoraleThreshold, 0.0F, 100.0F);
			this.highMoraleRetreatResistance = clamp(this.highMoraleRetreatResistance, 0.0F, 0.5F);
			this.lowMoraleRetreatSensitivity = clamp(this.lowMoraleRetreatSensitivity, 0.0F, 0.5F);
			this.highMoraleFormationTightness = clamp(this.highMoraleFormationTightness, 0.5F, 1.0F);
			this.lowMoraleFormationScatter = clamp(this.lowMoraleFormationScatter, 1.0F, 3.0F);
			this.reactionSpeedBonus = clamp(this.reactionSpeedBonus, 0.0F, 1.0F);
		}
	}

	public static final class Logistics {
		public boolean enabled = true;
		public double pickupRange = 8.0D;
		public double chestSearchRange = 16.0D;
		public boolean shareItems = true;
		public boolean arrowRecovery = true;
		public int maxArrowReserve = 64;
		public int lowArrowThreshold = 8;
		public int shareAmount = 16;
		public int pickupIntervalTicks = 10;

		private void sanitize() {
			this.pickupRange = clamp(this.pickupRange, 1.0D, 32.0D);
			this.chestSearchRange = clamp(this.chestSearchRange, 1.0D, 64.0D);
			this.maxArrowReserve = clamp(this.maxArrowReserve, 1, 256);
			this.lowArrowThreshold = clamp(this.lowArrowThreshold, 0, 64);
			this.shareAmount = clamp(this.shareAmount, 1, 64);
			this.pickupIntervalTicks = clamp(this.pickupIntervalTicks, 1, 200);
		}
	}

	public static final class ResourceGathering {
		public boolean enabled = true;
		public double scanRadius = 24.0D;
		public double creeperHuntRange = 32.0D;
		public int sandTargetAmount = 4;
		public int gunpowderTargetAmount = 5;

		private void sanitize() {
			this.scanRadius = clamp(this.scanRadius, 4.0D, 64.0D);
			this.creeperHuntRange = clamp(this.creeperHuntRange, 8.0D, 64.0D);
			this.sandTargetAmount = clamp(this.sandTargetAmount, 1, 64);
			this.gunpowderTargetAmount = clamp(this.gunpowderTargetAmount, 1, 64);
		}
	}
}

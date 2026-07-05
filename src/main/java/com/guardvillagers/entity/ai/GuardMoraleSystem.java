package com.guardvillagers.entity.ai;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public final class GuardMoraleSystem {
	private GuardMoraleSystem() {
	}

	public static void tick(GuardEntity guard, ServerWorld world) {
		GuardVillagersConfig.Morale config = GuardVillagersConfig.get().morale;
		if (!config.enabled) {
			return;
		}

		if (guard.age % config.recoveryIntervalTicks == 0) {
			guard.adjustMorale(config.recoveryPerTick);
		}

		if (guard.age % 40 == 0) {
			applyCaptainBonus(guard, world, config);
			applyOutnumberedPenalty(guard, world, config);
		}

		if (guard.age % 20 == 0) {
			applyLowHealthPenalty(guard, config);
		}
	}

	public static void onAllyDeath(GuardEntity deceased, ServerWorld world) {
		GuardVillagersConfig.Morale config = GuardVillagersConfig.get().morale;
		if (!config.enabled) {
			return;
		}

		double range = GuardVillagersConfig.get().captain.commandRadius;
		int affectedCount = 0;
		for (GuardEntity guard : world.getEntitiesByClass(
				GuardEntity.class,
				deceased.getBoundingBox().expand(range),
				entity -> entity.isAlive() && deceased.isSameGroup(entity))) {
			guard.adjustMorale(-config.allyDeathPenalty);
			affectedCount++;
		}
		
		GuardDebugLogger.logMorale(deceased, "ally death morale penalty applied",
			"affectedCount", String.valueOf(affectedCount),
			"penalty", String.format("%.2f", (float) config.allyDeathPenalty),
			"range", String.format("%.1f", range));
	}

	public static void onVictory(GuardEntity guard) {
		GuardVillagersConfig.Morale config = GuardVillagersConfig.get().morale;
		if (!config.enabled) {
			return;
		}
		guard.adjustMorale(config.victoryBonus);
		GuardDebugLogger.logMorale(guard, "victory morale bonus applied",
			"bonus", String.format("%.2f", (float) config.victoryBonus),
			"newMorale", String.format("%.2f", (float) guard.getMorale()));
	}

	public static float retreatEnterThreshold(GuardEntity guard) {
		GuardVillagersConfig.Combat combat = GuardVillagersConfig.get().combat;
		GuardVillagersConfig.Morale morale = GuardVillagersConfig.get().morale;
		float threshold = combat.retreatEnterHealthRatio;
		if (!morale.enabled) {
			return threshold;
		}
		if (guard.getMorale() >= morale.highMoraleThreshold) {
			return threshold - morale.highMoraleRetreatResistance;
		}
		if (guard.getMorale() <= morale.lowMoraleThreshold) {
			return threshold + morale.lowMoraleRetreatSensitivity;
		}
		return threshold;
	}

	public static float retreatExitThreshold(GuardEntity guard) {
		GuardVillagersConfig.Combat combat = GuardVillagersConfig.get().combat;
		GuardVillagersConfig.Morale morale = GuardVillagersConfig.get().morale;
		float threshold = combat.retreatExitHealthRatio;
		if (!morale.enabled) {
			return threshold;
		}
		if (guard.getMorale() >= morale.highMoraleThreshold) {
			return threshold + morale.highMoraleRetreatResistance;
		}
		if (guard.getMorale() <= morale.lowMoraleThreshold) {
			return threshold - morale.lowMoraleRetreatSensitivity;
		}
		return threshold;
	}

	public static double formationScatterMultiplier(GuardEntity guard) {
		GuardVillagersConfig.Morale config = GuardVillagersConfig.get().morale;
		if (!config.enabled) {
			return 1.0D;
		}
		if (guard.getMorale() >= config.highMoraleThreshold) {
			return config.highMoraleFormationTightness;
		}
		if (guard.getMorale() <= config.lowMoraleThreshold) {
			return config.lowMoraleFormationScatter;
		}
		return 1.0D;
	}

	public static int commandIntervalTicks(GuardEntity guard) {
		GuardVillagersConfig.Captain captain = GuardVillagersConfig.get().captain;
		GuardVillagersConfig.Morale morale = GuardVillagersConfig.get().morale;
		int base = captain.targetRefreshTicks;
		if (!morale.enabled || guard.getMorale() < morale.highMoraleThreshold) {
			return base;
		}
		int reduction = Math.max(1, Math.round(base * morale.reactionSpeedBonus));
		return Math.max(1, base - reduction);
	}

	private static void applyCaptainBonus(GuardEntity guard, ServerWorld world, GuardVillagersConfig.Morale config) {
		boolean captainAlive = world.getEntitiesByClass(
				GuardEntity.class,
				guard.getBoundingBox().expand(GuardVillagersConfig.get().captain.commandRadius),
				entity -> entity.isAlive() && entity.isSquadLeader() && guard.isSameSquad(entity))
				.stream()
				.findAny()
				.isPresent();
		if (captainAlive) {
			guard.adjustMorale(config.captainBonus / 4);
		}
	}

	private static void applyOutnumberedPenalty(GuardEntity guard, ServerWorld world, GuardVillagersConfig.Morale config) {
		int allies = 1;
		int enemies = 0;
		Box box = guard.getBoundingBox().expand(GuardVillagersConfig.get().captain.retreatEnemyRadius);
		for (GuardEntity ally : world.getEntitiesByClass(GuardEntity.class, box, entity -> entity.isAlive() && guard.isSameGroup(entity))) {
			allies++;
		}
		for (HostileEntity enemy : world.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive)) {
			if (!guard.isAlly(enemy)) {
				enemies++;
			}
		}
		if (enemies > allies) {
			guard.adjustMorale(-config.outnumberedPenalty / 2);
		}
	}

	private static void applyLowHealthPenalty(GuardEntity guard, GuardVillagersConfig.Morale config) {
		if (guard.getHealth() <= guard.getMaxHealth() * GuardVillagersConfig.get().survival.panicThreshold) {
			guard.adjustMorale(-config.lowHealthPenalty);
		}
	}
}

package com.guardvillagers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

public final class GuardPlayerUpgrades {
	public static final int MAX_ARMOR_LEVEL = 8;
	public static final int MAX_WEAPON_LEVEL = 5;
	public static final int MAX_SUPPORT_LEVEL = 3;
	public static final int MAX_HIRE_LEVEL = 1 + MAX_ARMOR_LEVEL + MAX_WEAPON_LEVEL + MAX_SUPPORT_LEVEL;
	private static final Codec<Integer> ARMOR_LEVEL_CODEC = Codec.intRange(0, MAX_ARMOR_LEVEL);
	private static final Codec<Integer> WEAPON_LEVEL_CODEC = Codec.intRange(0, MAX_WEAPON_LEVEL);
	private static final Codec<Integer> SUPPORT_LEVEL_CODEC = Codec.intRange(0, MAX_SUPPORT_LEVEL);

	public static final Codec<GuardPlayerUpgrades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ARMOR_LEVEL_CODEC.optionalFieldOf("armor_level", 0).forGetter(GuardPlayerUpgrades::getArmorLevel),
		WEAPON_LEVEL_CODEC.optionalFieldOf("weapon_level", 0).forGetter(GuardPlayerUpgrades::getWeaponLevel),
		SUPPORT_LEVEL_CODEC.optionalFieldOf("support_level", 0).forGetter(GuardPlayerUpgrades::getSupportLevel),
		Codec.BOOL.optionalFieldOf("healing_upgrade", false).forGetter(GuardPlayerUpgrades::hasHealingUpgradeLegacy)
	).apply(instance, GuardPlayerUpgrades::new));

	private int armorLevel;
	private int weaponLevel;
	private int supportLevel;
	private transient Runnable dirtyCallback = () -> {};

	public GuardPlayerUpgrades() {
		this(0, 0, 0, false);
	}

	private GuardPlayerUpgrades(int armorLevel, int weaponLevel, int supportLevel, boolean healingUpgradeLegacy) {
		this.armorLevel = Math.max(0, Math.min(MAX_ARMOR_LEVEL, armorLevel));
		this.weaponLevel = Math.max(0, Math.min(MAX_WEAPON_LEVEL, weaponLevel));
		int normalizedSupport = Math.max(0, Math.min(MAX_SUPPORT_LEVEL, supportLevel));
		if (normalizedSupport == 0 && healingUpgradeLegacy) {
			normalizedSupport = 1;
		}
		this.supportLevel = normalizedSupport;
	}

	public GuardPlayerUpgrades copy() {
		return new GuardPlayerUpgrades(this.armorLevel, this.weaponLevel, this.supportLevel, false);
	}

	public void setDirtyCallback(Runnable dirtyCallback) {
		this.dirtyCallback = dirtyCallback == null ? () -> {} : dirtyCallback;
	}

	public int getArmorLevel() {
		return this.armorLevel;
	}

	public int getWeaponLevel() {
		return this.weaponLevel;
	}

	public boolean hasHealingUpgrade() {
		return this.supportLevel >= 1;
	}

	private boolean hasHealingUpgradeLegacy() {
		return this.supportLevel >= 1;
	}

	public int getSupportLevel() {
		return this.supportLevel;
	}

	public boolean hasShieldUpgrade() {
		return this.supportLevel >= 2;
	}

	public boolean hasAdvancedHealingUpgrade() {
		return this.supportLevel >= 3;
	}

	public boolean upgradeArmor() {
		if (this.armorLevel >= MAX_ARMOR_LEVEL) {
			return false;
		}
		this.armorLevel++;
		this.dirtyCallback.run();
		return true;
	}

	public boolean upgradeWeapon() {
		if (this.weaponLevel >= MAX_WEAPON_LEVEL) {
			return false;
		}
		this.weaponLevel++;
		this.dirtyCallback.run();
		return true;
	}

	public boolean unlockHealingUpgrade() {
		if (this.supportLevel >= MAX_SUPPORT_LEVEL) {
			return false;
		}
		this.supportLevel++;
		this.dirtyCallback.run();
		return true;
	}

	public int getGuardCost() {
		return GuardHirePricing.getHirePrice(this.getHireLevel());
	}

	public int getHireLevel() {
		return Math.max(1, 1 + this.armorLevel + this.weaponLevel + this.supportLevel);
	}

	public int getArmorUpgradeCost() {
		return this.getArmorUpgradeCostForLevel(this.armorLevel);
	}

	public int getArmorUpgradeCostForLevel(int level) {
		int normalized = Math.max(0, Math.min(MAX_ARMOR_LEVEL - 1, level));
		return Math.min(64, 4 << normalized);
	}

	public int getWeaponUpgradeCost() {
		return this.getWeaponUpgradeCostForLevel(this.weaponLevel);
	}

	public int getWeaponUpgradeCostForLevel(int level) {
		int normalized = Math.max(0, Math.min(MAX_WEAPON_LEVEL - 1, level));
		return Math.min(64, 4 * (int) Math.pow(4, normalized));
	}

	public int getHealingUpgradeCost() {
		return switch (this.supportLevel) {
			case 0 -> 64;
			case 1 -> 128;
			case 2 -> 192;
			default -> 0;
		};
	}

	public float getHealingPerCycle() {
		return this.supportLevel >= 1 ? 2.0F : 1.0F;
	}

	public int getHealingIntervalTicks() {
		return switch (this.supportLevel) {
			case 1, 2 -> 50;
			case 3 -> 20;
			default -> 100;
		};
	}

	public ArmorDistribution getArmorDistribution() {
		return switch (this.armorLevel) {
			case 1 -> new ArmorDistribution(80, 15, 5, 0);
			case 2 -> new ArmorDistribution(60, 25, 12, 3);
			case 3 -> new ArmorDistribution(40, 35, 18, 7);
			case 4 -> new ArmorDistribution(25, 40, 23, 12);
			case 5 -> new ArmorDistribution(12, 43, 28, 17);
			case 6 -> new ArmorDistribution(6, 40, 32, 22);
			case 7 -> new ArmorDistribution(2, 36, 35, 27);
			case 8 -> new ArmorDistribution(0, 30, 35, 35);
			default -> new ArmorDistribution(100, 0, 0, 0);
		};
	}

	public ArmorTier rollArmorTier(Random random) {
		ArmorDistribution distribution = this.getArmorDistribution();
		int roll = random.nextInt(100);
		ArmorTier baseTier;
		if (roll < distribution.leather()) {
			baseTier = ArmorTier.LEATHER;
		} else if (roll < distribution.leather() + distribution.iron()) {
			baseTier = ArmorTier.IRON;
		} else if (roll < distribution.leather() + distribution.iron() + distribution.gold()) {
			baseTier = ArmorTier.GOLD;
		} else {
			baseTier = ArmorTier.DIAMOND;
		}

		int netheriteChance = switch (this.armorLevel) {
			case 6 -> 2;
			case 7 -> 5;
			case 8 -> 10;
			default -> 0;
		};
		if (baseTier == ArmorTier.DIAMOND && netheriteChance > 0 && random.nextInt(100) < netheriteChance) {
			return ArmorTier.NETHERITE;
		}

		if (baseTier == ArmorTier.LEATHER && this.armorLevel >= 2 && random.nextInt(100) < 35) {
			return ArmorTier.CHAINMAIL;
		}

		return baseTier;
	}

	public Text toArmorPercentText() {
		ArmorDistribution d = this.getArmorDistribution();
		return Text.literal("Armor odds L:" + d.leather() + "% I:" + d.iron() + "% G:" + d.gold() + "% D:" + d.diamond() + "%");
	}

	public enum ArmorTier {
		LEATHER,
		CHAINMAIL,
		IRON,
		GOLD,
		DIAMOND,
		NETHERITE
	}

	public record ArmorDistribution(int leather, int iron, int gold, int diamond) {
	}
}

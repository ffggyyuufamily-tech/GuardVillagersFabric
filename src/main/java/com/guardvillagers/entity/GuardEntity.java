package com.guardvillagers.entity;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.GuardDiplomacyManager;
import com.guardvillagers.GuardOwnershipIndex;
import com.guardvillagers.GuardPlayerUpgrades;
import com.guardvillagers.GuardReputationManager;
import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.ai.GuardAiController;
import com.guardvillagers.entity.ai.GuardAiIntent;
import com.guardvillagers.entity.ai.GuardBehaviorExecutor;
import com.guardvillagers.entity.ai.GuardCraftingSystem;
import com.guardvillagers.entity.ai.GuardMoraleSystem;
import com.guardvillagers.entity.ai.GuardMovementSlotResolver;
import com.guardvillagers.entity.ai.GuardTntSystem;
import com.guardvillagers.entity.goal.CaptainCommandGoal;
import com.guardvillagers.entity.goal.CrowdControlGoal;
import com.guardvillagers.entity.goal.ElectLeaderGoal;
import com.guardvillagers.entity.goal.FormationFollowOwnerGoal;
import com.guardvillagers.entity.goal.GuardBowAttackGoal;
import com.guardvillagers.entity.goal.GuardHomeAnchorGoal;
import com.guardvillagers.entity.goal.GuardIdleGoal;
import com.guardvillagers.entity.goal.GuardEngineeringGoal;
import com.guardvillagers.entity.goal.GuardLogisticsGoal;
import com.guardvillagers.entity.goal.GuardRallyGoal;
import com.guardvillagers.entity.goal.GuardResourceGatheringGoal;
import com.guardvillagers.entity.goal.GuardChestLootingGoal;
import com.guardvillagers.entity.goal.GuardCreeperHuntingGoal;
import com.guardvillagers.entity.goal.PerimeterPatrolGoal;
import com.guardvillagers.entity.goal.RaidTacticsGoal;
import com.guardvillagers.entity.goal.ReturnToLandGoal;
import com.guardvillagers.entity.goal.SeekAirGoal;
import com.guardvillagers.entity.goal.TacticalRetreatGoal;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.SpellcastingIllagerEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.rule.GameRules;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;
import com.guardvillagers.navigation.GuardNavigation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.guardvillagers.entity.projectile.GuardArrowEntity;

public class GuardEntity extends PathAwareEntity implements RangedAttackMob {
	private static final TrackedData<Integer> ROLE = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BEHAVIOR = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> FORMATION = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> SQUAD_LEADER = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> EXPERIENCE = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> DEBUG_ACTIVE = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Optional<BlockPos>> SYNCED_HOME = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS);
	private static final TrackedData<Integer> SYNCED_PATROL_RADIUS = DataTracker.registerData(GuardEntity.class,
			TrackedDataHandlerRegistry.INTEGER);

	private static final Identifier LEVEL_HEALTH_MODIFIER_ID = GuardVillagersMod.id("guard_level_health");
	private static final Identifier LEVEL_DAMAGE_MODIFIER_ID = GuardVillagersMod.id("guard_level_damage");
	private static final Identifier LEVEL_SPEED_MODIFIER_ID = GuardVillagersMod.id("guard_level_speed");
	private static final Identifier FOLLOW_CATCH_UP_SPEED_MODIFIER_ID = GuardVillagersMod.id("guard_follow_catch_up_speed");
	private static final double FOLLOW_CATCH_UP_SPEED_BONUS = 0.35D;
	private static final int INITIAL_SPREAD_TICKS = 5;
	private static final double INITIAL_SPREAD_SPEED = 1.0D;
	private static final double INITIAL_SPREAD_COMPLETE_DISTANCE_SQUARED = 0.45D * 0.45D;
	private static final double FOLLOW_SLOT_CATCH_UP_DISTANCE_SQUARED = 12.0D * 12.0D;
	private static final double FOLLOW_SLOT_VERTICAL_CATCH_UP_DISTANCE = 3.0D;

	private static final String OWNER_KEY = "GuardOwner";
	private static final String ROLE_KEY = "GuardRole";
	private static final String BEHAVIOR_KEY = "GuardBehavior";
	private static final String FORMATION_KEY = "GuardFormation";
	private static final String STAYING_KEY = "GuardStaying";
	private static final String FOLLOW_OVERRIDE_KEY = "GuardFollowOverride";
	private static final String SQUAD_ID_KEY = "GuardSquadId";
	private static final String SQUAD_LEADER_KEY = "GuardSquadLeader";
	private static final String EXPERIENCE_KEY = "GuardExperience";
	private static final String PLAYER_MAINHAND_KEY = "PlayerMainHand";
	private static final String PLAYER_HELMET_KEY = "PlayerHelmet";
	private static final String PLAYER_CHEST_KEY = "PlayerChest";
	private static final String PLAYER_LEGS_KEY = "PlayerLegs";
	private static final String PLAYER_FEET_KEY = "PlayerFeet";
	private static final String LOADOUT_ARMOR_LEVEL_KEY = "LoadoutArmorLevel";
	private static final String LOADOUT_WEAPON_LEVEL_KEY = "LoadoutWeaponLevel";
	private static final String LOADOUT_SUPPORT_LEVEL_KEY = "LoadoutSupportLevel";
	private static final String HAS_HOME_KEY = "HasHome";
	private static final String HOME_X_KEY = "HomeX";
	private static final String HOME_Y_KEY = "HomeY";
	private static final String HOME_Z_KEY = "HomeZ";
	private static final String PATROL_RADIUS_KEY = "PatrolRadius";
	private static final String GROUP_INDEX_KEY = "GroupIndex";
	private static final String GROUP_COLUMN_KEY = "GroupColumn";
	private static final String GROUP_NAME_KEY = "GroupName";
	private static final String SKIN_PROFILE_KEY = "GuardSkinProfile";
	private static final String GENERATED_NAME_KEY = "GeneratedName";
	private static final String SPECIAL_PROFILE_KEY = "SpecialProfile";
	private static final String NOTCH_APPLE_COOLDOWN_UNTIL_KEY = "NotchAppleCooldownUntil";
	private static final String MORALE_KEY = "GuardMorale";
	private static final String ARROW_RESERVE_KEY = "ArrowReserve";
	private static final String BUILDING_BLOCK_RESERVE_KEY = "BuildingBlockReserve";
	private static final String HAS_KNOWN_CRAFTING_TABLE_KEY = "HasKnownCraftingTable";
	private static final String CRAFTING_TABLE_X_KEY = "CraftingTableX";
	private static final String CRAFTING_TABLE_Y_KEY = "CraftingTableY";
	private static final String CRAFTING_TABLE_Z_KEY = "CraftingTableZ";
	// Legacy keys for migration
	private static final String LEGACY_HIERARCHY_ROW_KEY = "HierarchyRow";
	private static final String LEGACY_HIERARCHY_COLUMN_KEY = "HierarchyColumn";
	private static final String LEGACY_HIERARCHY_ROLE_KEY = "HierarchyRole";
	private static final String LAST_LAND_X_KEY = "LastLandX";
	private static final String LAST_LAND_Y_KEY = "LastLandY";
	private static final String LAST_LAND_Z_KEY = "LastLandZ";
	private static final String HAS_STAY_ORIGIN_KEY = "HasStayOrigin";
	private static final String STAY_ORIGIN_X_KEY = "StayOriginX";
	private static final String STAY_ORIGIN_Y_KEY = "StayOriginY";
	private static final String STAY_ORIGIN_Z_KEY = "StayOriginZ";
	public static final int STAY_RADIUS = 3;
	private static final String HAS_LAST_LAND_KEY = "HasLastLand";
	private static final int MIN_GROUP_INDEX = -1;
	private static final int MAX_GROUP_INDEX = Integer.MAX_VALUE / 2;
	private static final int MISSING_LOADOUT_LEVEL = -1;
	private static final String DEFAULT_UNASSIGNED_GROUP_NAME = "Unassigned";
	private static final int MAX_SKIN_PROFILE_LENGTH = 64;
	private static final String DEBUG_NAME_PREFIX = "[DBG] ";
	private static final int SPECIAL_PROFILE_NONE = 0;
	private static final int SPECIAL_PROFILE_NOTCH = 1;
	private static final int SPECIAL_PROFILE_JACK_BLACK = 2;
	private static final int SPECIAL_PROFILE_JASON_MOMOA = 3;
	private static final long NOTCH_APPLE_COOLDOWN_TICKS = 15L * 60L * 20L;
	private static final float NOTCH_APPLE_TRIGGER_HEALTH_RATIO = 0.30F;
	private static final int NOTCH_ROLL = 500;
	private static final int JACK_BLACK_ROLL = 400;
	private static final int JASON_MOMOA_ROLL = 400;

	private static final String[] FIRST_NAME_POOL = {
			"Aaron", "Abel", "Abigail", "Adam", "Adrian", "Aiden", "Alan", "Albert", "Alec", "Alexa",
			"Alexander", "Alice", "Alicia", "Allison", "Amanda", "Amelia", "Amy", "Andrea", "Andrew", "Angela",
			"Anna", "Anthony", "Arthur", "Ashley", "Aubrey", "Audrey", "Austin", "Ava", "Barbara", "Beatrice",
			"Benjamin", "Bethany", "Blake", "Brandon", "Brenda", "Brian", "Brianna", "Brittany", "Brooke", "Caleb",
			"Cameron", "Carla", "Carlos", "Carmen", "Caroline", "Carter", "Catherine", "Charles", "Charlotte", "Chloe",
			"Chris", "Christian", "Christina", "Claire", "Clara", "Cole", "Colin", "Connor", "Courtney", "Crystal",
			"Daisy", "Dakota", "Daniel", "Danielle", "David", "Dawn", "Dean", "Deborah", "Dennis", "Derek",
			"Diana", "Dominic", "Donna", "Dylan", "Eleanor", "Elena", "Eli", "Elijah", "Elizabeth", "Ella",
			"Emily", "Emma", "Eric", "Erica", "Ethan", "Eva", "Evan", "Evelyn", "Faith", "Felix",
			"Fiona", "Frank", "Gabriel", "Gavin", "George", "Georgia", "Grace", "Grant", "Gregory", "Hailey",
			"Hannah", "Harper", "Hazel", "Heather", "Helen", "Henry", "Holly", "Hunter", "Ian", "Irene",
			"Isaac", "Isabel", "Isabella", "Jack", "Jacob", "Jade", "Jamal", "James", "Jamie", "Jared",
			"Jason", "Jasmine", "Jayden", "Jeffrey", "Jenna", "Jennifer", "Jeremy", "Jerry", "Jesse", "Jessica",
			"Joan", "Jordan", "Joseph", "Joshua", "Joyce", "Juan", "Julia", "Julian", "Justin", "Kaitlyn",
			"Karen", "Katherine", "Kathleen", "Kayla", "Keith", "Kelly", "Kevin", "Kimberly", "Kyle", "Lance",
			"Laura", "Lauren", "Leah", "Leo", "Liam", "Lillian", "Lily", "Logan", "Lucas", "Lucy",
			"Luke", "Madeline", "Madison", "Maya", "Megan", "Melanie", "Michael", "Michelle", "Mila", "Molly",
			"Natalie", "Nathan", "Nicholas", "Nicole", "Noah", "Nora", "Olivia", "Owen", "Paige", "Pamela",
			"Patrick", "Paul", "Peter", "Rachel", "Rebecca", "Richard", "Robert", "Ryan", "Samantha", "Sarah",
			"Scott", "Sean", "Sophia", "Stephen", "Taylor", "Thomas", "Tristan", "Tyler", "Victoria", "William",
			"Wyatt", "Zachary"
	};

	private static final String[] LAST_NAME_POOL = {
			"Abbott", "Adams", "Alexander", "Allen", "Anderson", "Armstrong", "Atkins", "Austin", "Bailey", "Baker",
			"Barnes", "Barrett", "Bennett", "Bishop", "Black", "Blair", "Bowman", "Boyd", "Bradley", "Brooks",
			"Brown", "Bryant", "Butler", "Campbell", "Carter", "Castillo", "Chambers", "Chapman", "Clark", "Coleman",
			"Collins", "Cook", "Cooper", "Cox", "Crawford", "Cruz", "Cunningham", "Daniels", "Davis", "Dean",
			"Dixon", "Douglas", "Duncan", "Edwards", "Ellis", "Evans", "Ferguson", "Fisher", "Fleming", "Flores",
			"Ford", "Foster", "Fox", "Freeman", "Garcia", "Gardner", "Garrett", "Gibson", "Gomez", "Gonzalez",
			"Gordon", "Graham", "Grant", "Gray", "Green", "Griffin", "Hall", "Hamilton", "Harris", "Harrison",
			"Hart", "Hayes", "Henderson", "Hernandez", "Hill", "Hoffman", "Howard", "Hudson", "Hughes", "Hunt",
			"Jackson", "James", "Jenkins", "Johnson", "Jordan", "Kelly", "Kennedy", "Kim", "King", "Knight",
			"Lane", "Larson", "Lawrence", "Lee", "Lewis", "Long", "Lopez", "Marshall", "Martin", "Martinez",
			"Mason", "Matthews", "Miller", "Mitchell", "Moore", "Morales", "Morgan", "Morris", "Murphy", "Myers",
			"Nelson", "Nguyen", "Nichols", "Ortiz", "Owens", "Palmer", "Parker", "Patel", "Patterson", "Payne",
			"Perez", "Perry", "Peterson", "Phillips", "Pierce", "Porter", "Powell", "Price", "Ramirez", "Reed",
			"Reyes", "Reynolds", "Rice", "Richardson", "Rivera", "Roberts", "Robinson", "Rodriguez", "Rogers", "Ross",
			"Russell", "Sanchez", "Sanders", "Scott", "Shaw", "Simmons", "Smith", "Snyder", "Spencer", "Stevens",
			"Stewart", "Stone", "Sullivan", "Taylor", "Thomas", "Thompson", "Torres", "Turner", "Vargas", "Vasquez",
			"Walker", "Wallace", "Ward", "Washington", "Watkins", "Watson", "Weaver", "Webb", "Wells", "West",
			"Wheeler", "White", "Williams", "Wilson", "Wood", "Woods", "Wright", "Young"
	};

	/**
	 * Guards in different dimensions (Overworld / Nether / End) tick on separate
	 * threads, and hire-identity registration hooks are called from each thread's
	 * entity tick and from NBT load. A plain HashMap would be corrupted by
	 * concurrent put/remove. Both the outer map and the inner Set must be
	 * thread-safe.
	 */
	private static final Map<UUID, Set<String>> OWNER_USED_NAMES = new ConcurrentHashMap<>();

	private static final Map<Item, Integer> SWORD_SCORE = Map.ofEntries(
			Map.entry(Items.WOODEN_SWORD, 1),
			Map.entry(Items.STONE_SWORD, 2),
			Map.entry(Items.GOLDEN_SWORD, 2),
			Map.entry(Items.IRON_SWORD, 3),
			Map.entry(Items.DIAMOND_SWORD, 4),
			Map.entry(Items.NETHERITE_SWORD, 5));

	private static final Map<Item, ArmorDefinition> ARMOR_DEFINITIONS = Map.ofEntries(
			Map.entry(Items.LEATHER_HELMET, new ArmorDefinition(EquipmentSlot.HEAD, 1)),
			Map.entry(Items.CHAINMAIL_HELMET, new ArmorDefinition(EquipmentSlot.HEAD, 2)),
			Map.entry(Items.GOLDEN_HELMET, new ArmorDefinition(EquipmentSlot.HEAD, 2)),
			Map.entry(Items.IRON_HELMET, new ArmorDefinition(EquipmentSlot.HEAD, 3)),
			Map.entry(Items.DIAMOND_HELMET, new ArmorDefinition(EquipmentSlot.HEAD, 4)),
			Map.entry(Items.NETHERITE_HELMET, new ArmorDefinition(EquipmentSlot.HEAD, 5)),
			Map.entry(Items.LEATHER_CHESTPLATE, new ArmorDefinition(EquipmentSlot.CHEST, 1)),
			Map.entry(Items.CHAINMAIL_CHESTPLATE, new ArmorDefinition(EquipmentSlot.CHEST, 2)),
			Map.entry(Items.GOLDEN_CHESTPLATE, new ArmorDefinition(EquipmentSlot.CHEST, 2)),
			Map.entry(Items.IRON_CHESTPLATE, new ArmorDefinition(EquipmentSlot.CHEST, 3)),
			Map.entry(Items.DIAMOND_CHESTPLATE, new ArmorDefinition(EquipmentSlot.CHEST, 4)),
			Map.entry(Items.NETHERITE_CHESTPLATE, new ArmorDefinition(EquipmentSlot.CHEST, 5)),
			Map.entry(Items.LEATHER_LEGGINGS, new ArmorDefinition(EquipmentSlot.LEGS, 1)),
			Map.entry(Items.CHAINMAIL_LEGGINGS, new ArmorDefinition(EquipmentSlot.LEGS, 2)),
			Map.entry(Items.GOLDEN_LEGGINGS, new ArmorDefinition(EquipmentSlot.LEGS, 2)),
			Map.entry(Items.IRON_LEGGINGS, new ArmorDefinition(EquipmentSlot.LEGS, 3)),
			Map.entry(Items.DIAMOND_LEGGINGS, new ArmorDefinition(EquipmentSlot.LEGS, 4)),
			Map.entry(Items.NETHERITE_LEGGINGS, new ArmorDefinition(EquipmentSlot.LEGS, 5)),
			Map.entry(Items.LEATHER_BOOTS, new ArmorDefinition(EquipmentSlot.FEET, 1)),
			Map.entry(Items.CHAINMAIL_BOOTS, new ArmorDefinition(EquipmentSlot.FEET, 2)),
			Map.entry(Items.GOLDEN_BOOTS, new ArmorDefinition(EquipmentSlot.FEET, 2)),
			Map.entry(Items.IRON_BOOTS, new ArmorDefinition(EquipmentSlot.FEET, 3)),
			Map.entry(Items.DIAMOND_BOOTS, new ArmorDefinition(EquipmentSlot.FEET, 4)),
			Map.entry(Items.NETHERITE_BOOTS, new ArmorDefinition(EquipmentSlot.FEET, 5)));

	private MeleeAttackGoal meleeGoal;
	private GuardBowAttackGoal rangedGoal;
	private final GuardAiController aiController;

	private UUID ownerUuid;
	private UUID squadId;
	private boolean staying;
	private BlockPos stayOrigin;
	private boolean followOverride;
	private BlockPos home;
	private int patrolRadius = 0;
	private BlockPos lastLandPos;
	private BlockPos lastLandCheckPos;
	private int groupIndex = MIN_GROUP_INDEX;
	private int groupColumn = 1;
	private String groupName = DEFAULT_UNASSIGNED_GROUP_NAME;
	private String skinProfileId = "";
	private String generatedName = "";
	private int specialProfile = SPECIAL_PROFILE_NONE;
	private long notchAppleCooldownUntil;
	private UUID lastRegisteredOwner;
	private String lastRegisteredOwnerName = "";
	private boolean playerMainHand;
	private boolean catchUpSpeedActive;
	private boolean initialSpreadResolved;
	private BlockPos pendingInitialSpreadTarget;
	private int loadoutArmorLevel;
	private int loadoutWeaponLevel;
	private int loadoutSupportLevel;
	private int morale = GuardVillagersConfig.get().morale.defaultMorale;
	private int arrowReserve = 32;
	private int buildingBlockReserve = 16;
	private final EnumMap<EquipmentSlot, Boolean> playerArmor = new EnumMap<>(EquipmentSlot.class);
	private final GuardInventory inventory = new GuardInventory();
	private BlockPos knownCraftingTablePos;
	private int tntCooldown;
	private int tntRetreatTicks;

	public GuardEntity(EntityType<? extends PathAwareEntity> entityType, net.minecraft.world.World world) {
		super(entityType, world);
		this.aiController = new GuardAiController(this);
		this.getNavigation().setCanOpenDoors(true);
		this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER, 8.0F);
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET)) {
			this.playerArmor.put(slot, false);
		}
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0D)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.32D)
				.add(EntityAttributes.ATTACK_DAMAGE, 5.0D)
				.add(EntityAttributes.FOLLOW_RANGE, 32.0D);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		if ((spawnReason == SpawnReason.SPAWN_ITEM_USE || spawnReason == SpawnReason.DISPENSER
				|| spawnReason == SpawnReason.COMMAND) && this.getMainHandStack().isEmpty()) {
			this.applyNaturalLoadout(world.toServerWorld());
			this.setBehavior(GuardBehavior.random(world.toServerWorld().getRandom()));
		}
		return data;
	}

	@Override
	protected net.minecraft.entity.ai.pathing.EntityNavigation createNavigation(net.minecraft.world.World world) {
		return new com.guardvillagers.navigation.GuardNavigation(this, world);
	}

	@Override
	protected void initGoals() {
		this.meleeGoal = new MeleeAttackGoal(this, 1.2D, true);
		this.rangedGoal = new GuardBowAttackGoal(this);

		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new SeekAirGoal(this, 1.3D));
		this.goalSelector.add(2, new ReturnToLandGoal(this, 1.2D));
		// priority 3 = combat goals (added by updateCombatGoals())
		this.goalSelector.add(4, new TacticalRetreatGoal(this));
		this.goalSelector.add(5, new GuardLogisticsGoal(this));
		this.goalSelector.add(6, new LongDoorInteractGoal(this, true));
		this.goalSelector.add(7, new GuardEngineeringGoal(this));
		this.goalSelector.add(8, new GuardRallyGoal(this, 1.2D));
		this.goalSelector.add(9, new FormationFollowOwnerGoal(this, 1.0D));
		this.goalSelector.add(10, new GuardHomeAnchorGoal(this, 1.0D));
		this.goalSelector.add(11, new RaidTacticsGoal(this, 1.2D));
		this.goalSelector.add(12, new PerimeterPatrolGoal(this, 1.0D));
		this.goalSelector.add(13, new CrowdControlGoal(this, 1.0D));
		this.goalSelector.add(14, new GuardCreeperHuntingGoal(this));
		this.goalSelector.add(15, new GuardResourceGatheringGoal(this));
		this.goalSelector.add(16, new GuardChestLootingGoal(this));
		this.goalSelector.add(17, new GuardIdleGoal(this, 0.8D));
		this.goalSelector.add(18, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(19, new LookAroundGoal(this));

		this.targetSelector.add(1, new ElectLeaderGoal(this, 48.0D));
		this.targetSelector.add(2, new CaptainCommandGoal(this));
		this.updateCombatGoals();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, GuardRole.SWORDSMAN.getId());
		builder.add(BEHAVIOR, GuardBehavior.DEFENSIVE.getId());
		builder.add(FORMATION, FormationType.FOLLOW.getId());
		builder.add(SQUAD_LEADER, false);
		builder.add(EXPERIENCE, 0);
		builder.add(DEBUG_ACTIVE, false);
		builder.add(SYNCED_HOME, Optional.empty());
		builder.add(SYNCED_PATROL_RADIUS, 0);
	}

	public boolean isDebugActive() {
		return this.dataTracker.get(DEBUG_ACTIVE);
	}

	public void setDebugActive(boolean active) {
		this.dataTracker.set(DEBUG_ACTIVE, active);
	}

	public GuardRole getRole() {
		return GuardRole.fromId(this.dataTracker.get(ROLE));
	}

	public void setRole(GuardRole role) {
		this.dataTracker.set(ROLE, role.getId());
		if (this.getEntityWorld() instanceof ServerWorld) {
			this.updateCombatGoals();
		}
	}

	public GuardBehavior getBehavior() {
		return GuardBehavior.fromId(this.dataTracker.get(BEHAVIOR));
	}

	public void setBehavior(GuardBehavior behavior) {
		this.dataTracker.set(BEHAVIOR, behavior.getId());
	}

	public FormationType getFormationType() {
		return FormationType.fromId(this.dataTracker.get(FORMATION));
	}

	public void setFormationType(FormationType formationType) {
		FormationType resolved = formationType == null ? FormationType.FOLLOW : formationType;
		this.dataTracker.set(FORMATION, resolved.getId());
	}

	public int getExperience() {
		return this.dataTracker.get(EXPERIENCE);
	}

	public void setExperience(int experience) {
		this.dataTracker.set(EXPERIENCE, Math.max(0, experience));
		this.applyLevelModifiers();
	}

	public void addExperience(int amount) {
		if (amount <= 0) {
			return;
		}
		int beforeLevel = this.getLevel();
		this.setExperience(this.getExperience() + amount);
		if (this.getLevel() > beforeLevel) {
			this.heal(2.0F);
		}
	}

	public int getLevel() {
		return Math.min(10, 1 + (this.getExperience() / 120));
	}

	public boolean hasOwner() {
		return this.ownerUuid != null;
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public boolean isOwnedBy(UUID playerUuid) {
		return this.ownerUuid != null && this.ownerUuid.equals(playerUuid);
	}

	public void setOwnerUuid(UUID ownerUuid) {
		UUID previousOwner = this.ownerUuid;
		this.ownerUuid = ownerUuid;
		if (ownerUuid != null && this.squadId == null) {
			this.squadId = ownerUuid;
		}
		if (previousOwner != null && !previousOwner.equals(ownerUuid) && !this.lastRegisteredOwnerName.isBlank()) {
			unregisterOwnerName(this.lastRegisteredOwner != null ? this.lastRegisteredOwner : previousOwner,
					this.lastRegisteredOwnerName);
			this.lastRegisteredOwner = null;
			this.lastRegisteredOwnerName = "";
		}
		if (ownerUuid == null) {
			this.setCustomNameVisible(false);
		}
		if (this.getEntityWorld() instanceof ServerWorld) {
			ServerWorld world = (ServerWorld) this.getEntityWorld();
			if (ownerUuid != null) {
				this.ensureHireIdentity(world);
				this.syncRegisteredOwnerName();
			}
			GuardOwnershipIndex.track(this);
		} else if (ownerUuid == null) {
			GuardOwnershipIndex.untrack(this);
		}
	}

	public boolean hasGeneratedIdentity() {
		return !this.generatedName.isBlank();
	}

	public String getGeneratedName() {
		return this.generatedName;
	}

	private void ensureHireIdentity(ServerWorld world) {
		if (this.ownerUuid == null || !this.generatedName.isBlank()) {
			if (this.ownerUuid != null && this.getCustomName() == null && !this.generatedName.isBlank()) {
				this.setCustomName(Text.literal(this.generatedName));
			}
			if (this.ownerUuid != null) {
				this.setCustomNameVisible(true);
			}
			return;
		}

		Text customName = this.getCustomName();
		if (customName != null) {
			String existing = customName.getString().trim();
			if (!existing.isBlank() && !existing.startsWith(DEBUG_NAME_PREFIX)) {
				this.generatedName = existing;
				this.specialProfile = this.inferSpecialProfileFromName(existing);
				this.setCustomNameVisible(true);
				return;
			}
		}

		GuardNameRoll roll = this.rollUniqueGuardName(world, this.ownerUuid);
		this.generatedName = roll.name();
		this.specialProfile = roll.specialProfile();
		this.notchAppleCooldownUntil = 0L;
		this.setCustomName(Text.literal(this.generatedName));
		this.setCustomNameVisible(true);
	}

	public UUID getSquadId() {
		return this.squadId;
	}

	public boolean hasSquad() {
		return this.squadId != null;
	}

	public void setSquadId(UUID squadId) {
		this.squadId = squadId;
	}

	public boolean isSquadLeader() {
		return this.dataTracker.get(SQUAD_LEADER);
	}

	public void setSquadLeader(boolean squadLeader) {
		this.dataTracker.set(SQUAD_LEADER, squadLeader);
	}

	public boolean isSameSquad(GuardEntity other) {
		return this.squadId != null && this.squadId.equals(other.squadId);
	}

	public boolean isStaying() {
		return this.staying;
	}

	public void setStaying(boolean staying) {
		this.staying = staying;
		if (staying) {
			this.stayOrigin = this.getBlockPos();
			this.setFollowOverride(false);
			this.clearCombatTarget();
		} else {
			this.stayOrigin = null;
		}
	}

	public BlockPos getStayOrigin() {
		return this.stayOrigin;
	}

	public boolean hasFollowOverride() {
		return this.followOverride;
	}

	public void setFollowOverride(boolean followOverride) {
		this.followOverride = followOverride;
		if (!followOverride) {
			this.setCatchUpSpeedActive(false);
		}
	}

	public boolean isRetreating() {
		return this.aiController.isRetreating();
	}

	public void setRetreating(boolean retreating) {
		this.aiController.setRetreating(retreating);
	}

	public Optional<BlockPos> getHome() {
		return Optional.ofNullable(this.home);
	}

	public int getPatrolRadius() {
		return this.patrolRadius;
	}

	public void setHome(BlockPos home, int patrolRadius) {
		this.setFollowOverride(false);
		this.home = home.toImmutable();
		this.setPatrolRadius(patrolRadius);
		this.syncHomeData();
	}

	public void setHome(BlockPos home) {
		this.setHome(home, Math.max(24, this.patrolRadius));
	}

	public void clearHome() {
		this.setFollowOverride(false);
		this.home = null;
		this.patrolRadius = 0;
		this.syncHomeData();
	}

	public void setPatrolRadius(int patrolRadius) {
		this.patrolRadius = MathHelper.clamp(patrolRadius, 0, 128);
		this.syncHomeData();
	}

	private void syncHomeData() {
		this.dataTracker.set(SYNCED_HOME, Optional.ofNullable(this.home));
		this.dataTracker.set(SYNCED_PATROL_RADIUS, this.patrolRadius);
	}

	public Optional<BlockPos> getSyncedHome() {
		return this.dataTracker.get(SYNCED_HOME);
	}

	public int getSyncedPatrolRadius() {
		return this.dataTracker.get(SYNCED_PATROL_RADIUS);
	}

	public BlockPos getLastLandPos() {
		return this.lastLandPos;
	}

	private void updateLastLandPos() {
		if (this.isTouchingWater() || !this.isOnGround()) {
			return;
		}
		BlockPos currentPos = this.getBlockPos();
		if (currentPos.equals(this.lastLandCheckPos)) {
			return;
		}
		this.lastLandCheckPos = currentPos;
		this.lastLandPos = currentPos.toImmutable();
	}

	public int getGroupIndex() {
		return this.groupIndex;
	}

	public void setGroupIndex(int groupIndex) {
		this.groupIndex = MathHelper.clamp(groupIndex, MIN_GROUP_INDEX, MAX_GROUP_INDEX);
	}

	public int getGroupColumn() {
		return this.groupColumn;
	}

	public void setGroupColumn(int groupColumn) {
		this.groupColumn = MathHelper.clamp(groupColumn, 0, 2);
	}

	public String getGroupName() {
		if (this.groupName == null || this.groupName.isBlank()) {
			return DEFAULT_UNASSIGNED_GROUP_NAME;
		}
		return this.groupName;
	}

	public void setGroupName(String name) {
		if (name == null || name.isBlank()) {
			this.groupName = DEFAULT_UNASSIGNED_GROUP_NAME;
			return;
		}
		String trimmed = name.trim();
		this.groupName = trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
	}

	public String getSkinProfileId() {
		return this.skinProfileId;
	}

	public void setSkinProfileId(String skinProfileId) {
		if (skinProfileId == null || skinProfileId.isBlank()) {
			this.skinProfileId = "";
			return;
		}
		String trimmed = skinProfileId.trim();
		this.skinProfileId = trimmed.length() <= MAX_SKIN_PROFILE_LENGTH ? trimmed
				: trimmed.substring(0, MAX_SKIN_PROFILE_LENGTH);
	}

	public void clearCombatTarget() {
		this.aiController.clearCombatTarget();
	}

	public boolean canTargetWithinZone(BlockPos pos) {
		if (this.followOverride) {
			return true;
		}
		if (this.home == null || this.patrolRadius <= 0) {
			return true;
		}
		return this.home.getSquaredDistance(pos) <= (double) this.patrolRadius * this.patrolRadius;
	}

	public void rallyTo(BlockPos rallyPoint, int ticks) {
		this.aiController.rallyTo(rallyPoint, ticks);
		this.staying = false;
	}

	public boolean canExecuteBehaviorGoals() {
		return this.aiController.canExecuteBehaviorGoals();
	}

	public boolean canFollowOwnerFormation() {
		return this.aiController.canFollowOwnerFormation();
	}

	public GuardNavigation getGuardNavigation() {
		return (GuardNavigation) this.getNavigation();
	}

	public Vec3d resolveMovementSlot(ServerWorld world, Vec3d anchor, double spacing, boolean allowCenter) {
		return GuardMovementSlotResolver.resolveDynamicSlot(world, this, anchor, spacing, allowCenter);
	}

	public BlockPos resolveGroundMovementSlot(ServerWorld world, BlockPos anchor, double spacing, boolean allowCenter) {
		return GuardMovementSlotResolver.resolveGroundSlot(world, this, anchor, spacing, allowCenter);
	}

	private double getSlotSpacing() {
		return GuardVillagersConfig.get().formations.slotSpacing;
	}

	public Vec3d resolveFollowSlot(ServerWorld world, ServerPlayerEntity owner) {
		double ownerDistanceSq = this.squaredDistanceTo(owner);
		double verticalDelta = Math.abs(owner.getY() - this.getY());
		BlockPos groundedSlot = ownerDistanceSq > FOLLOW_SLOT_CATCH_UP_DISTANCE_SQUARED
				|| verticalDelta > FOLLOW_SLOT_VERTICAL_CATCH_UP_DISTANCE
						? GuardMovementSlotResolver.resolveFollowCatchUpSlot(
								world,
								this,
								new Vec3d(owner.getX(), owner.getY(), owner.getZ()),
								this.getSlotSpacing())
						: this.resolveGroundMovementSlot(world, owner.getBlockPos(), this.getSlotSpacing(), false);
		return Vec3d.ofBottomCenter(groundedSlot);
	}

	public Vec3d resolveCombatApproachSlot(ServerWorld world, LivingEntity target) {
		Vec3d offset = this.resolveTacticalCombatOffset(target);
		Vec3d anchor = new Vec3d(target.getX(), target.getY(), target.getZ()).add(offset);
		BlockPos groundedSlot = this.resolveGroundMovementSlot(world,
				BlockPos.ofFloored(anchor.x, target.getBlockY(), anchor.z),
				this.getSlotSpacing(),
				false);
		return Vec3d.ofBottomCenter(groundedSlot);
	}

	private Vec3d resolveTacticalCombatOffset(LivingEntity target) {
		GuardVillagersConfig.Formations config = GuardVillagersConfig.get().formations;
		double scatter = GuardMoraleSystem.formationScatterMultiplier(this);
		Vec3d away = this.getEntityPos().subtract(target.getEntityPos());
		if (away.lengthSquared() < 1.0E-4D) {
			double seedAngle = Math.toRadians(Math.floorMod(this.getUuid().hashCode(), 360));
			away = new Vec3d(Math.cos(seedAngle), 0.0D, Math.sin(seedAngle));
		}

		FormationType formation = this.resolveActiveCombatFormation(target);
		Vec3d base = away.normalize();
		Vec3d flank = new Vec3d(-base.z, 0.0D, base.x);
		int side = Math.floorMod(this.getUuid().hashCode(), 2) == 0 ? 1 : -1;
		float flankChance = GuardVillagersConfig.get().combat.flankChance;
		double flankWeight = (this.getRole() == GuardRole.BOWMAN ? config.bowmanFlankWeight : config.meleeFlankWeight) * flankChance * scatter;
		double distance = this.getRole() == GuardRole.BOWMAN ? config.spacing + config.bowmanExtraDistance : config.spacing;
		if (formation == FormationType.SHIELD_WALL) {
			distance = this.getRole() == GuardRole.BOWMAN
					? config.spacing + config.shieldWallBowmanExtraDistance
					: config.spacing + config.shieldWallSpacing;
			flankWeight = (this.getGroupColumn() - 1) * scatter;
		} else if (formation == FormationType.PHALANX) {
			distance = config.spacing + this.getGroupIndexOffset() * config.phalanxDepth;
			flankWeight = (this.getGroupColumn() - 1) * config.phalanxColumnSpacing * scatter;
		} else if (formation == FormationType.WEDGE) {
			int row = this.getGroupIndexOffset();
			distance = Math.max(1.5D, config.spacing - row * config.wedgeSpacing);
			flankWeight = (this.getGroupColumn() - 1) * (config.wedgeBaseColumnSpacing + row * config.wedgeColumnGrowth) * scatter;
		} else if (formation == FormationType.CIRCLE_DEFENSE || formation == FormationType.CIRCLE) {
			double angle = Math.toRadians(Math.floorMod(this.getUuid().hashCode(), 360));
			double radius = this.getRole() == GuardRole.BOWMAN
					? config.spacing + config.circleBowmanExtraDistance
					: config.spacing + config.circleRadius;
			return new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle)).multiply(radius * scatter);
		}
		if (scatter != 1.0D && scatter > 1.0D) {
			double jitter = (this.getRandom().nextDouble() - 0.5D) * (scatter - 1.0D);
			flankWeight += jitter;
		}
		return base.multiply(distance).add(flank.multiply(side * flankWeight));
	}

	private FormationType resolveActiveCombatFormation(LivingEntity target) {
		if (!GuardVillagersConfig.get().formations.enabled) {
			return this.getFormationType();
		}
		FormationType configured = this.getFormationType();
		if (configured == FormationType.SHIELD_WALL
				|| configured == FormationType.PHALANX
				|| configured == FormationType.WEDGE
				|| configured == FormationType.CIRCLE_DEFENSE) {
			return configured;
		}
		if (target instanceof WardenEntity || target instanceof WitherEntity || target instanceof SpellcastingIllagerEntity) {
			return FormationType.CIRCLE_DEFENSE;
		}
		if (target instanceof RaiderEntity || this.isBehaviorExecutor(GuardBehaviorExecutor.RAID_OFFENSIVE)) {
			return FormationType.WEDGE;
		}
		if (this.isBehaviorExecutor(GuardBehaviorExecutor.RAID_DEFENSIVE) || this.getBehavior() == GuardBehavior.DEFENSIVE) {
			return FormationType.SHIELD_WALL;
		}
		return configured;
	}

	private int getGroupIndexOffset() {
		return Math.max(0, this.groupIndex);
	}

	public void setCatchUpSpeedActive(boolean catchUpSpeedActive) {
		if (this.catchUpSpeedActive == catchUpSpeedActive) {
			return;
		}

		this.catchUpSpeedActive = catchUpSpeedActive;
		EntityAttributeInstance speedInstance = this.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
		if (speedInstance == null) {
			return;
		}

		speedInstance.removeModifier(FOLLOW_CATCH_UP_SPEED_MODIFIER_ID);
		if (catchUpSpeedActive) {
			speedInstance.addTemporaryModifier(new EntityAttributeModifier(
					FOLLOW_CATCH_UP_SPEED_MODIFIER_ID,
					FOLLOW_CATCH_UP_SPEED_BONUS,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	public boolean shouldTacticallyRetreat() {
		return this.aiController.shouldTacticallyRetreat();
	}

	public boolean shouldContinueRetreat() {
		return this.aiController.shouldContinueRetreat();
	}

	public BlockPos findSafeRetreatPoint(ServerWorld world) {
		GuardVillagersConfig.Combat config = GuardVillagersConfig.get().combat;
		VillagerEntity bestCleric = null;
		double bestDistance = Double.MAX_VALUE;
		for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class,
				this.getBoundingBox().expand(config.retreatSearchRadius), VillagerEntity::isAlive)) {
			if (!villager.getVillagerData().profession().matchesKey(VillagerProfession.CLERIC)) {
				continue;
			}
			double distanceSq = this.squaredDistanceTo(villager);
			if (distanceSq < bestDistance) {
				bestDistance = distanceSq;
				bestCleric = villager;
			}
		}
		if (bestCleric != null) {
			return this.resolveGroundMovementSlot(world, bestCleric.getBlockPos(), this.getSlotSpacing(), false);
		}

		Optional<BlockPos> bedPos = world.getPointOfInterestStorage().getNearestPosition(
				entry -> entry.matchesKey(PointOfInterestTypes.HOME),
				this.getBlockPos(),
				(int) config.retreatSearchRadius,
				PointOfInterestStorage.OccupationStatus.ANY);
		if (bedPos.isPresent()) {
			return this.resolveGroundMovementSlot(world, bedPos.get(), this.getSlotSpacing(), false);
		}

		if (this.home != null) {
			return this.resolveGroundMovementSlot(world, this.home, this.getSlotSpacing(), false);
		}

		LivingEntity target = this.aiController.getTrackedCombatTarget(world);
		if (target != null) {
			Vec3d away = this.getEntityPos().subtract(target.getEntityPos()).normalize().multiply(config.retreatFleeDistance);
			Vec3d destination = this.getEntityPos().add(away);
			BlockPos blockPos = new BlockPos((int) Math.floor(destination.x), (int) Math.floor(destination.y),
					(int) Math.floor(destination.z));
			BlockPos anchor = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockPos);
			return this.resolveGroundMovementSlot(world, anchor, this.getSlotSpacing(), false);
		}

		return this.getBlockPos();
	}

	public ServerPlayerEntity resolveOwner(ServerWorld world) {
		if (!this.hasOwner()) {
			return null;
		}
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(this.ownerUuid);
		if (owner == null || owner.getEntityWorld() != world) {
			return null;
		}
		return owner;
	}

	public void assignRandomRole(ServerWorld world) {
		this.setRole(GuardRole.random(world.getRandom()));
	}

	public void applyNaturalLoadout(ServerWorld world) {
		this.assignRandomRole(world);
		this.setBehavior(GuardBehavior.random(world.getRandom()));
		this.setFormationType(FormationType.FOLLOW);
		this.setGroupIndex(MIN_GROUP_INDEX);
		this.setGroupColumn(world.getRandom().nextBetween(0, 2));
		this.setGroupName(DEFAULT_UNASSIGNED_GROUP_NAME);
		this.storeLoadoutUpgrades(new GuardPlayerUpgrades());
		this.equipGuardGear(world, 0, new GuardPlayerUpgrades());
		this.getInventory().addItem(Items.TNT, 3); // Starting TNT supply
	}

	public void applyPurchasedLoadout(ServerWorld world, GuardPlayerUpgrades upgrades) {
		GuardPlayerUpgrades storedUpgrades = upgrades == null ? new GuardPlayerUpgrades() : upgrades.copy();
		if (!this.hasOwner()) {
			this.assignRandomRole(world);
		}
		this.setBehavior(GuardBehavior.DEFENSIVE);
		this.setFormationType(FormationType.FOLLOW);
		this.setGroupIndex(MIN_GROUP_INDEX);
		this.setGroupColumn(world.getRandom().nextBetween(0, 2));
		this.setGroupName(DEFAULT_UNASSIGNED_GROUP_NAME);
		this.storeLoadoutUpgrades(storedUpgrades);
		this.equipGuardGear(world, storedUpgrades.getWeaponLevel(), storedUpgrades);
		this.getInventory().addItem(Items.TNT, 3); // Starting TNT supply
	}

	private void equipGuardGear(ServerWorld world, int weaponLevel, GuardPlayerUpgrades upgrades) {
		if (this.getRole() == GuardRole.SWORDSMAN) {
			Item sword = switch (weaponLevel) {
				case 1 -> Items.IRON_SWORD;
				case 2, 3, 4, 5 -> Items.DIAMOND_SWORD;
				default -> Items.STONE_SWORD;
			};
			ItemStack swordStack = new ItemStack(sword);
			int sharpnessLevel = switch (weaponLevel) {
				case 1 -> 1;
				case 2 -> 2;
				default -> 3;
			};
			this.applyEnchantment(world, swordStack, Enchantments.SHARPNESS, sharpnessLevel);
			this.equipStack(EquipmentSlot.MAINHAND, swordStack);
		} else {
			ItemStack bowStack = new ItemStack(Items.BOW);
			this.applyEnchantment(world, bowStack, Enchantments.POWER, Math.min(5, Math.max(1, weaponLevel + 1)));
			this.equipStack(EquipmentSlot.MAINHAND, bowStack);
		}

		this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
		this.playerMainHand = false;
		if (upgrades.hasShieldUpgrade()) {
			this.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			this.setEquipmentDropChance(EquipmentSlot.OFFHAND, 0.0F);
		} else if (this.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD)) {
			this.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}

		this.equipArmorPieces(world, upgrades);
	}

	private void equipArmorPieces(ServerWorld world, GuardPlayerUpgrades upgrades) {
		int armorLevel = upgrades.getArmorLevel();
		int protectionLevel = Math.min(4, Math.max(0, armorLevel / 2));
		GuardPlayerUpgrades.ArmorTier helmetTier = this.rollArmorTierForLoadout(upgrades);
		this.equipArmorPiece(world, EquipmentSlot.HEAD, getArmorItemForSlot(helmetTier, EquipmentSlot.HEAD),
				protectionLevel);

		for (EquipmentSlot slot : List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			GuardPlayerUpgrades.ArmorTier tier = this.rollConstrainedArmorTier(upgrades, helmetTier);
			this.equipArmorPiece(world, slot, getArmorItemForSlot(tier, slot), protectionLevel);
		}

		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET)) {
			this.setEquipmentDropChance(slot, 0.0F);
			this.playerArmor.put(slot, false);
		}
	}

	private void storeLoadoutUpgrades(GuardPlayerUpgrades upgrades) {
		if (upgrades == null) {
			this.setStoredLoadoutLevels(0, 0, 0);
			return;
		}
		this.setStoredLoadoutLevels(
				upgrades.getArmorLevel(),
				upgrades.getWeaponLevel(),
				upgrades.getSupportLevel());
	}

	private void setStoredLoadoutLevels(int armorLevel, int weaponLevel, int supportLevel) {
		this.loadoutArmorLevel = MathHelper.clamp(armorLevel, 0, GuardPlayerUpgrades.MAX_ARMOR_LEVEL);
		this.loadoutWeaponLevel = MathHelper.clamp(weaponLevel, 0, GuardPlayerUpgrades.MAX_WEAPON_LEVEL);
		this.loadoutSupportLevel = MathHelper.clamp(supportLevel, 0, GuardPlayerUpgrades.MAX_SUPPORT_LEVEL);
	}

	private int getStoredHealingIntervalTicks() {
		return switch (this.loadoutSupportLevel) {
			case 1, 2 -> 50;
			case 3 -> 20;
			default -> 100;
		};
	}

	private float getStoredHealingAmount() {
		return this.loadoutSupportLevel >= 1 ? 2.0F : 1.0F;
	}

	private boolean hasStoredShieldUpgrade() {
		return this.loadoutSupportLevel >= 2;
	}

	private GuardPlayerUpgrades.ArmorTier rollConstrainedArmorTier(GuardPlayerUpgrades upgrades,
			GuardPlayerUpgrades.ArmorTier helmetTier) {
		GuardPlayerUpgrades.ArmorTier roll = this.rollArmorTierForLoadout(upgrades);
		int helmetIndex = helmetTier.ordinal();
		int minAllowed = Math.max(0, helmetIndex - 1);
		int clamped = Math.max(minAllowed, Math.min(helmetIndex, roll.ordinal()));
		return GuardPlayerUpgrades.ArmorTier.values()[clamped];
	}

	private GuardPlayerUpgrades.ArmorTier rollArmorTierForLoadout(GuardPlayerUpgrades upgrades) {
		GuardPlayerUpgrades.ArmorTier baseTier = upgrades.rollArmorTier(this.getRandom());
		GuardPlayerUpgrades.ArmorTier rolledTier = baseTier;
		if (!this.hasSpecialArmorBoost()) {
			return rolledTier;
		}

		GuardPlayerUpgrades.ArmorDistribution distribution = upgrades.getArmorDistribution();
		int boostedDiamondChance = Math.min(100, distribution.diamond() * 3);
		int roll = this.getRandom().nextInt(100);
		if (roll < boostedDiamondChance) {
			rolledTier = GuardPlayerUpgrades.ArmorTier.DIAMOND;
		}
		if (baseTier == GuardPlayerUpgrades.ArmorTier.DIAMOND
				&& ("Notch".equals(this.generatedName) || "Jack Black".equals(this.generatedName)
						|| "Jason Momoa".equals(this.generatedName))
				&& this.getRandom().nextInt(10) == 0) {
			rolledTier = GuardPlayerUpgrades.ArmorTier.NETHERITE;
		}
		return rolledTier;
	}

	private boolean hasSpecialArmorBoost() {
		return this.specialProfile == SPECIAL_PROFILE_NOTCH
				|| this.specialProfile == SPECIAL_PROFILE_JACK_BLACK
				|| this.specialProfile == SPECIAL_PROFILE_JASON_MOMOA;
	}

	private Item getArmorItemForSlot(GuardPlayerUpgrades.ArmorTier tier, EquipmentSlot slot) {
		return switch (tier) {
			case CHAINMAIL -> switch (slot) {
				case HEAD -> Items.CHAINMAIL_HELMET;
				case CHEST -> Items.CHAINMAIL_CHESTPLATE;
				case LEGS -> Items.CHAINMAIL_LEGGINGS;
				case FEET -> Items.CHAINMAIL_BOOTS;
				default -> Items.LEATHER_BOOTS;
			};
			case IRON -> switch (slot) {
				case HEAD -> Items.IRON_HELMET;
				case CHEST -> Items.IRON_CHESTPLATE;
				case LEGS -> Items.IRON_LEGGINGS;
				case FEET -> Items.IRON_BOOTS;
				default -> Items.LEATHER_BOOTS;
			};
			case GOLD -> switch (slot) {
				case HEAD -> Items.GOLDEN_HELMET;
				case CHEST -> Items.GOLDEN_CHESTPLATE;
				case LEGS -> Items.GOLDEN_LEGGINGS;
				case FEET -> Items.GOLDEN_BOOTS;
				default -> Items.LEATHER_BOOTS;
			};
			case DIAMOND -> switch (slot) {
				case HEAD -> Items.DIAMOND_HELMET;
				case CHEST -> Items.DIAMOND_CHESTPLATE;
				case LEGS -> Items.DIAMOND_LEGGINGS;
				case FEET -> Items.DIAMOND_BOOTS;
				default -> Items.LEATHER_BOOTS;
			};
			case NETHERITE -> switch (slot) {
				case HEAD -> Items.NETHERITE_HELMET;
				case CHEST -> Items.NETHERITE_CHESTPLATE;
				case LEGS -> Items.NETHERITE_LEGGINGS;
				case FEET -> Items.NETHERITE_BOOTS;
				default -> Items.LEATHER_BOOTS;
			};
			default -> switch (slot) {
				case HEAD -> Items.LEATHER_HELMET;
				case CHEST -> Items.LEATHER_CHESTPLATE;
				case LEGS -> Items.LEATHER_LEGGINGS;
				case FEET -> Items.LEATHER_BOOTS;
				default -> Items.LEATHER_BOOTS;
			};
		};
	}

	private void equipArmorPiece(ServerWorld world, EquipmentSlot slot, Item item, int protectionLevel) {
		ItemStack stack = new ItemStack(item);
		if (protectionLevel > 0) {
			this.applyEnchantment(world, stack, Enchantments.PROTECTION, protectionLevel);
		}
		this.equipStack(slot, stack);
	}

	private void applyEnchantment(ServerWorld world, ItemStack stack, RegistryKey<Enchantment> enchantment, int level) {
		Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
		if (!registry.contains(enchantment)) {
			return;
		}
		RegistryEntry<Enchantment> entry = registry.getEntry(registry.getValueOrThrow(enchantment));
		EnchantmentHelper.apply(stack, builder -> builder.set(entry, level));
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		if (this.ownerUuid != null && !this.ownerUuid.equals(player.getUuid())) {
			if (this.getEntityWorld() instanceof ServerWorld) {
				player.sendMessage(Text.literal("I'm not your guard!"), true);
			}
			return ActionResult.SUCCESS;
		}

		if (this.ownerUuid == null && stack.isOf(Items.EMERALD)) {
			if (this.getEntityWorld() instanceof ServerWorld world) {
				if (!GuardReputationManager.isTrustedByGuards(world, player.getUuid(), this.getBlockPos())) {
					player.sendMessage(Text.literal("The guard distrusts you due to village reputation."), true);
					return ActionResult.SUCCESS;
				}
				int hirePrice = com.guardvillagers.GuardHirePricing.getHirePrice(this.getLevel());
				if (!player.getAbilities().creativeMode && stack.getCount() < hirePrice) {
					player.sendMessage(Text.literal("Need " + hirePrice + " emerald(s) to hire this guard."), true);
					return ActionResult.SUCCESS;
				}

				this.setOwnerUuid(player.getUuid());
				this.setStaying(false);
				this.setBehavior(GuardBehavior.DEFENSIVE);
				this.setFormationType(FormationType.FOLLOW);
				if (!player.getAbilities().creativeMode) {
					stack.decrement(hirePrice);
				}
				player.sendMessage(Text.literal(this.getName().getString() + " is now loyal to you."), true);
			}
			return ActionResult.SUCCESS;
		}

		if (this.ownerUuid != null && this.ownerUuid.equals(player.getUuid()) && stack.isEmpty()) {
			if (player.isSneaking()) {
				this.cycleBehavior();
				player.sendMessage(Text.literal("Behavior set to " + this.getBehavior().name().toLowerCase()), true);
			} else {
				this.setStaying(!this.staying);
				player.sendMessage(Text.literal(this.staying ? "Guard staying." : "Guard following."), true);
			}
			return ActionResult.SUCCESS;
		}

		if (this.ownerUuid != null && this.ownerUuid.equals(player.getUuid()) && !stack.isEmpty()) {
			if (this.tryApplyPlayerUpgrade(player, hand, stack)) {
				return ActionResult.SUCCESS;
			}
			player.sendMessage(Text.literal("That item is not an upgrade for this guard."), true);
			return ActionResult.SUCCESS;
		}

		return super.interactMob(player, hand);
	}

	private void cycleBehavior() {
		GuardBehavior[] behaviors = GuardBehavior.values();
		int next = (this.getBehavior().ordinal() + 1) % behaviors.length;
		this.setBehavior(behaviors[next]);
	}

	private boolean tryApplyPlayerUpgrade(PlayerEntity player, Hand hand, ItemStack offered) {
		if (!(this.getEntityWorld() instanceof ServerWorld)) {
			return false;
		}

		if (this.tryApplyArmorUpgrade(player, hand, offered)) {
			player.sendMessage(Text.literal("Guard armor upgraded."), true);
			return true;
		}

		if (this.tryApplyWeaponUpgrade(player, hand, offered)) {
			player.sendMessage(Text.literal("Guard weapon upgraded."), true);
			return true;
		}

		return false;
	}

	private boolean tryApplyArmorUpgrade(PlayerEntity player, Hand hand, ItemStack offered) {
		if (!player.isSneaking() || this.squaredDistanceTo(player) > 25.0D) {
			return false;
		}

		ArmorDefinition definition = ARMOR_DEFINITIONS.get(offered.getItem());
		if (definition == null) {
			return false;
		}

		ItemStack equipped = this.getEquippedStack(definition.slot());
		int currentScore = this.getArmorScore(equipped);
		int offeredProtection = this.getEnchantmentLevel(offered, Enchantments.PROTECTION);
		int currentProtection = this.getEnchantmentLevel(equipped, Enchantments.PROTECTION);
		if (definition.score() < currentScore) {
			return false;
		}
		if (definition.score() == currentScore && offeredProtection <= currentProtection) {
			return false;
		}

		this.equipStack(definition.slot(), offered.copyWithCount(1));
		this.setEquipmentDropChance(definition.slot(), 1.0F);
		this.playerArmor.put(definition.slot(), true);
		if (!player.getAbilities().creativeMode) {
			player.getStackInHand(hand).decrement(1);
		}
		return true;
	}

	public UUID getMainTargetUuid() {
		return this.aiController.getMainTargetUuid();
	}

	public UUID getUrgentTargetUuid() {
		return this.aiController.getUrgentTargetUuid();
	}

	public boolean isCombatSuspended() {
		return this.aiController.isCombatSuspended();
	}

	public void suspendCombat() {
		this.aiController.suspendCombat();
	}

	public void resumeCombat() {
		this.aiController.resumeCombat();
	}

	public int getCombatCooldown() {
		return this.aiController.getCombatCooldown();
	}

	public boolean isAiIntent(GuardAiIntent intent) {
		return this.aiController.isIntent(intent);
	}

	public boolean isBehaviorExecutor(GuardBehaviorExecutor executor) {
		return this.aiController.isBehaviorExecutor(executor);
	}

	public Optional<BlockPos> getRallyPoint() {
		return this.aiController.getRallyPoint();
	}

	public GuardDebugSnapshot getDebugSnapshot(int maxNodes) {
		int nodeCap = Math.max(0, maxNodes);
		Path path = this.getNavigation().getCurrentPath();
		List<BlockPos> nodes = new ArrayList<>();
		int currentNodeIndex = -1;
		if (path != null && !path.isFinished() && nodeCap > 0) {
			int length = Math.min(path.getLength(), nodeCap);
			for (int i = 0; i < length; i++) {
				PathNode node = path.getNode(i);
				nodes.add(new BlockPos(node.x, node.y, node.z));
			}
			currentNodeIndex = Math.min(path.getCurrentNodeIndex(), Math.max(0, length - 1));
		}
		LivingEntity target = this.getTarget();
		int targetEntityId = target == null ? -1 : target.getId();
		return new GuardDebugSnapshot(nodes, currentNodeIndex, targetEntityId);
	}

	public record GuardDebugSnapshot(List<BlockPos> pathNodes, int currentPathIndex, int targetEntityId) {
		public GuardDebugSnapshot {
			pathNodes = List.copyOf(pathNodes);
		}
	}

	private boolean tryApplyWeaponUpgrade(PlayerEntity player, Hand hand, ItemStack offered) {
		if (this.getRole() == GuardRole.SWORDSMAN) {
			int currentScore = this.getSwordScore(this.getMainHandStack());
			int offeredScore = this.getSwordScore(offered);
			if (offeredScore <= currentScore) {
				return false;
			}
		} else {
			if (!offered.isOf(Items.BOW)) {
				return false;
			}
			int offeredPower = this.getEnchantmentLevel(offered, Enchantments.POWER);
			int currentPower = this.getEnchantmentLevel(this.getMainHandStack(), Enchantments.POWER);
			if (offeredPower <= currentPower) {
				return false;
			}
		}

		this.equipStack(EquipmentSlot.MAINHAND, offered.copyWithCount(1));
		this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 1.0F);
		this.playerMainHand = true;
		if (!player.getAbilities().creativeMode) {
			player.getStackInHand(hand).decrement(1);
		}
		return true;
	}

	private int getSwordScore(ItemStack stack) {
		int base = SWORD_SCORE.getOrDefault(stack.getItem(), 0);
		if (base == 0) {
			return 0;
		}
		int sharpness = this.getEnchantmentLevel(stack, Enchantments.SHARPNESS);
		return base * 10 + sharpness;
	}

	private int getEnchantmentLevel(ItemStack stack, RegistryKey<Enchantment> enchantment) {
		if (!(this.getEntityWorld() instanceof ServerWorld world)) {
			return 0;
		}
		Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
		if (!registry.contains(enchantment)) {
			return 0;
		}
		RegistryEntry<Enchantment> entry = registry.getEntry(registry.getValueOrThrow(enchantment));
		return EnchantmentHelper.getLevel(entry, stack);
	}

	private int getArmorScore(ItemStack stack) {
		ArmorDefinition definition = ARMOR_DEFINITIONS.get(stack.getItem());
		return definition == null ? 0 : definition.score();
	}

	public void setMainTarget(LivingEntity target) {
		this.aiController.setMainTarget(target);
	}

	public void setPriorityTarget(LivingEntity target) {
		this.aiController.setPriorityTarget(target);
	}

	public void receiveOwnerAlert(LivingEntity target, long alertTick) {
		this.receiveOwnerAttackAlert(target, alertTick);
	}

	public void receiveOwnerAttackAlert(LivingEntity target, long alertTick) {
		this.aiController.receiveOwnerAttackAlert(target, alertTick);
	}

	public void receiveOwnerDamagedAlert(LivingEntity target, long alertTick) {
		this.aiController.receiveOwnerDamagedAlert(target, alertTick);
	}

	public void receiveAlliedAlert(LivingEntity target, long alertTick) {
		this.aiController.receiveAlliedAlert(target, alertTick);
	}

	public void receiveUrgentPeel(LivingEntity urgentThreat, ServerWorld world) {
		this.aiController.receiveUrgentPeel(urgentThreat, world);
	}

	public int scoreTarget(LivingEntity target) {
		GuardVillagersConfig.Combat config = GuardVillagersConfig.get().combat;
		if (target == null || !target.isAlive()) {
			return 0;
		}
		if (this.ownerUuid != null && this.getEntityWorld() instanceof ServerWorld world) {
			ServerPlayerEntity owner = this.resolveOwner(world);
			if (owner != null && owner.getAttacking() == target) {
				return config.baseScoreOwnerAttacker;
			}
		}
		if (target instanceof PlayerEntity) {
			return config.baseScorePlayer;
		}
		if (target instanceof HostileEntity) {
			return config.baseScoreHostile;
		}
		return config.baseScoreOther;
	}

	public boolean isSameGroup(GuardEntity other) {
		if (other == null || other == this) {
			return true;
		}
		if (this.ownerUuid != null && this.ownerUuid.equals(other.ownerUuid)) {
			return true;
		}
		if (this.ownerUuid == null && other.ownerUuid == null) {
			if (this.squadId != null && this.squadId.equals(other.squadId)) {
				return true;
			}
			if (this.home != null && other.home != null
					&& this.home.getSquaredDistance(other.home) <= 64.0D * 64.0D) {
				return true;
			}
			if (this.home == null && other.home == null
					&& this.squaredDistanceTo(other) <= 48.0D * 48.0D) {
				return true;
			}
		}
		return false;
	}

	public boolean isSameMovementGroup(GuardEntity other) {
		if (other == null || other == this) {
			return true;
		}
		if (this.ownerUuid != null && this.ownerUuid.equals(other.ownerUuid)) {
			boolean hasExplicitGroup = this.groupIndex >= 0 || other.groupIndex >= 0;
			if (hasExplicitGroup) {
				return this.groupIndex >= 0 && this.groupIndex == other.groupIndex;
			}
			return this.groupColumn == other.groupColumn;
		}
		return this.isSameGroup(other);
	}

	public boolean hasActiveCombatTarget() {
		return this.aiController.hasActiveCombatTarget();
	}

	public GuardAiController getAiController() {
		return this.aiController;
	}

	public boolean isFleingTnt() {
		return this.tntRetreatTicks > 0;
	}

	public void startTntRetreat(int fuseTicks) {
		this.tntRetreatTicks = fuseTicks + 10;
	}

	@Override
	public int getSafeFallDistance() {
		GuardVillagersConfig.Survival config = GuardVillagersConfig.get().survival;
		if (!config.enabled) {
			return super.getSafeFallDistance();
		}
		return config.safeFallDistance + this.getLevel() * config.safeFallDistancePerLevel;
	}

	@Override
	protected int computeFallDamage(double fallDistance, float damageMultiplier) {
		GuardVillagersConfig.Survival config = GuardVillagersConfig.get().survival;
		if (!config.enabled) {
			return super.computeFallDamage(fallDistance, damageMultiplier);
		}
		int vanillaDamage = super.computeFallDamage(fallDistance, damageMultiplier);
		if (vanillaDamage <= 0) {
			return 0;
		}
		int reducedDamage = MathHelper.floor(vanillaDamage * config.fallDamageMultiplier);
		return Math.max(0, reducedDamage - (this.getLevel() / 2) * config.damageReductionPerTwoLevels);
	}

	@Override
	protected void mobTick(ServerWorld world) {
		super.mobTick(world);
		if (this.age % 20 == 0) {
			this.syncRegisteredOwnerName();
		}
		if (this.age % 40 == 0 || this.ownerUuid == null) {
			GuardOwnershipIndex.track(this);
		}

		if (this.age % 20 == 0) {
			this.updateLastLandPos();
		}

		if (this.age % 1200 == 0) {
			this.addExperience(1);
		}

		if (this.age % 100 == 0) {
			this.tickCraftingSystem(world);
		}

		if (this.age % 5 == 0) {
			this.tickGunpowderPickup(world);
		}

		// Decrement retreat timer and re-apply navigation every tick to prevent
		// combat goals from overriding the retreat path
		if (this.tntRetreatTicks > 0) {
			this.tntRetreatTicks--;
			LivingEntity retreatTarget = this.getTarget();
			if (retreatTarget != null && this.getEntityWorld() instanceof ServerWorld sw) {
				double dx = this.getX() - retreatTarget.getX();
				double dz = this.getZ() - retreatTarget.getZ();
				double hLen = Math.sqrt(dx * dx + dz * dz);
				if (hLen > 1e-4) {
					GuardVillagersConfig cfg = GuardVillagersConfig.get();
					double dist = cfg.tnt.safeRadius + 3;
					double nx = dx / hLen, nz = dz / hLen;
					double tx = this.getX() + nx * dist;
					double tz = this.getZ() + nz * dist;
					// Check dot product to avoid jitter when already moving away
					Vec3d vel = this.getVelocity();
					double dot = vel.x * nx + vel.z * nz;
					if (dot < 0.5D) {
						this.getNavigation().startMovingTo(tx, this.getY(), tz, 1.5D);
					}
				}
			}
		}

		if (this.age % 5 == 0) {
			this.tickTntDeployment(world);
		}

		if (!this.initialSpreadResolved && this.age <= INITIAL_SPREAD_TICKS) {
			this.resolveInitialSpawnSpacing(world);
		}

		this.aiController.tick(world);
		GuardMoraleSystem.tick(this, world);
		this.tickInitialSpawnDispersal(world);
		this.syncSupportEquipment();
		this.updateShieldUsage();
		this.updateGroupNameplate();
		this.tryTriggerNotchGoldenApple(world);

		int healInterval = this.getStoredHealingIntervalTicks();
		if (healInterval > 0 && this.age % healInterval == 0 && this.getCombatCooldown() <= 0
				&& this.getHealth() < this.getMaxHealth() * 0.6F) {
			this.heal(this.getStoredHealingAmount());
		}
	}

	private void tickCraftingSystem(ServerWorld world) {
		BlockPos tablePos = this.knownCraftingTablePos;
		if (tablePos == null || !world.getBlockState(tablePos).isOf(Blocks.CRAFTING_TABLE)) {
			this.knownCraftingTablePos = null;
			// Only try to craft planks and tables if we don't have a known table
			GuardCraftingSystem.tryCraftPlanksFromLogs(this);
			GuardCraftingSystem.tryCraftCraftingTable(this);
			GuardCraftingSystem.tryPlaceCraftingTable(this, world);
		}

		// Try to craft TNT if we have materials and a placed table
		if (GuardVillagersConfig.get().tnt.enabled) {
			boolean crafted = GuardCraftingSystem.tryCraftTnt(this, world);
			if (!crafted) {
				// If craft failed because we're not close enough, walk to table
				BlockPos currentTablePos = this.getKnownCraftingTablePos();
				if (currentTablePos != null
						&& this.getInventory().hasAtLeast(Items.SAND, 4)
						&& this.getInventory().hasAtLeast(Items.GUNPOWDER, 5)) {
					GuardCraftingSystem.tryWalkToCraftingTable(this, world);
				}
			}
		}
	}

	private void tickGunpowderPickup(ServerWorld world) {
		if (!GuardVillagersConfig.get().resourceGathering.enabled) {
			return;
		}

		// Check gunpowder level
		int gunpowderHave = this.getInventory().getCount(Items.GUNPOWDER);
		int gunpowderNeed = GuardVillagersConfig.get().resourceGathering.gunpowderTargetAmount;
		if (gunpowderHave >= gunpowderNeed) {
			return;
		}

		// Look for nearby gunpowder item entities within 3 blocks
		List<ItemEntity> gunpowderItems = world.getEntitiesByClass(
			ItemEntity.class,
			this.getBoundingBox().expand(3.0D),
			item -> item.isAlive() && item.getStack().isOf(Items.GUNPOWDER)
		);

		for (ItemEntity itemEntity : gunpowderItems) {
			int take = Math.min(
				itemEntity.getStack().getCount(),
				gunpowderNeed - this.getInventory().getCount(Items.GUNPOWDER)
			);
			this.getInventory().addItem(Items.GUNPOWDER, take);
			itemEntity.getStack().decrement(take);
			if (itemEntity.getStack().isEmpty()) {
				itemEntity.discard();
			}
			break; // One item entity per tick is enough
		}

		// Also check for sand
		int sandHave = this.getInventory().getCount(Items.SAND);
		int sandNeed = GuardVillagersConfig.get().resourceGathering.sandTargetAmount;
		if (sandHave >= sandNeed) {
			return;
		}

		List<ItemEntity> sandItems = world.getEntitiesByClass(
			ItemEntity.class,
			this.getBoundingBox().expand(3.0D),
			item -> item.isAlive() && item.getStack().isOf(Items.SAND)
		);

		for (ItemEntity itemEntity : sandItems) {
			int take = Math.min(
				itemEntity.getStack().getCount(),
				sandNeed - this.getInventory().getCount(Items.SAND)
			);
			this.getInventory().addItem(Items.SAND, take);
			itemEntity.getStack().decrement(take);
			if (itemEntity.getStack().isEmpty()) {
				itemEntity.discard();
			}
			break; // One item entity per tick is enough
		}
	}

	private void tickTntDeployment(ServerWorld world) {
		if (this.tntRetreatTicks > 0) return; // Don't place more TNT while retreating
		GuardVillagersConfig config = GuardVillagersConfig.get();
		if (!config.tnt.enabled) return;
		if (this.tntCooldown > 0) {
			this.tntCooldown--;
			return;
		}
		if (GuardTntSystem.tryUseTnt(this, world, config)) {
			this.tntCooldown = config.tnt.cooldownTicks;
		}
	}

	private void resolveInitialSpawnSpacing(ServerWorld world) {
		this.initialSpreadResolved = true;
		List<GuardEntity> crowded = world.getEntitiesByClass(
				GuardEntity.class,
				this.getBoundingBox().expand(1.1D),
				entity -> entity != this && entity.isAlive());
		if (crowded.isEmpty()) {
			return;
		}

		BlockPos spreadTarget = this.resolveGroundMovementSlot(world, this.getBlockPos(), this.getSlotSpacing(), false);
		if (spreadTarget.getSquaredDistance(this.getBlockPos()) < 1.0D
				|| !GuardVillagersMod.canGuardSpawnAt(world, spreadTarget)) {
			return;
		}

		this.pendingInitialSpreadTarget = spreadTarget.toImmutable();
	}

	private void tickInitialSpawnDispersal(ServerWorld world) {
		BlockPos spreadTarget = this.pendingInitialSpreadTarget;
		if (spreadTarget == null) {
			return;
		}
		if (!GuardVillagersMod.canGuardSpawnAt(world, spreadTarget)) {
			this.pendingInitialSpreadTarget = null;
			return;
		}

		Vec3d targetPos = Vec3d.ofBottomCenter(spreadTarget);
		if (this.squaredDistanceTo(targetPos.x, targetPos.y, targetPos.z) <= INITIAL_SPREAD_COMPLETE_DISTANCE_SQUARED) {
			this.pendingInitialSpreadTarget = null;
			return;
		}

		if (this.age <= INITIAL_SPREAD_TICKS) {
			this.getGuardNavigation().startMovingToStatic(spreadTarget, INITIAL_SPREAD_SPEED);
			return;
		}

		if (this.getNavigation().isIdle()) {
			this.pendingInitialSpreadTarget = null;
		}
	}

	private void syncSupportEquipment() {
		if (this.ownerUuid == null) {
			return;
		}
		boolean shouldHaveShield = this.hasStoredShieldUpgrade();
		boolean hasShield = this.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD);
		if (shouldHaveShield && !hasShield) {
			this.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			this.setEquipmentDropChance(EquipmentSlot.OFFHAND, 0.0F);
		} else if (!shouldHaveShield && hasShield) {
			this.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
	}

	private void updateShieldUsage() {
		ItemStack shield = this.getEquippedStack(EquipmentSlot.OFFHAND);
		if (!shield.isOf(Items.SHIELD)) {
			if (this.isUsingItem() && this.getActiveHand() == Hand.OFF_HAND) {
				this.clearActiveItem();
			}
			return;
		}

		LivingEntity target = this.getTarget();
		boolean shouldBlock = false;
		if (target != null && target.isAlive()) {
			double distanceSq = this.squaredDistanceTo(target);
			boolean rangedThreat = target instanceof RangedAttackMob
					|| target.getMainHandStack().isOf(Items.BOW)
					|| target.getMainHandStack().isOf(Items.CROSSBOW)
					|| target.getMainHandStack().isOf(Items.TRIDENT);
			boolean meleeWindow = this.getRole() != GuardRole.BOWMAN && distanceSq < 9.0D && !rangedThreat;
			shouldBlock = !meleeWindow && distanceSq <= (rangedThreat ? 256.0D : 81.0D);
			if (this.getRole() == GuardRole.BOWMAN && !rangedThreat && distanceSq <= 196.0D) {
				shouldBlock = false;
			}
		}

		if (shouldBlock) {
			if (this.isUsingItem() && this.getActiveHand() == Hand.MAIN_HAND) {
				this.clearActiveItem();
			}
			if (!this.isUsingItem() || this.getActiveHand() != Hand.OFF_HAND) {
				this.setCurrentHand(Hand.OFF_HAND);
			}
		} else if (this.isUsingItem() && this.getActiveHand() == Hand.OFF_HAND) {
			this.clearActiveItem();
		}
	}

	public void updateGroupNameplate() {
		if (this.age % 10 != 0) {
			return;
		}
		Text current = this.getCustomName();
		if (current != null && current.getString().startsWith(DEBUG_NAME_PREFIX)) {
			return;
		}

		if (!this.hasOwner()) {
			if (this.isCustomNameVisible()) {
				this.setCustomNameVisible(false);
			}
			return;
		}

		if (current == null && !this.generatedName.isBlank()) {
			this.setCustomName(Text.literal(this.generatedName));
		}
		if (!this.isCustomNameVisible()) {
			this.setCustomNameVisible(true);
		}
	}

	private void syncRegisteredOwnerName() {
		if (this.ownerUuid == null) {
			if (!this.lastRegisteredOwnerName.isBlank()) {
				unregisterOwnerName(this.lastRegisteredOwner, this.lastRegisteredOwnerName);
				this.lastRegisteredOwner = null;
				this.lastRegisteredOwnerName = "";
			}
			return;
		}
		String currentDisplayName = this.resolveCurrentDisplayName();
		if (currentDisplayName.isBlank()) {
			return;
		}
		if (currentDisplayName.equals(this.lastRegisteredOwnerName)) {
			return;
		}
		if (!this.lastRegisteredOwnerName.isBlank()) {
			unregisterOwnerName(this.lastRegisteredOwner != null ? this.lastRegisteredOwner : this.ownerUuid,
					this.lastRegisteredOwnerName);
		}
		registerOwnerName(this.ownerUuid, currentDisplayName);
		this.lastRegisteredOwner = this.ownerUuid;
		this.lastRegisteredOwnerName = currentDisplayName;
	}

	private String resolveCurrentDisplayName() {
		Text customName = this.getCustomName();
		if (customName != null) {
			String text = customName.getString().trim();
			if (!text.isBlank() && !text.startsWith(DEBUG_NAME_PREFIX)) {
				return text;
			}
		}
		return this.generatedName == null ? "" : this.generatedName.trim();
	}

	private void tryTriggerNotchGoldenApple(ServerWorld world) {
		if (this.specialProfile != SPECIAL_PROFILE_NOTCH) {
			return;
		}
		if (this.getHealth() > this.getMaxHealth() * NOTCH_APPLE_TRIGGER_HEALTH_RATIO) {
			return;
		}
		if (world.getTime() < this.notchAppleCooldownUntil) {
			return;
		}
		this.notchAppleCooldownUntil = world.getTime() + NOTCH_APPLE_COOLDOWN_TICKS;
		this.playSound(SoundEvents.ENTITY_GENERIC_EAT.value(), 1.0F, 1.0F);
		this.heal(8.0F);
		this.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 400, 1));
		this.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 3));
		this.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 6000, 0));
		this.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 6000, 0));
	}

	private GuardNameRoll rollUniqueGuardName(ServerWorld world, UUID ownerUuid) {
		Set<String> usedNames = OWNER_USED_NAMES.computeIfAbsent(ownerUuid, ignored -> ConcurrentHashMap.newKeySet());
		for (int i = 0; i < 4096; i++) {
			GuardNameRoll roll = this.rollNameCandidate(world);
			if (!usedNames.contains(roll.name())) {
				registerOwnerName(ownerUuid, roll.name());
				return roll;
			}
		}
		String fallback = this.composeRegularName(world) + " " + Integer.toString(Math.max(1, usedNames.size() + 1));
		registerOwnerName(ownerUuid, fallback);
		return new GuardNameRoll(fallback, SPECIAL_PROFILE_NONE);
	}

	private GuardNameRoll rollNameCandidate(ServerWorld world) {
		int notchRoll = world.getRandom().nextInt(NOTCH_ROLL);
		if (notchRoll == 0) {
			return new GuardNameRoll("Notch", SPECIAL_PROFILE_NOTCH);
		}
		int jackRoll = world.getRandom().nextInt(JACK_BLACK_ROLL);
		if (jackRoll == 0) {
			return new GuardNameRoll("Jack Black", SPECIAL_PROFILE_JACK_BLACK);
		}
		int jasonRoll = world.getRandom().nextInt(JASON_MOMOA_ROLL);
		if (jasonRoll == 0) {
			return new GuardNameRoll("Jason Momoa", SPECIAL_PROFILE_JASON_MOMOA);
		}
		return new GuardNameRoll(this.composeRegularName(world), SPECIAL_PROFILE_NONE);
	}

	private String composeRegularName(ServerWorld world) {
		String firstName = FIRST_NAME_POOL[world.getRandom().nextInt(FIRST_NAME_POOL.length)];
		String lastName = LAST_NAME_POOL[world.getRandom().nextInt(LAST_NAME_POOL.length)];
		return firstName + " " + lastName;
	}

	private int inferSpecialProfileFromName(String name) {
		if (name == null || name.isBlank()) {
			return SPECIAL_PROFILE_NONE;
		}
		String normalized = name.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals("notch")) {
			return SPECIAL_PROFILE_NOTCH;
		}
		if (normalized.equals("jack black")) {
			return SPECIAL_PROFILE_JACK_BLACK;
		}
		if (normalized.equals("jason momoa")) {
			return SPECIAL_PROFILE_JASON_MOMOA;
		}
		return SPECIAL_PROFILE_NONE;
	}

	private static void registerOwnerName(UUID ownerUuid, String name) {
		if (ownerUuid == null || name == null || name.isBlank()) {
			return;
		}
		OWNER_USED_NAMES.computeIfAbsent(ownerUuid, ignored -> ConcurrentHashMap.newKeySet()).add(name);
	}

	private static void unregisterOwnerName(UUID ownerUuid, String name) {
		if (ownerUuid == null || name == null || name.isBlank()) {
			return;
		}
		Set<String> usedNames = OWNER_USED_NAMES.get(ownerUuid);
		if (usedNames == null) {
			return;
		}
		usedNames.remove(name);
		if (usedNames.isEmpty()) {
			OWNER_USED_NAMES.remove(ownerUuid);
		}
	}

	public boolean isAlly(Entity entity) {
		if (entity == null || entity == this) {
			return true;
		}
		if (entity instanceof IronGolemEntity) {
			return true;
		}
		if (entity instanceof GuardEntity otherGuard) {
			if (this.ownerUuid != null && this.ownerUuid.equals(otherGuard.ownerUuid)) {
				return true;
			}
			if (this.ownerUuid == null && otherGuard.ownerUuid == null) {
				return true;
			}
			return false;
		}
		if (entity instanceof VillagerEntity) {
			return true;
		}
		if (entity instanceof PlayerEntity player) {
			if (player.isSpectator() || player.getAbilities().creativeMode) {
				return true;
			}
			if (this.ownerUuid != null && this.ownerUuid.equals(player.getUuid())) {
				return true;
			}
			if (this.ownerUuid != null && this.getEntityWorld() instanceof ServerWorld world) {
				return GuardDiplomacyManager.isWhitelisted(world.getServer(), this.ownerUuid, player.getUuid());
			}
		}
		return false;
	}

	@Override
	public void setTarget(LivingEntity target) {
		if (target != null && this.isAlly(target)) {
			target = null;
		}
		super.setTarget(target);
	}

	private void keepBowRange() {
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		GuardVillagersConfig.Combat config = GuardVillagersConfig.get().combat;

		double distanceSq = this.squaredDistanceTo(target);
		double tooCloseSq = config.bowTooCloseDistance * config.bowTooCloseDistance;
		double tooFarSq = config.bowTooFarDistance * config.bowTooFarDistance;
		if (distanceSq < tooCloseSq) {
			Vec3d retreat = this.getEntityPos().subtract(target.getEntityPos());
			if (retreat.lengthSquared() < 1.0E-4D) {
				retreat = new Vec3d(this.getRandom().nextDouble() - 0.5D, 0.0D, this.getRandom().nextDouble() - 0.5D);
			}
			Vec3d destination = this.getEntityPos().add(retreat.normalize().multiply(config.bowRetreatDistance));
			if (this.canTargetWithinZone(new BlockPos((int) destination.x, (int) destination.y, (int) destination.z))) {
				this.getNavigation().startMovingTo(destination.x, destination.y, destination.z, config.bowRetreatSpeed);
			}
		} else if (distanceSq > tooFarSq) {
			this.getNavigation().startMovingTo(target, config.bowApproachSpeed);
		} else {
			this.getNavigation().stop();
		}
	}

	public int getMorale() {
		return this.morale;
	}

	public void adjustMorale(int delta) {
		GuardVillagersConfig.Morale config = GuardVillagersConfig.get().morale;
		if (!config.enabled) {
			return;
		}
		this.morale = MathHelper.clamp(this.morale + delta, 0, 100);
	}

	public int getArrowReserve() {
		return this.arrowReserve;
	}

	public void addArrowReserve(int amount) {
		GuardVillagersConfig.Logistics config = GuardVillagersConfig.get().logistics;
		this.arrowReserve = MathHelper.clamp(this.arrowReserve + amount, 0, config.maxArrowReserve);
	}

	public int getBuildingBlockReserve() {
		return this.buildingBlockReserve;
	}

	public void addBuildingBlockReserve(int amount) {
		this.buildingBlockReserve = MathHelper.clamp(this.buildingBlockReserve + amount, 0, 64);
	}

	public GuardInventory getInventory() {
		return this.inventory;
	}

	public BlockPos getKnownCraftingTablePos() {
		return this.knownCraftingTablePos;
	}

	public void setKnownCraftingTablePos(BlockPos pos) {
		this.knownCraftingTablePos = pos;
	}

	public boolean consumeBuildingBlockReserve(int amount) {
		if (!GuardVillagersConfig.get().logistics.enabled) {
			return true;
		}
		if (this.buildingBlockReserve < amount) {
			return false;
		}
		this.buildingBlockReserve -= amount;
		return true;
	}

	public boolean canShootBow() {
		GuardVillagersConfig.Logistics config = GuardVillagersConfig.get().logistics;
		if (!config.enabled || !config.arrowRecovery || this.getRole() != GuardRole.BOWMAN) {
			return true;
		}
		return this.arrowReserve > 0;
	}

	@Override
	public boolean tryAttack(ServerWorld world, Entity target) {
		boolean attacked = super.tryAttack(world, target);
		if (attacked && !this.getMainHandStack().isOf(Items.BOW)) {
			this.swingHand(Hand.MAIN_HAND);
		}
		return attacked;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		GuardDebugLogger.logDeath(this, "guard died",
			"cause", "unknown",
			"health", "0",
			"position", this.getBlockPos().toShortString());
		
		super.onDeath(damageSource);
		if (this.ownerUuid != null && this.getEntityWorld() instanceof ServerWorld world) {
			ServerPlayerEntity owner = this.resolveOwner(world);
			boolean showDeathMessages = Boolean.TRUE.equals(world.getGameRules().getValue(GameRules.SHOW_DEATH_MESSAGES));
			if (owner != null && showDeathMessages) {
				owner.sendMessage(damageSource.getDeathMessage(this));
			}
		}
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		ItemStack bow = this.getMainHandStack();
		if (!bow.isOf(Items.BOW) || !(this.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		if (!this.canShootBow()) {
			return;
		}

		ItemStack arrowStack = this.getProjectileType(bow);
		if (arrowStack.isEmpty()) {
			arrowStack = new ItemStack(Items.ARROW);
		}
		if (GuardVillagersConfig.get().logistics.enabled && GuardVillagersConfig.get().logistics.arrowRecovery) {
			this.addArrowReserve(-1);
		}

		PersistentProjectileEntity arrow = new GuardArrowEntity(world, this, arrowStack.copyWithCount(1), bow.copy());
		arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		int power = this.getEnchantmentLevel(bow, Enchantments.POWER);
		double baseDamage = 2.0D + Math.max(0.0F, pullProgress) * 1.6D + (double) power * 0.6D;
		arrow.setDamage(baseDamage);
		if (this.getEnchantmentLevel(bow, Enchantments.FLAME) > 0) {
			arrow.setOnFireFor(5.0F);
		}
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getBodyY(0.3333333333333333D) - arrow.getY() + horizontal * 0.2D;
		arrow.setVelocity(dx, dy, dz, 1.6F, (float) (14 - this.getEntityWorld().getDifficulty().getId() * 4));
		this.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		world.spawnEntity(arrow);
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		boolean activelyBlockingWithShield = this.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD)
				&& this.isUsingItem()
				&& this.getActiveHand() == Hand.OFF_HAND;
		if (this.hasStoredShieldUpgrade() && activelyBlockingWithShield) {
			amount *= 0.55F;
		}
		float dodgeChance = GuardVillagersConfig.get().combat.dodgeChance;
		if (dodgeChance > 0.0F && this.getRandom().nextFloat() < dodgeChance) {
			amount *= 0.5F;
		}
		boolean damaged = super.damage(world, source, amount);
		if (!damaged) {
			return false;
		}

		GuardDebugLogger.logCombat(this, "took damage",
			"amount", String.format("%.1f", amount),
			"health", String.format("%.1f", this.getHealth()),
			"source", source.getDeathMessage(this).getString());

		if (source.getAttacker() instanceof LivingEntity attacker) {
			GuardDebugLogger.logCombat(this, "received damage from attacker",
				"attacker", attacker.getName().getString(),
				"attackerType", attacker.getClass().getSimpleName(),
				"ownerUuid", this.ownerUuid != null ? this.ownerUuid.toString().substring(0, 8) : "none",
				"isAlly", String.valueOf(this.isAlly(attacker)));

			if (attacker instanceof PlayerEntity playerAttacker && this.ownerUuid != null
					&& this.ownerUuid.equals(playerAttacker.getUuid())) {
				GuardDebugLogger.logCombat(this, "damage blocked: owner attacking guard");
				this.clearCombatTarget();
				return true;
			}
			if (attacker instanceof GuardEntity attackerGuard && this.ownerUuid != null
					&& this.ownerUuid.equals(attackerGuard.ownerUuid)) {
				GuardDebugLogger.logCombat(this, "damage blocked: ally guard attacking");
				this.clearCombatTarget();
				return true;
			}
			if (attacker instanceof PlayerEntity player && this.isCreativeOperator(player)) {
				GuardDebugLogger.logCombat(this, "damage blocked: creative operator");
				return true;
			}
			boolean isAlly = this.isAlly(attacker);
			GuardDebugLogger.logCombat(this, "damage alert decision",
				"isAlly", String.valueOf(isAlly),
				"willSendAlert", String.valueOf(!isAlly));
			if (!isAlly) {
				this.aiController.receiveDamageAlert(attacker, world.getTime());
				this.rallyNearbyGuards(world, attacker);
			}
			if (attacker instanceof PlayerEntity player) {
				GuardReputationManager.recordGuardHarm(world, player.getUuid());
			}
		}

		return true;
	}

	private boolean isCreativeOperator(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (!serverPlayer.getAbilities().creativeMode) {
			return false;
		}
		if (!(serverPlayer.getCommandSource().getPermissions() instanceof LeveledPermissionPredicate leveled)) {
			return false;
		}
		return leveled.getLevel().isAtLeast(PermissionLevel.GAMEMASTERS);
	}

	@Override
	public boolean onKilledOther(ServerWorld world, LivingEntity other, DamageSource damageSource) {
		boolean result = super.onKilledOther(world, other, damageSource);
		
		GuardDebugLogger.logCombat(this, "killed enemy",
			"victim", other.getName().getString(),
			"type", other.getClass().getSimpleName(),
			"reward_xp", other instanceof HostileEntity ? "20" : "8");
		
		if (other instanceof HostileEntity) {
			this.addExperience(20);
		} else {
			this.addExperience(8);
		}

		if (this.ownerUuid != null) {
			GuardReputationManager.recordHostileKill(world, this.ownerUuid, other);
			if (other instanceof RaiderEntity) {
				GuardReputationManager.recordRaidDefense(world, this.ownerUuid);
			}
		}
		if (other instanceof PlayerEntity player) {
			GuardReputationManager.resetReputation(world, player.getUuid());
		}
		return result;
	}

	private void rallyNearbyGuards(ServerWorld world, LivingEntity attacker) {
		double range = Math.max(8.0D, this.getAttributeValue(EntityAttributes.FOLLOW_RANGE));
		List<GuardEntity> nearby = world.getEntitiesByClass(GuardEntity.class, this.getBoundingBox().expand(range),
				guard -> {
					if (!guard.isAlive() || guard == this) {
						return false;
					}
					return this.isSameGroup(guard);
		});

		for (GuardEntity guard : nearby) {
			guard.receiveAlliedAlert(attacker, world.getTime());
			if (guard.getUrgentTargetUuid() == null
					&& guard.getMainTargetUuid() != null
					&& !attacker.getUuid().equals(guard.getMainTargetUuid())) {
				guard.receiveUrgentPeel(attacker, world);
			}
		}
	}

	@Override
	protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
		this.dropPlayerGear(world, EquipmentSlot.MAINHAND, this.playerMainHand);
		this.dropPlayerGear(world, EquipmentSlot.HEAD, this.playerArmor.getOrDefault(EquipmentSlot.HEAD, false));
		this.dropPlayerGear(world, EquipmentSlot.CHEST, this.playerArmor.getOrDefault(EquipmentSlot.CHEST, false));
		this.dropPlayerGear(world, EquipmentSlot.LEGS, this.playerArmor.getOrDefault(EquipmentSlot.LEGS, false));
		this.dropPlayerGear(world, EquipmentSlot.FEET, this.playerArmor.getOrDefault(EquipmentSlot.FEET, false));
	}

	private void dropPlayerGear(ServerWorld world, EquipmentSlot slot, boolean playerProvided) {
		if (!playerProvided) {
			return;
		}
		ItemStack stack = this.getEquippedStack(slot);
		if (!stack.isEmpty()) {
			this.dropStack(world, stack.copy());
		}
	}

	private void applyLevelModifiers() {
		int level = this.getLevel();
		double healthBonus = (level - 1) * 2.0D;
		double damageBonus = (level - 1) * 0.5D;
		double speedBonus = (level - 1) * 0.01D;

		this.updateAttributeModifier(EntityAttributes.MAX_HEALTH, LEVEL_HEALTH_MODIFIER_ID, healthBonus);
		this.updateAttributeModifier(EntityAttributes.ATTACK_DAMAGE, LEVEL_DAMAGE_MODIFIER_ID, damageBonus);
		this.updateAttributeModifier(EntityAttributes.MOVEMENT_SPEED, LEVEL_SPEED_MODIFIER_ID, speedBonus);
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	private void updateAttributeModifier(RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
			Identifier modifierId, double value) {
		EntityAttributeInstance instance = this.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifierId);
		if (value != 0.0D) {
			instance.addPersistentModifier(
					new EntityAttributeModifier(modifierId, value, EntityAttributeModifier.Operation.ADD_VALUE));
		}
	}

	@Override
	protected void writeCustomData(WriteView view) {
		super.writeCustomData(view);
		view.putInt(ROLE_KEY, this.dataTracker.get(ROLE));
		view.putInt(BEHAVIOR_KEY, this.dataTracker.get(BEHAVIOR));
		view.putInt(FORMATION_KEY, this.dataTracker.get(FORMATION));
		view.putBoolean(STAYING_KEY, this.staying);
		boolean hasStayOrigin = this.stayOrigin != null;
		view.putBoolean(HAS_STAY_ORIGIN_KEY, hasStayOrigin);
		if (hasStayOrigin) {
			view.putInt(STAY_ORIGIN_X_KEY, this.stayOrigin.getX());
			view.putInt(STAY_ORIGIN_Y_KEY, this.stayOrigin.getY());
			view.putInt(STAY_ORIGIN_Z_KEY, this.stayOrigin.getZ());
		}
		view.putBoolean(FOLLOW_OVERRIDE_KEY, this.followOverride);
		view.putString(OWNER_KEY, this.ownerUuid == null ? "" : this.ownerUuid.toString());
		view.putString(SQUAD_ID_KEY, this.squadId == null ? "" : this.squadId.toString());
		view.putBoolean(SQUAD_LEADER_KEY, this.isSquadLeader());
		view.putInt(EXPERIENCE_KEY, this.getExperience());
		view.putBoolean(PLAYER_MAINHAND_KEY, this.playerMainHand);
		view.putBoolean(PLAYER_HELMET_KEY, this.playerArmor.getOrDefault(EquipmentSlot.HEAD, false));
		view.putBoolean(PLAYER_CHEST_KEY, this.playerArmor.getOrDefault(EquipmentSlot.CHEST, false));
		view.putBoolean(PLAYER_LEGS_KEY, this.playerArmor.getOrDefault(EquipmentSlot.LEGS, false));
		view.putBoolean(PLAYER_FEET_KEY, this.playerArmor.getOrDefault(EquipmentSlot.FEET, false));
		view.putInt(LOADOUT_ARMOR_LEVEL_KEY, this.loadoutArmorLevel);
		view.putInt(LOADOUT_WEAPON_LEVEL_KEY, this.loadoutWeaponLevel);
		view.putInt(LOADOUT_SUPPORT_LEVEL_KEY, this.loadoutSupportLevel);

		boolean hasHome = this.home != null;
		view.putBoolean(HAS_HOME_KEY, hasHome);
		if (hasHome) {
			view.putInt(HOME_X_KEY, this.home.getX());
			view.putInt(HOME_Y_KEY, this.home.getY());
			view.putInt(HOME_Z_KEY, this.home.getZ());
		}
		view.putInt(PATROL_RADIUS_KEY, this.patrolRadius);
		view.putInt(GROUP_INDEX_KEY, this.groupIndex);
		view.putInt(GROUP_COLUMN_KEY, this.groupColumn);
		view.putString(GROUP_NAME_KEY, this.getGroupName());
		view.putString(SKIN_PROFILE_KEY, this.skinProfileId);
		view.putString(GENERATED_NAME_KEY, this.generatedName == null ? "" : this.generatedName);
		view.putInt(SPECIAL_PROFILE_KEY, this.specialProfile);
		view.putLong(NOTCH_APPLE_COOLDOWN_UNTIL_KEY, this.notchAppleCooldownUntil);
		view.putInt(MORALE_KEY, this.morale);
		view.putInt(ARROW_RESERVE_KEY, this.arrowReserve);
		view.putInt(BUILDING_BLOCK_RESERVE_KEY, this.buildingBlockReserve);
		this.inventory.writeToView(view);
		boolean hasKnownCraftingTable = this.knownCraftingTablePos != null;
		view.putBoolean(HAS_KNOWN_CRAFTING_TABLE_KEY, hasKnownCraftingTable);
		if (hasKnownCraftingTable) {
			view.putInt(CRAFTING_TABLE_X_KEY, this.knownCraftingTablePos.getX());
			view.putInt(CRAFTING_TABLE_Y_KEY, this.knownCraftingTablePos.getY());
			view.putInt(CRAFTING_TABLE_Z_KEY, this.knownCraftingTablePos.getZ());
		}
		boolean hasLastLand = this.lastLandPos != null;
		view.putBoolean(HAS_LAST_LAND_KEY, hasLastLand);
		if (hasLastLand) {
			view.putInt(LAST_LAND_X_KEY, this.lastLandPos.getX());
			view.putInt(LAST_LAND_Y_KEY, this.lastLandPos.getY());
			view.putInt(LAST_LAND_Z_KEY, this.lastLandPos.getZ());
		}
	}

	@Override
	protected void readCustomData(ReadView view) {
		super.readCustomData(view);
		this.dataTracker.set(ROLE, view.getInt(ROLE_KEY, GuardRole.SWORDSMAN.getId()));
		this.dataTracker.set(BEHAVIOR, view.getInt(BEHAVIOR_KEY, GuardBehavior.DEFENSIVE.getId()));
		// Previously this unconditionally reset to FOLLOW, silently dropping any
		// formation the player had configured on every server restart. Read the
		// stored value and fall back to FOLLOW only when absent.
		this.dataTracker.set(FORMATION, view.getInt(FORMATION_KEY, FormationType.FOLLOW.getId()));
		this.staying = view.getBoolean(STAYING_KEY, false);
		if (view.getBoolean(HAS_STAY_ORIGIN_KEY, false)) {
			this.stayOrigin = new BlockPos(
					view.getInt(STAY_ORIGIN_X_KEY, 0),
					view.getInt(STAY_ORIGIN_Y_KEY, 0),
					view.getInt(STAY_ORIGIN_Z_KEY, 0));
		} else {
			this.stayOrigin = null;
		}
		this.followOverride = view.getBoolean(FOLLOW_OVERRIDE_KEY, false);
		this.catchUpSpeedActive = false;
		this.ownerUuid = parseUuid(view.getString(OWNER_KEY, ""));
		this.squadId = parseUuid(view.getString(SQUAD_ID_KEY, ""));
		this.dataTracker.set(SQUAD_LEADER, view.getBoolean(SQUAD_LEADER_KEY, false));
		this.dataTracker.set(EXPERIENCE, Math.max(0, view.getInt(EXPERIENCE_KEY, 0)));
		this.playerMainHand = view.getBoolean(PLAYER_MAINHAND_KEY, false);
		this.playerArmor.put(EquipmentSlot.HEAD, view.getBoolean(PLAYER_HELMET_KEY, false));
		this.playerArmor.put(EquipmentSlot.CHEST, view.getBoolean(PLAYER_CHEST_KEY, false));
		this.playerArmor.put(EquipmentSlot.LEGS, view.getBoolean(PLAYER_LEGS_KEY, false));
		this.playerArmor.put(EquipmentSlot.FEET, view.getBoolean(PLAYER_FEET_KEY, false));
		int storedArmorLevel = view.getInt(LOADOUT_ARMOR_LEVEL_KEY, MISSING_LOADOUT_LEVEL);
		int storedWeaponLevel = view.getInt(LOADOUT_WEAPON_LEVEL_KEY, MISSING_LOADOUT_LEVEL);
		int storedSupportLevel = view.getInt(LOADOUT_SUPPORT_LEVEL_KEY, MISSING_LOADOUT_LEVEL);
		if (storedArmorLevel == MISSING_LOADOUT_LEVEL
				|| storedWeaponLevel == MISSING_LOADOUT_LEVEL
				|| storedSupportLevel == MISSING_LOADOUT_LEVEL) {
			if (this.ownerUuid != null && this.getEntityWorld() instanceof ServerWorld world) {
				this.storeLoadoutUpgrades(GuardVillagersMod.getUpgrades(world, this.ownerUuid));
			} else {
				this.setStoredLoadoutLevels(0, 0, 0);
			}
		} else {
			this.setStoredLoadoutLevels(storedArmorLevel, storedWeaponLevel, storedSupportLevel);
		}

		if (view.getBoolean(HAS_HOME_KEY, false)) {
			this.home = new BlockPos(
					view.getInt(HOME_X_KEY, 0),
					view.getInt(HOME_Y_KEY, 0),
					view.getInt(HOME_Z_KEY, 0));
		} else {
			this.home = null;
		}
		this.patrolRadius = MathHelper.clamp(view.getInt(PATROL_RADIUS_KEY, 0), 0, 128);
		// Read new keys first, fall back to legacy keys for migration
		int readGroupIndex = view.getInt(GROUP_INDEX_KEY, Integer.MIN_VALUE);
		if (readGroupIndex == Integer.MIN_VALUE) {
			readGroupIndex = view.getInt(LEGACY_HIERARCHY_ROW_KEY, 0);
		}
		this.groupIndex = MathHelper.clamp(readGroupIndex, MIN_GROUP_INDEX, MAX_GROUP_INDEX);
		int readGroupColumn = view.getInt(GROUP_COLUMN_KEY, -1);
		if (readGroupColumn < 0) {
			readGroupColumn = view.getInt(LEGACY_HIERARCHY_COLUMN_KEY, 1);
		}
		this.groupColumn = MathHelper.clamp(readGroupColumn, 0, 2);
		String readGroupName = view.getString(GROUP_NAME_KEY, "");
		if (readGroupName.isEmpty()) {
			readGroupName = view.getString(LEGACY_HIERARCHY_ROLE_KEY, "Alpha");
		}
		this.setGroupName(readGroupName);
		this.setSkinProfileId(view.getString(SKIN_PROFILE_KEY, ""));
		this.generatedName = view.getString(GENERATED_NAME_KEY, "");
		this.specialProfile = view.getInt(SPECIAL_PROFILE_KEY, this.inferSpecialProfileFromName(this.generatedName));
		this.notchAppleCooldownUntil = Math.max(0L, view.getLong(NOTCH_APPLE_COOLDOWN_UNTIL_KEY, 0L));
		this.morale = MathHelper.clamp(view.getInt(MORALE_KEY, GuardVillagersConfig.get().morale.defaultMorale), 0, 100);
		this.arrowReserve = Math.max(0, view.getInt(ARROW_RESERVE_KEY, 32));
		this.buildingBlockReserve = Math.max(0, view.getInt(BUILDING_BLOCK_RESERVE_KEY, 16));
		this.inventory.readFromView(view);
		if (view.getBoolean(HAS_KNOWN_CRAFTING_TABLE_KEY, false)) {
			this.knownCraftingTablePos = new BlockPos(
					view.getInt(CRAFTING_TABLE_X_KEY, 0),
					view.getInt(CRAFTING_TABLE_Y_KEY, 0),
					view.getInt(CRAFTING_TABLE_Z_KEY, 0));
		} else {
			this.knownCraftingTablePos = null;
		}
		if (view.getBoolean(HAS_LAST_LAND_KEY, false)) {
			this.lastLandPos = new BlockPos(
					view.getInt(LAST_LAND_X_KEY, 0),
					view.getInt(LAST_LAND_Y_KEY, 0),
					view.getInt(LAST_LAND_Z_KEY, 0));
		}
		this.setCatchUpSpeedActive(false);
		this.applyLevelModifiers();
		this.updateCombatGoals();
		if (this.getEntityWorld() instanceof ServerWorld) {
			if (this.ownerUuid != null) {
				this.ensureHireIdentity((ServerWorld) this.getEntityWorld());
			}
			GuardOwnershipIndex.track(this);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		String removalReason = reason != null ? reason.name() : "UNKNOWN";
		GuardDebugLogger.logDespawn(this, "guard removed",
			"reason", removalReason,
			"shouldDestroy", reason != null ? String.valueOf(reason.shouldDestroy()) : "unknown");
		
		if (!this.lastRegisteredOwnerName.isBlank()) {
			unregisterOwnerName(this.lastRegisteredOwner != null ? this.lastRegisteredOwner : this.ownerUuid,
					this.lastRegisteredOwnerName);
			this.lastRegisteredOwnerName = "";
			this.lastRegisteredOwner = null;
		}
		if (reason == null || reason.shouldDestroy()) {
			GuardOwnershipIndex.untrack(this);
		}
		super.remove(reason);
	}

	private UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private void updateCombatGoals() {
		if (this.meleeGoal == null || this.rangedGoal == null) {
			return;
		}

		this.goalSelector.remove(this.meleeGoal);
		this.goalSelector.remove(this.rangedGoal);
		if (this.getRole() == GuardRole.BOWMAN) {
			this.goalSelector.add(3, this.rangedGoal);
		} else {
			this.goalSelector.add(3, this.meleeGoal);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_VILLAGER_DEATH;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	private record GuardNameRoll(String name, int specialProfile) {
	}

	private record ArmorDefinition(EquipmentSlot slot, int score) {
	}
}

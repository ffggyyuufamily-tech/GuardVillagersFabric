package com.guardvillagers;

import com.guardvillagers.data.GuardUpgradeState;
import com.guardvillagers.data.GuardVillageState;
import com.guardvillagers.data.GuardReputationState;
import com.guardvillagers.data.GuardDebugState;
import com.guardvillagers.data.GuardDiplomacyState;
import com.guardvillagers.data.GuardTacticsState;
import com.guardvillagers.entity.GuardBehavior;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardMoraleSystem;
import com.guardvillagers.entity.GuardRole;
import com.guardvillagers.item.GuardSpawnEggItem;
import com.guardvillagers.network.GuardDebugDataPayload;
import com.guardvillagers.network.GuardDebugSyncPayload;
import com.guardvillagers.network.GuardRosterSyncPayload;
import com.guardvillagers.item.GuardWhistleItem;
import com.guardvillagers.shop.GuardShopScreenHandler;
import com.guardvillagers.tactics.GuardTacticsScreenHandler;
import com.guardvillagers.village.VillageManagerHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public class GuardVillagersMod implements ModInitializer {
	public static final String MOD_ID = "guardvillagers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int DEBUG_SYNC_INTERVAL_TICKS = 5;
	private static final int DEBUG_MAX_PATH_NODES = 64;
	/**
	 * Upper bound on the group-row index exposed through /guards groups rename/assign.
	 * Prevents an unbounded IntegerArgumentType from forcing the server to allocate a
	 * multi-billion-entry group list (GuardTacticsState.ensureGroupCount).
	 */
	private static final int MAX_GROUP_ROW = 64;
	private static final Pattern REPUTATION_INPUT_PATTERN = Pattern.compile("^(?:0(?:\\.\\d{1,2})?|1(?:\\.0{1,2})?)$");
	/**
	 * Touched from END_WORLD_TICK, which fires per-world and may run concurrently
	 * across dimensions on servers with multiple loaded worlds. A plain HashMap
	 * would corrupt under concurrent put/remove; use a ConcurrentHashMap so the
	 * outer lookup is thread-safe. Inner per-player maps are only mutated for
	 * the owning player's map at a time, so they remain plain HashMaps.
	 */
	private static final Map<UUID, Map<Integer, Integer>> DEBUG_PATH_HASH_CACHE = new ConcurrentHashMap<>();

	public enum GuardPurchaseResult {
		SUCCESS,
		NOT_TRUSTED,
		INSUFFICIENT_FUNDS,
		SPAWN_FAILED,
		INTERNAL_ERROR
	}

	public static final EntityType<GuardEntity> GUARD_ENTITY_TYPE = Registry.register(
			Registries.ENTITY_TYPE,
			id("guard"),
			EntityType.Builder.create(GuardEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.6F, 1.95F)
					.maxTrackingRange(10)
					.build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, id("guard"))));

	public static final Item GUARD_WHISTLE = Registry.register(
			Registries.ITEM,
			id("guard_whistle"),
			new GuardWhistleItem(new Item.Settings()
					.registryKey(RegistryKey.of(RegistryKeys.ITEM, id("guard_whistle")))
					.maxCount(1)));

	public static final Item GUARD_SPAWN_EGG = Registry.register(
			Registries.ITEM,
			id("guard_spawn_egg"),
			new GuardSpawnEggItem(new Item.Settings()
					.registryKey(RegistryKey.of(RegistryKeys.ITEM, id("guard_spawn_egg")))
					.component(DataComponentTypes.ENTITY_DATA,
							TypedEntityData.create(GUARD_ENTITY_TYPE, new NbtCompound()))));

	public static final net.minecraft.item.ItemGroup GUARD_KIT_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			id("guards"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.guardvillagers.guards"))
					.icon(() -> new ItemStack(GUARD_SPAWN_EGG))
					.entries((context, entries) -> {
						entries.add(GUARD_SPAWN_EGG);
						entries.add(GUARD_WHISTLE);
						entries.add(Items.EMERALD);
					})
					.build());

	public static final ScreenHandlerType<GuardTacticsScreenHandler> GUARD_TACTICS_SCREEN_HANDLER = Registry.register(
			Registries.SCREEN_HANDLER,
			id("guard_tactics"),
			new ScreenHandlerType<>((syncId, playerInventory) -> new GuardTacticsScreenHandler(syncId, playerInventory),
					FeatureFlags.VANILLA_FEATURES));

	@Override
	public void onInitialize() {
		GuardVillagersConfig.load();
		warmupPersistentStateClasses();
		registerPayloads();
		FabricDefaultAttributeRegistry.register(GUARD_ENTITY_TYPE, GuardEntity.createAttributes());
		registerDispenserBehavior();
		registerCommands();
		registerEvents();
		LOGGER.info("Guard Villagers initialized");
	}

	private static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(GuardDebugSyncPayload.ID, GuardDebugSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GuardDebugDataPayload.ID, GuardDebugDataPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GuardRosterSyncPayload.ID, GuardRosterSyncPayload.CODEC);
	}

	private static void warmupPersistentStateClasses() {
		Class<?>[] stateClasses = {
				GuardReputationState.class,
				GuardUpgradeState.class,
				GuardDebugState.class,
				GuardDiplomacyState.class,
				GuardVillageState.class,
				GuardTacticsState.class
		};
		for (Class<?> stateClass : stateClasses) {
			try {
				Class.forName(stateClass.getName(), true, GuardVillagersMod.class.getClassLoader());
			} catch (Throwable throwable) {
				LOGGER.error("Failed to preload persistent state class {}", stateClass.getName(), throwable);
				throw new IllegalStateException(
						"Guard Villagers startup failed while preloading " + stateClass.getSimpleName(), throwable);
			}
		}
	}

	private static void registerDispenserBehavior() {
		DispenserBlock.registerBehavior(GUARD_SPAWN_EGG, new ItemDispenserBehavior() {
			@Override
			protected ItemStack dispenseSilently(net.minecraft.util.math.BlockPointer pointer, ItemStack stack) {
				ServerWorld world = pointer.world();
				Direction direction = pointer.state().get(DispenserBlock.FACING);
				BlockPos spawnPos = pointer.pos().offset(direction);

				GuardEntity guard = GUARD_ENTITY_TYPE.create(world, SpawnReason.DISPENSER);
				if (guard == null) {
					return stack;
				}

				BlockPos spawn = findNearbyGuardSpawnPos(world, spawnPos, 8);
				if (spawn == null) {
					return stack;
				}
				guard.refreshPositionAndAngles(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
						direction.getPositiveHorizontalDegrees(), 0.0F);
				guard.applyNaturalLoadout(world);
				guard.setBehavior(GuardBehavior.random(world.getRandom()));
				if (world.spawnEntity(guard)) {
					stack.decrement(1);
				}
				return stack;
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static GuardPlayerUpgrades getUpgrades(ServerPlayerEntity player) {
		return getUpgrades(player.getCommandSource().getServer(), player.getUuid());
	}

	public static GuardPlayerUpgrades getUpgrades(ServerWorld world, UUID playerUuid) {
		return getUpgrades(world.getServer(), playerUuid);
	}

	public static GuardPlayerUpgrades getUpgrades(MinecraftServer server, UUID playerUuid) {
		GuardUpgradeState state = server.getOverworld().getPersistentStateManager().getOrCreate(GuardUpgradeState.TYPE);
		return state.getOrCreate(playerUuid);
	}

	public static float getHealingAmount(ServerWorld world, UUID ownerUuid) {
		if (ownerUuid == null) {
			return 1.0F;
		}
		return getUpgrades(world, ownerUuid).getHealingPerCycle();
	}

	public static int getHealingIntervalTicks(ServerWorld world, UUID ownerUuid) {
		if (ownerUuid == null) {
			return 100;
		}
		return getUpgrades(world, ownerUuid).getHealingIntervalTicks();
	}

	public static boolean hasShieldUpgrade(ServerWorld world, UUID ownerUuid) {
		return ownerUuid != null && getUpgrades(world, ownerUuid).hasShieldUpgrade();
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register(this::registerGuardCommands);
	}

	private void registerGuardCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(buildGuardsCommand("guards"));
	}

	private com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildGuardsCommand(
			String rootLiteral) {
		return CommandManager.literal(rootLiteral)
				.then(CommandManager.literal("shop")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							openShop(player);
							return Command.SINGLE_SUCCESS;
						}))
				.then(CommandManager.literal("tactics")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							openTacticsScreen(player);
							return Command.SINGLE_SUCCESS;
						}))
				.then(CommandManager.literal("stay")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							int updated = setStance(player, true);
							context.getSource()
									.sendFeedback(() -> Text.literal("Ordered " + updated + " guards to stay."), false);
							return updated;
						}))
				.then(CommandManager.literal("follow")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							int updated = setFollowUnzoned(player);
							context.getSource().sendFeedback(
									() -> Text.literal("Ordered " + updated + " owned guards to follow."), false);
							return updated;
						})
						.then(CommandManager.argument("group", StringArgumentType.greedyString())
								.executes(context -> {
									ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
									String groupName = StringArgumentType.getString(context, "group");
									int updated = setFollowByGroup(player, groupName);
									context.getSource().sendFeedback(() -> Text.literal("Ordered " + updated
											+ " guards from group \"" + groupName + "\" to follow."), false);
									return updated;
								})))
				.then(CommandManager.literal("zone")
						.then(CommandManager.argument("radius", IntegerArgumentType.integer(8, 128))
								.executes(context -> {
									ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
									int radius = IntegerArgumentType.getInteger(context, "radius");
									int updated = assignZone(player, radius);
									context.getSource().sendFeedback(
											() -> Text.literal("Assigned home zone to " + updated + " guards."), false);
									return updated;
								})))
				.then(CommandManager.literal("groups")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							openGroupsScreen(player);
							return Command.SINGLE_SUCCESS;
						})
						.then(CommandManager.literal("add")
								.executes(context -> {
									ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
									int row = addGroup(player);
									refreshOpenTacticsScreen(player);
									context.getSource()
											.sendFeedback(() -> Text.literal("Added group " + (row + 1) + "."), false);
									return row + 1;
								}))
						.then(CommandManager.literal("rename")
								// Upper bound prevents an ensureGroupCount() balloon-allocation OOM if a
								// non-operator player invokes this with Integer.MAX_VALUE.
								.then(CommandManager.argument("row", IntegerArgumentType.integer(1, MAX_GROUP_ROW))
										.then(CommandManager.argument("name", StringArgumentType.greedyString())
												.executes(context -> {
													ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
													int row = IntegerArgumentType.getInteger(context, "row") - 1;
													String name = StringArgumentType.getString(context, "name");
													boolean renamed = renameGroup(player, row, name);
													if (renamed) {
														refreshOpenTacticsScreen(player);
														context.getSource().sendFeedback(() -> Text.literal(
																"Renamed group " + (row + 1) + " to \"" + name + "\"."),
																false);
														return Command.SINGLE_SUCCESS;
													}
													context.getSource()
															.sendError(Text.literal("Could not rename that group."));
													return 0;
												}))))
						.then(CommandManager.literal("assign")
								.then(CommandManager.argument("guardUuid", StringArgumentType.string())
										// 0 means "unassign"; positive values are a row index clamped to the same
										// upper bound as rename to keep group-list allocation bounded.
										.then(CommandManager.argument("groupRow", IntegerArgumentType.integer(0, MAX_GROUP_ROW))
												.executes(context -> {
													ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
													String uuidStr = StringArgumentType.getString(context, "guardUuid");
													int groupRow = IntegerArgumentType.getInteger(context, "groupRow")
															- 1;
													int result = assignGuardToGroup(player, uuidStr, groupRow);
													if (result > 0) {
														if (groupRow < 0) {
															context.getSource()
																	.sendFeedback(() -> Text.literal(
																			"Unassigned guard from all groups."),
																			false);
														} else {
															context.getSource().sendFeedback(() -> Text.literal(
																	"Assigned guard to group " + (groupRow + 1) + "."),
																	false);
														}
													} else {
														context.getSource().sendError(Text.literal(
																"Could not assign guard (not found or not owned)."));
													}
													return result;
												})))))
				.then(CommandManager.literal("count")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							int count = countOwnedGuards(context.getSource().getServer(), player.getUuid());
							context.getSource().sendFeedback(() -> Text.literal("You own " + count + " guards."),
									false);
							return count;
						}))
				.then(CommandManager.literal("reputation")
						.requires(GuardVillagersMod::hasOperatorPermission)
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.then(CommandManager.argument("value", StringArgumentType.word())
										.executes(context -> {
											ServerPlayerEntity admin = context.getSource().getPlayerOrThrow();
											ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
											String input = StringArgumentType.getString(context, "value");
											return setReputationValue(admin, target, input);
										})))
						.then(CommandManager.argument("value", StringArgumentType.word())
								.executes(context -> {
									ServerPlayerEntity admin = context.getSource().getPlayerOrThrow();
									String input = StringArgumentType.getString(context, "value");
									return setReputationValue(admin, admin, input);
								})))
				.then(CommandManager.literal("debug")
					.requires(GuardVillagersMod::hasOperatorPermission)
					.executes(context -> toggleDebug(context.getSource().getPlayerOrThrow(), -1))
						.then(CommandManager.argument("range", IntegerArgumentType.integer(1))
								.executes(context -> toggleDebug(
										context.getSource().getPlayerOrThrow(),
										IntegerArgumentType.getInteger(context, "range")))))
				.then(CommandManager.literal("config")
						.requires(GuardVillagersMod::hasOperatorPermission)
						.then(CommandManager.literal("reload")
								.executes(context -> {
									GuardVillagersConfig.reload();
									context.getSource().sendFeedback(
											() -> Text.literal("Guard Villagers config reloaded from "
													+ GuardVillagersConfig.configPath()),
											false);
									return Command.SINGLE_SUCCESS;
								}))
						.then(CommandManager.literal("path")
								.executes(context -> {
									context.getSource().sendFeedback(
											() -> Text.literal("Guard Villagers config: "
													+ GuardVillagersConfig.configPath()),
											false);
									return Command.SINGLE_SUCCESS;
								})));
	}

	private static void openShop(ServerPlayerEntity player) {
		try {
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, playerInventory, ignoredPlayer) -> new GuardShopScreenHandler(syncId, playerInventory,
							player),
					Text.translatable("screen.guardvillagers.shop")));
		} catch (RuntimeException exception) {
			LOGGER.error("Failed to open guard shop for {}", player.getName().getString(), exception);
			player.sendMessage(Text.literal("Could not open the guard shop. Check server logs."), false);
		}
	}

	public static void openTacticsScreen(ServerPlayerEntity player) {
		try {
			syncOwnedGuardRoster(player);
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, playerInventory, ignoredPlayer) -> new GuardTacticsScreenHandler(syncId, playerInventory,
							player),
					Text.translatable("screen.guardvillagers.tactics")));
		} catch (RuntimeException exception) {
			LOGGER.error("Failed to open tactics screen for {}", player.getName().getString(), exception);
			player.sendMessage(Text.literal("Could not open tactics screen. Check server logs."), false);
		}
	}

	public static void openGroupsScreen(ServerPlayerEntity player) {
		try {
			syncOwnedGuardRoster(player);
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, playerInventory, ignoredPlayer) -> new GuardTacticsScreenHandler(syncId, playerInventory,
							player),
					Text.translatable("screen.guardvillagers.groups")));
		} catch (RuntimeException exception) {
			LOGGER.error("Failed to open groups screen for {}", player.getName().getString(), exception);
			player.sendMessage(Text.literal("Could not open groups screen. Check server logs."), false);
		}
	}

	public static int getAdjustedGuardCost(ServerPlayerEntity player) {
		GuardPlayerUpgrades upgrades = getUpgrades(player);
		int basePrice = upgrades.getGuardCost();
		double reputation = getReputation(player);
		double modifier = 1.5D - (reputation * 0.75D);
		modifier = Math.max(0.75D, Math.min(1.5D, modifier));
		return Math.max(1, (int) Math.round(basePrice * modifier));
	}

	public static double getReputation(ServerPlayerEntity player) {
		return GuardReputationManager.getEffectiveReputation(player);
	}

	public static GuardPurchaseResult purchaseGuard(ServerPlayerEntity player) {
		return purchaseGuards(player, 1).result();
	}

	public static PurchaseBatchResult purchaseGuards(ServerPlayerEntity player, int requestedCount) {
		if (!GuardReputationManager.isTrustedByGuards(player.getEntityWorld(), player.getUuid(),
				player.getBlockPos())) {
			return new PurchaseBatchResult(GuardPurchaseResult.NOT_TRUSTED, 0, List.of());
		}

		int normalizedCount = Math.max(1, requestedCount);
		GuardPlayerUpgrades upgrades = getUpgrades(player);
		int costPerGuard = getAdjustedGuardCost(player);
		boolean creativeMode = player.getAbilities().creativeMode;
		int toSpawn = normalizedCount;
		if (!creativeMode) {
			int affordable = costPerGuard <= 0 ? 0
					: GuardEconomy.countEmeraldBlocks(player.getInventory()) / costPerGuard;
			toSpawn = Math.min(normalizedCount, affordable);
		}
		if (toSpawn <= 0 && !creativeMode) {
			return new PurchaseBatchResult(GuardPurchaseResult.INSUFFICIENT_FUNDS, 0, List.of());
		}

		ServerWorld world = player.getEntityWorld();
		int spawned = 0;
		List<String> spawnedNames = new ArrayList<>();
		try {
			int startRoleIndex = countOwnedGuards(player.getCommandSource().getServer(), player.getUuid())
					% GuardRole.values().length;
			for (int i = 0; i < toSpawn; i++) {
				if (!GuardEconomy.spendEmeraldBlocks(player, costPerGuard)) {
					break;
				}
				GuardRole roleForSpawn = GuardRole.values()[(startRoleIndex + i) % GuardRole.values().length];
				GuardEntity spawnedGuard = trySpawnPurchasedGuard(world, player, upgrades, roleForSpawn);
				if (spawnedGuard != null) {
					spawned++;
					spawnedNames.add(spawnedGuard.getName().getString());
					continue;
				}
				if (!creativeMode) {
					GuardEconomy.refundEmeraldBlocks(player, costPerGuard);
				}
				break;
			}
			if (spawned > 0) {
				return new PurchaseBatchResult(GuardPurchaseResult.SUCCESS, spawned, List.copyOf(spawnedNames));
			}
			LOGGER.warn("Guard purchase failed to spawn for {} in world {}", player.getName().getString(),
					world.getRegistryKey().getValue());
			return new PurchaseBatchResult(GuardPurchaseResult.SPAWN_FAILED, 0, List.of());
		} catch (RuntimeException exception) {
			LOGGER.error("Guard purchase crashed for {}", player.getName().getString(), exception);
			return new PurchaseBatchResult(GuardPurchaseResult.INTERNAL_ERROR, spawned, List.copyOf(spawnedNames));
		}
	}

	public static boolean spendEmeraldBlocks(ServerPlayerEntity player, int amount) {
		return GuardEconomy.spendEmeraldBlocks(player, amount);
	}

	private static GuardEntity trySpawnPurchasedGuard(ServerWorld world, ServerPlayerEntity player,
			GuardPlayerUpgrades upgrades, GuardRole assignedRole) {
		GuardEntity guard = GUARD_ENTITY_TYPE.create(world, SpawnReason.EVENT);
		if (guard == null) {
			return null;
		}

		BlockPos candidate = new BlockPos(MathHelper.floor(player.getX()), player.getBlockY(),
				MathHelper.floor(player.getZ()));
		BlockPos spawn = findNearbyGuardSpawnPos(world, candidate, 10);
		if (spawn == null) {
			return null;
		}

		guard.refreshPositionAndAngles(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYaw(),
				0.0F);
		guard.setOwnerUuid(player.getUuid());
		guard.setStaying(false);
		guard.setFollowOverride(true);
		guard.setRole(assignedRole);
		guard.applyPurchasedLoadout(world, upgrades);
		guard.setSquadLeader(false);
		if (world.spawnEntity(guard)) {
			return guard;
		}
		GuardOwnershipIndex.untrack(guard);
		return null;
	}

	private static int setStance(ServerPlayerEntity player, boolean staying) {
		int changed = 0;
		for (GuardEntity guard : getOwnedGuards(player.getCommandSource().getServer(), player.getUuid())) {
			guard.setStaying(staying);
			if (!staying) {
				guard.clearHome();
				guard.clearCombatTarget();
			}
			changed++;
		}
		return changed;
	}

	private static int setFollowUnzoned(ServerPlayerEntity player) {
		int changed = 0;
		for (GuardEntity guard : getOwnedGuards(player.getCommandSource().getServer(), player.getUuid())) {
			guard.setStaying(false);
			guard.setFollowOverride(true);
			guard.clearCombatTarget();
			changed++;
		}
		return changed;
	}

	private static int setFollowByGroup(ServerPlayerEntity player, String groupName) {
		int changed = 0;
		for (GuardEntity guard : getOwnedGuards(player.getCommandSource().getServer(), player.getUuid())) {
			if (guard.getGroupName().equalsIgnoreCase(groupName)) {
				guard.setStaying(false);
				guard.setFollowOverride(true);
				guard.clearCombatTarget();
				changed++;
			}
		}
		return changed;
	}

	private static int setBehavior(ServerPlayerEntity player, GuardBehavior behavior) {
		int changed = 0;
		for (GuardEntity guard : getOwnedGuards(player.getCommandSource().getServer(), player.getUuid())) {
			guard.setBehavior(behavior);
			guard.clearCombatTarget();
			changed++;
		}
		player.sendMessage(Text.literal("Set " + changed + " guards to " + behavior.name().toLowerCase() + "."), true);
		return changed;
	}

	private static int setBehaviorRandom(ServerPlayerEntity player) {
		List<GuardEntity> ownedGuards = getOwnedGuards(player.getCommandSource().getServer(), player.getUuid());
		if (ownedGuards.isEmpty()) {
			player.sendMessage(Text.literal("No owned guards found."), true);
			return 0;
		}
		Collections.shuffle(ownedGuards, new java.util.Random(player.getEntityWorld().getRandom().nextLong()));
		GuardBehavior[] behaviors = GuardBehavior.values();
		for (int i = 0; i < ownedGuards.size(); i++) {
			GuardEntity guard = ownedGuards.get(i);
			GuardBehavior behavior = behaviors[i % behaviors.length];
			guard.setBehavior(behavior);
			guard.clearCombatTarget();
		}
		player.sendMessage(Text.literal("Distributed " + ownedGuards.size() + " guards evenly across behaviors."),
				true);
		return ownedGuards.size();
	}

	private static int assignZone(ServerPlayerEntity player, int radius) {
		int changed = 0;
		BlockPos home = player.getBlockPos();
		for (GuardEntity guard : player.getEntityWorld().getEntitiesByClass(
				GuardEntity.class,
				player.getBoundingBox().expand(96.0D),
				entity -> entity.isOwnedBy(player.getUuid()))) {
			guard.setHome(home, radius);
			guard.clearCombatTarget();
			changed++;
		}
		return changed;
	}

	private static int addGroup(ServerPlayerEntity player) {
		MinecraftServer server = player.getCommandSource().getServer();
		if (server == null) {
			return 0;
		}
		GuardTacticsState state = GuardTacticsManager.getState(server);
		GuardTacticsState.PlayerTactics tactics = state.getOrCreate(player.getUuid());
		int row = tactics.addGroup();
		if (row < 0) {
			player.sendMessage(Text.literal("Maximum group count (256) reached."), true);
			return 0;
		}
		state.markDirty();
		syncOwnedGuardRoster(player);
		return row;
	}

	private static boolean renameGroup(ServerPlayerEntity player, int row, String requestedName) {
		if (row < 0 || requestedName == null || requestedName.isBlank()) {
			return false;
		}
		MinecraftServer server = player.getCommandSource().getServer();
		if (server == null) {
			return false;
		}
		GuardTacticsState state = GuardTacticsManager.getState(server);
		GuardTacticsState.PlayerTactics tactics = state.getOrCreate(player.getUuid());
		tactics.setGroupName(row, requestedName);
		String normalizedName = tactics.getGroupName(row);
		for (GuardEntity guard : getOwnedGuards(server, player.getUuid())) {
			if (guard.getGroupIndex() == row) {
				guard.setGroupName(normalizedName);
			}
		}
		state.markDirty();
		syncOwnedGuardRoster(player);
		return true;
	}

	private static int assignGuardToGroup(ServerPlayerEntity player, String uuidStr, int groupRow) {
		UUID guardUuid;
		try {
			guardUuid = UUID.fromString(uuidStr);
		} catch (IllegalArgumentException e) {
			return 0;
		}
		MinecraftServer server = player.getCommandSource().getServer();
		if (server == null) {
			return 0;
		}
		for (GuardEntity guard : getOwnedGuards(server, player.getUuid())) {
			if (guard.getUuid().equals(guardUuid)) {
				guard.setGroupIndex(groupRow);
				if (groupRow < 0) {
					guard.setGroupName("Unassigned");
				} else {
					GuardTacticsState state = GuardTacticsManager.getState(server);
					GuardTacticsState.PlayerTactics tactics = state.getOrCreate(player.getUuid());
					String groupName = tactics.getGroupName(groupRow);
					if (groupName != null && !groupName.isBlank()) {
						guard.setGroupName(groupName);
					}
				}
				guard.updateGroupNameplate();
				syncOwnedGuardRoster(player);
				return 1;
			}
		}
		return 0;
	}

	private static int setReputationValue(ServerPlayerEntity admin, ServerPlayerEntity target, String input) {
		Double parsedValue = parseReputationInput(input);
		if (parsedValue == null) {
			admin.sendMessage(Text.literal(
					"Invalid reputation value \"" + input + "\". Use 0.00 to 1.00 with up to 2 decimal places.")
					.formatted(Formatting.RED), false);
			return 0;
		}

		GuardReputationManager.setReputation(admin.getEntityWorld(), target.getUuid(), parsedValue);
		String valueText = formatReputation(parsedValue);
		if (admin.getUuid().equals(target.getUuid())) {
			admin.sendMessage(Text.literal("Set your guard reputation to " + valueText + "."), false);
		} else {
			admin.sendMessage(
					Text.literal("Set " + target.getName().getString() + "'s guard reputation to " + valueText + "."),
					false);
			target.sendMessage(Text.literal("Your guard reputation was set to " + valueText + " by an operator."),
					false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static Double parseReputationInput(String input) {
		if (input == null) {
			return null;
		}
		String normalized = input.trim();
		if (!REPUTATION_INPUT_PATTERN.matcher(normalized).matches()) {
			return null;
		}
		try {
			BigDecimal value = new BigDecimal(normalized);
			if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0 || value.scale() > 2) {
				return null;
			}
			return value.setScale(2, RoundingMode.UNNECESSARY).doubleValue();
		} catch (NumberFormatException | ArithmeticException exception) {
			return null;
		}
	}

	private static String formatReputation(double value) {
		BigDecimal normalized = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
		return normalized.toPlainString();
	}

	private static boolean hasOperatorPermission(ServerCommandSource source) {
		if (source == null) {
			return false;
		}
		if (!(source.getPermissions() instanceof LeveledPermissionPredicate leveled)) {
			return false;
		}
		return leveled.getLevel().isAtLeast(PermissionLevel.GAMEMASTERS);
	}

	private static void refreshOpenTacticsScreen(ServerPlayerEntity player) {
		if (player.currentScreenHandler instanceof GuardTacticsScreenHandler tacticsScreenHandler) {
			tacticsScreenHandler.refreshInventory();
		}
	}

	public static void syncOwnedGuardRoster(ServerPlayerEntity player) {
		MinecraftServer server = player.getCommandSource().getServer();
		if (server == null) {
			return;
		}

		GuardTacticsState state = GuardTacticsManager.getState(server);
		GuardTacticsState.PlayerTactics tactics = state.getOrCreate(player.getUuid());
		List<String> groupNames = new ArrayList<>(tactics.groupCount());
		for (int row = 0; row < tactics.groupCount(); row++) {
			groupNames.add(tactics.getGroupName(row));
		}

		List<GuardRosterSyncPayload.GuardSummary> guards = new ArrayList<>();
		for (GuardEntity guard : getOwnedGuards(server, player.getUuid())) {
			int groupIndex = guard.getGroupIndex();
			String groupName = resolveRosterGroupName(groupNames, guard, groupIndex);
			guards.add(new GuardRosterSyncPayload.GuardSummary(
					guard.getUuid(),
					guard.getName().getString(),
					guard.getLevel(),
					groupIndex,
					groupName,
					guard.getMainHandStack(),
					guard.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),
					guard.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
					guard.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
					guard.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET)));
		}

		ServerPlayNetworking.send(player, new GuardRosterSyncPayload(groupNames, guards));
	}

	private static String resolveRosterGroupName(List<String> groupNames, GuardEntity guard, int groupIndex) {
		if (groupIndex >= 0 && groupIndex < groupNames.size()) {
			return groupNames.get(groupIndex);
		}
		return guard.getGroupName();
	}

	private static int countOwnedGuards(MinecraftServer server, UUID ownerUuid) {
		return GuardOwnershipIndex.countOwnedGuards(server, ownerUuid);
	}

	private static List<GuardEntity> getOwnedGuards(MinecraftServer server, UUID ownerUuid) {
		return GuardOwnershipIndex.getOwnedGuards(server, ownerUuid);
	}

	public record PurchaseBatchResult(GuardPurchaseResult result, int spawnedCount, List<String> guardNames) {
		public PurchaseBatchResult {
			guardNames = List.copyOf(guardNames);
		}
	}

	public static BlockPos findGuardSpawnPos(ServerWorld world, BlockPos origin, int verticalRange) {
		int bottomY = world.getBottomY() + 1;
		int topY = world.getTopYInclusive() - 1;
		int clampedY = Math.max(bottomY, Math.min(topY, origin.getY()));
		int range = Math.max(0, verticalRange);
		int x = origin.getX();
		int z = origin.getZ();

		BlockPos base = new BlockPos(x, clampedY, z);
		if (canGuardSpawnAt(world, base)) {
			return base;
		}

		for (int step = 1; step <= range; step++) {
			int upY = clampedY + step;
			int downY = clampedY - step;
			if (upY <= topY) {
				BlockPos up = new BlockPos(x, upY, z);
				if (canGuardSpawnAt(world, up)) {
					return up;
				}
			}
			if (downY >= bottomY) {
				BlockPos down = new BlockPos(x, downY, z);
				if (canGuardSpawnAt(world, down)) {
					return down;
				}
			}
		}
		return null;
	}

	public static BlockPos findNearbyGuardSpawnPos(ServerWorld world, BlockPos origin, int verticalRange) {
		int range = Math.max(0, verticalRange);
		BlockPos inPlace = findGuardSpawnPos(world, origin, range);
		if (inPlace != null) {
			return inPlace;
		}

		int horizontalRange = Math.max(2, range);
		for (int radius = 1; radius <= horizontalRange; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					BlockPos candidate = findGuardSpawnPos(world, origin.add(dx, 0, dz), range);
					if (candidate != null) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	public static boolean canGuardSpawnAt(ServerWorld world, BlockPos feetPos) {
		int y = feetPos.getY();
		if (y <= world.getBottomY() || y >= world.getTopYInclusive()) {
			return false;
		}

		BlockPos below = feetPos.down();
		BlockPos head = feetPos.up();
		if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) {
			return false;
		}
		if (!world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty()) {
			return false;
		}
		if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
			return false;
		}
		if (!world.getFluidState(feetPos).isEmpty() || !world.getFluidState(head).isEmpty()) {
			return false;
		}

		Box spawnBox = GUARD_ENTITY_TYPE.getDimensions().getBoxAt(
				feetPos.getX() + 0.5D,
				feetPos.getY(),
				feetPos.getZ() + 0.5D);
		return world.isSpaceEmpty(null, spawnBox);
	}

	private static int toggleDebug(ServerPlayerEntity player, int requestedRange) {
		MinecraftServer server = player.getCommandSource().getServer();
		if (server == null) {
			return 0;
		}

		UUID playerId = player.getUuid();
		boolean currentlyEnabled = GuardDebugManager.isEnabled(server, playerId);
		if (currentlyEnabled) {
			GuardDebugManager.setEnabled(server, playerId, false);
			DEBUG_PATH_HASH_CACHE.remove(playerId);
			sendDebugSync(player, false, 0.0D);
			clearDebugData(player);
			player.sendMessage(Text.literal("Debug disabled."), false);
			return Command.SINGLE_SUCCESS;
		}

		double configuredRange = requestedRange < 0 ? -1.0D : clampRequestedDebugRange(player, requestedRange);
		GuardDebugManager.setRange(server, playerId, configuredRange);
		GuardDebugManager.setEnabled(server, playerId, true);
		double effectiveRange = GuardDebugManager.getEffectiveRange(player);
		sendDebugSync(player, true, effectiveRange);
		player.sendMessage(Text.literal("Debug enabled. Range: " + formatDebugRange(effectiveRange) + " blocks."),
				false);
		return Command.SINGLE_SUCCESS;
	}

	private static double clampRequestedDebugRange(ServerPlayerEntity player, int requestedRange) {
		int maxRange = GuardDebugManager.getRangeCapBlocks(player);
		return Math.max(1.0D, Math.min(requestedRange, maxRange));
	}

	private static String formatDebugRange(double range) {
		return Integer.toString((int) Math.round(range));
	}

	private static void sendDebugSync(ServerPlayerEntity player, boolean enabled, double range) {
		ServerPlayNetworking.send(player, new GuardDebugSyncPayload(enabled, range));
	}

	private static void clearDebugData(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, new GuardDebugDataPayload(List.of()));
	}

	private void registerEvents() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)
					|| !(entity instanceof LivingEntity livingTarget)) {
				return ActionResult.PASS;
			}

			ServerWorld serverWorld = serverPlayer.getEntityWorld();
			if (livingTarget instanceof VillagerEntity || livingTarget instanceof IronGolemEntity) {
				GuardReputationManager.recordVillagerHarm(serverWorld, serverPlayer.getUuid());
			}

			if (livingTarget instanceof GuardEntity targetGuard && targetGuard.isOwnedBy(serverPlayer.getUuid())) {
				return ActionResult.PASS;
			}

			try {
				List<GuardEntity> ownedGuards = GuardOwnershipIndex
						.getOwnedGuards(serverPlayer.getCommandSource().getServer(), serverPlayer.getUuid());
				long alertTick = serverWorld.getTime();
				for (GuardEntity guard : ownedGuards) {
					if (!guard.isAlive()) {
						continue;
					}
					double alertRange = Math.max(8.0D,
							guard.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.FOLLOW_RANGE));
					if (guard.squaredDistanceTo(livingTarget) > alertRange * alertRange) {
						continue;
					}
					guard.receiveOwnerAttackAlert(livingTarget, alertTick);
				}
			} catch (RuntimeException exception) {
				LOGGER.error("Failed to dispatch manual target from {}", serverPlayer.getName().getString(), exception);
			}

			return ActionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			if (entity instanceof VillagerEntity villager) {
				GuardReputationManager.recordTradeInteraction(serverPlayer, villager);
			}
			return ActionResult.PASS;
		});

		ServerTickEvents.END_WORLD_TICK.register(world -> {
			try {
				syncGuardDebug(world);
				VillageManagerHandler.maintainVillageGuards(world);
				if (world.getRegistryKey() == World.OVERWORLD) {
					GuardReputationManager.tickDecay(world);
				}
				if (world.getTime() % 200 == 0) {
					for (ServerPlayerEntity player : world.getPlayers()) {
						boolean golemAggro = !world.getEntitiesByClass(
								IronGolemEntity.class,
								player.getBoundingBox().expand(24.0D),
								golem -> golem.getTarget() == player).isEmpty();
						if (golemAggro) {
							GuardReputationManager.applyReputationDelta(world, player.getUuid(), -2);
						}
					}
				}
			} catch (RuntimeException exception) {
				LOGGER.error("Village manager tick failed in world {}", world.getRegistryKey().getValue(), exception);
			}
		});

		// Owner defense: covers BOTH owner-as-victim and owner-as-attacker
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity.getEntityWorld() instanceof ServerWorld serverWorld)) {
				return;
			}
			LivingEntity attacker = source.getAttacker() instanceof LivingEntity la ? la : null;
			if (attacker == null) {
				return;
			}

			// Case A: owner is the victim — alert guards to target the attacker
			if (entity instanceof ServerPlayerEntity ownerVictim) {
				try {
					List<GuardEntity> guards = GuardOwnershipIndex
							.getOwnedGuards(ownerVictim.getCommandSource().getServer(), ownerVictim.getUuid());
					long tick = serverWorld.getTime();
					for (GuardEntity guard : guards) {
						if (!guard.isAlive() || guard.isAlly(attacker)) {
							continue;
						}
						double range = Math.max(8.0D,
								guard.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.FOLLOW_RANGE));
						if (guard.squaredDistanceTo(attacker) > range * range) {
							continue;
						}
						guard.receiveOwnerDamagedAlert(attacker, tick);
					}
				} catch (RuntimeException ex) {
					LOGGER.error("Failed owner-defense alert (victim) for {}", ownerVictim.getName().getString(), ex);
				}
			}

			// Case B: owner is the attacker — alert guards to target the victim
			if (attacker instanceof ServerPlayerEntity ownerAttacker && entity instanceof LivingEntity target) {
				if (target instanceof GuardEntity guardTarget && guardTarget.isOwnedBy(ownerAttacker.getUuid())) {
					return; // Don't target own guards
				}
				try {
					List<GuardEntity> guards = GuardOwnershipIndex
							.getOwnedGuards(ownerAttacker.getCommandSource().getServer(), ownerAttacker.getUuid());
					long tick = serverWorld.getTime();
					for (GuardEntity guard : guards) {
						if (!guard.isAlive() || guard.isAlly(target)) {
							continue;
						}
						double range = Math.max(8.0D,
								guard.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.FOLLOW_RANGE));
						if (guard.squaredDistanceTo(target) > range * range) {
							continue;
						}
						guard.receiveOwnerAttackAlert(target, tick);
					}
				} catch (RuntimeException ex) {
					LOGGER.error("Failed owner-defense alert (attacker) for {}", ownerAttacker.getName().getString(),
							ex);
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity.getEntityWorld() instanceof ServerWorld serverWorld)) {
				return;
			}
			if (entity instanceof GuardEntity guard) {
				GuardMoraleSystem.onAllyDeath(guard, serverWorld);
				return;
			}
			for (GuardEntity guard : serverWorld.getEntitiesByClass(
					GuardEntity.class,
					entity.getBoundingBox().expand(GuardVillagersConfig.get().captain.commandRadius),
					g -> g.isAlive() && !g.isAlly(entity))) {
				if (guard.getTarget() == entity || entity.getUuid().equals(guard.getMainTargetUuid())) {
					GuardMoraleSystem.onVictory(guard);
				}
			}
		});
	}

	private static void syncGuardDebug(ServerWorld world) {
		if (world.getTime() % DEBUG_SYNC_INTERVAL_TICKS != 0) {
			return;
		}

		MinecraftServer server = world.getServer();
		for (ServerPlayerEntity player : world.getPlayers()) {
			UUID playerId = player.getUuid();
			if (!GuardDebugManager.isEnabled(server, playerId)) {
				continue;
			}

			double effectiveRange = GuardDebugManager.getEffectiveRange(player);
			sendDebugSync(player, true, effectiveRange);
			double maxDistanceSq = effectiveRange * effectiveRange;
			Map<Integer, Integer> previousHashes = DEBUG_PATH_HASH_CACHE.getOrDefault(playerId, Map.of());
			Map<Integer, Integer> nextHashes = new HashMap<>(previousHashes.size());
			List<GuardDebugDataPayload.GuardDebugEntry> changedEntries = new ArrayList<>(previousHashes.size());

			for (GuardEntity guard : world.getEntitiesByClass(
					GuardEntity.class,
					player.getBoundingBox().expand(effectiveRange),
					entity -> (entity.getOwnerUuid() == null || entity.isOwnedBy(playerId))
							&& entity.squaredDistanceTo(player) <= maxDistanceSq)) {
				int guardId = guard.getId();
				GuardEntity.GuardDebugSnapshot snapshot = guard.getDebugSnapshot(DEBUG_MAX_PATH_NODES);
				int hash = hashDebugSnapshot(snapshot);
				nextHashes.put(guardId, hash);
				Integer previousHash = previousHashes.get(guardId);
				if (previousHash == null || previousHash != hash) {
					changedEntries.add(new GuardDebugDataPayload.GuardDebugEntry(
							guardId,
							snapshot.pathNodes(),
							snapshot.currentPathIndex(),
							snapshot.targetEntityId()));
				}
			}

			for (Integer previousGuardId : previousHashes.keySet()) {
				if (!nextHashes.containsKey(previousGuardId)) {
					changedEntries.add(new GuardDebugDataPayload.GuardDebugEntry(previousGuardId, List.of(), -1, -1));
				}
			}

			DEBUG_PATH_HASH_CACHE.put(playerId, nextHashes);
			if (!changedEntries.isEmpty()) {
				ServerPlayNetworking.send(player, new GuardDebugDataPayload(changedEntries));
			}
		}

		if (world.getRegistryKey() == World.OVERWORLD) {
			DEBUG_PATH_HASH_CACHE.keySet().removeIf(playerId -> server.getPlayerManager().getPlayer(playerId) == null
					|| !GuardDebugManager.isEnabled(server, playerId));
		}
	}

	private static int hashDebugSnapshot(GuardEntity.GuardDebugSnapshot snapshot) {
		int hash = Objects.hash(snapshot.currentPathIndex(), snapshot.targetEntityId());
		for (BlockPos node : snapshot.pathNodes()) {
			hash = 31 * hash + node.hashCode();
		}
		return hash;
	}

}
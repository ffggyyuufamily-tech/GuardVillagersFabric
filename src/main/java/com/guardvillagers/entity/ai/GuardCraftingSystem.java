package com.guardvillagers.entity.ai;

import com.guardvillagers.entity.GuardEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Map;

/**
 * Static helper methods for guard crafting operations.
 * Provides 2x2 grid-style crafting without requiring a GUI.
 * Guards can convert logs to planks, planks to crafting tables, etc.
 */
public final class GuardCraftingSystem {

	private static final Map<Item, Item> LOG_TO_PLANK_MAP = Map.ofEntries(
		Map.entry(Items.OAK_LOG, Items.OAK_PLANKS),
		Map.entry(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS),
		Map.entry(Items.BIRCH_LOG, Items.BIRCH_PLANKS),
		Map.entry(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS),
		Map.entry(Items.ACACIA_LOG, Items.ACACIA_PLANKS),
		Map.entry(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS),
		Map.entry(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS),
		Map.entry(Items.CHERRY_LOG, Items.CHERRY_PLANKS),
		Map.entry(Items.BAMBOO_BLOCK, Items.BAMBOO_PLANKS)
	);

	private GuardCraftingSystem() {
		// Static utility class
	}

	/**
	 * Attempts to craft planks from logs in the guard's inventory.
	 * Finds the first available log type, removes 1, and adds 4 corresponding planks.
	 *
	 * @param guard The guard attempting to craft
	 * @return true if a craft occurred, false otherwise
	 */
	public static boolean tryCraftPlanksFromLogs(GuardEntity guard) {
		var inventory = guard.getInventory();

		for (Map.Entry<Item, Item> entry : LOG_TO_PLANK_MAP.entrySet()) {
			Item logItem = entry.getKey();
			Item plankItem = entry.getValue();

			if (inventory.hasAtLeast(logItem, 1)) {
				if (inventory.removeItem(logItem, 1)) {
					inventory.addItem(plankItem, 4);
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Attempts to craft a crafting table from planks in the guard's inventory.
	 * Requires 4 of any single plank type. Removes the planks and adds 1 crafting table.
	 *
	 * @param guard The guard attempting to craft
	 * @return true if a craft occurred, false otherwise
	 */
	public static boolean tryCraftCraftingTable(GuardEntity guard) {
		var inventory = guard.getInventory();

		Item[] plankTypes = {
			Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
			Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
			Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS
		};

		for (Item plankType : plankTypes) {
			if (inventory.hasAtLeast(plankType, 4)) {
				if (inventory.removeItem(plankType, 4)) {
					inventory.addItem(Items.CRAFTING_TABLE, 1);
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Attempts to place a crafting table adjacent to the guard.
	 * Searches for a valid empty block position with solid ground below.
	 * Removes 1 crafting table from inventory on success.
	 *
	 * @param guard The guard attempting to place the table
	 * @param world The server world
	 * @return true if a crafting table was placed, false otherwise
	 */
	public static boolean tryPlaceCraftingTable(GuardEntity guard, ServerWorld world) {
		var inventory = guard.getInventory();

		if (!inventory.hasAtLeast(Items.CRAFTING_TABLE, 1)) {
			return false;
		}

		BlockPos guardPos = guard.getBlockPos();
		BlockPos[] adjacentPositions = {
			guardPos.offset(Direction.NORTH),
			guardPos.offset(Direction.SOUTH),
			guardPos.offset(Direction.EAST),
			guardPos.offset(Direction.WEST)
		};

		for (BlockPos candidatePos : adjacentPositions) {
			if (isValidPlacementPosition(guard, world, candidatePos)) {
				BlockState craftingTableState = Blocks.CRAFTING_TABLE.getDefaultState();
				world.setBlockState(candidatePos, craftingTableState, 3);

				inventory.removeItem(Items.CRAFTING_TABLE, 1);
				guard.setKnownCraftingTablePos(candidatePos);

				return true;
			}
		}

		return false;
	}

	/**
	 * Checks if a position is valid for placing a crafting table.
	 * Must be air, have solid ground below, within the guard's zone, and not collide with entities.
	 */
	private static boolean isValidPlacementPosition(GuardEntity guard, ServerWorld world, BlockPos pos) {
		BlockState blockState = world.getBlockState(pos);

		if (!blockState.isAir()) {
			return false;
		}

		if (!guard.canTargetWithinZone(pos)) {
			return false;
		}

		BlockPos below = pos.down();
		BlockState belowState = world.getBlockState(below);

		if (belowState.isAir() || !belowState.isSolid()) {
			return false;
		}

		// Check for entity collisions
		return world.getOtherEntities(guard, Blocks.CRAFTING_TABLE.getDefaultState().getOutlineShape(world, pos).getBoundingBox().offset(pos)).isEmpty();
	}

	/**
	 * Attempts to craft TNT from sand and gunpowder at the guard's known crafting table.
	 * Recipe: 4 sand + 5 gunpowder → 1 TNT (3x3 shaped recipe from Minecraft)
	 *
	 * @param guard The guard attempting to craft TNT
	 * @param world The server world
	 * @return true if TNT was crafted, false otherwise
	 */
	public static boolean tryCraftTnt(GuardEntity guard, ServerWorld world) {
		// Check has enough materials
		var inventory = guard.getInventory();
		if (!inventory.hasAtLeast(Items.SAND, 4) ||
			!inventory.hasAtLeast(Items.GUNPOWDER, 5)) {
			return false;
		}

		// Check guard has a known crafting table that still exists
		BlockPos tablePos = guard.getKnownCraftingTablePos();
		if (tablePos == null) {
			return false;
		}
		if (!world.getBlockState(tablePos).isOf(Blocks.CRAFTING_TABLE)) {
			// Table was destroyed — clear the reference
			guard.setKnownCraftingTablePos(null);
			return false;
		}

		// Check guard is within 4 blocks of the crafting table
		double distSq = guard.getBlockPos().getSquaredDistance(tablePos);
		if (distSq > 16.0D) { // 4 blocks squared
			return false; // Must walk to table first
		}

		// Consume materials and add TNT
		if (inventory.removeItem(Items.SAND, 4) &&
			inventory.removeItem(Items.GUNPOWDER, 5)) {
			inventory.addItem(Items.TNT, 1);
			return true;
		}
		return false;
	}

	/**
	 * Causes the guard to walk to their known crafting table.
	 * Used when materials are available but guard is not close enough to craft.
	 *
	 * @param guard The guard to navigate
	 * @param world The server world
	 */
	public static void tryWalkToCraftingTable(GuardEntity guard, ServerWorld world) {
		BlockPos tablePos = guard.getKnownCraftingTablePos();
		if (tablePos == null) return;
		if (!world.getBlockState(tablePos).isOf(Blocks.CRAFTING_TABLE)) {
			guard.setKnownCraftingTablePos(null);
			return;
		}
		double distSq = guard.getBlockPos().getSquaredDistance(tablePos);
		if (distSq > 16.0D) {
			// Navigate toward the table
			guard.getNavigation().startMovingTo(
				tablePos.getX() + 0.5D,
				tablePos.getY(),
				tablePos.getZ() + 0.5D,
				1.0D
			);
		}
	}
}

package com.guardvillagers.entity;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Virtual inventory system for guards.
 * Provides a backend data structure (no UI) that holds items as integer counts.
 * Backed by a Map<Item, Integer> and includes NBT serialization support.
 */
public final class GuardInventory {

	private static final String ITEM_COUNT_KEY = "GuardInv_ItemCount";
	private static final String ITEM_ID_KEY = "GuardInv_Item_{0}_Id";
	private static final String ITEM_AMOUNT_KEY = "GuardInv_Item_{0}_Count";

	private final Map<Item, Integer> items = new HashMap<>();

	/**
	 * Gets the count of a specific item in this inventory.
	 * Returns 0 if the item is not present.
	 */
	public int getCount(Item item) {
		return items.getOrDefault(item, 0);
	}

	/**
	 * Checks if the inventory contains at least the specified amount of an item.
	 */
	public boolean hasAtLeast(Item item, int amount) {
		return getCount(item) >= amount;
	}

	/**
	 * Adds the specified amount of an item to the inventory.
	 * Automatically creates the entry if it doesn't exist.
	 */
	public void addItem(Item item, int amount) {
		if (amount <= 0) {
			return;
		}
		items.put(item, getCount(item) + amount);
	}

	/**
	 * Removes the specified amount of an item from the inventory.
	 * Returns true if successful, false if there aren't enough items.
	 * On failure, nothing is removed (all-or-nothing semantics).
	 */
	public boolean removeItem(Item item, int amount) {
		if (amount <= 0) {
			return true;
		}
		int current = getCount(item);
		if (current < amount) {
			return false;
		}
		int newCount = current - amount;
		if (newCount <= 0) {
			items.remove(item);
		} else {
			items.put(item, newCount);
		}
		return true;
	}

	/**
	 * Clears all items from the inventory.
	 */
	public void clear() {
		items.clear();
	}

	/**
	 * Writes the inventory to a WriteView for NBT serialization.
	 * Stores items as a list of item-id and count pairs.
	 */
	public void writeToView(WriteView view) {
		int itemCount = items.size();
		view.putInt(ITEM_COUNT_KEY, itemCount);

		int index = 0;
		for (Map.Entry<Item, Integer> entry : items.entrySet()) {
			Item item = entry.getKey();
			Integer count = entry.getValue();

			Identifier itemId = Registries.ITEM.getId(item);
			String itemIdStr = itemId != null ? itemId.toString() : "minecraft:air";

			view.putString("GuardInv_Item_" + index + "_Id", itemIdStr);
			view.putInt("GuardInv_Item_" + index + "_Count", count);
			index++;
		}
	}

	/**
	 * Reads the inventory from a ReadView for NBT deserialization.
	 * Restores items from the stored list of item-id and count pairs.
	 */
	public void readFromView(ReadView view) {
		clear();

		int itemCount = view.getInt(ITEM_COUNT_KEY, 0);
		for (int i = 0; i < itemCount; i++) {
			String itemIdStr = view.getString("GuardInv_Item_" + i + "_Id", "minecraft:air");
			int count = view.getInt("GuardInv_Item_" + i + "_Count", 0);

			if (count > 0) {
				Identifier itemId = Identifier.tryParse(itemIdStr);
				if (itemId != null) {
					Item item = Registries.ITEM.get(itemId);
					if (item != null && item != Items.AIR) {
						items.put(item, count);
					}
				}
			}
		}
	}
}


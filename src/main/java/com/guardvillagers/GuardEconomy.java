package com.guardvillagers;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuardEconomy {
	private static final Logger LOGGER = LoggerFactory.getLogger(GuardVillagersMod.MOD_ID + "/economy");

	private GuardEconomy() {
	}

	public static int countEmeraldBlocks(PlayerInventory inventory) {
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(Items.EMERALD_BLOCK)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static boolean spendEmeraldBlocks(ServerPlayerEntity player, int amount) {
		if (amount < 0) {
			LOGGER.warn("Rejected negative emerald cost {} for {}", amount, player.getName().getString());
			return false;
		}
		if (amount == 0 || player.getAbilities().creativeMode) {
			return true;
		}

		PlayerInventory inventory = player.getInventory();
		int available = countEmeraldBlocks(inventory);
		if (available < amount) {
			return false;
		}

		int remaining = amount;
		int removed = 0;

		try {
			for (int i = 0; i < inventory.size() && remaining > 0; i++) {
				ItemStack stack = inventory.getStack(i);
				if (!stack.isOf(Items.EMERALD_BLOCK)) {
					continue;
				}
				int remove = Math.min(remaining, stack.getCount());
				stack.decrement(remove);
				remaining -= remove;
				removed += remove;
			}
			inventory.markDirty();
		} catch (RuntimeException exception) {
			LOGGER.error("Failed emerald debit for player {}", player.getName().getString(), exception);
			refundEmeraldBlocks(player, removed);
			return false;
		}

		if (remaining == 0) {
			return true;
		}

		LOGGER.error("Emerald debit drift: requested {}, removed {} for {}", amount, removed, player.getName().getString());
		refundEmeraldBlocks(player, removed);
		return false;
	}

	public static void refundEmeraldBlocks(ServerPlayerEntity player, int amount) {
		if (amount <= 0) {
			return;
		}

		int remaining = amount;
		PlayerInventory inventory = player.getInventory();
		while (remaining > 0) {
			ItemStack stack = new ItemStack(Items.EMERALD_BLOCK, Math.min(64, remaining));
			inventory.insertStack(stack);
			if (!stack.isEmpty()) {
				player.dropItem(stack, false);
			}
			remaining -= Math.min(64, remaining);
		}
		inventory.markDirty();
	}
}

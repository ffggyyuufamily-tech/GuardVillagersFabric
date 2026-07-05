package com.guardvillagers.shop;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuardShopScreenHandler extends GenericContainerScreenHandler {
	private static final int SHOP_SLOT_COUNT = 27;
	private static final Logger LOGGER = LoggerFactory.getLogger("guardvillagers/shop");

	private final GuardShopInventory shopInventory;
	private final ServerPlayerEntity owner;

	public GuardShopScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity owner) {
		this(syncId, playerInventory, new GuardShopInventory(owner), owner);
	}

	private GuardShopScreenHandler(int syncId, PlayerInventory playerInventory, GuardShopInventory shopInventory, ServerPlayerEntity owner) {
		super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, shopInventory, 3);
		this.shopInventory = shopInventory;
		this.owner = owner;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (player != this.owner) {
			return;
		}

		if (!this.getCursorStack().isEmpty()) {
			this.setCursorStack(ItemStack.EMPTY);
		}

		if (slotIndex >= 0 && slotIndex < SHOP_SLOT_COUNT) {
			if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) {
				return;
			}

			try {
				boolean bulkPurchase = actionType == SlotActionType.QUICK_MOVE;
				if (this.shopInventory.handleClick(slotIndex, bulkPurchase)) {
					this.shopInventory.refresh();
					this.sendContentUpdates();
				}
			} catch (RuntimeException exception) {
				LOGGER.error("Shop click failed for {} on slot {}", this.owner.getName().getString(), slotIndex, exception);
			}
			return;
		}

		if (slotIndex >= SHOP_SLOT_COUNT || slotIndex == -999) {
			return;
		}

		super.onSlotClick(slotIndex, button, actionType, player);
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return false;
	}

	@Override
	public boolean canInsertIntoSlot(Slot slot) {
		return false;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player == this.owner;
	}
}

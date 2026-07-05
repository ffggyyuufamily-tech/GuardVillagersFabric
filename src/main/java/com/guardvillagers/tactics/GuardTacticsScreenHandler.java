package com.guardvillagers.tactics;

import com.guardvillagers.GuardVillagersMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class GuardTacticsScreenHandler extends GenericContainerScreenHandler {
	private static final int TACTICS_SLOT_COUNT = 54;
	private final GuardTacticsInventory tacticsInventory;
	private final ServerPlayerEntity owner;

	public GuardTacticsScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(54), null, null);
	}

	public GuardTacticsScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity owner) {
		this(syncId, playerInventory, createServerInventory(owner), owner);
	}

	private GuardTacticsScreenHandler(int syncId, PlayerInventory playerInventory, GuardTacticsInventory tacticsInventory, ServerPlayerEntity owner) {
		this(syncId, playerInventory, tacticsInventory, tacticsInventory, owner);
	}

	private GuardTacticsScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, GuardTacticsInventory tacticsInventory, ServerPlayerEntity owner) {
		super(GuardVillagersMod.GUARD_TACTICS_SCREEN_HANDLER, syncId, playerInventory, inventory, 6);
		this.tacticsInventory = tacticsInventory;
		this.owner = owner;
	}

	private static GuardTacticsInventory createServerInventory(ServerPlayerEntity owner) {
		if (owner == null) {
			throw new IllegalArgumentException("owner cannot be null for server GuardTacticsScreenHandler");
		}
		return new GuardTacticsInventory(owner);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (this.owner == null || this.tacticsInventory == null) {
			super.onSlotClick(slotIndex, button, actionType, player);
			return;
		}

		if (player != this.owner) {
			return;
		}

		if (!this.getCursorStack().isEmpty()) {
			this.setCursorStack(ItemStack.EMPTY);
		}

		if (slotIndex >= 0 && slotIndex < TACTICS_SLOT_COUNT) {
			if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) {
				return;
			}
			if (this.tacticsInventory.handleClick(slotIndex, button)) {
				this.tacticsInventory.refresh();
				this.sendContentUpdates();
			}
			return;
		}

		if (slotIndex >= TACTICS_SLOT_COUNT || slotIndex == -999) {
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
		if (this.owner == null) {
			return true;
		}
		return player == this.owner;
	}

	public void refreshInventory() {
		if (this.tacticsInventory == null) {
			return;
		}
		this.tacticsInventory.refresh();
		this.sendContentUpdates();
	}
}

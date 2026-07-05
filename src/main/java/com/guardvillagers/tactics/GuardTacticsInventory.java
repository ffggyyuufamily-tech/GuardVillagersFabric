package com.guardvillagers.tactics;

import com.guardvillagers.GuardTacticsManager;
import com.guardvillagers.data.GuardTacticsState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;

public final class GuardTacticsInventory extends SimpleInventory {
	private static final int SIZE = 54;

	private final ServerPlayerEntity owner;
	private final GuardTacticsState tacticsState;
	private final GuardTacticsState.PlayerTactics playerTactics;

	public GuardTacticsInventory(ServerPlayerEntity owner) {
		super(SIZE);
		this.owner = Objects.requireNonNull(owner, "owner");
		MinecraftServer server = this.owner.getCommandSource().getServer();
		if (server == null) {
			throw new IllegalStateException("Cannot create GuardTacticsInventory without an active server");
		}
		this.tacticsState = GuardTacticsManager.getState(server);
		this.playerTactics = this.tacticsState.getOrCreate(this.owner.getUuid());
		this.refresh();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return player == this.owner;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return false;
	}

	public boolean handleClick(int slot, int button) {
		return false;
	}

	public void refresh() {
		for (int i = 0; i < this.size(); i++) {
			this.setStack(i, ItemStack.EMPTY);
		}
	}
}

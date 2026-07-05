package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.GuardRole;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public final class GuardLogisticsGoal extends Goal {
	private final GuardEntity guard;
	private int cooldown;

	public GuardLogisticsGoal(GuardEntity guard) {
		this.guard = guard;
		this.setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		GuardVillagersConfig.Logistics config = GuardVillagersConfig.get().logistics;
		if (!config.enabled || this.guard.isStaying()) {
			return false;
		}
		if (this.guard.getRole() == GuardRole.BOWMAN && config.arrowRecovery && this.guard.getArrowReserve() <= config.lowArrowThreshold) {
			return true;
		}
		return this.guard.getHealth() < this.guard.getMaxHealth() * 0.85F
				|| this.guard.getBuildingBlockReserve() < 8;
	}

	@Override
	public boolean shouldContinue() {
		return this.canStart() && !this.guard.hasActiveCombatTarget();
	}

	@Override
	public void tick() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		GuardVillagersConfig.Logistics config = GuardVillagersConfig.get().logistics;
		if (!config.enabled) {
			return;
		}
		if (this.cooldown-- > 0) {
			return;
		}
		this.cooldown = config.pickupIntervalTicks;

		if (config.shareItems && this.tryShareWithAlly(world, config)) {
			return;
		}
		if (this.tryPickupNearbyItem(world, config)) {
			return;
		}
		if (config.shareItems) {
			this.tryLootChest(world, config);
		}
	}

	private boolean tryPickupNearbyItem(ServerWorld world, GuardVillagersConfig.Logistics config) {
		List<ItemEntity> items = world.getEntitiesByClass(
				ItemEntity.class,
				this.guard.getBoundingBox().expand(config.pickupRange),
				item -> item.isAlive() && !item.cannotPickup() && this.isUsefulItem(item.getStack()));
		if (items.isEmpty()) {
			return false;
		}
		items.sort(Comparator.comparingDouble(this.guard::squaredDistanceTo));
		ItemEntity nearest = items.getFirst();
		if (this.guard.squaredDistanceTo(nearest) > 1.44D) {
			this.guard.getGuardNavigation().startMovingToStatic(nearest.getBlockPos(), 1.0D);
			return true;
		}
		this.collectItem(nearest);
		return true;
	}

	private void collectItem(ItemEntity itemEntity) {
		ItemStack stack = itemEntity.getStack();
		if (stack.isEmpty()) {
			return;
		}
		Item item = stack.getItem();
		int count = stack.getCount();
		if (item == Items.ARROW) {
			this.guard.addArrowReserve(count);
		} else if (this.isFoodItem(stack)) {
			float heal = Math.min(4.0F, count * 2.0F);
			this.guard.heal(heal);
		} else if (this.isBuildingBlock(stack)) {
			this.guard.addBuildingBlockReserve(count);
		}
		itemEntity.discard();
	}

	private boolean tryShareWithAlly(ServerWorld world, GuardVillagersConfig.Logistics config) {
		if (this.guard.getRole() != GuardRole.BOWMAN || this.guard.getArrowReserve() <= config.lowArrowThreshold) {
			for (GuardEntity ally : world.getEntitiesByClass(
					GuardEntity.class,
					this.guard.getBoundingBox().expand(config.pickupRange),
					entity -> entity.isAlive() && entity != this.guard && this.guard.isSameGroup(entity))) {
				if (ally.getRole() == GuardRole.BOWMAN && ally.getArrowReserve() <= config.lowArrowThreshold
						&& this.guard.getArrowReserve() > config.lowArrowThreshold + config.shareAmount) {
					if (this.guard.squaredDistanceTo(ally) > 2.25D) {
						this.guard.getGuardNavigation().startMovingToStatic(ally.getBlockPos(), 1.0D);
					} else {
						int transfer = Math.min(config.shareAmount, this.guard.getArrowReserve() - config.lowArrowThreshold);
						this.guard.addArrowReserve(-transfer);
						ally.addArrowReserve(transfer);
					}
					return true;
				}
			}
			return false;
		}

		for (GuardEntity ally : world.getEntitiesByClass(
				GuardEntity.class,
				this.guard.getBoundingBox().expand(config.pickupRange),
				entity -> entity.isAlive() && entity != this.guard && this.guard.isSameGroup(entity))) {
			if (ally.getArrowReserve() > config.lowArrowThreshold + config.shareAmount) {
				if (this.guard.squaredDistanceTo(ally) > 2.25D) {
					this.guard.getGuardNavigation().startMovingToStatic(ally.getBlockPos(), 1.0D);
				} else {
					int transfer = Math.min(config.shareAmount, ally.getArrowReserve() - config.lowArrowThreshold);
					ally.addArrowReserve(-transfer);
					this.guard.addArrowReserve(transfer);
				}
				return true;
			}
		}
		return false;
	}

	private void tryLootChest(ServerWorld world, GuardVillagersConfig.Logistics config) {
		BlockPos guardPos = this.guard.getBlockPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		int range = (int) Math.ceil(config.chestSearchRange);
		for (int dx = -range; dx <= range; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -range; dz <= range; dz++) {
					mutable.set(guardPos.getX() + dx, guardPos.getY() + dy, guardPos.getZ() + dz);
					if (guardPos.getSquaredDistance(mutable) > config.chestSearchRange * config.chestSearchRange) {
						continue;
					}
					BlockState state = world.getBlockState(mutable);
					if (!(state.getBlock() instanceof ChestBlock)) {
						continue;
					}
					BlockEntity blockEntity = world.getBlockEntity(mutable);
					if (!(blockEntity instanceof ChestBlockEntity chest)) {
						continue;
					}
					if (this.tryExtractFromChest(chest, config)) {
						this.guard.getGuardNavigation().startMovingToStatic(mutable.toImmutable(), 1.0D);
						return;
					}
				}
			}
		}
	}

	private boolean tryExtractFromChest(ChestBlockEntity chest, GuardVillagersConfig.Logistics config) {
		for (int slot = 0; slot < chest.size(); slot++) {
			ItemStack stack = chest.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (this.guard.getRole() == GuardRole.BOWMAN && config.arrowRecovery && stack.isOf(Items.ARROW)) {
				int take = Math.min(stack.getCount(), config.shareAmount);
				this.guard.addArrowReserve(take);
				stack.decrement(take);
				return true;
			}
			if (this.isFoodItem(stack) && this.guard.getHealth() < this.guard.getMaxHealth()) {
				this.guard.heal(4.0F);
				stack.decrement(1);
				return true;
			}
			if (this.isBuildingBlock(stack)) {
				int take = Math.min(stack.getCount(), config.shareAmount);
				this.guard.addBuildingBlockReserve(take);
				stack.decrement(take);
				return true;
			}
		}
		return false;
	}

	private boolean isUsefulItem(ItemStack stack) {
		GuardVillagersConfig.Logistics config = GuardVillagersConfig.get().logistics;
		if (stack.isOf(Items.ARROW) && config.arrowRecovery) {
			return true;
		}
		if (this.isFoodItem(stack)) {
			return true;
		}
		return this.isBuildingBlock(stack);
	}

	private boolean isFoodItem(ItemStack stack) {
		return stack.contains(DataComponentTypes.FOOD) || stack.contains(DataComponentTypes.CONSUMABLE);
	}

	private boolean isBuildingBlock(ItemStack stack) {
		return stack.isOf(Items.COBBLESTONE)
				|| stack.isOf(Items.COBBLESTONE_STAIRS)
				|| stack.isOf(Items.LADDER)
				|| stack.isOf(Items.OAK_PLANKS)
				|| stack.isOf(Items.STONE);
	}
}

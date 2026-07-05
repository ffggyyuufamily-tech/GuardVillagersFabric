package com.guardvillagers.item;

import com.guardvillagers.GuardVillagersMod;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;

public class GuardSpawnEggItem extends SpawnEggItem {
	public GuardSpawnEggItem(Settings settings) {
		super(settings);
	}

	@Override
	public EntityType<?> getEntityType(ItemStack stack) {
		return GuardVillagersMod.GUARD_ENTITY_TYPE;
	}
}

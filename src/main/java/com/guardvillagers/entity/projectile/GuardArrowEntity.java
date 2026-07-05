package com.guardvillagers.entity.projectile;

import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public class GuardArrowEntity extends ArrowEntity {
	public GuardArrowEntity(EntityType<? extends ArrowEntity> type, World world) {
		super(type, world);
	}

	public GuardArrowEntity(World world, LivingEntity owner, ItemStack stack, ItemStack shotFrom) {
		super(world, owner, stack, shotFrom);
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		Entity hitEntity = entityHitResult.getEntity();
		Entity owner = this.getOwner();
		if (hitEntity instanceof GuardEntity hitGuard && owner instanceof GuardEntity shooterGuard) {
			float avoidance = GuardVillagersConfig.get().combat.friendlyFireAvoidance;
			if (shooterGuard.getOwnerUuid() != null
					&& shooterGuard.getOwnerUuid().equals(hitGuard.getOwnerUuid())
					&& (avoidance >= 1.0F || shooterGuard.getRandom().nextFloat() < avoidance)) {
				if (this.getEntityWorld() instanceof ServerWorld) {
					this.discard();
				}
				return;
			}
		}
		super.onEntityHit(entityHitResult);
	}

	@Override
	protected void onHit(LivingEntity target) {
		super.onHit(target);
		if (this.getEntityWorld() instanceof ServerWorld) {
			this.discard();
		}
	}
}

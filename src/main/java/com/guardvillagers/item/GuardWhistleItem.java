package com.guardvillagers.item;

import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.List;

public final class GuardWhistleItem extends Item {
	public GuardWhistleItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(net.minecraft.world.World world, PlayerEntity user, Hand hand) {
		if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
			return ActionResult.SUCCESS;
		}

		if (player.isSneaking()) {
			this.rallyNearbyLeaders(player);
			return ActionResult.SUCCESS;
		}

		GuardVillagersMod.openTacticsScreen(player);
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof GuardEntity guard) || !(user instanceof ServerPlayerEntity player)) {
			return ActionResult.PASS;
		}
		if (!guard.isOwnedBy(player.getUuid())) {
			player.sendMessage(Text.literal("This guard does not follow your commands."), true);
			return ActionResult.SUCCESS;
		}

		if (player.isSneaking()) {
			int nextRadius = Math.min(96, Math.max(16, guard.getPatrolRadius() + 8));
			guard.setFollowOverride(false);
			guard.clearCombatTarget();
			if (guard.getHome().isEmpty()) {
				guard.setHome(guard.getBlockPos(), nextRadius);
			} else {
				guard.setPatrolRadius(nextRadius);
			}
			player.sendMessage(Text.literal("Patrol radius set to " + nextRadius + " blocks."), true);
		} else {
			int radius = Math.max(24, guard.getPatrolRadius());
			guard.setFollowOverride(false);
			guard.clearCombatTarget();
			guard.setHome(guard.getBlockPos(), radius);
			player.sendMessage(Text.literal("Guard home zone assigned at current location."), true);
		}
		return ActionResult.SUCCESS;
	}

	private void rallyNearbyLeaders(ServerPlayerEntity caller) {
		ServerWorld world = caller.getEntityWorld();
		Box rallyRange = caller.getBoundingBox().expand(64.0D);
		List<GuardEntity> leaders = world.getEntitiesByClass(
			GuardEntity.class,
			rallyRange,
			guard -> guard.isSquadLeader() && guard.isOwnedBy(caller.getUuid())
		);

		LivingEntity threat = caller.getAttacker();
		for (GuardEntity leader : leaders) {
			leader.setStaying(false);
			leader.rallyTo(caller.getBlockPos(), 240);
			if (threat != null && threat.isAlive()) {
				leader.setPriorityTarget(threat);
			}
		}
		caller.sendMessage(Text.literal("Rally command sent to " + leaders.size() + " squad leaders."), true);
	}
}

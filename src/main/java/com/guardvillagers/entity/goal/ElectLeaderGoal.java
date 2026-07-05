package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public final class ElectLeaderGoal extends Goal {
	private final GuardEntity guard;
	private final double searchRadius;

	public ElectLeaderGoal(GuardEntity guard, double searchRadius) {
		this.guard = guard;
		this.searchRadius = searchRadius;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		return this.guard.hasSquad()
			&& this.guard.age % 80 == 0
			&& this.guard.getEntityWorld() instanceof ServerWorld;
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}

	private static final Comparator<GuardEntity> LEADER_COMPARATOR = Comparator
			.comparingInt(GuardEntity::getLevel)
			.thenComparingInt(GuardEntity::getExperience)
			.thenComparing(entity -> entity.getUuid());

	@Override
	public void start() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world) || !this.guard.hasSquad()) {
			return;
		}

		List<GuardEntity> squad = world.getEntitiesByClass(
			GuardEntity.class,
			this.guard.getBoundingBox().expand(this.searchRadius),
			other -> other.isAlive() && this.guard.isSameSquad(other)
		);
		if (squad.isEmpty()) {
			return;
		}

		GuardEntity leader = null;
		for (GuardEntity member : squad) {
			if (member.isSquadLeader()) {
				return;
			}
			if (leader == null || LEADER_COMPARATOR.compare(member, leader) > 0) {
				leader = member;
			}
		}
		if (leader == null) {
			leader = this.guard;
		}

		for (GuardEntity member : squad) {
			member.setSquadLeader(member == leader);
		}
		
		GuardDebugLogger.logSquad(this.guard, "elected leader",
			"leader", leader.getName().getString(),
			"squadSize", String.valueOf(squad.size()));
	}
}

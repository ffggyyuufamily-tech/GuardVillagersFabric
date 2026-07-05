package com.guardvillagers.entity.goal;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.entity.ai.GuardAiController;
import com.guardvillagers.entity.ai.GuardAiIntent;
import com.guardvillagers.entity.ai.GuardEngineeringDecisionSystem;
import com.guardvillagers.entity.goal.engineering.BarricadeBuilder;
import com.guardvillagers.entity.goal.engineering.BridgeBuilder;
import com.guardvillagers.entity.goal.engineering.EngineeringTask;
import com.guardvillagers.entity.goal.engineering.LadderBuilder;
import com.guardvillagers.entity.goal.engineering.PillarBuilder;
import com.guardvillagers.entity.goal.engineering.StairBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.rule.GameRules;

import java.util.EnumSet;
import java.util.List;

/**
 * Engineering goal that uses a state machine to manage persistent build tasks.
 *
 * <p>Unlike the old implementation which placed at most 1 block per tick and then returned,
 * this version maintains an {@link EngineeringTask} across ticks, allowing multi-block
 * bridges, ladders, and barricades to be built incrementally.</p>
 *
 * <h2>Why the old version only placed 1 block:</h2>
 * <ul>
 *   <li>{@code tryPlaceUtilityBlock} placed a single block and returned immediately via
 *       {@code return this.placeBlock(...)} — it never looped or persisted state.</li>
 *   <li>{@code tryPlaceDefensiveBarricade} checked {@code maxBlocksPlaced >= 2} but still
 *       only placed up to 2 blocks in a single tick, then retreated. No multi-tick support.</li>
 *   <li>There was no {@code EngineeringTask} or {@code BuildPlan} — every tick started fresh
 *       with no memory of what was being built.</li>
 *   <li>The {@code buildCooldown} reset after each build, preventing continuous construction.</li>
 * </ul>
 *
 * <h2>How the new version fixes this:</h2>
 * <ul>
 *   <li>An {@link EngineeringTask} persists across ticks with direction, target, blocks placed, etc.</li>
 *   <li>Each tick, the task places blocks incrementally until {@code maxBlocksPlaced} is reached,
 *       materials run out, or the destination is reached.</li>
 *   <li>Separate builders ({@link BridgeBuilder}, {@link LadderBuilder}, {@link BarricadeBuilder})
 *       handle terrain analysis and block placement logic.</li>
 *   <li>After each block placement, the navigation path is invalidated so the guard
 *       recalculates and walks over the newly placed blocks.</li>
 * </ul>
 */
public final class GuardEngineeringGoal extends Goal {
	private final GuardEntity guard;
	private int buildCooldown;

	/** The current persistent build task. Null when no engineering is in progress. */
	private EngineeringTask currentTask;

	public GuardEngineeringGoal(GuardEntity guard) {
		this.guard = guard;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (this.guard.age % 20 == 0) {
			GuardVillagersMod.LOGGER.info("[FORCE-DEBUG] canStart CALLED | hasTarget={} | intent={}",
				this.guard.getTarget() != null,
				this.guard.getAiController().getCurrentIntent().name());
		}
		if (!this.guard.isAlive()) return false;
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) return false;
		GuardVillagersConfig config = GuardVillagersConfig.get();
		if (!config.engineering.enabled && !config.tnt.enabled) return false;

		var decision = GuardEngineeringDecisionSystem.evaluateForStart(this.guard, world, config);
		GuardDebugLogger.logEngineeringTask(this.guard, "engineering decision",
			"shouldBuild", String.valueOf(decision.shouldBuild()),
			"reason", decision.reason().toString());
		return decision.shouldBuild();
	}

	@Override
	public boolean shouldContinue() {
		if (!this.guard.isAlive()) return false;
		if (this.guard.isRetreating()) return false;
		// Keep running if there's an active unfinished task
		if (this.currentTask != null && !this.currentTask.isFinished()) {
			return true;
		}
		// No active task — re-check if building is still needed
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) return false;
		GuardVillagersConfig config = GuardVillagersConfig.get();
		if (!config.engineering.enabled) return false;
		var decision = GuardEngineeringDecisionSystem.evaluateForStart(this.guard, world, config);
		return decision.shouldBuild();
	}

	@Override
	public void start() {
		this.currentTask = null;
		this.buildCooldown = 0;
	}

	@Override
	public void stop() {
		if (this.currentTask != null) {
			debugLog("[Engineering] Task interrupted: " + this.currentTask.type());
			this.currentTask = null;
		}
	}

	@Override
	public void tick() {
		if (!(this.guard.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		GuardVillagersConfig config = GuardVillagersConfig.get();
		if (!config.engineering.enabled) {
			return;
		}
		if (this.buildCooldown > 0) {
			this.buildCooldown--;
		}
		if (config.engineering.requireMobGriefing
				&& !Boolean.TRUE.equals(world.getGameRules().getValue(GameRules.DO_MOB_GRIEFING))) {
			return;
		}

		// === BUILDING LOGIC — gate by decision system ===
		GuardEngineeringDecisionSystem.Decision liveDecision =
			GuardEngineeringDecisionSystem.evaluateWhileBuilding(this.guard, world, config);

		if (liveDecision.shouldAbortIfBuilding()) {
			if (this.currentTask != null && !this.currentTask.isFinished()) {
				debugLog("[Engineering] Build aborted: " + liveDecision.reason());
				GuardDebugLogger.logEngineeringTask(this.guard, "engineering decision abort",
					"reason", liveDecision.reason().toString());
				this.currentTask = null;
				this.buildCooldown = config.engineering.buildCooldownTicks;
			}
			return;
		}

		// Also keep the retreating check as always-on guard
		if (this.guard.isRetreating()) {
			if (this.currentTask != null && !this.currentTask.isFinished()) {
				debugLog("[Engineering] Building paused — guard retreating");
				this.currentTask = null;
				this.buildCooldown = config.engineering.buildCooldownTicks;
			}
			return;
		}

		if (!config.engineering.enabled) {
			return;
		}

		// If we have an active task, continue it
		if (this.currentTask != null && !this.currentTask.isFinished()) {
			this.continueCurrentTask(world, config);
			return;
		}

		// Clear finished tasks
		if (this.currentTask != null && this.currentTask.isFinished()) {
			this.onTaskFinished(config);
			GuardDebugLogger.logEngineeringTask(this.guard, "task finished",
				"type", this.currentTask.type().name(),
				"blocksPlaced", this.currentTask.blocksPlaced());
			this.currentTask = null;
			// Add cooldown so guard walks forward before starting the next task
			this.buildCooldown = Math.max(this.buildCooldown, config.engineering.buildCooldownTicks);
		}

		// Don't start a new task if on cooldown
		if (this.buildCooldown > 0) {
			return;
		}

		// Only attempt to start a new task at the configured interval
		if (this.guard.age % config.engineering.buildIntervalTicks != 0) {
			return;
		}

		// Try to start a new task: barricade first (defensive priority), then bridge/ladder
		EngineeringTask newTask = this.tryStartBarricadeTask(world, config);
		if (newTask == null) {
			newTask = this.tryStartBridgeOrLadderTask(world, config);
		}

		if (newTask != null) {
			this.currentTask = newTask;
			this.buildCooldown = config.engineering.buildCooldownTicks;
			GuardDebugLogger.logEngineeringTask(this.guard, "task started",
				"type", newTask.type().name(),
				"maxBlocks", newTask.maxBlocksToPlace(),
				"position", newTask.startPos().toShortString());
			debugLog("[Engineering] Started " + newTask.type().name().toLowerCase()
					+ " task, max " + newTask.maxBlocksToPlace() + " blocks");
		}
	}

	/**
	 * Continues the current engineering task, placing one or more blocks per tick.
	 */
	private void continueCurrentTask(ServerWorld world, GuardVillagersConfig config) {
		EngineeringTask task = this.currentTask;
		if (task == null || task.isFinished()) {
			return;
		}

		boolean placed = false;
		switch (task.type()) {
			case BRIDGE -> placed = BridgeBuilder.tickTask(this.guard, world, config, task);
			case LADDER -> placed = LadderBuilder.tickTask(this.guard, world, config, task);
			case BARRICADE -> placed = BarricadeBuilder.tickTask(this.guard, world, config, task);
			case PILLAR -> placed = PillarBuilder.tickTask(this.guard, world, config, task);
		}

		if (placed) {
			debugLog("[Engineering] Placed block " + task.blocksPlaced() + "/" + task.maxBlocksToPlace()
					+ " (" + task.type().name().toLowerCase() + ")");

			// Invalidate and restart navigation so the guard walks over the new block
			this.guard.getNavigation().stop();
			Vec3d taskTarget = this.currentTask.targetPos();
			this.guard.getNavigation().startMovingTo(
					taskTarget.x, taskTarget.y, taskTarget.z, 1.0);

			// If this is a barricade, also retreat after placing
			if (task.type() == EngineeringTask.Type.BARRICADE) {
				this.retreatFromTarget(world, config);
			}
		}

		// Check if the guard has reached the destination (for bridge/ladder)
		if (task.type() != EngineeringTask.Type.BARRICADE && this.isGuardAtDestination(task)) {
			task.setState(EngineeringTask.State.COMPLETED);
			debugLog("[Engineering] Reached destination");
		}
	}

	/**
	 * Called when a task finishes (completed or failed).
	 */
	private void onTaskFinished(GuardVillagersConfig config) {
		EngineeringTask task = this.currentTask;
		if (task == null) {
			return;
		}

		if (task.state() == EngineeringTask.State.COMPLETED) {
			debugLog("[Engineering] " + task.type().name().toLowerCase() + " completed: "
					+ task.blocksPlaced() + " blocks placed");
		} else if (task.state() == EngineeringTask.State.FAILED) {
			debugLog("[Engineering] " + task.type().name().toLowerCase() + " failed: "
					+ (task.failureReason() != null ? task.failureReason() : "unknown"));
		}
	}

	/**
	 * Tries to start a barricade task if the guard is a bowman with a nearby target.
	 */
	private EngineeringTask tryStartBarricadeTask(ServerWorld world, GuardVillagersConfig config) {
		return BarricadeBuilder.analyzeAndCreateTask(this.guard, world, config);
	}

	/**
	 * Tries to start a bridge or ladder task based on the terrain between the guard and its destination.
	 */
	private EngineeringTask tryStartBridgeOrLadderTask(ServerWorld world, GuardVillagersConfig config) {
		if (this.guard.isStaying() || this.guard.isTouchingWater() || this.guard.hasVehicle()) {
			return null;
		}

		Vec3d destination = this.resolveEngineeringDestination(world);
		if (destination == null) {
			return null;
		}

		double buildTargetRangeSq = config.engineering.bridgeLength * config.engineering.bridgeLength;
		if (this.guard.squaredDistanceTo(destination.x, destination.y, destination.z) > buildTargetRangeSq) {
			return null;
		}

		Direction direction = this.horizontalDirectionTo(destination);
		double heightDiff = destination.y - this.guard.getY();
		LivingEntity target = this.guard.getTarget();

		// 1. Try pillar first for high targets (works in open terrain)
		if (heightDiff > config.engineering.preferLadderAbove) {
			EngineeringTask pillarTask = PillarBuilder.analyzeAndCreateTask(
				this.guard, world, config, destination, target);
			if (pillarTask != null) {
				return pillarTask;
			}
		}

		// 2. Try ladder only if pillar failed (requires wall)
		if (config.engineering.ladderBuilding
				&& heightDiff > config.engineering.preferLadderAbove) {
			EngineeringTask ladderTask = LadderBuilder.analyzeAndCreateTask(
				this.guard, world, config, destination, direction, target);
			if (ladderTask != null) {
				return ladderTask;
			}
		}

		// 3. Try stairs for moderate height differences
		if (config.engineering.stairBuilding
				&& heightDiff > 0.5D
				&& heightDiff <= config.engineering.preferLadderAbove) {
			EngineeringTask stairTask = StairBuilder.analyzeAndCreateTask(
				this.guard, world, config, destination, direction);
			if (stairTask != null) {
				return stairTask;
			}
		}

		// 4. Try bridge for horizontal gaps
		if (config.engineering.stairBuilding || config.engineering.ladderBuilding) {
			EngineeringTask bridgeTask = BridgeBuilder.analyzeAndCreateTask(
				this.guard, world, config, destination, direction, target);
			if (bridgeTask != null) {
				return bridgeTask;
			}
		}

		// Try filling lava at feet level
		if (config.engineering.lavaFill) {
			BlockPos frontFeet = this.guard.getBlockPos().offset(direction);
			if (this.tryFillLava(world, frontFeet.down())) {
				// Lava fill is a one-shot action, not a persistent task
				this.buildCooldown = config.engineering.buildCooldownTicks;
				debugLog("[Engineering] Filled lava");
			}
		}

		return null;
	}

	/**
	 * Checks if the guard has reached the destination of the task.
	 */
	private boolean isGuardAtDestination(EngineeringTask task) {
		double distanceSq = this.guard.squaredDistanceTo(
				task.targetPos().x, task.targetPos().y, task.targetPos().z);
		return distanceSq < 2.25D; // Within 1.5 blocks
	}

	/**
	 * Makes the guard retreat from its target after placing a barricade block.
	 */
	private void retreatFromTarget(ServerWorld world, GuardVillagersConfig config) {
		LivingEntity target = this.guard.getTarget();
		if (target == null) {
			return;
		}
		Vec3d retreat = this.guard.getEntityPos().subtract(target.getEntityPos());
		if (retreat.lengthSquared() < 1.0E-4D) {
			retreat = Vec3d.of(this.currentTask.direction().getOpposite().getVector());
		}
		Vec3d destination = this.guard.getEntityPos().add(
				retreat.normalize().multiply(config.engineering.archerRetreatDistance));
		this.guard.getNavigation().startMovingTo(
				destination.x, destination.y, destination.z,
				config.engineering.archerRetreatSpeed);
	}

	// ==================== Destination Resolution ====================

	private Vec3d resolveEngineeringDestination(ServerWorld world) {
		LivingEntity target = this.guard.getTarget();
		if (target != null && target.isAlive()) {
			return target.getEntityPos();
		}
		if (this.guard.canFollowOwnerFormation()) {
			ServerPlayerEntity owner = this.guard.resolveOwner(world);
			if (owner != null && !owner.isSpectator()) {
				return owner.getEntityPos();
			}
		}
		return null;
	}

	// ==================== Lava Fill (one-shot) ====================

	private boolean tryFillLava(ServerWorld world, BlockPos pos) {
		if (!world.getBlockState(pos).isOf(Blocks.LAVA) || !this.guard.canTargetWithinZone(pos)) {
			return false;
		}
		return world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
	}

	// ==================== Utility ====================

	private Direction horizontalDirectionTo(Vec3d destination) {
		double dx = destination.x - this.guard.getX();
		double dz = destination.z - this.guard.getZ();
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx >= 0.0D ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * Logs a debug message if engineering debug is enabled in the config.
	 * Uses the guard's debug flag as a proxy for engineering debug logging.
	 */
	private void debugLog(String message) {
		if (this.guard.isDebugActive()) {
			GuardVillagersMod.LOGGER.info("[{}] {}: {}",
					this.guard.getName().getString(),
					this.guard.getUuid().toString().substring(0, 8),
					message);
		}
	}
}

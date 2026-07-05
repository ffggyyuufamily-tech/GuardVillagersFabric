package com.guardvillagers.entity.goal.engineering;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Persistent build task that survives across ticks.
 * Stores the state of an ongoing engineering operation (bridge, ladder, or barricade)
 * so the guard can continue placing blocks over multiple ticks instead of resetting each tick.
 */
public final class EngineeringTask {

	public enum Type {
		BRIDGE,
		LADDER,
		BARRICADE,
		PILLAR
	}

	public enum State {
		/** Task is being analyzed/scanning the terrain. */
		SCANNING,
		/** Actively placing blocks. */
		BUILDING,
		/** All blocks placed successfully. */
		COMPLETED,
		/** Failed: no materials, invalid position, etc. */
		FAILED
	}

	private final Type type;
	private final Direction direction;
	private final Vec3d targetPos;
	private final int maxBlocksToPlace;
	private final BlockPos startPos;

	private State state;
	private int blocksPlaced;
	private int blocksAvailable;
	private BlockPos lastPlacedPos;
	private int retryTicks;
	private String failureReason;

	EngineeringTask(Type type, Direction direction, Vec3d targetPos, int maxBlocksToPlace, BlockPos startPos, int blocksAvailable) {
		this.type = type;
		this.direction = direction;
		this.targetPos = targetPos;
		this.maxBlocksToPlace = maxBlocksToPlace;
		this.startPos = startPos;
		this.blocksAvailable = blocksAvailable;
		this.state = State.SCANNING;
		this.blocksPlaced = 0;
		this.lastPlacedPos = startPos;
		this.retryTicks = 0;
	}

	public Type type() {
		return this.type;
	}

	public Direction direction() {
		return this.direction;
	}

	public Vec3d targetPos() {
		return this.targetPos;
	}

	public int maxBlocksToPlace() {
		return this.maxBlocksToPlace;
	}

	public BlockPos startPos() {
		return this.startPos;
	}

	public State state() {
		return this.state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public int blocksPlaced() {
		return this.blocksPlaced;
	}

	public void incrementBlocksPlaced() {
		this.blocksPlaced++;
	}

	public int blocksAvailable() {
		return this.blocksAvailable;
	}

	public void consumeBlock() {
		if (this.blocksAvailable > 0) {
			this.blocksAvailable--;
		}
	}

	public BlockPos lastPlacedPos() {
		return this.lastPlacedPos;
	}

	public void setLastPlacedPos(BlockPos pos) {
		this.lastPlacedPos = pos;
	}

	public int retryTicks() {
		return this.retryTicks;
	}

	public void setRetryTicks(int ticks) {
		this.retryTicks = ticks;
	}

	public void decrementRetryTicks() {
		if (this.retryTicks > 0) {
			this.retryTicks--;
		}
	}

	public String failureReason() {
		return this.failureReason;
	}

	public void setFailureReason(String reason) {
		this.failureReason = reason;
	}

	public boolean isFinished() {
		return this.state == State.COMPLETED || this.state == State.FAILED;
	}

	public boolean canPlaceMore() {
		return this.blocksPlaced < this.maxBlocksToPlace && this.blocksAvailable > 0;
	}
}


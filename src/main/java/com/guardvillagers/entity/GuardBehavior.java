package com.guardvillagers.entity;

import net.minecraft.util.math.random.Random;

public enum GuardBehavior {
	PERIMETER(0),
	CROWD_CONTROL(1),
	OFFENSIVE(2),
	DEFENSIVE(3);

	private final int id;

	GuardBehavior(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static GuardBehavior fromId(int id) {
		for (GuardBehavior behavior : values()) {
			if (behavior.id == id) {
				return behavior;
			}
		}
		return DEFENSIVE;
	}

	public static GuardBehavior random(Random random) {
		GuardBehavior[] values = values();
		return values[random.nextInt(values.length)];
	}
}

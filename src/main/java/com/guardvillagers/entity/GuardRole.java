package com.guardvillagers.entity;

import net.minecraft.util.math.random.Random;

public enum GuardRole {
	SWORDSMAN(0),
	BOWMAN(1);

	private final int id;

	GuardRole(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static GuardRole fromId(int id) {
		return id == BOWMAN.id ? BOWMAN : SWORDSMAN;
	}

	public static GuardRole random(Random random) {
		return random.nextBoolean() ? SWORDSMAN : BOWMAN;
	}
}

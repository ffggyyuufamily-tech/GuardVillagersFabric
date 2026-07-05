package com.guardvillagers.entity;

public enum FormationType {
	LINE(0),
	SQUARE(1),
	CIRCLE(2),
	FOLLOW(3),
	SHIELD_WALL(4),
	PHALANX(5),
	WEDGE(6),
	CIRCLE_DEFENSE(7);

	private final int id;

	FormationType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static FormationType fromId(int id) {
		for (FormationType type : values()) {
			if (type.id == id) {
				return type;
			}
		}
		return FOLLOW;
	}
}

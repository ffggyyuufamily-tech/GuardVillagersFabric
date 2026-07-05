package com.guardvillagers.client;

import net.minecraft.text.Text;

public enum RegionColor {
	NONE(0, "None", 0x00000000, 0xFF2A2F36),
	BLUE(1, "Blue", 0x663975D8, 0xFF3975D8),
	RED(2, "Red", 0x66D84A4A, 0xFFD84A4A),
	YELLOW(3, "Yellow", 0x66D8C34A, 0xFFD8C34A),
	GREEN(4, "Green", 0x6648B36A, 0xFF48B36A),
	PURPLE(5, "Purple", 0x669B59D8, 0xFF9B59D8),
	ORANGE(6, "Orange", 0x66E67E22, 0xFFE67E22),
	CYAN(7, "Cyan", 0x6600BCD4, 0xFF00BCD4),
	PINK(8, "Pink", 0x66E91E8C, 0xFFE91E8C),
	BROWN(9, "Brown", 0x66795548, 0xFF795548),
	WHITE(10, "White", 0x66ECEFF1, 0xFFECEFF1);

	private static final RegionColor[] PALETTE_COLORS = {
		BLUE, RED, YELLOW, GREEN, PURPLE, ORANGE, CYAN, PINK, BROWN, WHITE
	};

	private final int id;
	private final String label;
	private final int overlayArgb;
	private final int swatchArgb;

	RegionColor(int id, String label, int overlayArgb, int swatchArgb) {
		this.id = id;
		this.label = label;
		this.overlayArgb = overlayArgb;
		this.swatchArgb = swatchArgb;
	}

	public int id() {
		return this.id;
	}

	public int overlayArgb() {
		return this.overlayArgb;
	}

	public int swatchArgb() {
		return this.swatchArgb;
	}

	public String label() {
		return this.label;
	}

	public Text labelText() {
		return Text.literal(this.label);
	}

	public static RegionColor[] paletteColors() {
		return PALETTE_COLORS;
	}

	public static int paletteCount() {
		return PALETTE_COLORS.length;
	}

	public static RegionColor paletteAt(int index) {
		if (index < 0 || index >= PALETTE_COLORS.length) {
			return BLUE;
		}
		return PALETTE_COLORS[index];
	}

	public RegionColor nextPaletteColor() {
		for (int i = 0; i < PALETTE_COLORS.length; i++) {
			if (PALETTE_COLORS[i] == this) {
				return PALETTE_COLORS[(i + 1) % PALETTE_COLORS.length];
			}
		}
		return BLUE;
	}

	public RegionColor previousPaletteColor() {
		for (int i = 0; i < PALETTE_COLORS.length; i++) {
			if (PALETTE_COLORS[i] == this) {
				return PALETTE_COLORS[(i - 1 + PALETTE_COLORS.length) % PALETTE_COLORS.length];
			}
		}
		return BLUE;
	}

	public static RegionColor fromId(int id) {
		for (RegionColor color : values()) {
			if (color.id == id) {
				return color;
			}
		}
		return NONE;
	}
}

package com.guardvillagers.client;

import net.minecraft.util.Identifier;

public final class GuardSkinResolver {
	private static final Identifier DEFAULT_TEXTURE = Identifier.of("minecraft",
			"textures/entity/villager/villager.png");

	private GuardSkinResolver() {
	}

	public static Identifier resolveTexture(String skinProfileId) {
		if (skinProfileId == null || skinProfileId.isBlank()) {
			return DEFAULT_TEXTURE;
		}
		// Placeholder for future custom skin profile resolution.
		return DEFAULT_TEXTURE;
	}
}

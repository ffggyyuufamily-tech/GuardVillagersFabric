package com.guardvillagers;

public final class GuardHirePricing {
	public static final int BASE_PRICE = 4;
	public static final int PRICE_PER_LEVEL = 2;

	private GuardHirePricing() {
	}

	public static int getHirePrice(int level) {
		int normalizedLevel = Math.max(1, level);
		return BASE_PRICE + (normalizedLevel - 1) * PRICE_PER_LEVEL;
	}
}

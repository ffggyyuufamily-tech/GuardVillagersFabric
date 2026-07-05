package com.guardvillagers.client;

public final class ClientDebugState {
	private static volatile boolean enabled;
	private static volatile double range;

	private ClientDebugState() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static double getRange() {
		return range;
	}

	public static void update(boolean enabled, double range) {
		ClientDebugState.enabled = enabled;
		ClientDebugState.range = Math.max(0.0D, range);
	}

	public static void reset() {
		enabled = false;
		range = 0.0D;
	}
}

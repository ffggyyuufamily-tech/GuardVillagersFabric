package com.guardvillagers.village;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public record VillageDescriptor(String id, BlockBox bounds, BlockPos center, int horizontalRadius) {
	public Box toEntityBox(int expansion) {
		return new Box(
			this.bounds.getMinX() - expansion,
			this.bounds.getMinY() - 4,
			this.bounds.getMinZ() - expansion,
			this.bounds.getMaxX() + expansion + 1,
			this.bounds.getMaxY() + 8,
			this.bounds.getMaxZ() + expansion + 1
		);
	}
}

package com.guardvillagers.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class GuardDragHandler {
	private static final ItemStack GUARD_HEAD_ICON = new ItemStack(Items.PLAYER_HEAD);

	private ClientGuardRosterStore.GuardRosterEntry draggedGuard;
	private int sourceGroupIndex = -1;
	private boolean fromUnassigned;
	private double dragX;
	private double dragY;
	private boolean active;

	public void beginDrag(ClientGuardRosterStore.GuardRosterEntry guard, int sourceGroup, boolean unassigned, double mouseX, double mouseY) {
		this.draggedGuard = guard;
		this.sourceGroupIndex = sourceGroup;
		this.fromUnassigned = unassigned;
		this.dragX = mouseX;
		this.dragY = mouseY;
		this.active = true;
	}

	public void updateDrag(double mouseX, double mouseY) {
		this.dragX = mouseX;
		this.dragY = mouseY;
	}

	public boolean isActive() {
		return this.active;
	}

	public ClientGuardRosterStore.GuardRosterEntry draggedGuard() {
		return this.draggedGuard;
	}

	public int sourceGroupIndex() {
		return this.sourceGroupIndex;
	}

	public boolean isFromUnassigned() {
		return this.fromUnassigned;
	}

	public void cancel() {
		this.active = false;
		this.draggedGuard = null;
		this.sourceGroupIndex = -1;
		this.fromUnassigned = false;
	}

	public DropResult drop() {
		DropResult result = new DropResult(this.draggedGuard, this.sourceGroupIndex, this.fromUnassigned);
		this.cancel();
		return result;
	}

	public void render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer) {
		if (!this.active || this.draggedGuard == null) {
			return;
		}

		int cardW = 82;
		int cardH = 48;
		int x = (int) this.dragX - cardW / 2;
		int y = (int) this.dragY - cardH / 2;

		context.getMatrices().pushMatrix();
		context.getMatrices().translate((float) this.dragX, (float) this.dragY);
		context.getMatrices().scale(1.1F, 1.1F);
		context.getMatrices().translate((float) -this.dragX, (float) -this.dragY);

		context.fill(x, y, x + cardW, y + cardH, 0xEE1E2A38);
		drawBorder(context, x, y, cardW, cardH, 0xFF6A8FBF);

		context.drawItem(GUARD_HEAD_ICON, x + 4, y + 4);
		String name = this.draggedGuard.displayName();
		if (name.length() > 10) {
			name = name.substring(0, 9) + "...";
		}
		context.drawText(textRenderer, Text.literal(name), x + 22, y + 4, 0xFFEAF1FA, false);
		context.drawText(textRenderer, Text.literal("Lv " + this.draggedGuard.level()), x + 22, y + 14, 0xFFB6C6D6, false);

		drawArmorIcon(context, this.draggedGuard.helmet(), x + 4, y + 26);
		drawArmorIcon(context, this.draggedGuard.chest(), x + 22, y + 26);
		drawArmorIcon(context, this.draggedGuard.legs(), x + 40, y + 26);
		drawArmorIcon(context, this.draggedGuard.boots(), x + 58, y + 26);

		context.getMatrices().popMatrix();
	}

	private static void drawArmorIcon(DrawContext context, ItemStack stack, int x, int y) {
		if (stack == null || stack.isEmpty()) {
			drawBorder(context, x, y, 16, 16, 0x884D5F72);
			return;
		}
		context.drawItem(stack, x, y);
	}

	private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}

	public record DropResult(ClientGuardRosterStore.GuardRosterEntry guard, int sourceGroupIndex, boolean fromUnassigned) {
	}
}

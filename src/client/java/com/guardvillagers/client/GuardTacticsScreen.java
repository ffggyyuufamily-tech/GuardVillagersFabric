package com.guardvillagers.client;

import com.guardvillagers.entity.GuardEntity;
import com.guardvillagers.tactics.GuardTacticsScreenHandler;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GuardTacticsScreen extends HandledScreen<GuardTacticsScreenHandler> {
	private static final int PANEL_BACKGROUND = 0xF1161C25;
	private static final int PANEL_BORDER = 0xFF3B4A5E;
	private static final int TEXT_PRIMARY = 0xFFEAF1FA;
	private static final int TEXT_SECONDARY = 0xFFB6C6D6;
	private static final int SUBTITLE_TACTICS = 0xFF8FD2FF;
	private static final int SUBTITLE_GROUPS = 0xFFFFCB90;
	private static final int ROW_BACKGROUND = 0xCC1B2531;
	private static final int ROW_BORDER = 0xFF334154;
	private static final int CARD_BACKGROUND = 0xEE17212D;
	private static final int CARD_BORDER = 0xFF425067;
	private static final int CONNECTOR_COLOR = 0xFF4F6176;

	private static final int SWATCH_SIZE = 14;
	private static final int SWATCH_GAP = 4;
	private static final int HEADER_HEIGHT = 26;
	private static final int BOTTOM_BAR_HEIGHT = 18;
	private static final int PLAYER_PANEL_WIDTH = 180;
	private static final int PLAYER_PANEL_HEIGHT = 28;
	private static final int ROW_HEIGHT = 58;
	private static final int GROUP_HEADER_WIDTH = 190;
	private static final int GROUP_HEADER_HEIGHT = 24;
	private static final int GUARD_CARD_WIDTH = 78;
	private static final int GUARD_CARD_HEIGHT = 46;
	private static final int GUARD_CARD_GAP = 6;
	private static final int LEFT_PANE_RATIO = 55;
	private static final int PANE_GAP = 8;
	private static final int GROUP_BOX_HEIGHT = 74;
	private static final int GROUP_BOX_GAP = 4;
	private static final int MINI_CARD_HEIGHT = 38;
	private static final int UNASSIGNED_CARD_HEIGHT = 26;
	private static final int UNASSIGNED_CARD_GAP = 2;
	private static final int SAVE_BUTTON_WIDTH = 60;
	private static final int SAVE_BUTTON_HEIGHT = 18;
	private static final int ADD_GROUP_BUTTON_WIDTH = 86;
	private static final int ADD_GROUP_BUTTON_HEIGHT = 18;
	private static final int DIALOG_WIDTH = 260;
	private static final int DIALOG_HEIGHT = 90;
	private static final int DIALOG_BUTTON_WIDTH = 70;
	private static final int DIALOG_BUTTON_HEIGHT = 18;
	private static final int VISIBLE_SWATCHES = 4;
	private static final int PALETTE_SCROLL_ANIMATION_MILLIS = 140;
	private static final double PANE_SCROLL_LERP_FACTOR = 0.35D;
	private static final int LEFT_PANE_SCROLL_STEP = GROUP_BOX_HEIGHT + GROUP_BOX_GAP;
	private static final int RIGHT_PANE_SCROLL_STEP = UNASSIGNED_CARD_HEIGHT + UNASSIGNED_CARD_GAP;
	private static final int SCROLLBAR_TRACK_COLOR = 0x55394656;

	private static final ItemStack GUARD_HEAD_ICON = new ItemStack(Items.PLAYER_HEAD);

	private final ClientTacticsDataStore dataStore = ClientTacticsDataStore.getInstance();
	private final ClientGuardRosterStore rosterStore = ClientGuardRosterStore.getInstance();
	private final ChunkMapWidget chunkMapWidget = new ChunkMapWidget(this.dataStore, GuardVillagersClient.terrainCache());
	private ViewMode mode;
	private final List<PaletteSwatch> paletteSwatches = new ArrayList<>();
	private final List<GroupRowHitbox> groupRows = new ArrayList<>();
	private final List<GuardCardHitbox> guardCards = new ArrayList<>();
	private PaletteSwatch groupsToggleSwatch;
	private final GuardDragHandler dragHandler = new GuardDragHandler();

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int contentHeight;
	private int groupRowsStartY;
	private int groupRowsHeight;
	private int groupMaxScroll;
	private int groupScrollRows;
	private TextFieldWidget groupRenameField;
	private int editingRow = -1;
	private int paletteScrollOffset = 0;
	private double paletteScrollAnimatedOffset;
	private double paletteScrollAnimationFrom;
	private double paletteScrollAnimationTo;
	private long paletteScrollAnimationStartedAt;
	private int paletteClipX;
	private int paletteClipY;
	private int paletteClipWidth;
	private int paletteClipHeight;

	// Groups two-pane layout
	private int leftPaneX;
	private int leftPaneY;
	private int leftPaneW;
	private int leftPaneH;
	private int rightPaneX;
	private int rightPaneY;
	private int rightPaneW;
	private int rightPaneH;
	private int leftPaneScroll;
	private int rightPaneScroll;
	private int leftPaneMaxScroll;
	private int rightPaneMaxScroll;
	private int leftPaneScrollTarget;
	private int rightPaneScrollTarget;
	private double leftPaneScrollAnimated;
	private double rightPaneScrollAnimated;
	private int addGroupButtonX;
	private int addGroupButtonY;

	// Dirty tracking for group assignments
	private final Map<UUID, Integer> pendingAssignments = new HashMap<>();
	private boolean dirty;
	private boolean showUnsavedDialog;
	private Runnable pendingAction;

	// Drop targets
	private final List<DropTarget> dropTargets = new ArrayList<>();

	public GuardTacticsScreen(GuardTacticsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		String normalized = title.getString().toLowerCase(Locale.ROOT);
		this.mode = normalized.contains("group") || normalized.contains("hierarchy") ? ViewMode.GROUPS : ViewMode.TACTICS;
	}

	@Override
	protected void init() {
		super.init();
		this.backgroundWidth = this.width;
		this.backgroundHeight = this.height;
		this.x = 0;
		this.y = 0;
		this.computeLayout();

		this.groupRenameField = new TextFieldWidget(this.textRenderer, 0, 0, GROUP_HEADER_WIDTH - 8, 18, Text.literal("Group Name"));
		this.groupRenameField.setVisible(false);
		this.groupRenameField.setMaxLength(24);
		this.addDrawableChild(this.groupRenameField);
		this.paletteScrollAnimatedOffset = this.paletteScrollOffset;
		this.paletteScrollAnimationFrom = this.paletteScrollOffset;
		this.paletteScrollAnimationTo = this.paletteScrollOffset;
		this.leftPaneScrollAnimated = this.leftPaneScroll;
		this.rightPaneScrollAnimated = this.rightPaneScroll;

		if (this.client != null && this.client.player != null) {
			this.chunkMapWidget.ensureCameraCentered(this.client.player.getChunkPos());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.computeLayout();
		this.renderBackground(context, mouseX, mouseY, delta);
		context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, PANEL_BACKGROUND);
		this.drawBorder(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL_BORDER);

		if (this.mode == ViewMode.TACTICS) {
			this.renderTactics(context, mouseX, mouseY);
		} else {
			this.renderGroups(context, mouseX, mouseY);
		}
		this.renderCommonHeader(context);
		if (this.groupRenameField != null && this.groupRenameField.isVisible()) {
			this.groupRenameField.render(context, mouseX, mouseY, delta);
		}
		if (this.dragHandler.isActive()) {
			this.dragHandler.render(context, this.textRenderer);
		}
		if (this.showUnsavedDialog) {
			this.renderUnsavedDialog(context, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();

		if (this.showUnsavedDialog) {
			return this.handleDialogClick(mouseX, mouseY);
		}

		if (this.groupRenameField != null && this.groupRenameField.isVisible() && this.groupRenameField.mouseClicked(click, doubleClick)) {
			return true;
		}

		if (this.mode == ViewMode.TACTICS) {
			if (this.handlePaletteClick(mouseX, mouseY)) {
				return true;
			}
			if (this.chunkMapWidget.mouseClicked(
				mouseX,
				mouseY,
				button,
				click.buttonInfo().hasShift(),
				this.client == null ? null : this.client.world,
				this.resolveWorldContext()
			)) {
				return true;
			}
			if (this.contains(this.contentX, this.contentY, this.contentWidth, this.contentHeight, mouseX, mouseY)) {
				return true;
			}
			return super.mouseClicked(click, doubleClick);
		}

		if (this.handleGroupsClick(mouseX, mouseY, button, click.buttonInfo().hasShift())) {
			return true;
		}
		if (this.contains(this.panelX, this.panelY, this.panelWidth, this.panelHeight, mouseX, mouseY)) {
			return true;
		}
		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		if (this.dragHandler.isActive()) {
			this.dragHandler.updateDrag(click.x(), click.y());
			return true;
		}
		if (this.mode == ViewMode.TACTICS) {
			if (this.chunkMapWidget.mouseDragged(
				click.x(),
				click.y(),
				click.button(),
				deltaX,
				deltaY,
				this.client == null ? null : this.client.world,
				this.resolveWorldContext()
			)) {
				return true;
			}
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (this.dragHandler.isActive()) {
			this.handleDrop(click.x(), click.y());
			return true;
		}
		if (this.mode == ViewMode.TACTICS && this.chunkMapWidget.mouseReleased(click.x(), click.y(), click.button())) {
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.mode == ViewMode.TACTICS) {
			// Check if scrolling over palette area
			if (this.isPaletteHovered(mouseX, mouseY)) {
				int direction = verticalAmount < 0 ? 1 : -1;
				int maxOffset = Math.max(0, RegionColor.paletteCount() - VISIBLE_SWATCHES);
				int nextOffset = MathHelper.clamp(this.paletteScrollOffset + direction, 0, maxOffset);
				this.startPaletteWheelAnimation(nextOffset);
				return true;
			}
			if (this.chunkMapWidget.mouseScrolled(mouseX, mouseY, verticalAmount)) {
				return true;
			}
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		int direction = verticalAmount < 0 ? 1 : -1;
		if (this.contains(this.leftPaneX, this.leftPaneY, this.leftPaneW, this.leftPaneH, mouseX, mouseY)) {
			int next = this.leftPaneScrollTarget + (direction * LEFT_PANE_SCROLL_STEP);
			this.leftPaneScrollTarget = MathHelper.clamp(next, 0, this.leftPaneMaxScroll);
			return true;
		}
		if (this.contains(this.rightPaneX, this.rightPaneY, this.rightPaneW, this.rightPaneH, mouseX, mouseY)) {
			int next = this.rightPaneScrollTarget + (direction * RIGHT_PANE_SCROLL_STEP);
			this.rightPaneScrollTarget = MathHelper.clamp(next, 0, this.rightPaneMaxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput keyInput) {
		if (this.groupRenameField != null && this.groupRenameField.isVisible()) {
			if (keyInput.isEnter()) {
				this.commitGroupRename();
				return true;
			}
			if (keyInput.isEscape()) {
				this.cancelGroupRename();
				return true;
			}
			if (this.groupRenameField.keyPressed(keyInput)) {
				return true;
			}
		}

		if (this.mode == ViewMode.TACTICS && keyInput.getKeycode() == GLFW.GLFW_KEY_C) {
			this.chunkMapWidget.clearSelection();
			return true;
		}
		return super.keyPressed(keyInput);
	}

	@Override
	public boolean charTyped(CharInput charInput) {
		if (this.groupRenameField != null && this.groupRenameField.isVisible()) {
			return this.groupRenameField.charTyped(charInput);
		}
		return super.charTyped(charInput);
	}

	private void renderCommonHeader(DrawContext context) {
		context.drawText(this.textRenderer, this.title, this.panelX + 10, this.panelY + 8, TEXT_PRIMARY, false);
		context.drawText(
			this.textRenderer,
			this.mode == ViewMode.GROUPS ? Text.literal("Group Configuration") : Text.literal("Zone Tactical View"),
			this.panelX + 10,
			this.panelY + 20,
			this.mode == ViewMode.GROUPS ? SUBTITLE_GROUPS : SUBTITLE_TACTICS,
			false
		);
	}

	private void renderTactics(DrawContext context, int mouseX, int mouseY) {
		if (this.groupRenameField != null && this.groupRenameField.isVisible()) {
			this.groupRenameField.setVisible(false);
			this.editingRow = -1;
		}

		ClientTacticsDataStore.WorldContext worldContext = this.resolveWorldContext();
		if (this.client != null && this.client.player != null) {
			this.chunkMapWidget.ensureCameraCentered(this.client.player.getChunkPos());
		}

		int mapX = this.contentX;
		int mapY = this.contentY;
		int mapW = this.contentWidth;
		int mapH = this.contentHeight - BOTTOM_BAR_HEIGHT;
		this.chunkMapWidget.setBounds(mapX, mapY, mapW, mapH);
		this.renderPalette(context);
		this.chunkMapWidget.render(context, this.client == null ? null : this.client.world, worldContext, mouseX, mouseY);

		context.fill(this.contentX, this.contentY + this.contentHeight - BOTTOM_BAR_HEIGHT, this.contentX + this.contentWidth, this.contentY + this.contentHeight, 0xAA131B25);
		context.drawText(
			this.textRenderer,
			Text.literal("LMB drag: select chunks | RMB: paint | Shift+RMB: clear | Wheel: zoom | Middle drag: pan | C: clear selection"),
			this.contentX + 6,
			this.contentY + this.contentHeight - 13,
			TEXT_SECONDARY,
			false
		);
		context.drawText(
			this.textRenderer,
			Text.literal(String.format(Locale.ROOT, "Zoom %.2fx", this.chunkMapWidget.zoom())),
			this.contentX + this.contentWidth - 74,
			this.contentY + this.contentHeight - 13,
			SUBTITLE_TACTICS,
			false
		);

		ChunkPos hovered = this.chunkMapWidget.hoveredChunk();
		if (hovered != null && worldContext != null) {
			RegionColor zoneColor = this.dataStore.getRegionColor(worldContext, hovered.x, hovered.z);
			String groupLabel = "None";
			if (zoneColor != RegionColor.NONE) {
				groupLabel = this.dataStore.groupBindingForColor(worldContext, zoneColor)
					.map(binding -> binding.groupName() + " (R" + (binding.row() + 1) + ")")
					.orElse("None");
			}
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(Text.literal("Chunk: " + hovered.x + ", " + hovered.z));
			tooltip.add(Text.literal("Zone: " + zoneColor.label()));
			tooltip.add(Text.literal("Group: " + groupLabel));
			tooltip.add(Text.literal("LMB drag select | RMB paint"));
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
	}

	private void renderPalette(DrawContext context) {
		this.paletteSwatches.clear();
		RegionColor[] allColors = RegionColor.paletteColors();
		int maxOffset = Math.max(0, allColors.length - VISIBLE_SWATCHES);
		if (this.paletteScrollOffset > maxOffset) {
			this.paletteScrollOffset = maxOffset;
			this.paletteScrollAnimatedOffset = maxOffset;
			this.paletteScrollAnimationFrom = maxOffset;
			this.paletteScrollAnimationTo = maxOffset;
		}
		double renderedOffset = this.currentPaletteScrollOffset();

		// Toggle button (rightmost position)
		int toggleSize = SWATCH_SIZE;
		int toggleX = this.panelX + this.panelWidth - 10 - toggleSize;
		int toggleY = this.panelY + 9;
		context.fill(toggleX, toggleY, toggleX + toggleSize, toggleY + toggleSize, 0xFF334154);
		this.drawBorder(context, toggleX, toggleY, toggleSize, toggleSize, 0xFF5A6A7E);
		// Draw "G" for groups toggle
		context.drawText(this.textRenderer, Text.literal("G"), toggleX + 3, toggleY + 3, 0xFFEAF1FA, false);
		this.paletteSwatches.add(new PaletteSwatch(null, toggleX, toggleY, toggleSize, toggleSize));

		// Separator line
		int sepX = toggleX - 5;
		context.fill(sepX, toggleY, sepX + 1, toggleY + toggleSize, PANEL_BORDER);

		// Color swatches with scroll window
		int swatchAreaWidth = VISIBLE_SWATCHES * SWATCH_SIZE + (VISIBLE_SWATCHES - 1) * SWATCH_GAP;
		int startX = sepX - 5 - swatchAreaWidth;
		int y = toggleY;
		this.paletteClipX = startX;
		this.paletteClipY = y;
		this.paletteClipWidth = swatchAreaWidth;
		this.paletteClipHeight = toggleSize;
		context.enableScissor(this.paletteClipX, this.paletteClipY, this.paletteClipX + this.paletteClipWidth, this.paletteClipY + this.paletteClipHeight);
		int swatchStride = SWATCH_SIZE + SWATCH_GAP;
		for (int colorIndex = 0; colorIndex < allColors.length; colorIndex++) {
			double slot = colorIndex - renderedOffset;
			int x = startX + (int) Math.round(slot * swatchStride);
			RegionColor color = allColors[colorIndex];
			context.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, color.swatchArgb());
			int border = this.chunkMapWidget.activeColor() == color ? 0xFFDCEBFF : 0xFF3B4A5E;
			this.drawBorder(context, x, y, SWATCH_SIZE, SWATCH_SIZE, border);
			int clampedLeft = Math.max(x, this.paletteClipX);
			int clampedRight = Math.min(x + SWATCH_SIZE, this.paletteClipX + this.paletteClipWidth);
			if (clampedRight > clampedLeft) {
				this.paletteSwatches.add(new PaletteSwatch(color, x, y, SWATCH_SIZE, SWATCH_SIZE));
			}
		}
		context.disableScissor();

		// Separator before palette area
		int sepX2 = startX - 5;
		context.fill(sepX2, y, sepX2 + 1, y + toggleSize, PANEL_BORDER);
	}

	private boolean handlePaletteClick(double mouseX, double mouseY) {
		for (PaletteSwatch swatch : this.paletteSwatches) {
			if (swatch.contains(mouseX, mouseY)) {
				if (swatch.color() == null) {
					this.mode = ViewMode.GROUPS;
					return true;
				}
				this.chunkMapWidget.setActiveColor(swatch.color());
				return true;
			}
		}
		return false;
	}

	private void startPaletteWheelAnimation(int targetOffset) {
		if (targetOffset == this.paletteScrollOffset) {
			return;
		}
		double currentOffset = this.currentPaletteScrollOffset();
		this.paletteScrollOffset = targetOffset;
		this.paletteScrollAnimationFrom = currentOffset;
		this.paletteScrollAnimationTo = targetOffset;
		this.paletteScrollAnimationStartedAt = System.currentTimeMillis();
	}

	private double currentPaletteScrollOffset() {
		if (this.paletteScrollAnimationStartedAt <= 0L) {
			return this.paletteScrollOffset;
		}
		long elapsed = System.currentTimeMillis() - this.paletteScrollAnimationStartedAt;
		if (elapsed >= PALETTE_SCROLL_ANIMATION_MILLIS) {
			this.paletteScrollAnimationStartedAt = 0L;
			this.paletteScrollAnimatedOffset = this.paletteScrollOffset;
			return this.paletteScrollOffset;
		}
		double progress = elapsed / (double) PALETTE_SCROLL_ANIMATION_MILLIS;
		double eased = progress * progress * (3.0D - 2.0D * progress);
		this.paletteScrollAnimatedOffset = this.paletteScrollAnimationFrom + ((this.paletteScrollAnimationTo - this.paletteScrollAnimationFrom) * eased);
		return this.paletteScrollAnimatedOffset;
	}

	private double lerpPaneScroll(double current, int target) {
		if (Math.abs(target - current) <= 0.5D) {
			return target;
		}
		return current + ((target - current) * PANE_SCROLL_LERP_FACTOR);
	}

	private void renderGroups(DrawContext context, int mouseX, int mouseY) {
		if (this.groupRenameField != null && this.editingRow >= 0) {
			this.groupRenameField.setVisible(false);
		}

		// Toggle button to switch back to Zones view
		int toggleSize = SWATCH_SIZE;
		int toggleX = this.panelX + this.panelWidth - 10 - toggleSize;
		int toggleY = this.panelY + 9;
		context.fill(toggleX, toggleY, toggleX + toggleSize, toggleY + toggleSize, 0xFF334154);
		this.drawBorder(context, toggleX, toggleY, toggleSize, toggleSize, 0xFF5A6A7E);
		context.drawText(this.textRenderer, Text.literal("Z"), toggleX + 3, toggleY + 3, 0xFFEAF1FA, false);
		this.groupsToggleSwatch = new PaletteSwatch(null, toggleX, toggleY, toggleSize, toggleSize);

		ClientTacticsDataStore.WorldContext worldContext = this.resolveWorldContext();
		if (worldContext == null || this.client == null || this.client.player == null || this.client.world == null) {
			context.drawText(this.textRenderer, Text.literal("Group data unavailable."), this.contentX, this.contentY, TEXT_SECONDARY, false);
			return;
		}

		boolean rosterLoaded = this.rosterStore.hasRoster(worldContext);
		List<ClientGuardRosterStore.GuardRosterEntry> allGuards = this.collectOwnedGuards(worldContext);
		Map<Integer, List<ClientGuardRosterStore.GuardRosterEntry>> guardsByGroup = new HashMap<>();
		List<ClientGuardRosterStore.GuardRosterEntry> unassigned = new ArrayList<>();
		int maxGroupRow = -1;

		for (ClientGuardRosterStore.GuardRosterEntry guard : allGuards) {
			int assignedGroup = this.getEffectiveGroup(guard);
			if (assignedGroup < 0) {
				unassigned.add(guard);
			} else {
				maxGroupRow = Math.max(maxGroupRow, assignedGroup);
				guardsByGroup.computeIfAbsent(assignedGroup, ignored -> new ArrayList<>()).add(guard);
			}
		}

		Comparator<ClientGuardRosterStore.GuardRosterEntry> guardSorter = Comparator
			.comparingInt(this::armorGearScore).reversed()
			.thenComparing(Comparator.comparingInt(ClientGuardRosterStore.GuardRosterEntry::level).reversed())
			.thenComparing(ClientGuardRosterStore.GuardRosterEntry::displayName, String.CASE_INSENSITIVE_ORDER);
		for (List<ClientGuardRosterStore.GuardRosterEntry> groupGuards : guardsByGroup.values()) {
			groupGuards.sort(guardSorter);
		}
		unassigned.sort(guardSorter);

		int groupCount = Math.max(0, Math.max(maxGroupRow + 1, this.dataStore.groupCount(worldContext)));
		this.dataStore.ensureGroupCount(worldContext, groupCount);

		// Two-pane layout
		this.leftPaneW = (this.contentWidth - PANE_GAP) * LEFT_PANE_RATIO / 100;
		this.rightPaneW = this.contentWidth - PANE_GAP - this.leftPaneW;
		this.leftPaneX = this.contentX;
		this.rightPaneX = this.contentX + this.leftPaneW + PANE_GAP;
		this.leftPaneY = this.contentY;
		this.rightPaneY = this.contentY;
		int paneH = this.contentHeight - BOTTOM_BAR_HEIGHT;
		this.leftPaneH = paneH;
		this.rightPaneH = paneH;

		this.groupRows.clear();
		this.guardCards.clear();
		this.dropTargets.clear();

		// === LEFT PANE: Groups ===
		context.fill(this.leftPaneX, this.leftPaneY, this.leftPaneX + this.leftPaneW, this.leftPaneY + this.leftPaneH, 0xCC131B25);
		this.drawBorder(context, this.leftPaneX, this.leftPaneY, this.leftPaneW, this.leftPaneH, ROW_BORDER);
		context.drawText(this.textRenderer, Text.literal("Groups"), this.leftPaneX + 6, this.leftPaneY + 4, SUBTITLE_GROUPS, false);

		int leftContentY = this.leftPaneY + 16;
		int leftContentH = this.leftPaneH - 16;
		int totalLeftContent = (groupCount + 1) * (GROUP_BOX_HEIGHT + GROUP_BOX_GAP);
		this.leftPaneMaxScroll = Math.max(0, totalLeftContent - leftContentH);
		this.leftPaneScrollTarget = MathHelper.clamp(this.leftPaneScrollTarget, 0, this.leftPaneMaxScroll);
		this.leftPaneScrollAnimated = this.lerpPaneScroll(this.leftPaneScrollAnimated, this.leftPaneScrollTarget);
		this.leftPaneScroll = MathHelper.clamp((int) Math.round(this.leftPaneScrollAnimated), 0, this.leftPaneMaxScroll);

		context.enableScissor(this.leftPaneX + 1, leftContentY, this.leftPaneX + this.leftPaneW - 1, leftContentY + leftContentH);
		for (int row = 0; row < groupCount; row++) {
			int boxY = leftContentY + row * (GROUP_BOX_HEIGHT + GROUP_BOX_GAP) - this.leftPaneScroll;
			int boxX = this.leftPaneX + 4;
			int boxW = this.leftPaneW - 8;

			// Highlight drop target if dragging
			boolean isDropHighlighted = this.dragHandler.isActive() && this.contains(boxX, boxY, boxW, GROUP_BOX_HEIGHT, mouseX, mouseY);
			int bgColor = isDropHighlighted ? 0xCC253545 : ROW_BACKGROUND;
			context.fill(boxX, boxY, boxX + boxW, boxY + GROUP_BOX_HEIGHT, bgColor);
			this.drawBorder(context, boxX, boxY, boxW, GROUP_BOX_HEIGHT, isDropHighlighted ? 0xFF5A8ABF : ROW_BORDER);

			// Color swatch
			int swatchX = boxX + 6;
			int swatchY = boxY + 4;
			RegionColor groupColor = this.dataStore.getGroupColor(worldContext, row);
			int swatchFill = groupColor == RegionColor.NONE ? 0xFF2C323D : groupColor.swatchArgb();
			context.fill(swatchX, swatchY, swatchX + 14, swatchY + 14, swatchFill);
			this.drawBorder(context, swatchX, swatchY, 14, 14, 0xFF6A7A8D);

			// Group name
			List<ClientGuardRosterStore.GuardRosterEntry> rowGuards = guardsByGroup.getOrDefault(row, List.of());
			String groupName = this.resolveGroupName(worldContext, row, rowGuards);
			context.drawText(this.textRenderer, Text.literal(groupName), swatchX + 20, swatchY + 3, TEXT_PRIMARY, false);
			context.drawText(this.textRenderer, Text.literal(rowGuards.size() + " guards"), boxX + 6, boxY + 22, TEXT_SECONDARY, false);

			this.groupRows.add(new GroupRowHitbox(row, swatchX, swatchY, 14, 14, boxX, boxY, boxW, GROUP_BOX_HEIGHT, groupName));
			this.dropTargets.add(new DropTarget(row, boxX, boxY, boxW, GROUP_BOX_HEIGHT, false));

			if (this.editingRow == row && this.groupRenameField != null) {
				this.groupRenameField.setVisible(true);
				this.groupRenameField.setX(swatchX + 20);
				this.groupRenameField.setY(swatchY);
				this.groupRenameField.setWidth(boxW - 32);
			}

			// Guard cards inside group box
			int cardsY = boxY + 34;
			int cardX = boxX + 6;
			int availableW = boxW - 12;
			int cardsFit = Math.max(0, (availableW + GUARD_CARD_GAP) / (GUARD_CARD_WIDTH + GUARD_CARD_GAP));
			int cardsToDraw = Math.min(cardsFit, rowGuards.size());
			for (int i = 0; i < cardsToDraw; i++) {
				ClientGuardRosterStore.GuardRosterEntry guard = rowGuards.get(i);
				int cx = cardX + i * (GUARD_CARD_WIDTH + GUARD_CARD_GAP);
				this.renderGuardMiniCard(context, guard, cx, cardsY);
				this.guardCards.add(new GuardCardHitbox(guard, cx, cardsY, GUARD_CARD_WIDTH, MINI_CARD_HEIGHT, row));
			}
			if (rowGuards.size() > cardsToDraw) {
				int overflowX = cardX + cardsToDraw * (GUARD_CARD_WIDTH + GUARD_CARD_GAP);
				context.drawText(this.textRenderer, Text.literal("+" + (rowGuards.size() - cardsToDraw)), overflowX, cardsY + 6, TEXT_SECONDARY, false);
			}
		}

		// "Create new group" drop target
		int createY = leftContentY + groupCount * (GROUP_BOX_HEIGHT + GROUP_BOX_GAP) - this.leftPaneScroll;
		int createX = this.leftPaneX + 4;
		int createW = this.leftPaneW - 8;
		boolean createHighlighted = this.dragHandler.isActive() && this.contains(createX, createY, createW, GROUP_BOX_HEIGHT, mouseX, mouseY);
		int createBg = createHighlighted ? 0xCC253545 : 0x66131B25;
		context.fill(createX, createY, createX + createW, createY + GROUP_BOX_HEIGHT, createBg);
		this.drawBorder(context, createX, createY, createW, GROUP_BOX_HEIGHT, createHighlighted ? 0xFF5A8ABF : 0xFF2A3548);
		context.drawText(this.textRenderer, Text.literal("+ Drag guards here to create group"), createX + 10, createY + 24, TEXT_SECONDARY, false);
		this.dropTargets.add(new DropTarget(groupCount, createX, createY, createW, GROUP_BOX_HEIGHT, true));

		context.disableScissor();

		// Left pane scrollbar
		if (this.leftPaneMaxScroll > 0) {
			this.renderScrollbar(context, this.leftPaneX + this.leftPaneW - 4, leftContentY, 3, leftContentH, this.leftPaneScroll, this.leftPaneMaxScroll, totalLeftContent);
		}

		// Left pane bottom fade
		this.renderBottomFade(context, this.leftPaneX, leftContentY + leftContentH - 16, this.leftPaneW - 4, 16);

		// === RIGHT PANE: Unassigned Guards ===
		context.fill(this.rightPaneX, this.rightPaneY, this.rightPaneX + this.rightPaneW, this.rightPaneY + this.rightPaneH, 0xCC131B25);
		this.drawBorder(context, this.rightPaneX, this.rightPaneY, this.rightPaneW, this.rightPaneH, ROW_BORDER);
		String guardHeader = rosterLoaded ? "All Guards (" + allGuards.size() + ")" : "All Guards (syncing...)";
		context.drawText(this.textRenderer, Text.literal(guardHeader), this.rightPaneX + 6, this.rightPaneY + 4, SUBTITLE_GROUPS, false);

		int rightContentY = this.rightPaneY + 16;
		int rightContentH = this.rightPaneH - 16;
		int totalRightContent = unassigned.size() * (UNASSIGNED_CARD_HEIGHT + UNASSIGNED_CARD_GAP);
		this.rightPaneMaxScroll = Math.max(0, totalRightContent - rightContentH);
		this.rightPaneScrollTarget = MathHelper.clamp(this.rightPaneScrollTarget, 0, this.rightPaneMaxScroll);
		this.rightPaneScrollAnimated = this.lerpPaneScroll(this.rightPaneScrollAnimated, this.rightPaneScrollTarget);
		this.rightPaneScroll = MathHelper.clamp((int) Math.round(this.rightPaneScrollAnimated), 0, this.rightPaneMaxScroll);

		context.enableScissor(this.rightPaneX + 1, rightContentY, this.rightPaneX + this.rightPaneW - 1, rightContentY + rightContentH);
		for (int i = 0; i < unassigned.size(); i++) {
			ClientGuardRosterStore.GuardRosterEntry guard = unassigned.get(i);
			int cardY = rightContentY + i * (UNASSIGNED_CARD_HEIGHT + UNASSIGNED_CARD_GAP) - this.rightPaneScroll;
			int cardX = this.rightPaneX + 4;
			int cardW = this.rightPaneW - 12;
			this.renderUnassignedCard(context, guard, cardX, cardY, cardW);
			this.guardCards.add(new GuardCardHitbox(guard, cardX, cardY, cardW, UNASSIGNED_CARD_HEIGHT, -1));
		}
		context.disableScissor();

		// Right pane scrollbar
		if (this.rightPaneMaxScroll > 0) {
			this.renderScrollbar(context, this.rightPaneX + this.rightPaneW - 4, rightContentY, 3, rightContentH, this.rightPaneScroll, this.rightPaneMaxScroll, totalRightContent);
		}

		// Right pane bottom fade
		this.renderBottomFade(context, this.rightPaneX, rightContentY + rightContentH - 16, this.rightPaneW - 4, 16);

		if (this.editingRow >= 0 && this.groupRenameField != null && !this.groupRenameField.isVisible()) {
			this.cancelGroupRename();
		}

		// Bottom bar
		context.fill(this.contentX, this.contentY + this.contentHeight - BOTTOM_BAR_HEIGHT, this.contentX + this.contentWidth, this.contentY + this.contentHeight, 0xAA131B25);
		this.addGroupButtonX = this.contentX + 6;
		this.addGroupButtonY = this.contentY + this.contentHeight - BOTTOM_BAR_HEIGHT + 1;
		boolean addGroupHovered = this.contains(this.addGroupButtonX, this.addGroupButtonY, ADD_GROUP_BUTTON_WIDTH, ADD_GROUP_BUTTON_HEIGHT - 2, mouseX, mouseY);
		context.fill(
			this.addGroupButtonX,
			this.addGroupButtonY,
			this.addGroupButtonX + ADD_GROUP_BUTTON_WIDTH,
			this.addGroupButtonY + ADD_GROUP_BUTTON_HEIGHT - 2,
			addGroupHovered ? 0xFF3B5771 : 0xFF2A3E54
		);
		this.drawBorder(context, this.addGroupButtonX, this.addGroupButtonY, ADD_GROUP_BUTTON_WIDTH, ADD_GROUP_BUTTON_HEIGHT - 2, 0xFF6C95BC);
		context.drawText(this.textRenderer, Text.literal("Add Group"), this.addGroupButtonX + 12, this.addGroupButtonY + 4, TEXT_PRIMARY, false);
		String groupHelpText = rosterLoaded
			? "Drag guards to assign | Shift+RMB header: rename | Click swatch: cycle color"
			: "Syncing guard roster from server...";
		context.drawText(
			this.textRenderer,
			Text.literal(groupHelpText),
			this.addGroupButtonX + ADD_GROUP_BUTTON_WIDTH + 8,
			this.contentY + this.contentHeight - 13,
			TEXT_SECONDARY,
			false
		);

		// Save button (only when dirty)
		if (this.dirty) {
			int saveX = this.contentX + this.contentWidth - SAVE_BUTTON_WIDTH - 6;
			int saveY = this.contentY + this.contentHeight - BOTTOM_BAR_HEIGHT + 1;
			boolean hovered = this.contains(saveX, saveY, SAVE_BUTTON_WIDTH, SAVE_BUTTON_HEIGHT - 2, mouseX, mouseY);
			context.fill(saveX, saveY, saveX + SAVE_BUTTON_WIDTH, saveY + SAVE_BUTTON_HEIGHT - 2, hovered ? 0xFF2A7A4A : 0xFF1E5A3A);
			this.drawBorder(context, saveX, saveY, SAVE_BUTTON_WIDTH, SAVE_BUTTON_HEIGHT - 2, 0xFF3EAA6A);
			context.drawText(this.textRenderer, Text.literal("Save"), saveX + 20, saveY + 4, TEXT_PRIMARY, false);
		}

		// Guard tooltip
		if (!this.dragHandler.isActive()) {
			GuardCardHitbox hoveredGuard = this.findHoveredGuard(mouseX, mouseY);
			if (hoveredGuard != null) {
				this.renderGuardTooltip(context, hoveredGuard.guard(), mouseX, mouseY);
			}
		}
	}

	private boolean handleGroupsClick(double mouseX, double mouseY, int button, boolean shiftDown) {
		// Check toggle button first
		if (this.groupsToggleSwatch != null && this.groupsToggleSwatch.contains(mouseX, mouseY)) {
			if (this.dirty) {
				this.pendingAction = () -> this.mode = ViewMode.TACTICS;
				this.showUnsavedDialog = true;
			} else {
				this.mode = ViewMode.TACTICS;
			}
			return true;
		}

		if (button == 0 && this.contains(this.addGroupButtonX, this.addGroupButtonY, ADD_GROUP_BUTTON_WIDTH, ADD_GROUP_BUTTON_HEIGHT - 2, mouseX, mouseY)) {
			ClientTacticsDataStore.WorldContext worldContext = this.resolveWorldContext();
			if (worldContext != null) {
				int nextRow = this.dataStore.groupCount(worldContext);
				this.dataStore.ensureGroupCount(worldContext, nextRow + 1);
			}
			if (this.client != null && this.client.getNetworkHandler() != null) {
				this.client.getNetworkHandler().sendChatCommand("guards groups add");
			}
			return true;
		}

		// Check save button
		if (this.dirty && this.handleSaveButtonClick(mouseX, mouseY)) {
			return true;
		}

		ClientTacticsDataStore.WorldContext worldContext = this.resolveWorldContext();
		if (worldContext == null) {
			return false;
		}

		// Check group header interactions (rename, color cycle)
		for (GroupRowHitbox row : this.groupRows) {
			if (row.containsSwatch(mouseX, mouseY)) {
				RegionColor current = this.dataStore.getGroupColor(worldContext, row.row());
				if (button == 1) {
					this.dataStore.setGroupColor(worldContext, row.row(), RegionColor.NONE);
				} else {
					RegionColor next = (current == RegionColor.NONE) ? RegionColor.BLUE : current.nextPaletteColor();
					this.dataStore.setGroupColor(worldContext, row.row(), next);
				}
				return true;
			}
			if (row.containsHeader(mouseX, mouseY) && button == 1 && shiftDown) {
				this.startGroupRename(row.row(), row.groupName());
				return true;
			}
		}

		// Start drag from guard card
		if (button == 0) {
			for (GuardCardHitbox card : this.guardCards) {
				if (card.contains(mouseX, mouseY)) {
					int groupIdx = card.groupIndex();
					boolean unassigned = card.groupIndex() < 0;
					this.dragHandler.beginDrag(card.guard(), groupIdx, unassigned, mouseX, mouseY);
					return true;
				}
			}
		}

		return false;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
	}

	private void startGroupRename(int row, String currentName) {
		if (this.groupRenameField == null) {
			return;
		}
		this.editingRow = row;
		this.groupRenameField.setVisible(true);
		this.groupRenameField.setText(currentName);
		this.groupRenameField.setCursorToEnd(false);
		this.setFocused(this.groupRenameField);
	}

	private void commitGroupRename() {
		if (this.groupRenameField == null || this.editingRow < 0) {
			return;
		}
		ClientTacticsDataStore.WorldContext worldContext = this.resolveWorldContext();
		if (worldContext == null) {
			this.cancelGroupRename();
			return;
		}
		String requested = this.groupRenameField.getText();
		if (requested == null || requested.isBlank()) {
			requested = "Role";
		}
		this.dataStore.setGroupName(worldContext, this.editingRow, requested);

		if (this.client != null && this.client.getNetworkHandler() != null) {
			String command = "guards groups rename " + (this.editingRow + 1) + " " + requested.trim();
			this.client.getNetworkHandler().sendChatCommand(command);
		}

		this.groupRenameField.setVisible(false);
		this.editingRow = -1;
		this.setFocused(null);
	}

	private void cancelGroupRename() {
		if (this.groupRenameField != null) {
			this.groupRenameField.setVisible(false);
		}
		this.editingRow = -1;
		this.setFocused(null);
	}

	private List<ClientGuardRosterStore.GuardRosterEntry> collectOwnedGuards(ClientTacticsDataStore.WorldContext worldContext) {
		return this.rosterStore.roster(worldContext);
	}

	private void renderGuardCard(DrawContext context, ClientGuardRosterStore.GuardRosterEntry guard, int x, int y) {
		context.fill(x, y, x + GUARD_CARD_WIDTH, y + GUARD_CARD_HEIGHT, CARD_BACKGROUND);
		this.drawBorder(context, x, y, GUARD_CARD_WIDTH, GUARD_CARD_HEIGHT, CARD_BORDER);
		context.drawItem(GUARD_HEAD_ICON, x + 4, y + 4);
		context.drawText(this.textRenderer, Text.literal("Lv " + guard.level()), x + 24, y + 8, TEXT_PRIMARY, false);

		this.drawArmorIcon(context, guard.helmet(), x + 4, y + 24);
		this.drawArmorIcon(context, guard.chest(), x + 20, y + 24);
		this.drawArmorIcon(context, guard.legs(), x + 36, y + 24);
		this.drawArmorIcon(context, guard.boots(), x + 52, y + 24);
	}

	private void drawArmorIcon(DrawContext context, ItemStack stack, int x, int y) {
		if (stack == null || stack.isEmpty()) {
			this.drawBorder(context, x, y, 16, 16, 0x884D5F72);
			return;
		}
		context.drawItem(stack, x, y);
	}

	private int armorGearScore(ClientGuardRosterStore.GuardRosterEntry guard) {
		return this.pieceScore(guard.helmet())
			+ this.pieceScore(guard.chest())
			+ this.pieceScore(guard.legs())
			+ this.pieceScore(guard.boots());
	}

	private int pieceScore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		if (path.contains("netherite")) {
			return 5;
		}
		if (path.contains("diamond")) {
			return 4;
		}
		if (path.contains("iron")) {
			return 3;
		}
		if (path.contains("chainmail") || path.contains("golden")) {
			return 2;
		}
		if (path.contains("leather")) {
			return 1;
		}
		return 0;
	}

	private String resolveGroupName(ClientTacticsDataStore.WorldContext worldContext, int row, List<ClientGuardRosterStore.GuardRosterEntry> rowGuards) {
		String stored = this.dataStore.getGroupName(worldContext, row);
		if (!rowGuards.isEmpty() && this.isGenericGroupName(stored)) {
			String fromGuard = rowGuards.getFirst().groupName();
			if (fromGuard != null && !fromGuard.isBlank()) {
				return fromGuard;
			}
		}
		return stored;
	}

	private boolean isGenericGroupName(String groupName) {
		if (groupName == null) {
			return true;
		}
		String trimmed = groupName.trim();
		return trimmed.equals("Role") || trimmed.matches("Role\\s+\\d+");
	}

	private String itemName(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "None";
		}
		return stack.getName().getString();
	}

	private int armorPoints(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		if (path.contains("helmet")) {
			if (path.contains("netherite") || path.contains("diamond")) return 3;
			if (path.contains("iron")) return 2;
			if (path.contains("chainmail") || path.contains("golden")) return 2;
			if (path.contains("leather")) return 1;
		}
		if (path.contains("chestplate")) {
			if (path.contains("netherite") || path.contains("diamond")) return 8;
			if (path.contains("iron")) return 6;
			if (path.contains("chainmail") || path.contains("golden")) return 5;
			if (path.contains("leather")) return 3;
		}
		if (path.contains("leggings")) {
			if (path.contains("netherite") || path.contains("diamond")) return 6;
			if (path.contains("iron")) return 5;
			if (path.contains("chainmail") || path.contains("golden")) return 3;
			if (path.contains("leather")) return 2;
		}
		if (path.contains("boots")) {
			if (path.contains("netherite") || path.contains("diamond")) return 3;
			if (path.contains("iron")) return 2;
			if (path.contains("chainmail") || path.contains("golden")) return 1;
			if (path.contains("leather")) return 1;
		}
		return 0;
	}

	private int weaponDamage(ItemStack stack) {
		if (stack.isOf(Items.STONE_SWORD)) {
			return 5;
		}
		if (stack.isOf(Items.IRON_SWORD)) {
			return 6;
		}
		if (stack.isOf(Items.DIAMOND_SWORD)) {
			return 7;
		}
		return 1;
	}

	private GuardCardHitbox findHoveredGuard(int mouseX, int mouseY) {
		for (GuardCardHitbox card : this.guardCards) {
			if (card.contains(mouseX, mouseY)) {
				return card;
			}
		}
		return null;
	}

	private ClientTacticsDataStore.WorldContext resolveWorldContext() {
		if (this.client == null || this.client.world == null) {
			return null;
		}
		return ClientTacticsDataStore.resolveContext(this.client, this.client.world);
	}

	private boolean isPaletteHovered(double mouseX, double mouseY) {
		return this.paletteClipWidth > 0
			&& mouseX >= this.paletteClipX
			&& mouseX < this.paletteClipX + this.paletteClipWidth
			&& mouseY >= this.paletteClipY
			&& mouseY < this.paletteClipY + this.paletteClipHeight;
	}

	private void renderGuardMiniCard(DrawContext context, ClientGuardRosterStore.GuardRosterEntry guard, int x, int y) {
		context.fill(x, y, x + GUARD_CARD_WIDTH, y + MINI_CARD_HEIGHT, CARD_BACKGROUND);
		this.drawBorder(context, x, y, GUARD_CARD_WIDTH, MINI_CARD_HEIGHT, CARD_BORDER);
		context.drawItem(GUARD_HEAD_ICON, x + 2, y + 3);
		String name = guard.displayName();
		if (name.length() > 8) {
			name = name.substring(0, 7) + "...";
		}
		context.drawText(this.textRenderer, Text.literal(name), x + 20, y + 3, TEXT_PRIMARY, false);
		context.drawText(this.textRenderer, Text.literal("Lv" + guard.level()), x + 20, y + 12, TEXT_SECONDARY, false);

		// Armor icons row
		int armorY = y + 22;
		this.drawMiniArmorIcon(context, guard.helmet(), x + 4, armorY);
		this.drawMiniArmorIcon(context, guard.chest(), x + 22, armorY);
		this.drawMiniArmorIcon(context, guard.legs(), x + 40, armorY);
		this.drawMiniArmorIcon(context, guard.boots(), x + 58, armorY);
	}

	private void drawMiniArmorIcon(DrawContext context, ItemStack stack, int x, int y) {
		if (stack == null || stack.isEmpty()) {
			context.fill(x + 2, y + 2, x + 12, y + 12, 0x444D5F72);
			return;
		}
		context.getMatrices().pushMatrix();
		context.getMatrices().translate((float) x, (float) y);
		context.getMatrices().scale(0.875F, 0.875F);
		context.drawItem(stack, 0, 0);
		context.getMatrices().popMatrix();
	}

	private void renderUnassignedCard(DrawContext context, ClientGuardRosterStore.GuardRosterEntry guard, int x, int y, int width) {
		context.fill(x, y, x + width, y + UNASSIGNED_CARD_HEIGHT, CARD_BACKGROUND);
		this.drawBorder(context, x, y, width, UNASSIGNED_CARD_HEIGHT, CARD_BORDER);
		context.drawItem(GUARD_HEAD_ICON, x + 2, y + 5);
		context.drawText(this.textRenderer, Text.literal(guard.displayName()), x + 20, y + 4, TEXT_PRIMARY, false);
		context.drawText(this.textRenderer, Text.literal("Lv " + guard.level() + " | " + guard.groupName()), x + 20, y + 14, TEXT_SECONDARY, false);
	}

	private void renderScrollbar(DrawContext context, int x, int y, int width, int height, int scroll, int maxScroll, int totalContent) {
		context.fill(x, y, x + width, y + height, SCROLLBAR_TRACK_COLOR);
		if (maxScroll <= 0 || totalContent <= 0) {
			return;
		}
		int thumbH = Math.max(10, height * height / totalContent);
		int thumbY = y + (int) ((long) scroll * (height - thumbH) / maxScroll);
		context.fill(x, thumbY, x + width, thumbY + thumbH, PANEL_BORDER);
	}

	private void renderBottomFade(DrawContext context, int x, int y, int width, int height) {
		for (int i = 0; i < height; i++) {
			int alpha = (int) (0x99 * (float) i / height);
			context.fill(x, y + height - 1 - i, x + width, y + height - i, (alpha << 24) | 0x131B25);
		}
	}

	private void renderGuardTooltip(DrawContext context, ClientGuardRosterStore.GuardRosterEntry guard, int mouseX, int mouseY) {
		List<Text> tooltip = new ArrayList<>();
		tooltip.add(Text.literal(guard.displayName()));
		ItemStack weapon = guard.mainHand();
		ItemStack helmet = guard.helmet();
		ItemStack chest = guard.chest();
		ItemStack legs = guard.legs();
		ItemStack boots = guard.boots();
		int totalArmor = this.armorPoints(helmet) + this.armorPoints(chest) + this.armorPoints(legs) + this.armorPoints(boots);
		tooltip.add(Text.literal("\u00A77Weapon: \u00A7f" + this.itemName(weapon) + " (" + this.weaponDamage(weapon) + " dmg)"));
		tooltip.add(Text.literal("\u00A77Armor: \u00A7f" + totalArmor + " | Level: " + guard.level()));
		tooltip.add(Text.literal("\u00A77Group: \u00A7f" + guard.groupName()));
		context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
	}

	private void renderUnsavedDialog(DrawContext context, int mouseX, int mouseY) {
		// Dim background
		context.fill(0, 0, this.width, this.height, 0x88000000);

		int dx = (this.width - DIALOG_WIDTH) / 2;
		int dy = (this.height - DIALOG_HEIGHT) / 2;
		context.fill(dx, dy, dx + DIALOG_WIDTH, dy + DIALOG_HEIGHT, PANEL_BACKGROUND);
		this.drawBorder(context, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT, PANEL_BORDER);

		context.drawText(this.textRenderer, Text.literal("Warning: Unsaved Changes!"), dx + 10, dy + 10, TEXT_PRIMARY, false);
		context.drawText(this.textRenderer, Text.literal("You have unsaved group assignments."), dx + 10, dy + 26, TEXT_SECONDARY, false);

		int buttonY = dy + DIALOG_HEIGHT - DIALOG_BUTTON_HEIGHT - 12;

		// Discard button
		int discardX = dx + 10;
		boolean discardHover = this.contains(discardX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, mouseX, mouseY);
		context.fill(discardX, buttonY, discardX + DIALOG_BUTTON_WIDTH, buttonY + DIALOG_BUTTON_HEIGHT, discardHover ? 0xFF5A2A2A : 0xFF3A1A1A);
		this.drawBorder(context, discardX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 0xFFAA4444);
		context.drawText(this.textRenderer, Text.literal("Discard"), discardX + 12, buttonY + 5, TEXT_PRIMARY, false);

		// Save button
		int saveDialogX = dx + DIALOG_WIDTH / 2 - DIALOG_BUTTON_WIDTH / 2;
		boolean saveHover = this.contains(saveDialogX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, mouseX, mouseY);
		context.fill(saveDialogX, buttonY, saveDialogX + DIALOG_BUTTON_WIDTH, buttonY + DIALOG_BUTTON_HEIGHT, saveHover ? 0xFF2A7A4A : 0xFF1E5A3A);
		this.drawBorder(context, saveDialogX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 0xFF3EAA6A);
		context.drawText(this.textRenderer, Text.literal("Save"), saveDialogX + 20, buttonY + 5, TEXT_PRIMARY, false);

		// Cancel button
		int cancelX = dx + DIALOG_WIDTH - DIALOG_BUTTON_WIDTH - 10;
		boolean cancelHover = this.contains(cancelX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, mouseX, mouseY);
		context.fill(cancelX, buttonY, cancelX + DIALOG_BUTTON_WIDTH, buttonY + DIALOG_BUTTON_HEIGHT, cancelHover ? 0xFF3A4A5A : 0xFF2A3A4A);
		this.drawBorder(context, cancelX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, PANEL_BORDER);
		context.drawText(this.textRenderer, Text.literal("Cancel"), cancelX + 14, buttonY + 5, TEXT_PRIMARY, false);
	}

	private boolean handleDialogClick(double mouseX, double mouseY) {
		int dx = (this.width - DIALOG_WIDTH) / 2;
		int dy = (this.height - DIALOG_HEIGHT) / 2;
		int buttonY = dy + DIALOG_HEIGHT - DIALOG_BUTTON_HEIGHT - 12;

		int discardX = dx + 10;
		if (this.contains(discardX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, mouseX, mouseY)) {
			this.pendingAssignments.clear();
			this.dirty = false;
			this.showUnsavedDialog = false;
			if (this.pendingAction != null) {
				this.pendingAction.run();
				this.pendingAction = null;
			}
			return true;
		}

		int saveDialogX = dx + DIALOG_WIDTH / 2 - DIALOG_BUTTON_WIDTH / 2;
		if (this.contains(saveDialogX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, mouseX, mouseY)) {
			this.saveAssignments();
			this.showUnsavedDialog = false;
			if (this.pendingAction != null) {
				this.pendingAction.run();
				this.pendingAction = null;
			}
			return true;
		}

		int cancelX = dx + DIALOG_WIDTH - DIALOG_BUTTON_WIDTH - 10;
		if (this.contains(cancelX, buttonY, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, mouseX, mouseY)) {
			this.showUnsavedDialog = false;
			this.pendingAction = null;
			return true;
		}

		return true;
	}

	private void handleDrop(double mouseX, double mouseY) {
		if (!this.dragHandler.isActive()) {
			return;
		}
		GuardDragHandler.DropResult result = this.dragHandler.drop();
		if (result.guard() == null) {
			return;
		}

		for (DropTarget target : this.dropTargets) {
			if (target.contains(mouseX, mouseY)) {
				int targetGroup = target.groupIndex();
				if (target.isCreateNew()) {
					ClientTacticsDataStore.WorldContext worldContext = this.resolveWorldContext();
					if (worldContext != null) {
						this.dataStore.ensureGroupCount(worldContext, targetGroup + 1);
					}
				}
				UUID guardId = result.guard().guardUuid();
				this.pendingAssignments.put(guardId, targetGroup);
				this.dirty = true;
				return;
			}
		}
		// Dropped outside any target, cancel assignment.
	}

	private boolean handleSaveButtonClick(double mouseX, double mouseY) {
		int saveX = this.contentX + this.contentWidth - SAVE_BUTTON_WIDTH - 6;
		int saveY = this.contentY + this.contentHeight - BOTTOM_BAR_HEIGHT + 1;
		if (this.contains(saveX, saveY, SAVE_BUTTON_WIDTH, SAVE_BUTTON_HEIGHT - 2, mouseX, mouseY)) {
			this.saveAssignments();
			return true;
		}
		return false;
	}

	private void saveAssignments() {
		if (this.client == null || this.client.getNetworkHandler() == null) {
			return;
		}
		for (Map.Entry<UUID, Integer> entry : this.pendingAssignments.entrySet()) {
			String command = "guards groups assign " + entry.getKey() + " " + (entry.getValue() + 1);
			this.client.getNetworkHandler().sendChatCommand(command);
		}
		this.pendingAssignments.clear();
		this.dirty = false;
	}

	private int getEffectiveGroup(ClientGuardRosterStore.GuardRosterEntry guard) {
		Integer pending = this.pendingAssignments.get(guard.guardUuid());
		if (pending != null) {
			return pending;
		}
		return guard.groupIndex();
	}

	@Override
	public void close() {
		if (this.dirty) {
			this.pendingAction = () -> {
				if (this.client != null) {
					this.client.setScreen(null);
				}
			};
			this.showUnsavedDialog = true;
			return;
		}
		super.close();
	}

	private void drawConnector(DrawContext context, int fromX, int fromY, int toX, int toY, int color) {
		int verticalTop = Math.min(fromY, toY);
		int verticalBottom = Math.max(fromY, toY);
		context.fill(fromX, verticalTop, fromX + 1, verticalBottom + 1, color);
		int horizontalLeft = Math.min(fromX, toX);
		int horizontalRight = Math.max(fromX, toX);
		context.fill(horizontalLeft, toY, horizontalRight + 1, toY + 1, color);
	}

	private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}

	private boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	private void computeLayout() {
		this.panelX = 10;
		this.panelY = 10;
		this.panelWidth = this.width - 20;
		this.panelHeight = this.height - 20;
		this.contentX = this.panelX + 8;
		this.contentY = this.panelY + HEADER_HEIGHT + 4;
		this.contentWidth = this.panelWidth - 16;
		this.contentHeight = this.panelHeight - HEADER_HEIGHT - 8;
	}

	private enum ViewMode {
		TACTICS,
		GROUPS
	}

	private record PaletteSwatch(RegionColor color, int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
		}
	}

	private record GroupRowHitbox(
		int row,
		int swatchX,
		int swatchY,
		int swatchW,
		int swatchH,
		int headerX,
		int headerY,
		int headerW,
		int headerH,
		String groupName
	) {
		private boolean containsSwatch(double mouseX, double mouseY) {
			return mouseX >= this.swatchX && mouseY >= this.swatchY && mouseX < this.swatchX + this.swatchW && mouseY < this.swatchY + this.swatchH;
		}

		private boolean containsHeader(double mouseX, double mouseY) {
			return mouseX >= this.headerX && mouseY >= this.headerY && mouseX < this.headerX + this.headerW && mouseY < this.headerY + this.headerH;
		}
	}

	private record GuardCardHitbox(ClientGuardRosterStore.GuardRosterEntry guard, int x, int y, int width, int height, int groupIndex) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
		}
	}

	private record DropTarget(int groupIndex, int x, int y, int width, int height, boolean isCreateNew) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
		}
	}
}

package com.guardvillagers.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;
import java.util.List;

public final class ChunkMapWidget {
	private static final double MIN_ZOOM = 0.05D;
	private static final double MAX_ZOOM = 32.0D;
	private static final double ZOOM_STEP_BASE = 1.1D;
	private static final double BASE_CHUNK_PIXELS = 16.0D;
	private static final double DETAILED_TEXTURE_MIN_CHUNK_PIXELS = 4.0D;

	private static final int MAP_BACKGROUND = 0xFF10161D;
	private static final int SELECTION_OVERLAY = 0x446FD3FF;
	private static final int HOVER_OVERLAY = 0x33FFFFFF;
	private static final int ZONE_OUTLINE_ALPHA_MASK = 0xCC000000;

	private final ClientTacticsDataStore dataStore;
	private final ChunkTerrainCache terrainCache;

	private int x;
	private int y;
	private int width;
	private int height;
	private boolean cameraInitialized;
	private double centerChunkX;
	private double centerChunkZ;
	private double zoom = 1.0D;
	private RegionColor activeColor = RegionColor.BLUE;
	private ChunkPos selectionStart;
	private ChunkPos selectionEnd;
	private boolean selecting;
	private boolean panning;
	private ChunkPos hoveredChunk;

	public ChunkMapWidget(ClientTacticsDataStore dataStore, ChunkTerrainCache terrainCache) {
		this.dataStore = dataStore;
		this.terrainCache = terrainCache;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = Math.max(1, width);
		this.height = Math.max(1, height);
	}

	public void ensureCameraCentered(ChunkPos centerChunk) {
		if (this.cameraInitialized || centerChunk == null) {
			return;
		}
		this.centerChunkX = centerChunk.x;
		this.centerChunkZ = centerChunk.z;
		this.cameraInitialized = true;
	}

	public void render(
			DrawContext context,
			ClientWorld world,
			ClientTacticsDataStore.WorldContext worldContext,
			int mouseX,
			int mouseY) {
		context.fill(this.x, this.y, this.x + this.width, this.y + this.height, MAP_BACKGROUND);
		this.hoveredChunk = null;
		if (world == null || worldContext == null) {
			return;
		}

		double chunkScale = this.chunkScale();
		double viewportCenterX = this.x + (this.width / 2.0D);
		double viewportCenterY = this.y + (this.height / 2.0D);
		VisibleChunkFrame visibleFrame = this.computeVisibleFrame(viewportCenterX, viewportCenterY, chunkScale);
		List<ChunkPos> visibleChunks = this.dataStore.discoveredChunksInBounds(
				worldContext,
				visibleFrame.minChunkX(),
				visibleFrame.maxChunkX(),
				visibleFrame.minChunkZ(),
				visibleFrame.maxChunkZ());
		visibleChunks.sort(Comparator.comparingDouble(this::distanceToCameraSq));
		this.terrainCache.prepareVisibleTiles(worldContext, world, visibleChunks);

		int[] chunkXEdges = this.computeChunkEdges(visibleFrame.minChunkX(), visibleFrame.maxChunkX(), this.centerChunkX, viewportCenterX, chunkScale);
		int[] chunkZEdges = this.computeChunkEdges(visibleFrame.minChunkZ(), visibleFrame.maxChunkZ(), this.centerChunkZ, viewportCenterY, chunkScale);

		context.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
		for (ChunkPos chunkPos : visibleChunks) {
			int xIndex = chunkPos.x - visibleFrame.minChunkX();
			int zIndex = chunkPos.z - visibleFrame.minChunkZ();
			int left = chunkXEdges[xIndex];
			int right = chunkXEdges[xIndex + 1];
			int top = chunkZEdges[zIndex];
			int bottom = chunkZEdges[zIndex + 1];
			if (right <= left || bottom <= top) {
				continue;
			}

			this.renderChunkTerrain(context, worldContext, chunkPos.x, chunkPos.z, left, top, right, bottom);

			RegionColor regionColor = this.dataStore.getRegionColor(worldContext, chunkPos.x, chunkPos.z);
			if (regionColor != RegionColor.NONE) {
				this.drawZoneHighlight(context, worldContext, chunkPos.x, chunkPos.z, left, top, right, bottom, regionColor);
			}

			if (this.isChunkSelected(chunkPos.x, chunkPos.z)) {
				context.fill(left, top, right, bottom, SELECTION_OVERLAY);
			}
		}
		context.disableScissor();

		if (!this.contains(mouseX, mouseY)) {
			return;
		}

		ChunkPos hovered = this.chunkAt(mouseX, mouseY);
		if (hovered == null || !this.dataStore.isDiscovered(worldContext, hovered.x, hovered.z)) {
			return;
		}

		this.hoveredChunk = hovered;
		int hoveredXIndex = hovered.x - visibleFrame.minChunkX();
		int hoveredZIndex = hovered.z - visibleFrame.minChunkZ();
		if (hoveredXIndex < 0 || hoveredXIndex + 1 >= chunkXEdges.length || hoveredZIndex < 0 || hoveredZIndex + 1 >= chunkZEdges.length) {
			return;
		}

		context.fill(
				chunkXEdges[hoveredXIndex],
				chunkZEdges[hoveredZIndex],
				chunkXEdges[hoveredXIndex + 1],
				chunkZEdges[hoveredZIndex + 1],
				HOVER_OVERLAY);
	}

	public boolean mouseClicked(
			double mouseX,
			double mouseY,
			int button,
			boolean shiftDown,
			ClientWorld world,
			ClientTacticsDataStore.WorldContext worldContext) {
		if (!this.contains(mouseX, mouseY) || world == null || worldContext == null) {
			return false;
		}

		if (button == 2) {
			this.panning = true;
			this.selecting = false;
			return true;
		}

		if (button == 0) {
			ChunkPos anchor = this.chunkAt(mouseX, mouseY);
			if (anchor == null || !this.dataStore.isDiscovered(worldContext, anchor.x, anchor.z)) {
				this.clearSelection();
				return true;
			}
			this.selectionStart = anchor;
			this.selectionEnd = anchor;
			this.selecting = true;
			this.panning = false;
			return true;
		}

		if (button == 1) {
			this.paintAtCursor(mouseX, mouseY, shiftDown, worldContext);
			return true;
		}

		return false;
	}

	public boolean mouseDragged(
			double mouseX,
			double mouseY,
			int button,
			double deltaX,
			double deltaY,
			ClientWorld world,
			ClientTacticsDataStore.WorldContext worldContext) {
		if (world == null || worldContext == null) {
			return false;
		}

		if (button == 2 && this.panning) {
			double scale = this.chunkScale();
			this.centerChunkX -= deltaX / scale;
			this.centerChunkZ -= deltaY / scale;
			return true;
		}

		if (button == 0 && this.selecting) {
			ChunkPos next = this.chunkAt(mouseX, mouseY);
			if (next != null) {
				this.selectionEnd = next;
			}
			return true;
		}
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && this.selecting) {
			ChunkPos next = this.chunkAt(mouseX, mouseY);
			if (next != null) {
				this.selectionEnd = next;
			}
			this.selecting = false;
			return true;
		}
		if (button == 2 && this.panning) {
			this.panning = false;
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (!this.contains(mouseX, mouseY)) {
			return false;
		}
		double chunkUnderCursorX = this.screenToChunkX(mouseX);
		double chunkUnderCursorZ = this.screenToChunkZ(mouseY);
		double zoomFactor = Math.pow(ZOOM_STEP_BASE, verticalAmount);
		double nextZoom = clamp(this.zoom * zoomFactor, MIN_ZOOM, MAX_ZOOM);
		if (Math.abs(nextZoom - this.zoom) < 1.0E-5D) {
			return true;
		}
		this.zoom = nextZoom;

		double centerScreenX = this.x + (this.width / 2.0D);
		double centerScreenY = this.y + (this.height / 2.0D);
		double chunkScale = this.chunkScale();
		this.centerChunkX = chunkUnderCursorX - ((mouseX - centerScreenX) / chunkScale);
		this.centerChunkZ = chunkUnderCursorZ - ((mouseY - centerScreenY) / chunkScale);
		return true;
	}

	public void clearSelection() {
		this.selectionStart = null;
		this.selectionEnd = null;
		this.selecting = false;
	}

	public boolean hasSelection() {
		return this.selectionStart != null && this.selectionEnd != null;
	}

	public RegionColor activeColor() {
		return this.activeColor;
	}

	public void setActiveColor(RegionColor activeColor) {
		if (activeColor == null || activeColor == RegionColor.NONE) {
			return;
		}
		this.activeColor = activeColor;
	}

	public ChunkPos hoveredChunk() {
		return this.hoveredChunk;
	}

	public double zoom() {
		return this.zoom;
	}

	private void paintAtCursor(double mouseX, double mouseY, boolean clear, ClientTacticsDataStore.WorldContext worldContext) {
		RegionColor paintColor = clear ? RegionColor.NONE : this.activeColor;
		if (this.hasSelection()) {
			int minX = Math.min(this.selectionStart.x, this.selectionEnd.x);
			int maxX = Math.max(this.selectionStart.x, this.selectionEnd.x);
			int minZ = Math.min(this.selectionStart.z, this.selectionEnd.z);
			int maxZ = Math.max(this.selectionStart.z, this.selectionEnd.z);
			for (int chunkX = minX; chunkX <= maxX; chunkX++) {
				for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
					long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
					this.dataStore.setRegionColor(worldContext, chunkKey, paintColor);
				}
			}
			return;
		}
		ChunkPos hovered = this.chunkAt(mouseX, mouseY);
		if (hovered != null) {
			this.dataStore.setRegionColor(worldContext, hovered.x, hovered.z, paintColor);
		}
	}

	private void renderChunkTerrain(
			DrawContext context,
			ClientTacticsDataStore.WorldContext worldContext,
			int chunkX,
			int chunkZ,
			int left,
			int top,
			int right,
			int bottom) {
		ChunkTerrainCache.TerrainTile tile = this.terrainCache.getCachedTile(worldContext, chunkX, chunkZ);
		if (tile == null) {
			return;
		}

		int widthPixels = right - left;
		int heightPixels = bottom - top;
		if (Math.min(widthPixels, heightPixels) < DETAILED_TEXTURE_MIN_CHUNK_PIXELS) {
			context.fill(left, top, right, bottom, tile.averageColor());
			return;
		}

		context.drawTexture(
				RenderPipelines.GUI_TEXTURED,
				tile.textureId(),
				left,
				top,
				0.0F,
				0.0F,
				widthPixels,
				heightPixels,
				ChunkTerrainCache.tileResolution(),
				ChunkTerrainCache.tileResolution());
	}

	private void drawZoneHighlight(
			DrawContext context,
			ClientTacticsDataStore.WorldContext worldContext,
			int chunkX,
			int chunkZ,
			int left,
			int top,
			int right,
			int bottom,
			RegionColor regionColor) {
		if (right - left < 1 || bottom - top < 1) {
			return;
		}

		context.fill(left, top, right, bottom, regionColor.overlayArgb());

		int outlineColor = (regionColor.swatchArgb() & 0x00FFFFFF) | ZONE_OUTLINE_ALPHA_MASK;
		if (this.dataStore.getRegionColor(worldContext, chunkX, chunkZ - 1) != regionColor) {
			context.fill(left, top, right, top + 1, outlineColor);
		}
		if (this.dataStore.getRegionColor(worldContext, chunkX, chunkZ + 1) != regionColor) {
			context.fill(left, bottom - 1, right, bottom, outlineColor);
		}
		if (this.dataStore.getRegionColor(worldContext, chunkX - 1, chunkZ) != regionColor) {
			context.fill(left, top, left + 1, bottom, outlineColor);
		}
		if (this.dataStore.getRegionColor(worldContext, chunkX + 1, chunkZ) != regionColor) {
			context.fill(right - 1, top, right, bottom, outlineColor);
		}
	}

	private boolean isChunkSelected(int chunkX, int chunkZ) {
		if (!this.hasSelection()) {
			return false;
		}
		int minX = Math.min(this.selectionStart.x, this.selectionEnd.x);
		int maxX = Math.max(this.selectionStart.x, this.selectionEnd.x);
		int minZ = Math.min(this.selectionStart.z, this.selectionEnd.z);
		int maxZ = Math.max(this.selectionStart.z, this.selectionEnd.z);
		return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
	}

	private ChunkPos chunkAt(double mouseX, double mouseY) {
		if (!this.contains(mouseX, mouseY)) {
			return null;
		}
		return new ChunkPos((int) Math.floor(this.screenToChunkX(mouseX)), (int) Math.floor(this.screenToChunkZ(mouseY)));
	}

	private VisibleChunkFrame computeVisibleFrame(double viewportCenterX, double viewportCenterY, double chunkScale) {
		double minChunkX = this.centerChunkX + ((this.x - viewportCenterX) / chunkScale);
		double maxChunkX = this.centerChunkX + (((this.x + this.width) - viewportCenterX) / chunkScale);
		double minChunkZ = this.centerChunkZ + ((this.y - viewportCenterY) / chunkScale);
		double maxChunkZ = this.centerChunkZ + (((this.y + this.height) - viewportCenterY) / chunkScale);

		return new VisibleChunkFrame(
				(int) Math.floor(Math.min(minChunkX, maxChunkX)) - 1,
				(int) Math.ceil(Math.max(minChunkX, maxChunkX)) + 1,
				(int) Math.floor(Math.min(minChunkZ, maxChunkZ)) - 1,
				(int) Math.ceil(Math.max(minChunkZ, maxChunkZ)) + 1);
	}

	private int[] computeChunkEdges(int minChunk, int maxChunk, double centerChunkAxis, double viewportCenterAxis, double chunkScale) {
		int[] edges = new int[(maxChunk - minChunk) + 2];
		for (int index = 0; index < edges.length; index++) {
			double rawEdge = viewportCenterAxis + (((minChunk + index) - centerChunkAxis) * chunkScale);
			int edge = (int) Math.round(rawEdge);
			if (index > 0 && edge <= edges[index - 1]) {
				edge = edges[index - 1] + 1;
			}
			edges[index] = edge;
		}
		return edges;
	}

	private double distanceToCameraSq(ChunkPos chunkPos) {
		double dx = (chunkPos.x + 0.5D) - this.centerChunkX;
		double dz = (chunkPos.z + 0.5D) - this.centerChunkZ;
		return (dx * dx) + (dz * dz);
	}

	private double chunkScale() {
		return BASE_CHUNK_PIXELS * this.zoom;
	}

	private double screenToChunkX(double screenX) {
		double centerScreenX = this.x + (this.width / 2.0D);
		return this.centerChunkX + ((screenX - centerScreenX) / this.chunkScale());
	}

	private double screenToChunkZ(double screenY) {
		double centerScreenY = this.y + (this.height / 2.0D);
		return this.centerChunkZ + ((screenY - centerScreenY) / this.chunkScale());
	}

	private boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private record VisibleChunkFrame(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
	}
}

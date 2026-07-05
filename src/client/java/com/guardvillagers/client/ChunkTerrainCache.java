package com.guardvillagers.client;

import com.guardvillagers.GuardVillagersMod;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkTerrainCache {
	private static final int MAX_TILES_PER_WORLD_DIMENSION = 4_096;
	private static final int FALLBACK_COLOR = 0xFF161C25;
	private static final int TILE_RESOLUTION = 16;
	private static final int TILE_PIXEL_COUNT = TILE_RESOLUTION * TILE_RESOLUTION;
	private static final int MAX_SYNC_VISIBLE_GENERATIONS_PER_FRAME = 8;
	private static final int MAX_ASYNC_VISIBLE_QUEUES_PER_FRAME = 16;

	private final Map<String, LinkedHashMap<Long, TerrainTile>> tilesByWorldDimension = new HashMap<>();
	private final ConcurrentHashMap<TileKey, Long> pendingGeneration = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<TileKey, AsyncTileResult> asyncResults = new ConcurrentHashMap<>();
	private final AtomicLong nextGenerationId = new AtomicLong();

	public static int tileResolution() {
		return TILE_RESOLUTION;
	}

	public void prepareVisibleTiles(ClientTacticsDataStore.WorldContext context, ClientWorld world, List<ChunkPos> visibleChunks) {
		if (context == null || world == null || visibleChunks == null || visibleChunks.isEmpty()) {
			return;
		}

		this.drainAsyncResults();

		String worldKey = this.worldKey(context);
		LinkedHashMap<Long, TerrainTile> cache = this.cache(worldKey);
		int syncGenerated = 0;
		int asyncQueued = 0;

		for (ChunkPos chunkPos : visibleChunks) {
			long chunkKey = ChunkPos.toLong(chunkPos.x, chunkPos.z);
			TileKey tileKey = new TileKey(worldKey, chunkKey);
			if (cache.containsKey(chunkKey) || this.pendingGeneration.containsKey(tileKey)) {
				continue;
			}

			WorldChunk chunk = world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
			if (chunk == null) {
				continue;
			}

			if (syncGenerated < MAX_SYNC_VISIBLE_GENERATIONS_PER_FRAME) {
				cache.put(chunkKey, this.generateTile(worldKey, world, chunk));
				syncGenerated++;
				continue;
			}

			if (asyncQueued >= MAX_ASYNC_VISIBLE_QUEUES_PER_FRAME) {
				break;
			}

			this.queueAsyncGeneration(tileKey, world, chunk);
			asyncQueued++;
		}

		this.evictIfNeeded(cache);
	}

	public TerrainTile getCachedTile(ClientTacticsDataStore.WorldContext context, int chunkX, int chunkZ) {
		if (context == null) {
			return null;
		}
		LinkedHashMap<Long, TerrainTile> cache = this.tilesByWorldDimension.get(this.worldKey(context));
		if (cache == null) {
			return null;
		}
		return cache.get(ChunkPos.toLong(chunkX, chunkZ));
	}

	public void invalidate(ClientTacticsDataStore.WorldContext context, int chunkX, int chunkZ) {
		if (context == null) {
			return;
		}

		String worldKey = this.worldKey(context);
		long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
		TileKey tileId = new TileKey(worldKey, chunkKey);
		this.pendingGeneration.remove(tileId);
		this.asyncResults.remove(tileId);

		LinkedHashMap<Long, TerrainTile> cache = this.tilesByWorldDimension.get(worldKey);
		if (cache == null) {
			return;
		}

		TerrainTile removed = cache.remove(chunkKey);
		if (removed != null) {
			removed.close(MinecraftClient.getInstance().getTextureManager());
		}
	}

	public void clearAll() {
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
		for (LinkedHashMap<Long, TerrainTile> cache : this.tilesByWorldDimension.values()) {
			for (TerrainTile tile : cache.values()) {
				tile.close(textureManager);
			}
			cache.clear();
		}
		this.tilesByWorldDimension.clear();
		this.pendingGeneration.clear();
		this.asyncResults.clear();
	}

	private void queueAsyncGeneration(TileKey tileKey, ClientWorld world, WorldChunk chunk) {
		long generationId = this.nextGenerationId.incrementAndGet();
		if (this.pendingGeneration.putIfAbsent(tileKey, generationId) != null) {
			return;
		}

		ChunkPos chunkPos = chunk.getPos();
		int[] heightmap = snapshotHeightmap(chunk);
		int[] blockColors = snapshotBlockColors(world, chunk, chunkPos, heightmap);
		CompletableFuture
				.supplyAsync(() -> computeTileSnapshot(blockColors))
				.thenAccept(snapshot -> this.asyncResults.put(tileKey, new AsyncTileResult(generationId, snapshot)));
	}

	private void drainAsyncResults() {
		if (this.asyncResults.isEmpty()) {
			return;
		}

		for (Iterator<Map.Entry<TileKey, AsyncTileResult>> iterator = this.asyncResults.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<TileKey, AsyncTileResult> entry = iterator.next();
			TileKey tileKey = entry.getKey();
			AsyncTileResult result = entry.getValue();
			Long pendingId = this.pendingGeneration.get(tileKey);
			if (pendingId == null || pendingId.longValue() != result.generationId()) {
				iterator.remove();
				continue;
			}

			LinkedHashMap<Long, TerrainTile> cache = this.cache(tileKey.worldKey());
			cache.put(tileKey.chunkKey(), this.createTerrainTile(tileKey.worldKey(), tileKey.chunkKey(), result.snapshot()));
			this.pendingGeneration.remove(tileKey, pendingId);
			iterator.remove();
			this.evictIfNeeded(cache);
		}
	}

	private TerrainTile generateTile(String worldKey, ClientWorld world, WorldChunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		int[] colors = snapshotBlockColors(world, chunk, chunkPos, snapshotHeightmap(chunk));
		return this.createTerrainTile(worldKey, ChunkPos.toLong(chunkPos.x, chunkPos.z), computeTileSnapshot(colors));
	}

	private TerrainTile createTerrainTile(String worldKey, long chunkKey, TerrainSnapshot snapshot) {
		NativeImage image = new NativeImage(TILE_RESOLUTION, TILE_RESOLUTION, false);
		int[] colors = snapshot.colors();
		for (int pixelZ = 0; pixelZ < TILE_RESOLUTION; pixelZ++) {
			int rowOffset = pixelZ * TILE_RESOLUTION;
			for (int pixelX = 0; pixelX < TILE_RESOLUTION; pixelX++) {
				image.setColorArgb(pixelX, pixelZ, colors[rowOffset + pixelX]);
			}
		}

		Identifier textureId = GuardVillagersMod.id("tactics/chunk/" + Integer.toUnsignedString(worldKey.hashCode())
				+ "/" + Long.toUnsignedString(chunkKey));
		NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "guardvillagers_chunk_terrain", image);
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
		textureManager.registerTexture(textureId, texture);
		texture.upload();
		// Set nearest-neighbor filtering for crisp pixel-art rendering (1 block = 1 pixel).
		if (texture.getGlTexture() instanceof net.minecraft.client.texture.GlTexture glTex) {
			GlStateManager._bindTexture(glTex.getGlId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		}
		return new TerrainTile(snapshot.averageColor(), textureId, texture);
	}

	private LinkedHashMap<Long, TerrainTile> cache(String worldKey) {
		return this.tilesByWorldDimension.computeIfAbsent(worldKey, ignored -> new LinkedHashMap<>(256, 0.75F, true));
	}

	private String worldKey(ClientTacticsDataStore.WorldContext context) {
		return context.worldId() + "|" + context.dimensionId();
	}

	private void evictIfNeeded(LinkedHashMap<Long, TerrainTile> cache) {
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
		while (cache.size() > MAX_TILES_PER_WORLD_DIMENSION) {
			Iterator<Map.Entry<Long, TerrainTile>> iterator = cache.entrySet().iterator();
			if (!iterator.hasNext()) {
				return;
			}
			Map.Entry<Long, TerrainTile> entry = iterator.next();
			entry.getValue().close(textureManager);
			iterator.remove();
		}
	}

	private static int[] snapshotHeightmap(WorldChunk chunk) {
		int[] heights = new int[TILE_PIXEL_COUNT];
		for (int localZ = 0; localZ < TILE_RESOLUTION; localZ++) {
			int rowOffset = localZ * TILE_RESOLUTION;
			for (int localX = 0; localX < TILE_RESOLUTION; localX++) {
				heights[rowOffset + localX] = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, localX, localZ);
			}
		}
		return heights;
	}

	private static int[] snapshotBlockColors(ClientWorld world, WorldChunk chunk, ChunkPos chunkPos, int[] heightmap) {
		int[] colors = new int[TILE_PIXEL_COUNT];
		int worldBottom = world.getBottomY();
		int startX = chunkPos.getStartX();
		int startZ = chunkPos.getStartZ();
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int localZ = 0; localZ < TILE_RESOLUTION; localZ++) {
			int worldZ = startZ + localZ;
			int rowOffset = localZ * TILE_RESOLUTION;
			for (int localX = 0; localX < TILE_RESOLUTION; localX++) {
				int worldX = startX + localX;
				int index = rowOffset + localX;
				int sampleY = Math.max(worldBottom, heightmap[index] - 1);

				pos.set(worldX, sampleY, worldZ);
				BlockState state = chunk.getBlockState(pos);
				while (sampleY > worldBottom && state.isAir()) {
					sampleY--;
					pos.set(worldX, sampleY, worldZ);
					state = chunk.getBlockState(pos);
				}

				MapColor mapColor = state.getMapColor(world, pos);
				colors[index] = mapColor == MapColor.CLEAR ? FALLBACK_COLOR : 0xFF000000 | mapColor.color;
			}
		}
		return colors;
	}

	private static TerrainSnapshot computeTileSnapshot(int[] colors) {
		long sumRed = 0L;
		long sumGreen = 0L;
		long sumBlue = 0L;
		for (int color : colors) {
			sumRed += (color >> 16) & 0xFF;
			sumGreen += (color >> 8) & 0xFF;
			sumBlue += color & 0xFF;
		}
		int averageColor = 0xFF000000
				| ((int) (sumRed / TILE_PIXEL_COUNT) << 16)
				| ((int) (sumGreen / TILE_PIXEL_COUNT) << 8)
				| (int) (sumBlue / TILE_PIXEL_COUNT);
		return new TerrainSnapshot(colors, averageColor);
	}

	public record TerrainTile(int averageColor, Identifier textureId, NativeImageBackedTexture texture) {
		private void close(TextureManager textureManager) {
			textureManager.destroyTexture(this.textureId);
			this.texture.close();
		}
	}

	private record TileKey(String worldKey, long chunkKey) {
	}

	private record TerrainSnapshot(int[] colors, int averageColor) {
	}

	private record AsyncTileResult(long generationId, TerrainSnapshot snapshot) {
	}
}

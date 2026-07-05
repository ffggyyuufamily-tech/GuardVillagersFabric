package com.guardvillagers.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ClientTacticsDataStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientTacticsDataStore.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int FILE_VERSION = 1;
	private static final long SAVE_DEBOUNCE_MILLIS = 1_200L;
	private static final int MAX_ROLE_NAME_LENGTH = 24;
	private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
	private static final Path SAVE_PATH = FabricLoader.getInstance().getConfigDir().resolve("guardvillagers_client_tactics.json");
	private static final List<String> DEFAULT_GROUP_NAMES = List.of();
	private static final ClientTacticsDataStore INSTANCE = new ClientTacticsDataStore();

	private final Map<String, WorldData> worlds = new HashMap<>();

	private boolean dirty;
	private long dirtyAtMillis;

	private ClientTacticsDataStore() {
		this.load();
	}

	public static ClientTacticsDataStore getInstance() {
		return INSTANCE;
	}

	// Dev note: discovered chunks are tracked from client chunk-load events and persisted by world + dimension.
	public void markDiscovered(WorldContext context, int chunkX, int chunkZ) {
		DimensionData dimensionData = this.dimension(context);
		long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
		if (dimensionData.discovered.add(chunkKey)) {
			this.indexDiscoveredChunk(dimensionData, chunkKey);
			this.markDirty();
		}
	}

	public boolean isDiscovered(WorldContext context, int chunkX, int chunkZ) {
		return this.dimension(context).discovered.contains(ChunkPos.toLong(chunkX, chunkZ));
	}

	public long[] discoveredChunkKeys(WorldContext context) {
		return this.dimension(context).discovered.toLongArray();
	}

	public List<ChunkPos> discoveredChunksInBounds(WorldContext context, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
		DimensionData dimensionData = this.dimension(context);
		List<ChunkPos> visibleChunks = new ArrayList<>();
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			IntOpenHashSet zValues = dimensionData.discoveredByChunkX.get(chunkX);
			if (zValues == null || zValues.isEmpty()) {
				continue;
			}
			for (int chunkZ : zValues) {
				if (chunkZ >= minChunkZ && chunkZ <= maxChunkZ) {
					visibleChunks.add(new ChunkPos(chunkX, chunkZ));
				}
			}
		}
		return visibleChunks;
	}

	public RegionColor getRegionColor(WorldContext context, int chunkX, int chunkZ) {
		int colorId = this.dimension(context).regionByChunk.get(ChunkPos.toLong(chunkX, chunkZ));
		return RegionColor.fromId(colorId);
	}

	public void setRegionColor(WorldContext context, int chunkX, int chunkZ, RegionColor color) {
		this.setRegionColor(context, ChunkPos.toLong(chunkX, chunkZ), color);
	}

	public void setRegionColor(WorldContext context, long chunkKey, RegionColor color) {
		DimensionData dimensionData = this.dimension(context);
		RegionColor normalized = color == null ? RegionColor.NONE : color;
		if (!dimensionData.discovered.contains(chunkKey)) {
			return;
		}
		if (normalized == RegionColor.NONE) {
			if (dimensionData.regionByChunk.remove(chunkKey) != 0) {
				this.markDirty();
			}
			return;
		}
		if (dimensionData.regionByChunk.put(chunkKey, normalized.id()) != normalized.id()) {
			this.markDirty();
		}
	}

	public int groupCount(WorldContext context) {
		return this.world(context).groupNames.size();
	}

	public void ensureGroupCount(WorldContext context, int groupCount) {
		int targetCount = Math.max(0, groupCount);
		WorldData worldData = this.world(context);
		if (targetCount <= worldData.groupNames.size()) {
			return;
		}
		while (worldData.groupNames.size() < targetCount) {
			worldData.groupNames.add("Group " + (worldData.groupNames.size() + 1));
		}
		this.markDirty();
	}

	public String getGroupName(WorldContext context, int row) {
		int normalizedRow = Math.max(0, row);
		this.ensureGroupCount(context, normalizedRow + 1);
		return this.world(context).groupNames.get(normalizedRow);
	}

	public void setGroupName(WorldContext context, int row, String roleName) {
		int normalizedRow = Math.max(0, row);
		this.ensureGroupCount(context, normalizedRow + 1);
		String sanitized = sanitizeGroupName(roleName);
		WorldData worldData = this.world(context);
		String previous = worldData.groupNames.set(normalizedRow, sanitized);
		if (!previous.equals(sanitized)) {
			this.markDirty();
		}
	}

	public void replaceGroupNames(WorldContext context, List<String> groupNames) {
		if (context == null) {
			return;
		}

		List<String> sanitizedNames = groupNames == null
				? List.of()
				: groupNames.stream().map(ClientTacticsDataStore::sanitizeGroupName).toList();
		WorldData worldData = this.world(context);
		if (worldData.groupNames.equals(sanitizedNames)) {
			return;
		}

		worldData.groupNames.clear();
		worldData.groupNames.addAll(sanitizedNames);
		this.markDirty();
	}

	public RegionColor getGroupColor(WorldContext context, int row) {
		int colorId = this.world(context).rowColorByGroup.get(Math.max(0, row));
		return RegionColor.fromId(colorId);
	}

	public void setGroupColor(WorldContext context, int row, RegionColor color) {
		int normalizedRow = Math.max(0, row);
		RegionColor normalized = color == null ? RegionColor.NONE : color;
		WorldData worldData = this.world(context);

		if (normalized != RegionColor.NONE) {
			int toClear = Integer.MIN_VALUE;
			for (Int2IntMap.Entry entry : worldData.rowColorByGroup.int2IntEntrySet()) {
				if (entry.getIntKey() != normalizedRow && entry.getIntValue() == normalized.id()) {
					toClear = entry.getIntKey();
					break;
				}
			}
			if (toClear != Integer.MIN_VALUE) {
				worldData.rowColorByGroup.remove(toClear);
			}
		}

		if (normalized == RegionColor.NONE) {
			if (worldData.rowColorByGroup.remove(normalizedRow) != 0) {
				this.markDirty();
			}
			return;
		}

		if (worldData.rowColorByGroup.put(normalizedRow, normalized.id()) != normalized.id()) {
			this.markDirty();
		} else {
			this.markDirty();
		}
	}

	public Optional<GroupColorBinding> groupBindingForColor(WorldContext context, RegionColor color) {
		if (color == null || color == RegionColor.NONE) {
			return Optional.empty();
		}
		int colorId = color.id();
		WorldData worldData = this.world(context);
		int bestRow = Integer.MAX_VALUE;
		for (Int2IntMap.Entry entry : worldData.rowColorByGroup.int2IntEntrySet()) {
			if (entry.getIntValue() == colorId && entry.getIntKey() < bestRow) {
				bestRow = entry.getIntKey();
			}
		}
		if (bestRow == Integer.MAX_VALUE) {
			return Optional.empty();
		}
		return Optional.of(new GroupColorBinding(bestRow, this.getGroupName(context, bestRow)));
	}

	public void tickSave() {
		if (!this.dirty) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - this.dirtyAtMillis < SAVE_DEBOUNCE_MILLIS) {
			return;
		}
		this.save();
	}

	public void flush() {
		if (this.dirty) {
			this.save();
		}
	}

	private void markDirty() {
		this.dirty = true;
		this.dirtyAtMillis = System.currentTimeMillis();
	}

	private void indexDiscoveredChunk(DimensionData dimensionData, long chunkKey) {
		int chunkX = ChunkPos.getPackedX(chunkKey);
		int chunkZ = ChunkPos.getPackedZ(chunkKey);
		dimensionData.discoveredByChunkX.computeIfAbsent(chunkX, ignored -> new IntOpenHashSet()).add(chunkZ);
	}

	private WorldData world(WorldContext context) {
		return this.worlds.computeIfAbsent(context.worldId(), ignored -> new WorldData());
	}

	private DimensionData dimension(WorldContext context) {
		WorldData worldData = this.world(context);
		return worldData.dimensions.computeIfAbsent(context.dimensionId(), ignored -> new DimensionData());
	}

	private void load() {
		this.worlds.clear();
		if (!Files.exists(SAVE_PATH)) {
			return;
		}
		try {
			long fileSize = Files.size(SAVE_PATH);
			if (fileSize > MAX_FILE_SIZE_BYTES) {
				LOGGER.warn("Guard Villagers client tactics file exceeds size limit ({} > {}) bytes, rotating to .bak", fileSize, MAX_FILE_SIZE_BYTES);
				try {
					Files.move(SAVE_PATH, SAVE_PATH.resolveSibling(SAVE_PATH.getFileName() + ".bak"));
				} catch (IOException e) {
					LOGGER.error("Failed to rotate oversized tactics file", e);
				}
				return;
			}
			String json = Files.readString(SAVE_PATH);
			JsonElement rootElement = JsonParser.parseString(json);
			if (!rootElement.isJsonObject()) {
				return;
			}
			JsonObject rootObject = rootElement.getAsJsonObject();
			JsonObject worldsObject = asObject(rootObject.get("worlds"));
			if (worldsObject == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> worldEntry : worldsObject.entrySet()) {
				JsonObject worldObject = asObject(worldEntry.getValue());
				if (worldObject == null) {
					continue;
				}
				WorldData worldData = new WorldData();
				worldData.groupNames.clear();

				JsonArray rolesArray = asArray(worldObject.get("groupNames"));
				if (rolesArray != null) {
					for (JsonElement roleElement : rolesArray) {
						String sanitized = sanitizeGroupName(asString(roleElement));
						if (!sanitized.isBlank()) {
							worldData.groupNames.add(sanitized);
						}
					}
				}
				JsonObject rowColorsObject = asObject(worldObject.get("rowColors"));
				if (rowColorsObject != null) {
					for (Map.Entry<String, JsonElement> colorEntry : rowColorsObject.entrySet()) {
						try {
							int row = Integer.parseInt(colorEntry.getKey());
							RegionColor color = RegionColor.fromId(asInt(colorEntry.getValue(), 0));
							if (row >= 0 && color != RegionColor.NONE) {
								worldData.rowColorByGroup.put(row, color.id());
							}
						} catch (NumberFormatException ignored) {
						}
					}
				}

				JsonObject dimensionsObject = asObject(worldObject.get("dimensions"));
				if (dimensionsObject != null) {
					for (Map.Entry<String, JsonElement> dimensionEntry : dimensionsObject.entrySet()) {
						JsonObject dimensionObject = asObject(dimensionEntry.getValue());
						if (dimensionObject == null) {
							continue;
						}
						DimensionData dimensionData = new DimensionData();
						JsonArray discoveredArray = asArray(dimensionObject.get("discovered"));
						if (discoveredArray != null) {
							for (JsonElement discoveredElement : discoveredArray) {
								try {
									long chunkKey = Long.parseLong(asString(discoveredElement));
									dimensionData.discovered.add(chunkKey);
									this.indexDiscoveredChunk(dimensionData, chunkKey);
								} catch (NumberFormatException ignored) {
								}
							}
						}

						JsonObject regionsObject = asObject(dimensionObject.get("regions"));
						if (regionsObject != null) {
							for (Map.Entry<String, JsonElement> regionEntry : regionsObject.entrySet()) {
								try {
									long chunkKey = Long.parseLong(regionEntry.getKey());
									RegionColor color = RegionColor.fromId(asInt(regionEntry.getValue(), 0));
									if (color != RegionColor.NONE) {
										dimensionData.regionByChunk.put(chunkKey, color.id());
									}
								} catch (NumberFormatException ignored) {
								}
							}
						}
						worldData.dimensions.put(dimensionEntry.getKey(), dimensionData);
					}
				}
				this.worlds.put(worldEntry.getKey(), worldData);
			}
			this.dirty = false;
		} catch (IOException exception) {
			LOGGER.error("Failed to load client tactics data from {}", SAVE_PATH, exception);
		}
	}

	private void save() {
		JsonObject rootObject = new JsonObject();
		rootObject.addProperty("version", FILE_VERSION);
		JsonObject worldsObject = new JsonObject();
		rootObject.add("worlds", worldsObject);

		for (Map.Entry<String, WorldData> worldEntry : this.worlds.entrySet()) {
			WorldData worldData = worldEntry.getValue();
			JsonObject worldObject = new JsonObject();
			worldsObject.add(worldEntry.getKey(), worldObject);

			JsonArray roleArray = new JsonArray();
			for (String roleName : worldData.groupNames) {
				roleArray.add(sanitizeGroupName(roleName));
			}
			worldObject.add("groupNames", roleArray);

			JsonObject rowColorObject = new JsonObject();
			for (Int2IntMap.Entry colorEntry : worldData.rowColorByGroup.int2IntEntrySet()) {
				RegionColor color = RegionColor.fromId(colorEntry.getIntValue());
				if (color != RegionColor.NONE) {
					rowColorObject.addProperty(Integer.toString(colorEntry.getIntKey()), color.id());
				}
			}
			worldObject.add("rowColors", rowColorObject);

			JsonObject dimensionsObject = new JsonObject();
			worldObject.add("dimensions", dimensionsObject);
			for (Map.Entry<String, DimensionData> dimensionEntry : worldData.dimensions.entrySet()) {
				DimensionData dimensionData = dimensionEntry.getValue();
				JsonObject dimensionObject = new JsonObject();
				dimensionsObject.add(dimensionEntry.getKey(), dimensionObject);

				JsonArray discoveredArray = new JsonArray();
				for (long discoveredChunk : dimensionData.discovered) {
					discoveredArray.add(Long.toString(discoveredChunk));
				}
				dimensionObject.add("discovered", discoveredArray);

				JsonObject regionsObject = new JsonObject();
				for (Long2IntMap.Entry regionEntry : dimensionData.regionByChunk.long2IntEntrySet()) {
					RegionColor color = RegionColor.fromId(regionEntry.getIntValue());
					if (color != RegionColor.NONE) {
						regionsObject.addProperty(Long.toString(regionEntry.getLongKey()), color.id());
					}
				}
				dimensionObject.add("regions", regionsObject);
			}
		}

		try {
			Files.createDirectories(SAVE_PATH.getParent());
			Files.writeString(
				SAVE_PATH,
				GSON.toJson(rootObject),
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE
			);
			this.dirty = false;
		} catch (IOException exception) {
			LOGGER.error("Failed to save client tactics data to {}", SAVE_PATH, exception);
		}
	}

	public static WorldContext resolveContext(MinecraftClient client, ClientWorld world) {
		String dimensionId = world.getRegistryKey().getValue().toString();
		return new WorldContext(resolveWorldId(client), dimensionId);
	}

	private static String resolveWorldId(MinecraftClient client) {
		if (client.isInSingleplayer() && client.getServer() != null) {
			String levelName = client.getServer().getSaveProperties().getLevelName();
			return "singleplayer:" + safeKey(levelName);
		}
		ServerInfo serverInfo = client.getCurrentServerEntry();
		if (serverInfo != null && serverInfo.address != null && !serverInfo.address.isBlank()) {
			return "multiplayer:" + safeKey(serverInfo.address.toLowerCase(Locale.ROOT));
		}
		return "session:default";
	}

	private static String safeKey(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.replace('\\', '_').replace('/', '_').replace(':', '_');
	}

	private static String sanitizeGroupName(String name) {
		if (name == null || name.isBlank()) {
			return "Alpha";
		}
		String trimmed = name.trim();
		return trimmed.length() <= MAX_ROLE_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_ROLE_NAME_LENGTH);
	}

	private static JsonObject asObject(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static JsonArray asArray(JsonElement element) {
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static String asString(JsonElement element) {
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static int asInt(JsonElement element, int fallback) {
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static final class WorldData {
		private final Map<String, DimensionData> dimensions = new HashMap<>();
		private final List<String> groupNames = new ArrayList<>(DEFAULT_GROUP_NAMES);
		private final Int2IntOpenHashMap rowColorByGroup = new Int2IntOpenHashMap();

		private WorldData() {
			this.rowColorByGroup.defaultReturnValue(0);
		}
	}

	private static final class DimensionData {
		private final LongOpenHashSet discovered = new LongOpenHashSet();
		private final Long2IntOpenHashMap regionByChunk = new Long2IntOpenHashMap();
		private final Int2ObjectOpenHashMap<IntOpenHashSet> discoveredByChunkX = new Int2ObjectOpenHashMap<>();

		private DimensionData() {
			this.regionByChunk.defaultReturnValue(0);
		}
	}

	public record WorldContext(String worldId, String dimensionId) {
	}

	public record GroupColorBinding(int row, String groupName) {
	}
}

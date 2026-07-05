package com.guardvillagers.client;

import com.guardvillagers.GuardVillagersMod;
import com.guardvillagers.network.GuardDebugDataPayload;
import com.guardvillagers.network.GuardDebugSyncPayload;
import com.guardvillagers.network.GuardRosterSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class GuardVillagersClient implements ClientModInitializer {
	private static final ChunkTerrainCache TERRAIN_CACHE = new ChunkTerrainCache();

	public static ChunkTerrainCache terrainCache() {
		return TERRAIN_CACHE;
	}

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(GuardRosterSyncPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				MinecraftClient client = context.client();
				if (client.world == null) {
					return;
				}
				ClientTacticsDataStore.WorldContext worldContext = ClientTacticsDataStore.resolveContext(client, client.world);
				ClientGuardRosterStore.getInstance().applyPayload(worldContext, payload);
				ClientTacticsDataStore.getInstance().replaceGroupNames(worldContext, payload.groupNames());
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(GuardDebugSyncPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				ClientDebugState.update(payload.enabled(), payload.range());
				if (!payload.enabled()) {
					ClientGuardDebugData.clear();
				}
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(GuardDebugDataPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientGuardDebugData.applyPayload(payload))
		);

		EntityModelLayerRegistry.registerModelLayer(GuardEntityModel.GUARD_LAYER, GuardEntityModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(
				GuardEntityModel.GUARD_BABY_LAYER,
				GuardEntityModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(
				GuardEntityModel.GUARD_CLOTHING_LAYER,
				GuardEntityModel::getClothingTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(
				GuardEntityModel.GUARD_CLOTHING_BABY_LAYER,
				GuardEntityModel::getClothingTexturedModelData);
		EntityRendererRegistry.register(GuardVillagersMod.GUARD_ENTITY_TYPE, GuardEntityRenderer::new);
		HandledScreens.register(GuardVillagersMod.GUARD_TACTICS_SCREEN_HANDLER, GuardTacticsScreen::new);
		GuardDebugRenderer.register();

		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientTacticsDataStore.WorldContext context = ClientTacticsDataStore.resolveContext(client, world);
			ClientTacticsDataStore.getInstance().markDiscovered(context, chunk.getPos().x, chunk.getPos().z);
			TERRAIN_CACHE.invalidate(context, chunk.getPos().x, chunk.getPos().z);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> ClientTacticsDataStore.getInstance().tickSave());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientTacticsDataStore.getInstance().flush();
			TERRAIN_CACHE.clearAll();
			ClientGuardRosterStore.getInstance().clear();
			ClientDebugState.reset();
			ClientGuardDebugData.clear();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ClientTacticsDataStore.getInstance().flush();
			ClientGuardRosterStore.getInstance().clear();
			ClientDebugState.reset();
			ClientGuardDebugData.clear();
		});
	}
}

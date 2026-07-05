package com.guardvillagers.client;

import com.guardvillagers.entity.GuardEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.VillagerClothingFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EquipmentModelData;
import net.minecraft.client.render.entity.state.VillagerDataRenderState;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

public class GuardEntityRenderer
		extends BipedEntityRenderer<GuardEntity, GuardEntityRenderer.GuardRenderState, GuardEntityModel> {
	private static final VillagerData FIXED_PLAINS_VILLAGER_DATA = new VillagerData(
			Registries.VILLAGER_TYPE.getEntry(Registries.VILLAGER_TYPE.getValueOrThrow(VillagerType.PLAINS)),
			Registries.VILLAGER_PROFESSION.getEntry(
					Registries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE)),
			1);

	public GuardEntityRenderer(EntityRendererFactory.Context context) {
		super(
				context,
				new GuardEntityModel(context.getPart(GuardEntityModel.GUARD_LAYER)),
				new GuardEntityModel(context.getPart(GuardEntityModel.GUARD_BABY_LAYER)),
				0.5F,
				VillagerEntityRenderer.HEAD_TRANSFORMATION);
		this.addFeature(
				new ArmorFeatureRenderer<>(
						this,
						EquipmentModelData.mapToEntityModel(
								EntityModelLayers.ZOMBIE_VILLAGER_EQUIPMENT,
								context.getEntityModels(),
								GuardEntityModel::new),
						EquipmentModelData.mapToEntityModel(
								EntityModelLayers.ZOMBIE_VILLAGER_BABY_EQUIPMENT,
								context.getEntityModels(),
								GuardEntityModel::new),
						context.getEquipmentRenderer()));
		this.addFeature(
				new VillagerClothingFeatureRenderer<>(
						this,
						context.getResourceManager(),
						"villager",
						new GuardEntityModel(context.getPart(GuardEntityModel.GUARD_CLOTHING_LAYER)),
						new GuardEntityModel(context.getPart(GuardEntityModel.GUARD_CLOTHING_BABY_LAYER))));
	}

	@Override
	public Identifier getTexture(GuardRenderState state) {
		return GuardSkinResolver.resolveTexture(state.skinProfileId);
	}

	@Override
	public GuardRenderState createRenderState() {
		return new GuardRenderState();
	}

	@Override
	public void updateRenderState(GuardEntity entity, GuardRenderState state, float tickDelta) {
		super.updateRenderState(entity, state, tickDelta);
		state.skinProfileId = entity.getSkinProfileId();
		state.villagerData = FIXED_PLAINS_VILLAGER_DATA;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.targetedEntity == entity && state.displayName == null) {
			state.displayName = Text.literal(
					"Lv " + entity.getLevel() + " | " + entity.getRole().name() + " | " + entity.getBehavior().name());
		}
	}

	@Override
	protected BipedEntityModel.ArmPose getArmPose(GuardEntity entity, Arm arm) {
		if (entity.isUsingItem()) {
			boolean isActiveArm = entity.getActiveHand() == Hand.MAIN_HAND
					? arm == entity.getMainArm()
					: arm != entity.getMainArm();
			if (isActiveArm) {
				ItemStack stack = entity.getStackInArm(arm);
				if (stack.isOf(Items.BOW)) {
					return BipedEntityModel.ArmPose.BOW_AND_ARROW;
				}
				if (stack.isOf(Items.SHIELD)) {
					return BipedEntityModel.ArmPose.BLOCK;
				}
			}
		}
		return super.getArmPose(entity, arm);
	}

	public static class GuardRenderState extends BipedEntityRenderState implements VillagerDataRenderState {
		public String skinProfileId = "";
		public VillagerData villagerData = FIXED_PLAINS_VILLAGER_DATA;

		@Override
		public VillagerData getVillagerData() {
			return this.villagerData;
		}
	}
}

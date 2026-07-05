package com.guardvillagers.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.ModelWithHat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

public final class
GuardEntityModel extends BipedEntityModel<GuardEntityRenderer.GuardRenderState>
		implements ModelWithHat<GuardEntityRenderer.GuardRenderState> {
	public static final EntityModelLayer GUARD_LAYER = new EntityModelLayer(Identifier.of("guardvillagers", "guard"),
			"main");
	public static final EntityModelLayer GUARD_BABY_LAYER = new EntityModelLayer(
			Identifier.of("guardvillagers", "guard"),
			"baby");
	public static final EntityModelLayer GUARD_CLOTHING_LAYER = new EntityModelLayer(
			Identifier.of("guardvillagers", "guard"),
			"clothing");
	public static final EntityModelLayer GUARD_CLOTHING_BABY_LAYER = new EntityModelLayer(
			Identifier.of("guardvillagers", "guard"),
			"clothing_baby");

	public GuardEntityModel(ModelPart root) {
		super(root);
	}

	public static TexturedModelData getTexturedModelData() {
		return createTexturedModelData(false);
	}

	public static TexturedModelData getClothingTexturedModelData() {
		return createTexturedModelData(true);
	}

	private static TexturedModelData createTexturedModelData(boolean includeJacket) {
		ModelData modelData = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
		ModelPartData root = modelData.getRoot();
		ModelPartData head = root.addChild(
				"head",
				ModelPartBuilder.create()
						.uv(0, 0)
						.cuboid(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F)
						.uv(24, 0)
						.cuboid(-1.0F, -3.0F, -6.0F, 2.0F, 4.0F, 2.0F),
				ModelTransform.NONE);
		ModelPartData hat = head.addChild(
				"hat",
				ModelPartBuilder.create().uv(32, 0).cuboid(
						-4.0F,
						-10.0F,
						-4.0F,
						8.0F,
						10.0F,
						8.0F,
						new Dilation(0.5F)),
				ModelTransform.NONE);
		hat.addChild(
				"hat_rim",
				ModelPartBuilder.create().uv(30, 47).cuboid(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F),
				ModelTransform.rotation(-1.5707964F, 0.0F, 0.0F));

		ModelPartData body = root.addChild(
				"body",
				ModelPartBuilder.create().uv(16, 20).cuboid(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
				ModelTransform.NONE);
		if (includeJacket) {
			body.addChild(
					"jacket",
					ModelPartBuilder.create().uv(0, 38).cuboid(
							-4.0F,
							0.0F,
							-3.0F,
							8.0F,
							20.0F,
							6.0F,
							new Dilation(0.5F)),
					ModelTransform.NONE);
		}
		root.addChild(
				"right_arm",
				ModelPartBuilder.create()
						.uv(44, 22)
						.cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
						.uv(44, 26)
						.cuboid(-3.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F),
				ModelTransform.origin(-5.0F, 2.0F, 0.0F));
		root.addChild(
				"left_arm",
				ModelPartBuilder.create()
						.uv(44, 22)
						.mirrored()
						.cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
						.uv(44, 26)
						.mirrored()
						.cuboid(-1.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F),
				ModelTransform.origin(5.0F, 2.0F, 0.0F));
		root.addChild(
				"right_leg",
				ModelPartBuilder.create().uv(0, 22).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				ModelTransform.origin(-2.0F, 12.0F, 0.0F));
		root.addChild(
				"left_leg",
				ModelPartBuilder.create().uv(0, 22).mirrored().cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				ModelTransform.origin(2.0F, 12.0F, 0.0F));
		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public void setAngles(GuardEntityRenderer.GuardRenderState state) {
		super.setAngles(state);
		// BipedEntityModel handles the side-arm idle pose plus bow/swing animations.
	}

	@Override
	public void rotateArms(GuardEntityRenderer.GuardRenderState state, MatrixStack matrices) {
		this.setArmAngle(state, Arm.RIGHT, matrices);
	}
}

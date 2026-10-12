package com.elderlexicon.mod.nahva.client;

import com.elderlexicon.mod.nahva.Reflection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Optional;
import java.util.UUID;

/** The reflection drawn as its owner is: their skin (wide or slim arms), what they wear and hold, no name. */
public class ReflectionRenderer extends LivingEntityRenderer<Reflection, PlayerModel<Reflection>> {

    private final PlayerModel<Reflection> wide;
    private final PlayerModel<Reflection> slim;

    public ReflectionRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wide = getModel();
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    private static Optional<PlayerInfo> info(Reflection reflection) {
        return reflection.owner().map(id -> Minecraft.getInstance().getConnection() == null ? null
                : Minecraft.getInstance().getConnection().getPlayerInfo(id));
    }

    @Override
    public void render(Reflection reflection, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        boolean thin = info(reflection).map(info -> "slim".equals(info.getModelName())).orElse(false);
        model = thin ? slim : wide;
        model.crouching = reflection.isCrouching();
        boolean rightHolds = !reflection.getItemInHand(reflection.getMainArm() == HumanoidArm.RIGHT
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).isEmpty();
        boolean leftHolds = !reflection.getItemInHand(reflection.getMainArm() == HumanoidArm.LEFT
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).isEmpty();
        model.rightArmPose = rightHolds ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = leftHolds ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
        super.render(reflection, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Reflection reflection) {
        Optional<PlayerInfo> info = info(reflection);
        if (info.isPresent()) {
            return info.get().getSkinLocation();
        }
        UUID id = reflection.owner().orElse(reflection.getUUID());
        return DefaultPlayerSkin.getDefaultSkin(id);
    }

    @Override
    protected boolean shouldShowName(Reflection reflection) {
        return false;
    }
}

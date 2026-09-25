package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.render.SpellMapHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class PlacedScrollRenderer extends EntityRenderer<PlacedScrollEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/map/map_background.png");

    public PlacedScrollRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PlacedScrollEntity entity,
                       float yaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight) {
        ItemStack stack = entity.getScroll();
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        rotateToFace(entity.getFace(), poseStack);
        MapItemSavedData data = SpellMapHelper.getSavedData(stack, entity.level());
        if (data != null) {
            // A map is drawn on one side only, seen from its local -Z. Turned like an item frame turns it (180 degrees
            // about Y), that side faces out of the surface and the small -Z offset below lifts it off the block;
            // without this the drawing sat inside the block with its back to the viewer, and the scroll vanished.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poseStack.scale(0.006640625F, 0.006640625F, 0.006640625F);
            poseStack.translate(-64.0F, -64.0F, -1.0F);
            Integer mapId = SpellMapHelper.getMapId(stack);
            if (mapId == null) {
                poseStack.popPose();
                return;
            }
            Minecraft.getInstance().gameRenderer.getMapRenderer().render(poseStack, buffer, mapId, data, true, packedLight);
        } else {
            poseStack.scale(0.85F, 0.85F, 0.85F);
            Minecraft.getInstance().getItemRenderer()
                    .renderStatic(stack, ItemDisplayContext.FIXED, packedLight, 0, poseStack, buffer, entity.level(), entity.getId());
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PlacedScrollEntity entity) {
        return TEXTURE;
    }

    private void rotateToFace(Direction face, PoseStack poseStack) {
        Direction safeFace = face == null ? Direction.NORTH : face;
        switch (safeFace) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case SOUTH -> {
            }
        }
    }
}

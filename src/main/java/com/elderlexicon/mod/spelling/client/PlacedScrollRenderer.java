package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spell.block.WrittenTexts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** A loose page laid on a block: drawn as the grimoire's page it is, with its runes ({@link ScrollPage}). */
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
        Direction face = entity.getFace();
        net.minecraft.world.phys.Vec3 rest = entity.position()
                .subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal()).scale(0.505D));
        // Part of a circle on show: its runes are written by the circle; the page shows blank.
        boolean blank = ClientCircles.hides(net.minecraft.core.BlockPos.containing(rest), face);
        CompoundTag tag = stack.getTag();
        WrittenTexts.upgrade(tag);
        String text = tag == null ? "" : tag.getString("DetachedPageText");
        // Turned as an item frame turns a map (180 degrees about Y, then Z), so the page faces out of the surface,
        // lifted a hair off it; then fitted into the block's face, a little inside its edges.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        float size = 0.85F / ScrollPage.H;
        poseStack.scale(size, size, size);
        poseStack.translate(-ScrollPage.W / 2.0F, -ScrollPage.H / 2.0F, -1.0F);
        ScrollPage.draw(poseStack, buffer, packedLight, text, blank);
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

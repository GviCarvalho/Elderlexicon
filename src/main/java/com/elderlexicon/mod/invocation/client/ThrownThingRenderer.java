package com.elderlexicon.mod.invocation.client;

import com.elderlexicon.mod.invocation.ThrownThing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A thrown thing drawn as the item it is, laid along its flight as an arrow is, point first: an item's picture has its
 * point (a blade's tip, an axe's head) at its upper right, turned here to lead the way.
 */
@OnlyIn(Dist.CLIENT)
public final class ThrownThingRenderer extends EntityRenderer<ThrownThing> {

    private static final float SCALE = 1.25F;
    private final ItemRenderer items;

    public ThrownThingRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ThrownThing thrown, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        ItemStack thing = thrown.thing();
        if (!thing.isEmpty()) {
            pose.pushPose();
            // As the arrow's renderer: its +x along the flight.
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, thrown.yRotO, thrown.getYRot()) - 90.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, thrown.xRotO, thrown.getXRot())));
            // The picture's upper right, its point, onto +x.
            pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            pose.scale(SCALE, SCALE, SCALE);
            items.renderStatic(thing, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    thrown.level(), thrown.getId());
            pose.popPose();
        }
        super.render(thrown, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownThing thrown) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

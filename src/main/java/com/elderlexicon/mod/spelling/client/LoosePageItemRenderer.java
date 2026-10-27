package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.block.WrittenTexts;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A loose page in the hand is the page itself, with its runes, as it is on a block and in a frame ({@link ScrollPage});
 * in the inventory and on the ground it is its small picture (item/loose_page_icon).
 */
@OnlyIn(Dist.CLIENT)
public final class LoosePageItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** The picture of the page, as a plain item draws it. */
    public static final ResourceLocation ICON = new ResourceLocation(ElderLexicon.MODID, "item/loose_page_icon");

    private static LoosePageItemRenderer instance;

    private LoosePageItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static LoosePageItemRenderer get() {
        if (instance == null) {
            instance = new LoosePageItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        if (!context.firstPerson() && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            // Its picture: drawn as a plain item would be, from the middle of the space it is given.
            BakedModel icon = Minecraft.getInstance().getModelManager().getModel(ICON);
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            Minecraft.getInstance().getItemRenderer().render(stack, context, false, pose, buffers, light, overlay,
                    icon);
            pose.popPose();
            return;
        }
        CompoundTag tag = stack.getTag();
        WrittenTexts.upgrade(tag);
        String text = tag == null ? "" : tag.getString("DetachedPageText");
        // The item's space is a block, 0 to 1, seen from +z: the page stands in it, as tall as it is, facing out.
        float size = 1.0F / ScrollPage.H;
        pose.pushPose();
        pose.translate((1.0F - ScrollPage.W * size) / 2.0F, 1.0F, 0.5F);
        pose.scale(size, -size, -size);
        ScrollPage.draw(pose, buffers, light, text, false);
        pose.popPose();
    }
}

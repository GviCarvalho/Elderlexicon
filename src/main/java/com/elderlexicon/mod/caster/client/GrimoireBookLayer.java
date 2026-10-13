package com.elderlexicon.mod.caster.client;

import com.elderlexicon.mod.caster.RuneCaster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * A caster reading or writing in its grimoire (docs/mago-design.md, 5c): the book open in its hands, before its chest,
 * the pages turning, as the enchanting table's book turns them. Only drawn, nothing in the world: it costs no
 * particles.
 */
public class GrimoireBookLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    /** Where the book is held, in the model's space: chest high, a little before the crossed arms. */
    private static final float HEIGHT = 0.38F;
    private static final float AHEAD = -0.42F;
    private static final float SIZE = 0.55F;

    private final BookModel book;

    public GrimoireBookLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.book = new BookModel(models.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof RuneCaster caster) || !caster.readingBook()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, HEIGHT, AHEAD);
        // Back out of the model's flipped space: up is up, ahead is -z.
        pose.scale(-1.0F, -1.0F, 1.0F);
        // The book's pages face +x and its spine runs up: turned so the pages face the reader, then tilted back so
        // they face up toward its eyes, the spine running away from it.
        pose.mulPose(Axis.XP.rotationDegrees(-67.5F));
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.scale(SIZE, SIZE, SIZE);
        float turning = ageInTicks * 0.04F;
        float right = Mth.clamp(Mth.frac(turning + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float left = Mth.clamp(Mth.frac(turning + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        book.setupAnim(ageInTicks, right, left, 1.2F);
        VertexConsumer vertices = EnchantTableRenderer.BOOK_LOCATION.buffer(buffers, RenderType::entitySolid);
        book.renderToBuffer(pose, vertices, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}

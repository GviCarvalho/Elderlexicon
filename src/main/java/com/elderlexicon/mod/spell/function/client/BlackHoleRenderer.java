package com.elderlexicon.mod.spell.function.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.function.BlackHole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a {@link BlackHole}: a sphere of perfect black, which no light leaves, turning slowly, and around it a flat
 * disc of glowing matter spinning as it falls in.
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHole> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElderLexicon.MODID, "textures/entity/element_orb.png");

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BlackHole hole, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float size = hole.horizon();
        float age = hole.tickCount + partialTick;
        // The heart: the starfield of the End's portal, the black beyond everything, turning slowly.
        starfield(pose, buffers, size * 0.45F, age);
        // The disc of matter falling in, glowing hot, tilted a little.
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        for (int ring = 0; ring < 2; ring++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(12.0F));
            pose.mulPose(Axis.YP.rotationDegrees(age * (9.0F + ring * 5.0F)));
            float width = size * (1.3F + ring * 0.5F);
            pose.scale(width, size * 0.04F, width);
            float alpha = ring == 0 ? 0.8F : 0.4F;
            cube(pose, glow, 1.0F, 1.0F, 0.62F - ring * 0.2F, 0.25F + ring * 0.35F, alpha, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        super.render(hole, yaw, partialTick, pose, buffers, light);
    }

    private static void cube(PoseStack pose, VertexConsumer buffer, float h, float red, float green, float blue,
                             float alpha, int light) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1},
                {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1},
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0},
                {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0},
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0},
                {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (float[] f : faces) {
            for (int corner = 0; corner < 4; corner++) {
                buffer.vertex(matrix, f[corner * 3], f[corner * 3 + 1], f[corner * 3 + 2]).color(red, green, blue, alpha)
                        .uv(uv[corner][0], uv[corner][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(normal, f[12], f[13], f[14]).endVertex();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHole hole) {
        return TEXTURE;
    }

    /** A cube of the End portal's starfield (the portal's own shader: its stars are fixed on the screen, not the cube). */
    static void starfield(PoseStack pose, MultiBufferSource buffers, float half, float age) {
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
        pose.mulPose(Axis.XP.rotationDegrees(age * 1.3F));
        Matrix4f matrix = pose.last().pose();
        float h = half;
        float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h},
                {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h},
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h},
                {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h},
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h},
                {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h}};
        for (float[] f : faces) {
            for (int corner = 0; corner < 4; corner++) {
                portal.vertex(matrix, f[corner * 3], f[corner * 3 + 1], f[corner * 3 + 2]).endVertex();
            }
        }
        pose.popPose();
    }
}

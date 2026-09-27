package com.elderlexicon.mod.spell.function.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.function.ElementOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws an {@link ElementOrb}: fire and air as nested layers of a swirling texture of our own, each turning about its
 * own axis (fire lit by itself, air translucent), and earth and pressed water as the block they will become, turning
 * slowly. Vis, whose everyday form is experience, is drawn like the other orbs but after it: nested glowing cubes with
 * a bead-like face of our own, pulsing between green and yellow as experience does, bobbing gently. It grows with the
 * orb.
 */
public class ElementOrbRenderer extends EntityRenderer<ElementOrb> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElderLexicon.MODID, "textures/entity/element_orb.png");
    private static final ResourceLocation VIS_TEXTURE =
            new ResourceLocation(ElderLexicon.MODID, "textures/entity/vis_orb.png");
    /** The layers of an orb: how big each is, relative to the orb, and how fast it turns. */
    private static final float[] LAYER_SIZE = {0.45F, 0.75F, 1.0F};
    private static final float[] LAYER_SPIN = {9.0F, -6.0F, 4.0F};
    private static final float[] LAYER_ALPHA = {0.95F, 0.6F, 0.35F};

    public ElementOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ElementOrb orb, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float size = orb.size(partialTick);
        float age = orb.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0.0D, size * 0.5D, 0.0D);
        if (orb.look(partialTick) == ElementOrb.Look.LIGHT) {
            drawVis(size, age * orb.spin(partialTick), pose, buffers);
        } else if (orb.look(partialTick) == ElementOrb.Look.VOID) {
            // Earth pressed into a black hole: the End's starfield, the black beyond everything.
            BlackHoleRenderer.starfield(pose, buffers, size * 0.45F, age);
        } else if (orb.look(partialTick) == ElementOrb.Look.BLOCK) {
            drawBlock(orb.block(partialTick), size, age, pose, buffers, light);
        } else {
            drawLayers(orb, size, age * orb.spin(partialTick), partialTick, pose, buffers, light);
        }
        pose.popPose();
        super.render(orb, yaw, partialTick, pose, buffers, light);
    }

    /** Vis after experience: the nested cubes of an orb, lit by themselves, pulsing green to yellow, bobbing. */
    private static void drawVis(float size, float age, PoseStack pose, MultiBufferSource buffers) {
        float phase = age / 2.0F;
        float red = (Mth.sin(phase) + 1.0F) * 0.5F;
        float green = 1.0F;
        float blue = (Mth.sin(phase + 4.1887903F) + 1.0F) * 0.1F;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(VIS_TEXTURE));
        pose.pushPose();
        pose.translate(0.0D, Mth.sin(age * 0.2F) * 0.05D * size, 0.0D);
        for (int layer = 0; layer < LAYER_SIZE.length; layer++) {
            pose.pushPose();
            float spin = age * LAYER_SPIN[layer];
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.7F + layer * 30.0F));
            pose.mulPose(Axis.XP.rotationDegrees(spin * 0.4F));
            cube(pose, buffer, size * LAYER_SIZE[layer] * 0.5F, red, green, blue, LAYER_ALPHA[layer],
                    LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void drawBlock(BlockState block, float size, float age, PoseStack pose, MultiBufferSource buffers,
                                  int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 4.0F));
        pose.mulPose(Axis.XP.rotationDegrees(age * 2.5F));
        pose.scale(size, size, size);
        pose.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block, pose, buffers, light,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    private static void drawLayers(ElementOrb orb, float size, float age, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        ElementOrb.Look look = orb.look(partialTick);
        boolean glows = look == ElementOrb.Look.FIRE || look == ElementOrb.Look.LIGHT;
        if (look == ElementOrb.Look.VOID) {
            light = 0; // nothing lights a black hole
        }
        int color = orb.color(partialTick);
        float thickness = orb.thickness(partialTick);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        VertexConsumer buffer = buffers.getBuffer(glows ? RenderType.entityTranslucentEmissive(TEXTURE)
                : RenderType.entityTranslucent(TEXTURE));
        int lit = glows ? LightTexture.FULL_BRIGHT : light;
        for (int layer = 0; layer < LAYER_SIZE.length; layer++) {
            pose.pushPose();
            float spin = age * LAYER_SPIN[layer];
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.7F + layer * 30.0F));
            pose.mulPose(Axis.XP.rotationDegrees(spin * 0.4F));
            float half = size * LAYER_SIZE[layer] * 0.5F;
            cube(pose, buffer, half, red, green, blue, LAYER_ALPHA[layer] * thickness, lit);
            pose.popPose();
        }
    }

    /** A cube of half-size {@code half} centred on the pose, the texture on each face. */
    private static void cube(PoseStack pose, VertexConsumer buffer, float half, float red, float green, float blue,
                             float alpha, int light) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float h = half;
        // Each face: four corners (counter-clockwise seen from outside) and its normal.
        face(matrix, normal, buffer, -h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1, red, green, blue, alpha, light);
        face(matrix, normal, buffer, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1, red, green, blue, alpha, light);
        face(matrix, normal, buffer, h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0, red, green, blue, alpha, light);
        face(matrix, normal, buffer, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0, red, green, blue, alpha, light);
        face(matrix, normal, buffer, -h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0, red, green, blue, alpha, light);
        face(matrix, normal, buffer, -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0, red, green, blue, alpha, light);
    }

    private static void face(Matrix4f matrix, Matrix3f normal, VertexConsumer buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz, float red, float green, float blue, float alpha, int light) {
        vertex(matrix, normal, buffer, x1, y1, z1, 0.0F, 1.0F, nx, ny, nz, red, green, blue, alpha, light);
        vertex(matrix, normal, buffer, x2, y2, z2, 1.0F, 1.0F, nx, ny, nz, red, green, blue, alpha, light);
        vertex(matrix, normal, buffer, x3, y3, z3, 1.0F, 0.0F, nx, ny, nz, red, green, blue, alpha, light);
        vertex(matrix, normal, buffer, x4, y4, z4, 0.0F, 0.0F, nx, ny, nz, red, green, blue, alpha, light);
    }

    private static void vertex(Matrix4f matrix, Matrix3f normal, VertexConsumer buffer, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, float red, float green, float blue,
                               float alpha, int light) {
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(normal, nx, ny, nz).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ElementOrb orb) {
        return TEXTURE;
    }
}

package com.elderlexicon.mod.client;

import com.elderlexicon.mod.Config;
import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.item.LifeCompassSlices;
import com.elderlexicon.mod.vita.VitaImbalanceTier;
import com.elderlexicon.mod.vita.VitaSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.jetbrains.annotations.NotNull;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the pie slices + overlay for the Life Compass item.
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class LifeCompassRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/item/life_compass_base.png");
    private static final ResourceLocation BORDER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/item/life_compass_border.png");

    private static final int SLICE_SEGMENTS = 24;
    private static final float OVERFLOW_INNER_RADIUS = 0.52F;
    private static final float OVERFLOW_OUTER_RADIUS = 0.60F;
    private static final float GLOW_INNER_RADIUS = 0.47F;
    private static final float GLOW_OUTER_RADIUS = 0.70F;
    private static final float GLOW_ALPHA = 0.35F;

    public LifeCompassRenderer(BlockEntityRenderDispatcher dispatcher, net.minecraft.client.model.geom.EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack,
                             @NotNull ItemDisplayContext transformType,
                             @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource buffer,
                             int packedLight,
                             int packedOverlay) {
        poseStack.pushPose();
        // Scale down to fit within standard GUI quad
        poseStack.scale(1.0F, 1.0F, 1.0F);

        renderTexturedQuad(poseStack, buffer, BASE_TEXTURE, packedLight, packedOverlay);

        LifeCompassSlices slices = LifeCompassSlices.fromStack(stack);
        if (!slices.isEmpty()) {
            float startAngle = 0.0F;
            startAngle = drawSlice(poseStack, buffer, packedLight, packedOverlay, startAngle, slices.aquaRatio(), resolveAquaColor(slices.aqua()), VitaSystem.evaluateAquaTier(slices.aqua(), VitaImbalanceTier.BALANCED));
            startAngle = drawSlice(poseStack, buffer, packedLight, packedOverlay, startAngle, slices.auraRatio(), resolveAuraColor(slices.aura()), VitaSystem.evaluateAuraTier(slices.aura(), VitaImbalanceTier.BALANCED));
            startAngle = drawSlice(poseStack, buffer, packedLight, packedOverlay, startAngle, slices.firmoRatio(), resolveFirmoColor(slices.firmo()), VitaSystem.evaluateFirmoTier(slices.firmo(), VitaImbalanceTier.BALANCED));
            drawSlice(poseStack, buffer, packedLight, packedOverlay, startAngle, slices.igniRatio(), resolveIgniColor(slices.igni()), VitaSystem.evaluateIgniTier(slices.igni(), VitaImbalanceTier.BALANCED));

            if (Config.lifeCompassOverflowRing && slices.hasOverflow()) {
                float overflowStart = 0.0F;
                overflowStart = drawRingSlice(poseStack, buffer, packedLight, packedOverlay, overflowStart, slices.aquaOverflowRatio(), 0x669CD7FF);
                overflowStart = drawRingSlice(poseStack, buffer, packedLight, packedOverlay, overflowStart, slices.auraOverflowRatio(), 0xE6F08BFF);
                overflowStart = drawRingSlice(poseStack, buffer, packedLight, packedOverlay, overflowStart, slices.firmoOverflowRatio(), 0xB2FF8BFF);
                drawRingSlice(poseStack, buffer, packedLight, packedOverlay, overflowStart, slices.igniOverflowRatio(), 0xFFAB91FF);
            }
        }

        renderTexturedQuad(poseStack, buffer, BORDER_TEXTURE, packedLight, packedOverlay);

        poseStack.popPose();
    }

    private static float drawSlice(PoseStack poseStack,
                                   MultiBufferSource buffer,
                                   int packedLight,
                                   int packedOverlay,
                                   float startAngle,
                                   double ratio,
                                   int argbColor,
                                   VitaImbalanceTier tier) {
        if (ratio <= 0.0D) {
            return startAngle;
        }
        float sweep = (float) (ratio * Mth.TWO_PI);
        float endAngle = startAngle + sweep;

        VertexConsumer consumer = buffer.getBuffer(RenderType.text(BASE_TEXTURE));
        var pose = poseStack.last().pose();
        float center = 0.5F;
        float radius = 0.45F;
        int alpha = argbColor & 0xFF;
        int blue = (argbColor >> 8) & 0xFF;
        int green = (argbColor >> 16) & 0xFF;
        int red = (argbColor >> 24) & 0xFF;

        float current = startAngle;
        float step = sweep / SLICE_SEGMENTS;
        for (int i = 0; i < SLICE_SEGMENTS; i++) {
            float next = Math.min(endAngle, current + step);
            float x1 = center + radius * Mth.cos(current);
            float y1 = center + radius * Mth.sin(current);
            float x2 = center + radius * Mth.cos(next);
            float y2 = center + radius * Mth.sin(next);
            consumer.vertex(pose, center, center, 0.0F).color(red, green, blue, alpha).uv(0.5F, 0.5F).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, x1, y1, 0.0F).color(red, green, blue, alpha).uv(x1, y1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, x2, y2, 0.0F).color(red, green, blue, alpha).uv(x2, y2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            current = next;
            if (next >= endAngle) {
                break;
            }
        }
        if (tier == VitaImbalanceTier.SEVERELY_LOW || tier == VitaImbalanceTier.SEVERELY_HIGH) {
            drawGlow(poseStack, buffer, packedLight, packedOverlay, startAngle, sweep, argbColor);
        }
        return endAngle;
    }

    private static void drawGlow(PoseStack poseStack,
                                 MultiBufferSource buffer,
                                 int packedLight,
                                 int packedOverlay,
                                 float startAngle,
                                 float sweep,
                                 int argbColor) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.text(BASE_TEXTURE));
        var pose = poseStack.last().pose();
        float center = 0.5F;
        int alpha = (int) (GLOW_ALPHA * 255.0F);
        int blue = (argbColor >> 8) & 0xFF;
        int green = (argbColor >> 16) & 0xFF;
        int red = (argbColor >> 24) & 0xFF;

        float endAngle = startAngle + sweep;
        float current = startAngle;
        float step = sweep / SLICE_SEGMENTS;
        for (int i = 0; i < SLICE_SEGMENTS; i++) {
            float next = Math.min(endAngle, current + step);
            float innerX1 = center + GLOW_INNER_RADIUS * Mth.cos(current);
            float innerY1 = center + GLOW_INNER_RADIUS * Mth.sin(current);
            float outerX1 = center + GLOW_OUTER_RADIUS * Mth.cos(current);
            float outerY1 = center + GLOW_OUTER_RADIUS * Mth.sin(current);
            float innerX2 = center + GLOW_INNER_RADIUS * Mth.cos(next);
            float innerY2 = center + GLOW_INNER_RADIUS * Mth.sin(next);
            float outerX2 = center + GLOW_OUTER_RADIUS * Mth.cos(next);
            float outerY2 = center + GLOW_OUTER_RADIUS * Mth.sin(next);

            consumer.vertex(pose, innerX1, innerY1, 0.0F).color(red, green, blue, alpha).uv(innerX1, innerY1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, outerX1, outerY1, 0.0F).color(red, green, blue, alpha).uv(outerX1, outerY1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, outerX2, outerY2, 0.0F).color(red, green, blue, alpha).uv(outerX2, outerY2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();

            consumer.vertex(pose, innerX1, innerY1, 0.0F).color(red, green, blue, alpha).uv(innerX1, innerY1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, outerX2, outerY2, 0.0F).color(red, green, blue, alpha).uv(outerX2, outerY2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, innerX2, innerY2, 0.0F).color(red, green, blue, alpha).uv(innerX2, innerY2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();

            current = next;
            if (next >= endAngle) {
                break;
            }
        }
    }

    private static float drawRingSlice(PoseStack poseStack,
                                       MultiBufferSource buffer,
                                       int packedLight,
                                       int packedOverlay,
                                       float startAngle,
                                       double ratio,
                                       int argbColor) {
        if (ratio <= 0.0D) {
            return startAngle;
        }
        float sweep = (float) (ratio * Mth.TWO_PI);
        float endAngle = startAngle + sweep;
        VertexConsumer consumer = buffer.getBuffer(RenderType.text(BASE_TEXTURE));
        var pose = poseStack.last().pose();
        float center = 0.5F;
        int alpha = argbColor & 0xFF;
        int blue = (argbColor >> 8) & 0xFF;
        int green = (argbColor >> 16) & 0xFF;
        int red = (argbColor >> 24) & 0xFF;

        float current = startAngle;
        float step = sweep / SLICE_SEGMENTS;
        for (int i = 0; i < SLICE_SEGMENTS; i++) {
            float next = Math.min(endAngle, current + step);
            float innerX1 = center + OVERFLOW_INNER_RADIUS * Mth.cos(current);
            float innerY1 = center + OVERFLOW_INNER_RADIUS * Mth.sin(current);
            float outerX1 = center + OVERFLOW_OUTER_RADIUS * Mth.cos(current);
            float outerY1 = center + OVERFLOW_OUTER_RADIUS * Mth.sin(current);
            float innerX2 = center + OVERFLOW_INNER_RADIUS * Mth.cos(next);
            float innerY2 = center + OVERFLOW_INNER_RADIUS * Mth.sin(next);
            float outerX2 = center + OVERFLOW_OUTER_RADIUS * Mth.cos(next);
            float outerY2 = center + OVERFLOW_OUTER_RADIUS * Mth.sin(next);

            consumer.vertex(pose, innerX1, innerY1, 0.0F).color(red, green, blue, alpha).uv(innerX1, innerY1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, outerX1, outerY1, 0.0F).color(red, green, blue, alpha).uv(outerX1, outerY1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, outerX2, outerY2, 0.0F).color(red, green, blue, alpha).uv(outerX2, outerY2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();

            consumer.vertex(pose, innerX1, innerY1, 0.0F).color(red, green, blue, alpha).uv(innerX1, innerY1).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, outerX2, outerY2, 0.0F).color(red, green, blue, alpha).uv(outerX2, outerY2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
            consumer.vertex(pose, innerX2, innerY2, 0.0F).color(red, green, blue, alpha).uv(innerX2, innerY2).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();

            current = next;
            if (next >= endAngle) {
                break;
            }
        }
        return endAngle;
    }

    private static void renderTexturedQuad(PoseStack poseStack,
                                           MultiBufferSource buffer,
                                           ResourceLocation texture,
                                           int packedLight,
                                           int packedOverlay) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        var pose = poseStack.last().pose();
        consumer.vertex(pose, 0.0F, 1.0F, 0.0F).color(255, 255, 255, 255).uv(0.0F, 1.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, 1.0F, 1.0F, 0.0F).color(255, 255, 255, 255).uv(1.0F, 1.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, 1.0F, 0.0F, 0.0F).color(255, 255, 255, 255).uv(1.0F, 0.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, 0.0F, 0.0F, 0.0F).color(255, 255, 255, 255).uv(0.0F, 0.0F).overlayCoords(packedOverlay).uv2(packedLight).normal(0.0F, 0.0F, 1.0F).endVertex();
    }

    private static int resolveAquaColor(double aquaValue) {
        VitaImbalanceTier tier = VitaSystem.evaluateAquaTier(aquaValue, VitaImbalanceTier.BALANCED);
        return switch (tier) {
            case SEVERELY_LOW -> 0xFFDC6EFF;
            case SLIGHTLY_LOW -> 0xFFA2CEFF;
            case SLIGHTLY_HIGH -> 0xFF5FE8FF;
            case SEVERELY_HIGH -> 0xFF1BC8FF;
            default -> 0x4D8BFFFF;
        };
    }

    private static int resolveAuraColor(double auraValue) {
        VitaImbalanceTier tier = VitaSystem.evaluateAuraTier(auraValue, VitaImbalanceTier.BALANCED);
        return switch (tier) {
            case SEVERELY_LOW -> 0xFF6FB2FF;
            case SLIGHTLY_LOW -> 0xFF8FD4FF;
            case SLIGHTLY_HIGH -> 0xFFFF9AE0;
            case SEVERELY_HIGH -> 0xFFFF6BF1;
            default -> 0xFFF176FF;
        };
    }

    private static int resolveFirmoColor(double firmoValue) {
        VitaImbalanceTier tier = VitaSystem.evaluateFirmoTier(firmoValue, VitaImbalanceTier.BALANCED);
        return switch (tier) {
            case SEVERELY_LOW -> 0xFFDCE775;
            case SLIGHTLY_LOW -> 0xFFC5E1A5;
            case SLIGHTLY_HIGH -> 0xFF66BB6A;
            case SEVERELY_HIGH -> 0xFF2E7D32;
            default -> 0x81C784FF;
        };
    }

    private static int resolveIgniColor(double igniValue) {
        VitaImbalanceTier tier = VitaSystem.evaluateIgniTier(igniValue, VitaImbalanceTier.BALANCED);
        return switch (tier) {
            case SEVERELY_LOW -> 0xFF90CAF9;
            case SLIGHTLY_LOW -> 0xFF64B5F6;
            case SLIGHTLY_HIGH -> 0xFFFFB74D;
            case SEVERELY_HIGH -> 0xFFFF7043;
            default -> 0xFF7043FF;
        };
    }
}

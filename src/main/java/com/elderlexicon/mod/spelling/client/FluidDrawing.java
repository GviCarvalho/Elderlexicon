package com.elderlexicon.mod.spelling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Function;

/**
 * Draws a block of water or lava on its own, outside the chunk it belongs to (a revealed pool, an image of water): water
 * and lava have no block model, so their surface is drawn here from the still texture, tinted like the biome's water, on
 * every face that does not touch the same fluid.
 */
final class FluidDrawing {

    private static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation LAVA_STILL = new ResourceLocation("minecraft", "block/lava_still");

    private FluidDrawing() {
    }

    /**
     * Draws {@code fluid} in the unit cube at the pose's origin.
     *
     * @param fluidAt what fluid stands at a neighbouring place, so faces between two blocks of it are left out
     */
    static void draw(ClientLevel level, BlockPos pos, FluidState fluid, PoseStack poseStack, MultiBufferSource buffers,
                     int light, Function<BlockPos, FluidState> fluidAt) {
        boolean water = fluid.is(FluidTags.WATER);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(water ? WATER_STILL : LAVA_STILL);
        int tint = water ? BiomeColors.getAverageWaterColor(level, pos) : 0xFFFFFF;
        float red = (tint >> 16 & 0xFF) / 255.0F;
        float green = (tint >> 8 & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = water ? 0.8F : 1.0F;
        float top = fluidAt.apply(pos.above()).getType().isSame(fluid.getType()) ? 1.0F : fluid.getHeight(level, pos);
        if (top <= 0.0F) {
            top = 8.0F / 9.0F; // a source standing alone, as an image of water is
        }

        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (Direction face : Direction.values()) {
            if (fluidAt.apply(pos.relative(face)).getType().isSame(fluid.getType())) {
                continue;
            }
            float[][] corners = corners(face, top);
            float[][] uvs = {{sprite.getU0(), sprite.getV0()}, {sprite.getU0(), sprite.getV1()},
                    {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()}};
            for (int i = 0; i < 4; i++) {
                out.vertex(pose, corners[i][0], corners[i][1], corners[i][2])
                        .color(red, green, blue, alpha)
                        .uv(uvs[i][0], uvs[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(normal, face.getStepX(), face.getStepY(), face.getStepZ())
                        .endVertex();
            }
        }
    }

    /** The four corners of one face of a unit cube whose top is at {@code top}, counter-clockwise seen from outside. */
    private static float[][] corners(Direction face, float top) {
        return switch (face) {
            case UP -> new float[][]{{0, top, 0}, {0, top, 1}, {1, top, 1}, {1, top, 0}};
            case DOWN -> new float[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
            case NORTH -> new float[][]{{1, top, 0}, {1, 0, 0}, {0, 0, 0}, {0, top, 0}};
            case SOUTH -> new float[][]{{0, top, 1}, {0, 0, 1}, {1, 0, 1}, {1, top, 1}};
            case WEST -> new float[][]{{0, top, 0}, {0, 0, 0}, {0, 0, 1}, {0, top, 1}};
            case EAST -> new float[][]{{1, top, 1}, {1, 0, 1}, {1, 0, 0}, {1, top, 0}};
        };
    }
}

package com.elderlexicon.mod.spell.scene.client;

import com.elderlexicon.mod.spell.scene.ArcBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws an {@link ArcBoltEntity} the way vanilla draws lightning (nested translucent layers with
 * the lightning render type), but along the line from the entity to its target instead of
 * straight down.
 */
public class ArcBoltRenderer extends EntityRenderer<ArcBoltEntity> {

    /** Vanilla lightning colour. */
    private static final float RED = 0.45F;
    private static final float GREEN = 0.45F;
    private static final float BLUE = 0.5F;
    private static final float LAYER_ALPHA = 0.3F;
    private static final int LAYERS = 4;
    private static final float CORE_HALF_WIDTH = 0.02F;
    private static final float LAYER_GROWTH = 0.035F;
    private static final double SEGMENT_LENGTH = 1.2D;
    private static final double JITTER = 0.35D;

    public ArcBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(ArcBoltEntity bolt, Frustum frustum, double camX, double camY, double camZ) {
        // The entity's own box is tiny, but the bolt can be many blocks long: never cull it.
        return true;
    }

    @Override
    public void render(ArcBoltEntity bolt, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        Vec3 end = bolt.offsetToEnd();
        double length = end.length();
        if (length < 0.1D) {
            return;
        }
        float age = bolt.tickCount + partialTick;
        float fade = Mth.clamp(1.0F - age / ArcBoltEntity.LIFETIME_TICKS, 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }

        Vec3 direction = end.scale(1.0D / length);
        Vec3 helper = Math.abs(direction.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 sideA = direction.cross(helper).normalize();
        Vec3 sideB = direction.cross(sideA).normalize();

        Vec3[] path = zigzag(end, length, sideA, sideB, RandomSource.create(bolt.seed()));

        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        float alpha = LAYER_ALPHA * fade;
        for (int layer = 0; layer < LAYERS; layer++) {
            float half = CORE_HALF_WIDTH + layer * LAYER_GROWTH;
            for (int i = 0; i < path.length - 1; i++) {
                quadPair(consumer, matrix, path[i], path[i + 1], sideA, half, alpha);
                quadPair(consumer, matrix, path[i], path[i + 1], sideB, half, alpha);
            }
        }
    }

    /** Points from the origin to {@code end}, pushed sideways at random except at both ends. */
    private static Vec3[] zigzag(Vec3 end, double length, Vec3 sideA, Vec3 sideB, RandomSource random) {
        int segments = Math.max(2, (int) Math.ceil(length / SEGMENT_LENGTH));
        Vec3[] points = new Vec3[segments + 1];
        points[0] = Vec3.ZERO;
        points[segments] = end;
        for (int i = 1; i < segments; i++) {
            Vec3 onLine = end.scale((double) i / segments);
            double a = (random.nextDouble() - 0.5D) * 2.0D * JITTER;
            double b = (random.nextDouble() - 0.5D) * 2.0D * JITTER;
            points[i] = onLine.add(sideA.scale(a)).add(sideB.scale(b));
        }
        return points;
    }

    /** A flat strip along one leg, emitted facing both ways because the render type culls back faces. */
    private static void quadPair(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 side,
                                 float half, float alpha) {
        Vec3 offset = side.scale(half);
        Vec3 a = from.subtract(offset);
        Vec3 b = from.add(offset);
        Vec3 c = to.add(offset);
        Vec3 d = to.subtract(offset);
        vertex(consumer, matrix, a, alpha);
        vertex(consumer, matrix, b, alpha);
        vertex(consumer, matrix, c, alpha);
        vertex(consumer, matrix, d, alpha);
        vertex(consumer, matrix, d, alpha);
        vertex(consumer, matrix, c, alpha);
        vertex(consumer, matrix, b, alpha);
        vertex(consumer, matrix, a, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 point, float alpha) {
        consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(RED, GREEN, BLUE, alpha)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ArcBoltEntity bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

package com.elderlexicon.mod.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.item.ArcaneCompassItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the dial of an arcane compass (docs/bussola-arcana-design.md) on its glass, which the texture shows as the
 * game's compass does: a disc seen a little from above, its glass an ellipse. What the nuggets show is drawn in the
 * glass's own pixels, as the game's compass draws its needle:
 * <ul>
 *   <li>the images of sources make <b>one wheel</b>, shared among them as they are abundant: the more of an element,
 *   the more of the wheel it takes;</li>
 *   <li>a pushed nugget is a loose pebble in the glass, pressed against the edge toward where it is pushed. An iron one
 *   is light: it darts and bounces before it settles; a gold one is heavy, rolls slowly and lies where it lands;</li>
 *   <li>a pulled nugget is an arc around the edge, as long as the speed is of the scale's end.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class ArcaneCompassRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "textures/item/arcane_compass_white.png");
    /**
     * The glass of the texture, in its pixels: an ellipse around (8, 7) from the top left, six pixels to each side and
     * three up and down (the disc seen a little from above).
     */
    private static final float PIXEL_CENTER_X = 8.0F;
    private static final float PIXEL_CENTER_Y = 7.0F;
    private static final float PIXEL_RADIUS_X = 6.0F;
    private static final float PIXEL_RADIUS_Y = 3.0F;
    private static final float FACE = 0.5F;
    private static final int[][] GLASS = glassPixels();
    private static final int[][] EDGE = edgePixels();

    /** Where each pebble lies now, and how fast it rolls: it has weight, and follows its push with it. */
    private static final Map<String, float[]> PEBBLES = new HashMap<>();
    /** When each pebble last moved, in nanoseconds. */
    private static final Map<String, Long> PEBBLE_TIMES = new HashMap<>();
    /** How far out a pebble's middle goes in the unit disc: the glass's edge less the pebble. */
    private static final float PEBBLE_REACH = 0.85F;
    /** The step its motion is worked out in, in seconds, so it never passes through the edge. */
    private static final float PEBBLE_STEP = 0.005F;
    /** How close two pebbles' middles come in the unit disc before they touch. */
    private static final float PEBBLE_SIZE = 0.3F;

    public ArcaneCompassRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource buffer, int light, int overlay) {
        if (!(stack.getItem() instanceof ArcaneCompassItem dial)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean gold = dial.metal() == ArcaneCompassItem.Metal.GOLD;
        ResourceLocation face = ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID,
                "textures/item/" + (gold ? "gold" : "iron") + "_arcane_compass.png");
        Matrix4f pose = poseStack.last().pose();
        quad(buffer.getBuffer(RenderType.entityCutoutNoCull(face)), pose, light, overlay);

        List<String> nuggets = ArcaneCompassItem.nuggets(stack);
        if (nuggets.isEmpty() || minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        float time = now + minecraft.getFrameTime();
        float viewerYaw = minecraft.player == null ? 0.0F : minecraft.player.getYRot();
        VertexConsumer paint = buffer.getBuffer(RenderType.entityTranslucent(WHITE));

        // Each nugget moves as its own metal does, whatever the case is made of.
        if (PEBBLES.size() > 64) {
            PEBBLES.clear();
            PEBBLE_TIMES.clear();
        }
        List<ArcaneCompassItem.Reading> images = new ArrayList<>();
        List<Object[]> speeds = new ArrayList<>();
        List<String> pebbles = new ArrayList<>();
        List<ArcaneCompassItem.Metal> pebbleMetals = new ArrayList<>();
        for (int slot = 0; slot < nuggets.size(); slot++) {
            ArcaneCompassItem.Reading reading = ArcaneCompassItem.reading(stack, slot, now);
            String key = nuggets.get(slot);
            ArcaneCompassItem.Metal metal = ArcaneCompassItem.nuggetMetal(stack, slot);
            if (reading == null) {
                // A pebble once pushed stays loose in the glass, and rolls to a stop.
                if (PEBBLES.containsKey(key)) {
                    roll(key, Float.NaN, metal);
                    pebbles.add(key);
                    pebbleMetals.add(metal);
                }
                continue;
            }
            switch (reading.kind()) {
                case IMAGE -> images.add(reading);
                case SPEED -> speeds.add(new Object[] {reading, metal});
                case NEEDLE -> {
                    roll(key, (float) reading.yaw(), metal);
                    pebbles.add(key);
                    pebbleMetals.add(metal);
                }
            }
            if (reading.kind() != ArcaneCompassItem.Reading.Kind.NEEDLE) {
                PEBBLES.remove(key);
                PEBBLE_TIMES.remove(key);
            }
        }
        wheel(paint, pose, light, overlay, images, time);
        // The longest arc first, so a shorter one shows over it.
        speeds.sort(java.util.Comparator.comparingDouble(entry -> -share((ArcaneCompassItem.Reading) entry[0],
                (ArcaneCompassItem.Metal) entry[1])));
        for (Object[] entry : speeds) {
            speed(paint, pose, light, overlay, (ArcaneCompassItem.Reading) entry[0], (ArcaneCompassItem.Metal) entry[1],
                    time);
        }
        knock(pebbles, pebbleMetals);
        for (int i = 0; i < pebbles.size(); i++) {
            drawPebble(paint, pose, light, overlay, onDial(pebbles.get(i), viewerYaw), pebbleMetals.get(i));
        }
    }

    /**
     * Pebbles in the same glass knock into each other: pushed apart, and what they carried toward each other shared as
     * their masses say (a gold one shoves an iron one aside, and is hardly moved).
     */
    private static void knock(List<String> keys, List<ArcaneCompassItem.Metal> metals) {
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                float[] a = PEBBLES.get(keys.get(i));
                float[] b = PEBBLES.get(keys.get(j));
                float dx = b[0] - a[0];
                float dz = b[1] - a[1];
                float distance = Mth.sqrt(dx * dx + dz * dz);
                if (distance >= PEBBLE_SIZE) {
                    continue;
                }
                float nx = distance < 1.0E-4F ? 1.0F : dx / distance;
                float nz = distance < 1.0E-4F ? 0.0F : dz / distance;
                float ma = (float) metals.get(i).nuggetMass();
                float mb = (float) metals.get(j).nuggetMass();
                float overlap = PEBBLE_SIZE - distance;
                a[0] -= nx * overlap * mb / (ma + mb);
                a[1] -= nz * overlap * mb / (ma + mb);
                b[0] += nx * overlap * ma / (ma + mb);
                b[1] += nz * overlap * ma / (ma + mb);
                float closing = (a[2] - b[2]) * nx + (a[3] - b[3]) * nz;
                if (closing > 0.0F) {
                    // An inelastic knock: metal on metal, a little bounce.
                    float impulse = (1.0F + 0.3F) * closing / (1.0F / ma + 1.0F / mb);
                    a[2] -= impulse / ma * nx;
                    a[3] -= impulse / ma * nz;
                    b[2] += impulse / mb * nx;
                    b[3] += impulse / mb * nz;
                }
            }
        }
    }

    // ------------------------------------------------------------------ what the nuggets show

    /**
     * The wheel of the images, in the glass's own pixels: each element takes of them the share it has of all the images
     * shown (at least one pixel, if it is there at all), in order from the top, clockwise. Fire flickers.
     */
    private static void wheel(VertexConsumer paint, Matrix4f pose, int light, int overlay,
                              List<ArcaneCompassItem.Reading> images, float time) {
        double total = 0.0D;
        for (ArcaneCompassItem.Reading image : images) {
            total += Math.max(0.0D, image.amount());
        }
        if (total <= 1.0E-6D) {
            return;
        }
        int pixels = GLASS.length;
        int[] counts = new int[images.size()];
        int given = 0;
        int largest = 0;
        for (int i = 0; i < images.size(); i++) {
            double amount = Math.max(0.0D, images.get(i).amount());
            counts[i] = amount <= 1.0E-6D ? 0 : Math.max(1, (int) Math.round(amount / total * pixels));
            given += counts[i];
            if (amount > images.get(largest).amount()) {
                largest = i;
            }
        }
        // Rounding may give a pixel too many or too few: the most abundant takes the difference.
        counts[largest] = Math.max(1, counts[largest] + pixels - given);
        int pixel = 0;
        boolean bright = Mth.sin(time * 1.9F) + 0.5F * Mth.sin(time * 3.3F) > 0.4F;
        for (int i = 0; i < images.size(); i++) {
            String element = images.get(i).element() == null ? "vis" : images.get(i).element();
            int color = "igni".equals(element) && bright ? 0xFFFF9628 : colorOf(element);
            for (int n = 0; n < counts[i] && pixel < pixels; n++, pixel++) {
                pixel(paint, pose, light, overlay, GLASS[pixel], color, 0.002F);
            }
        }
    }

    /** How fast a pulled nugget strains: the glass's edge lit from the top, clockwise, as far as the speed is of the scale. */
    private static void speed(VertexConsumer paint, Matrix4f pose, int light, int overlay,
                              ArcaneCompassItem.Reading reading, ArcaneCompassItem.Metal metal, float time) {
        float share = share(reading, metal);
        // A light needle trembles at what it holds back; a heavy one does not.
        if (metal == ArcaneCompassItem.Metal.IRON) {
            share = Math.min(1.0F, share + 0.05F * share * Mth.sin(time * 4.0F));
        }
        int lit = Math.round(share * EDGE.length);
        for (int i = 0; i < lit; i++) {
            pixel(paint, pose, light, overlay, EDGE[i], metal == ArcaneCompassItem.Metal.GOLD ? 0xFFFAD64A : 0xFFF0F0F0,
                    0.004F);
        }
    }

    /** How much of its scale a pulled nugget's speed is. */
    private static float share(ArcaneCompassItem.Reading reading, ArcaneCompassItem.Metal metal) {
        return (float) Math.min(1.0D, reading.speed() / metal.fullSpeed());
    }

    /**
     * The pebble of a pushed nugget: a loose weight in the glass, kept in the world's frame (a ball in a bowl stays put
     * when the bowl turns), pushed toward where its push points, against the glass's edge. Iron is light: it darts,
     * knocks and bounces before it settles. Gold is heavy: it rolls slowly and lies where it lands. Without a push, it
     * rolls to a stop where it was.
     *
     * @param push the yaw it is pushed toward, in degrees, or NaN for no push
     */
    private static void roll(String nugget, float push, ArcaneCompassItem.Metal metal) {
        // {x, z, speed x, speed z}: x and z in the world's frame, as a yaw sees them; and when it last moved.
        float[] stone = PEBBLES.computeIfAbsent(nugget, k -> new float[4]);
        long nanos = System.nanoTime();
        Long last = PEBBLE_TIMES.put(nugget, nanos);
        float dt = last == null ? 0.0F : Math.min(0.1F, Math.max(0.0F, (nanos - last) / 1.0E9F));
        boolean iron = metal == ArcaneCompassItem.Metal.IRON;
        boolean pushed = !Float.isNaN(push);
        float force = !pushed ? 0.0F : iron ? 45.0F : 16.0F;
        float drag = !pushed ? 4.0F : iron ? 1.2F : 3.0F;
        float bounce = iron ? 0.5F : 0.12F;
        float fx = pushed ? Mth.sin(push * Mth.DEG_TO_RAD) * force : 0.0F;
        float fz = pushed ? Mth.cos(push * Mth.DEG_TO_RAD) * force : 0.0F;
        for (float left = dt; left > 0.0F; left -= PEBBLE_STEP) {
            float step = Math.min(PEBBLE_STEP, left);
            stone[2] += (fx - drag * stone[2]) * step;
            stone[3] += (fz - drag * stone[3]) * step;
            stone[0] += stone[2] * step;
            stone[1] += stone[3] * step;
            float r = Mth.sqrt(stone[0] * stone[0] + stone[1] * stone[1]);
            if (r > PEBBLE_REACH) {
                // It knocks on the glass's edge: back inside, and what went outward comes back as a bounce.
                float nx = stone[0] / r;
                float nz = stone[1] / r;
                stone[0] = nx * PEBBLE_REACH;
                stone[1] = nz * PEBBLE_REACH;
                float out = stone[2] * nx + stone[3] * nz;
                if (out > 0.0F) {
                    stone[2] -= (1.0F + bounce) * out * nx;
                    stone[3] -= (1.0F + bounce) * out * nz;
                }
            }
        }
    }

    /**
     * Where a pebble is on the dial, {x, y} in the glass's unit disc (y toward the far side). Turned into the dial's
     * frame: the dial turns with the one who holds it, the pebble does not.
     */
    private static float[] onDial(String nugget, float viewerYaw) {
        float[] stone = PEBBLES.get(nugget);
        // Turned into the dial's frame: the dial turns with the one who holds it, the pebble does not.
        float v = viewerYaw * Mth.DEG_TO_RAD;
        float x = stone[0] * Mth.cos(v) - stone[1] * Mth.sin(v);
        float y = stone[1] * Mth.cos(v) + stone[0] * Mth.sin(v);
        return new float[] {x, y};
    }

    /** The pebble in the glass's pixels: the nugget's color, a little shadow under it. */
    private static void drawPebble(VertexConsumer paint, Matrix4f pose, int light, int overlay, float[] at,
                                   ArcaneCompassItem.Metal metal) {
        int[] spot = glassPixelAt(at[0], at[1]);
        int[] below = {spot[0], spot[1] + 1};
        if (isGlass(below)) {
            pixel(paint, pose, light, overlay, below, 0x90000000, 0.005F);
        }
        pixel(paint, pose, light, overlay, spot,
                metal == ArcaneCompassItem.Metal.GOLD ? 0xFFFAD64A : 0xFFD8D8D8, 0.006F);
    }

    /** The glass pixel at a point of the unit disc, drawn inward until it falls on the glass. */
    private static int[] glassPixelAt(float x, float y) {
        for (float shrink = 1.0F; shrink > 0.0F; shrink -= 0.05F) {
            int[] spot = {Mth.floor(PIXEL_CENTER_X + x * shrink * PIXEL_RADIUS_X),
                    Mth.floor(PIXEL_CENTER_Y - y * shrink * PIXEL_RADIUS_Y)};
            if (isGlass(spot)) {
                return spot;
            }
        }
        return new int[] {(int) PIXEL_CENTER_X, (int) PIXEL_CENTER_Y};
    }

    private static boolean isGlass(int[] at) {
        for (int[] glass : GLASS) {
            if (glass[0] == at[0] && glass[1] == at[1]) {
                return true;
            }
        }
        return false;
    }

    private static int colorOf(String element) {
        return switch (element) {
            case "igni" -> 0xFFFF6A2A;
            case "aqua" -> 0xFF3F76E4;
            case "aura" -> 0xFFE6E6D7;
            case "firmo" -> 0xFF7D603A;
            default -> 0xFFAA64E6;
        };
    }

    // ------------------------------------------------------------------ the glass, in pixels

    /** One pixel of the texture, {x, y} from its top left, drawn in {@code color} just over it. */
    private static void pixel(VertexConsumer paint, Matrix4f pose, int light, int overlay, int[] at, int color,
                              float lift) {
        float x0 = at[0] / 16.0F;
        float x1 = (at[0] + 1) / 16.0F;
        float y0 = 1.0F - (at[1] + 1) / 16.0F;
        float y1 = 1.0F - at[1] / 16.0F;
        float z = FACE + lift;
        vertex(paint, pose, x0, y0, z, color, light, overlay);
        vertex(paint, pose, x1, y0, z, color, light, overlay);
        vertex(paint, pose, x1, y1, z, color, light, overlay);
        vertex(paint, pose, x0, y1, z, color, light, overlay);
    }

    /** The angle of a pixel of the glass, seen in its perspective: 0 at the top (the far side), growing clockwise. */
    private static float angleOf(int[] at) {
        float dx = (at[0] + 0.5F - PIXEL_CENTER_X) / PIXEL_RADIUS_X;
        float dy = (PIXEL_CENTER_Y - (at[1] + 0.5F)) / PIXEL_RADIUS_Y;
        float angle = (float) Math.atan2(dx, dy);
        return angle < 0.0F ? angle + Mth.TWO_PI : angle;
    }

    private static float radiusOf(int[] at) {
        float dx = (at[0] + 0.5F - PIXEL_CENTER_X) / PIXEL_RADIUS_X;
        float dy = (PIXEL_CENTER_Y - (at[1] + 0.5F)) / PIXEL_RADIUS_Y;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    /** The pixels of the glass in the texture (the dark ones), in order from the top, clockwise. */
    private static int[][] glassPixels() {
        // Row, first and last column of the glass on it.
        int[][] rows = {{4, 5, 10}, {5, 3, 12}, {6, 2, 13}, {7, 2, 13}, {8, 3, 12}, {9, 5, 10}};
        List<int[]> pixels = new ArrayList<>();
        for (int[] row : rows) {
            for (int x = row[1]; x <= row[2]; x++) {
                pixels.add(new int[] {x, row[0]});
            }
        }
        pixels.sort(java.util.Comparator.comparingDouble(ArcaneCompassRenderer::angleOf));
        return pixels.toArray(new int[0][]);
    }

    /** The pixels at the edge of the glass, in order from the top, clockwise. */
    private static int[][] edgePixels() {
        List<int[]> edge = new ArrayList<>();
        for (int[] at : glassPixels()) {
            if (radiusOf(at) > 0.62F) {
                edge.add(at);
            }
        }
        return edge.toArray(new int[0][]);
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, int light, int overlay) {
        consumer.vertex(pose, 0.0F, 0.0F, FACE).color(255, 255, 255, 255).uv(0.0F, 1.0F).overlayCoords(overlay)
                .uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, 1.0F, 0.0F, FACE).color(255, 255, 255, 255).uv(1.0F, 1.0F).overlayCoords(overlay)
                .uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, 1.0F, 1.0F, FACE).color(255, 255, 255, 255).uv(1.0F, 0.0F).overlayCoords(overlay)
                .uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(pose, 0.0F, 1.0F, FACE).color(255, 255, 255, 255).uv(0.0F, 0.0F).overlayCoords(overlay)
                .uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int argb, int light,
                               int overlay) {
        consumer.vertex(pose, x, y, z).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .uv(0.5F, 0.5F).overlayCoords(overlay).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
    }
}

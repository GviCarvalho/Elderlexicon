package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.circle.CircleShape;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.inscription.ClientInscriptions;
import com.elderlexicon.mod.spelling.inscription.Inscription;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The circles as they are being laid (docs/circulos-design.md), drawn as magic circles: the client reads the scrolls on
 * the blocks and the runes written on faces round the player the way the spirit will ({@link CircleShape}); each ring
 * becomes a band between two lines of light, round (or oval, for a long circle), drawn in pixels on the grid of the
 * game's textures (a sixteenth of a block), with the runes of each of its pieces written along it, near where the piece
 * lies, their tops outward; the heart's runes are written in the middle. While a circle shows, the runes on its pieces
 * themselves are hidden (the scrolls show blank), so only the circle's are read. Shown while the player holds a scroll,
 * a quill or the grimoire, or stands near a piece.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class ClientCircles {

    private static final Style SGA = Style.EMPTY.withFont(new ResourceLocation("minecraft", "alt"));
    /** How far around the player circles are looked for, in blocks. */
    private static final double RANGE = 24.0D;
    /** Near a piece this close, its circle shows even with nothing in hand. */
    private static final double NEAR = 6.0D;
    /** Font pixels to a block, for the runes along a band (smaller when a ring has more to say than room). */
    private static final float GLYPH = 1.0F / 40.0F;
    /** The size of a pixel of the circle: a texel of the game's blocks. */
    private static final double PIXEL = 1.0D / 16.0D;
    private static final int LIGHT_COLOR = 0xD8BCFF;
    private static final int INK = 0xFF1A1420;

    private record Piece(BlockPos support, Direction face, Vec3 at, String text) {
    }

    /**
     * A ring to draw: the circle's middle, the ring's two half-widths across the plane, its pieces in order round it
     * with their angles, and whether it is a heart of a single piece (written in the middle, with no band).
     */
    private record Ring(Vec3 center, double a, double b, Direction face, List<Piece> pieces, List<Double> angles,
                        boolean alone, List<Vec3> pixels) {
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Vec3> PIECES = new ArrayList<>();
    /** The pieces of the circles on show, by block and face: their own runes are not drawn. */
    private static final java.util.Set<Long> HIDDEN = new java.util.HashSet<>();
    private static boolean visible;

    /** Whether the runes on this block face are hidden because a circle they are part of is on show. */
    public static boolean hides(BlockPos support, Direction face) {
        return visible && HIDDEN.contains(Inscription.key(support, face));
    }

    private ClientCircles() {
    }

    // ------------------------------------------------------------------ reading the circles

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.player == null
                || minecraft.level.getGameTime() % 10 != 0) {
            return;
        }
        RINGS.clear();
        PIECES.clear();
        HIDDEN.clear();
        Map<Long, List<Piece>> byPlane = new HashMap<>();
        for (Piece piece : pieces(minecraft.level, minecraft.player)) {
            PIECES.add(piece.at());
            long plane = (long) piece.face().get3DDataValue() << 32 | (plane(piece.support(), piece.face()) & 0xFFFFFFFFL);
            byPlane.computeIfAbsent(plane, key -> new ArrayList<>()).add(piece);
        }
        for (List<Piece> plane : byPlane.values()) {
            List<CircleShape.Spot> spots = new ArrayList<>();
            plane.forEach(piece -> spots.add(spot(piece.support(), piece.face())));
            for (List<Integer> group : CircleShape.groups(spots)) {
                if (group.size() >= 2) {
                    addCircle(plane, spots, group);
                    group.forEach(index -> HIDDEN.add(Inscription.key(plane.get(index).support(), plane.get(index).face())));
                }
            }
        }
        visible = shown(minecraft.player);
    }

    private static void addCircle(List<Piece> plane, List<CircleShape.Spot> spots, List<Integer> group) {
        List<CircleShape.Spot> groupSpots = new ArrayList<>();
        Vec3 sum = Vec3.ZERO;
        for (int index : group) {
            groupSpots.add(spots.get(index));
            sum = sum.add(plane.get(index).at());
        }
        Vec3 center = sum.scale(1.0D / group.size());
        Direction face = plane.get(group.get(0)).face();
        Vec3[] across = across(face);
        List<List<Integer>> rings = CircleShape.rings(groupSpots);
        for (List<Integer> ring : rings) {
            List<Piece> pieces = new ArrayList<>();
            double a = 0.0D;
            double b = 0.0D;
            for (int index : ring) {
                Piece piece = plane.get(group.get(index));
                pieces.add(piece);
                Vec3 offset = piece.at().subtract(center);
                a = Math.max(a, Math.abs(offset.dot(across[0])));
                b = Math.max(b, Math.abs(offset.dot(across[1])));
            }
            boolean alone = pieces.size() == 1 && a < 0.6D && b < 0.6D;
            // A ring stretched along one way only (a line of pieces) is drawn round.
            if (a < 0.3D) {
                a = b;
            }
            if (b < 0.3D) {
                b = a;
            }
            double ra = Math.max(a, 0.5D);
            double rb = Math.max(b, 0.5D);
            List<Double> angles = new ArrayList<>();
            List<Piece> sorted = new ArrayList<>(pieces);
            sorted.sort(Comparator.comparingDouble(piece -> angleOf(piece.at().subtract(center), across, ra, rb)));
            sorted.forEach(piece -> angles.add(angleOf(piece.at().subtract(center), across, ra, rb)));
            Ring drawn = new Ring(center, ra, rb, face, sorted, angles, alone, new ArrayList<>());
            if (!alone) {
                double half = 5.0D * glyphScale(Minecraft.getInstance().font, drawn) + 0.1D;
                drawn.pixels().addAll(pixelsOf(drawn, -half));
                drawn.pixels().addAll(pixelsOf(drawn, half));
            }
            RINGS.add(drawn);
        }
    }

    private static double angleOf(Vec3 offset, Vec3[] across, double a, double b) {
        return Math.atan2(offset.dot(across[1]) / b, offset.dot(across[0]) / a);
    }

    /** The pieces round the player: scrolls laid on blocks with a page, and faces with runes written on them. */
    private static List<Piece> pieces(ClientLevel level, LocalPlayer player) {
        List<Piece> pieces = new ArrayList<>();
        for (PlacedScrollEntity scroll : level.getEntitiesOfClass(PlacedScrollEntity.class,
                player.getBoundingBox().inflate(RANGE), PlacedScrollEntity::isAlive)) {
            CompoundTag tag = scroll.getScroll().getTag();
            String text = tag == null ? "" : tag.getString("DetachedPageText");
            if (text.isBlank()) {
                continue;
            }
            Direction face = scroll.getFace();
            // The scroll lies half a block out from the middle of the block it rests on.
            Vec3 support = scroll.position().subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(0.505D));
            pieces.add(new Piece(BlockPos.containing(support), face, scroll.position(), text));
        }
        for (Inscription written : ClientInscriptions.all()) {
            Vec3 at = Vec3.atCenterOf(written.pos()).add(Vec3.atLowerCornerOf(written.face().getNormal()).scale(0.5D));
            if (at.distanceToSqr(player.position()) <= RANGE * RANGE && !level.getBlockState(written.pos()).isAir()) {
                pieces.add(new Piece(written.pos(), written.face(), at, written.text()));
            }
        }
        return pieces;
    }

    /** The two directions along a face: the page's right and its down, as runes written on that face read. */
    private static Vec3[] across(Direction face) {
        return switch (face) {
            case UP -> new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
            case DOWN -> new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, -1)};
            case SOUTH -> new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, -1, 0)};
            case NORTH -> new Vec3[]{new Vec3(-1, 0, 0), new Vec3(0, -1, 0)};
            case EAST -> new Vec3[]{new Vec3(0, 0, -1), new Vec3(0, -1, 0)};
            case WEST -> new Vec3[]{new Vec3(0, 0, 1), new Vec3(0, -1, 0)};
        };
    }

    private static CircleShape.Spot spot(BlockPos pos, Direction face) {
        return switch (face.getAxis()) {
            case Y -> new CircleShape.Spot(pos.getX(), pos.getZ());
            case Z -> new CircleShape.Spot(pos.getX(), pos.getY());
            case X -> new CircleShape.Spot(pos.getZ(), pos.getY());
        };
    }

    private static int plane(BlockPos pos, Direction face) {
        return switch (face.getAxis()) {
            case Y -> pos.getY();
            case Z -> pos.getZ();
            case X -> pos.getX();
        };
    }

    private static boolean shown(LocalPlayer player) {
        for (ItemStack held : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof SpellScrollItem || held.getItem() instanceof GrimoireItem
                    || held.is(Items.FEATHER)) {
                return true;
            }
        }
        for (Vec3 piece : PIECES) {
            if (piece.distanceToSqr(player.position()) <= NEAR * NEAR) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing them

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || RINGS.isEmpty() || minecraft.player == null
                || minecraft.level == null || !shown(minecraft.player)) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);
        float glow = 0.75F + 0.2F * Mth.sin((minecraft.level.getGameTime() + event.getPartialTick()) * 0.12F);
        VertexConsumer light = buffers.getBuffer(RenderType.lightning());
        for (Ring ring : RINGS) {
            if (!ring.alone()) {
                pixels(light, pose.last().pose(), ring, glow);
            }
        }
        buffers.endBatch(RenderType.lightning());
        for (Ring ring : RINGS) {
            if (ring.alone()) {
                writeHeart(minecraft.font, buffers, pose, ring);
            } else {
                writeAlong(minecraft.font, buffers, pose, ring);
            }
        }
        buffers.endBatch();
        pose.popPose();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static Vec3 normalOf(Direction face) {
        return Vec3.atLowerCornerOf(face.getNormal());
    }

    /** A point on the ring's oval at {@code angle}, {@code grow} blocks out from it, a little off the face. */
    private static Vec3 pointAt(Ring ring, double angle, double grow) {
        Vec3[] across = across(ring.face());
        return ring.center().add(across[0].scale((ring.a() + grow) * Math.cos(angle)))
                .add(across[1].scale((ring.b() + grow) * Math.sin(angle)))
                .add(normalOf(ring.face()).scale(0.025D));
    }

    /**
     * The pixels of a line round the ring's oval, {@code grow} blocks out from it: the oval traced on the grid of the
     * game's texels, one pixel wide, so it is drawn like the blocks are.
     */
    private static List<Vec3> pixelsOf(Ring ring, double grow) {
        Vec3[] across = across(ring.face());
        Vec3 normal = normalOf(ring.face());
        double lift = ring.center().dot(normal) + 0.025D;
        double around = Math.PI * 2.0D * Math.max(ring.a(), ring.b()) + grow;
        int steps = (int) Math.ceil(around / PIXEL * 3.0D);
        java.util.Set<Long> cells = new java.util.LinkedHashSet<>();
        List<Vec3> pixels = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            Vec3 point = pointAt(ring, Math.PI * 2.0D * i / steps, grow);
            long u = (long) Math.floor(point.dot(across[0]) / PIXEL);
            long v = (long) Math.floor(point.dot(across[1]) / PIXEL);
            if (cells.add(u * 4_000_000L + v)) {
                pixels.add(across[0].scale(u * PIXEL).add(across[1].scale(v * PIXEL)).add(normal.scale(lift)));
            }
        }
        return pixels;
    }

    /** The ring's pixels of light, each a square texel on the plane, seen from either side. */
    private static void pixels(VertexConsumer buffer, Matrix4f matrix, Ring ring, float alpha) {
        float r = ((LIGHT_COLOR >> 16) & 0xFF) / 255.0F;
        float g = ((LIGHT_COLOR >> 8) & 0xFF) / 255.0F;
        float b = (LIGHT_COLOR & 0xFF) / 255.0F;
        Vec3[] across = across(ring.face());
        Vec3 du = across[0].scale(PIXEL);
        Vec3 dv = across[1].scale(PIXEL);
        for (Vec3 corner : ring.pixels()) {
            Vec3[] square = {corner, corner.add(du), corner.add(du).add(dv), corner.add(dv)};
            for (int side = 0; side < 2; side++) {
                for (int i = 0; i < 4; i++) {
                    Vec3 v = square[side == 0 ? i : 3 - i];
                    buffer.vertex(matrix, (float) v.x, (float) v.y, (float) v.z).color(r, g, b, alpha).endVertex();
                }
            }
        }
    }

    /** What a piece says along its ring: its page, line after line. */
    private static String words(Piece piece) {
        return String.join("   ", piece.text().split("\n")).trim();
    }

    /** How big the runes of a ring are: all of them must fit round it. */
    private static float glyphScale(Font font, Ring ring) {
        int width = 0;
        for (Piece piece : ring.pieces()) {
            width += font.width(Component.literal(words(piece)).withStyle(SGA)) + 12;
        }
        double around = Math.PI * (ring.a() + ring.b());
        return (float) Math.min(GLYPH, around / Math.max(1, width));
    }

    /** Each piece's runes written along the ring, centred on where it lies, their tops outward. */
    private static void writeAlong(Font font, MultiBufferSource buffers, PoseStack pose, Ring ring) {
        float scale = glyphScale(font, ring);
        double radius = (ring.a() + ring.b()) / 2.0D;
        Vec3 normal = normalOf(ring.face());
        for (int p = 0; p < ring.pieces().size(); p++) {
            String text = words(ring.pieces().get(p));
            int total = font.width(Component.literal(text).withStyle(SGA));
            // Along the oval, one font pixel is this much angle.
            double perPixel = scale / radius;
            double angle = ring.angles().get(p) - total * perPixel / 2.0D;
            for (int i = 0; i < text.length(); i++) {
                Component glyph = Component.literal(String.valueOf(text.charAt(i))).withStyle(SGA);
                int width = font.width(glyph);
                double at = angle + width * perPixel / 2.0D;
                Vec3 point = pointAt(ring, at, 0.0D);
                Vec3 outward = point.subtract(ring.center());
                outward = outward.subtract(normal.scale(outward.dot(normal))).normalize();
                Vec3 right = outward.cross(normal);
                Vec3 ahead = pointAt(ring, at + 0.01D, 0.0D).subtract(point);
                if (right.dot(ahead) < 0.0D) {
                    // This face's sides run the other way: write the other way round the ring.
                    right = right.scale(-1.0D);
                    outward = outward.scale(-1.0D);
                }
                drawGlyph(font, buffers, pose, glyph, point, right, outward.scale(-1.0D), scale);
                angle += width * perPixel;
            }
        }
    }

    /** The heart's runes, written flat in the middle, line under line. */
    private static void writeHeart(Font font, MultiBufferSource buffers, PoseStack pose, Ring ring) {
        Vec3[] across = across(ring.face());
        String[] lines = ring.pieces().get(0).text().split("\n");
        float scale = GLYPH;
        for (int i = 0; i < lines.length; i++) {
            Component line = Component.literal(lines[i]).withStyle(SGA);
            float width = font.width(line) * scale;
            Vec3 point = ring.pieces().get(0).at().add(normalOf(ring.face()).scale(0.025D))
                    .add(across[1].scale((i - (lines.length - 1) / 2.0D) * 10 * scale))
                    .subtract(across[0].scale(width / 2.0D));
            drawText(font, buffers, pose, line, point, across[0], across[1], scale, false);
        }
    }

    private static void drawGlyph(Font font, MultiBufferSource buffers, PoseStack pose, Component glyph, Vec3 center,
                                  Vec3 right, Vec3 down, float scale) {
        Vec3 corner = center.subtract(right.scale(font.width(glyph) * scale / 2.0D)).subtract(down.scale(4 * scale));
        drawText(font, buffers, pose, glyph, corner, right, down, scale, true);
    }

    /** Text with its top-left at {@code corner}, running along {@code right}, its lines going {@code down}. */
    private static void drawText(Font font, MultiBufferSource buffers, PoseStack pose, Component text, Vec3 corner,
                                 Vec3 right, Vec3 down, float scale, boolean along) {
        Vec3 normal = right.cross(down).scale(-1.0D);
        Matrix4f basis = new Matrix4f(
                (float) right.x * scale, (float) right.y * scale, (float) right.z * scale, 0.0F,
                (float) down.x * scale, (float) down.y * scale, (float) down.z * scale, 0.0F,
                (float) normal.x * scale, (float) normal.y * scale, (float) normal.z * scale, 0.0F,
                (float) corner.x, (float) corner.y, (float) corner.z, 1.0F);
        Matrix4f matrix = new Matrix4f(pose.last().pose()).mul(basis);
        font.drawInBatch(text, 0.0F, 0.0F, INK, false, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
    }
}

package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.block.GrimoirePage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.List;

/**
 * A loose page as it is seen, on a block or in the hand: the grimoire's own page, torn out at its bound edge, ruled as
 * the grimoire rules it, with the runes written on it in their cells, line under line, a column a step of time (the
 * first {@link #COLUMNS} of each line).
 * <p>
 * Drawn in the page's own space: x to the right and y down, from (0, 0) to ({@link #W}, {@link #H}), toward whoever
 * looks at it being -z (the space a map is drawn in).
 */
@OnlyIn(Dist.CLIENT)
public final class ScrollPage {

    /** The paper (tools/loose_page.py), and the page's size in its pixels. */
    private static final ResourceLocation PAPER = new ResourceLocation(ElderLexicon.MODID,
            "textures/entity/loose_page.png");
    public static final int W = 174;
    public static final int H = 188;

    private static final int COLUMNS = 10;
    private static final int CELL = 15;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int MARGIN_X = GRID_X + COLUMNS * CELL + 4;

    private static final int INK = 0xFF2E1C10;
    private static final int RULE = 0xFFD8C8A0;
    private static final int MARGIN = 0xFFD89080;
    private static final int PAUSE = 0x502E1C10;

    private ScrollPage() {
    }

    /** The page with {@code text} on it; {@code blank}, its runes not drawn (a circle on show writes them). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, String text, boolean blank) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer paper = buffers.getBuffer(RenderType.text(PAPER));
        quad(paper, matrix, 0, 0, W, H, 0.0F, 0xFFFFFFFF, light, true);
        VertexConsumer ink = buffers.getBuffer(RenderType.textBackground());
        for (int r = 0; r < GrimoirePage.ROWS; r++) {
            int y = GRID_Y + r * CELL + CELL - 1;
            quad(ink, matrix, GRID_X - 2, y, MARGIN_X, y + 1, -0.02F, RULE, light, false);
        }
        quad(ink, matrix, MARGIN_X, 6, MARGIN_X + 1, H - 8, -0.02F, MARGIN, light, false);
        if (blank || text == null || text.isBlank()) {
            return;
        }
        List<List<String>> rows = GrimoirePage.read(text);
        // The pauses first, while the ink's buffer is open; then the runes (the font takes buffers of its own).
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < Math.min(COLUMNS, row.size()); c++) {
                if (row.get(c).isEmpty()) {
                    int x = GRID_X + c * CELL;
                    int y = GRID_Y + r * CELL;
                    quad(ink, matrix, x + 6, y + 6, x + 8, y + 8, -0.02F, PAUSE, light, false);
                }
            }
        }
        Font font = Minecraft.getInstance().font;
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < Math.min(COLUMNS, row.size()); c++) {
                int x = GRID_X + c * CELL;
                int y = GRID_Y + r * CELL;
                String word = row.get(c);
                if (word.isEmpty()) {
                    continue;
                }
                Component glyph = Component.literal(word).withStyle(SgaFont.STYLE);
                int width = Math.max(1, font.width(glyph));
                float scale = Math.min(0.9F, 12.0F / width);
                pose.pushPose();
                pose.translate(x + CELL / 2.0F, y + CELL / 2.0F, -0.04F);
                pose.scale(scale, scale, scale);
                font.drawInBatch(glyph, -width / 2.0F, -4.0F, INK, false, pose.last().pose(), buffers,
                        Font.DisplayMode.NORMAL, 0, light);
                pose.popPose();
            }
        }
    }

    /** A flat quad from (x0, y0) to (x1, y1), facing -z: textured (the whole texture) or of one colour. */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z,
                             int argb, int light, boolean textured) {
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        float[][] corners = {{x0, y1, 0, 1}, {x1, y1, 1, 1}, {x1, y0, 1, 0}, {x0, y0, 0, 0}};
        for (float[] corner : corners) {
            buffer.vertex(matrix, corner[0], corner[1], z).color(r, g, b, a);
            if (textured) {
                buffer.uv(corner[2], corner[3]);
            }
            buffer.uv2(light).endVertex();
        }
    }
}

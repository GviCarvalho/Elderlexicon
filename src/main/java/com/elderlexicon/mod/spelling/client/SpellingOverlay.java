package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.config.SpellingClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight HUD overlay shown while the player records rune inputs.
 */
@SuppressWarnings("null")
public final class SpellingOverlay {

    private static final SpellingOverlay INSTANCE = new SpellingOverlay();
    private static final float RECITATION_SCALE = 2.0F;
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");

    private SpellingOverlay() {
    }

    public static SpellingOverlay getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        if (ClientFlow.flowing()) {
            renderFlow(graphics, minecraft.font, minecraft.getWindow().getGuiScaledWidth());
        }

        ClientSpellingController controller = ClientSpellingController.getInstance();
        boolean recording = controller.isRecording();
        if (!recording) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        Font font = minecraft.font;
        int centerX = width / 2;

        renderRecordingState(graphics, font, controller, centerX);
    }

    /** The state of flow, at the top of the screen: how long it has been held and what a second of it costs now. */
    private void renderFlow(GuiGraphics graphics, Font font, int screenWidth) {
        Component line = Component.translatable("overlay.elderlexicon.flow",
                String.format(Locale.ROOT, "%.0f", ClientFlow.seconds()),
                String.format(Locale.ROOT, "%.2f", ClientFlow.umuPerSecond()));
        graphics.drawCenteredString(font, line, screenWidth / 2, 4, 0x9FD8FF);
    }

    private void renderRecordingState(GuiGraphics graphics,
                                      Font font,
                                      ClientSpellingController controller,
                                      int centerX) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int hotbarLeft = screenWidth / 2 - 91;
        int hotbarTop = screenHeight - 22;

        // Stacked upward from above the hotbar and whatever vanilla draws over it (hearts, armor, food, air):
        // the words spoken so far in the old script, their names beneath, then the title and the last results.
        int stackBottom = screenHeight - usedAboveBottom(minecraft) - 3;
        int namesY = stackBottom - font.lineHeight;
        int glyphsY = namesY - Math.round(font.lineHeight * RECITATION_SCALE) - 2;
        int titleY = Math.max(4, glyphsY - font.lineHeight - 4);
        renderRecitation(graphics, font, controller.currentSequence(), centerX, glyphsY, namesY);

        Component title = Component.translatable("overlay.elderlexicon.spelling.title");
        graphics.drawCenteredString(font, title, centerX, titleY, 0xF0D080);

        if (SpellingClientConfig.showCountdown) {
            String timerValue = String.format(Locale.ROOT, "%.1fs", controller.remainingSeconds());
            Component timer = Component.literal(timerValue);
            int timerWidth = font.width(timerValue);
            int timerX = Math.min(screenWidth - timerWidth - 4, hotbarLeft + 182 + 8);
            int timerY = Math.max(4, hotbarTop - font.lineHeight - 2);
            graphics.drawString(font, timer, timerX, timerY, 0xFFFFFF, false);
        }

        renderSpellLogs(graphics, font, controller, centerX, titleY - font.lineHeight - 2);

        renderSlotBar(graphics, font, controller);
    }

    private void renderSlotBar(GuiGraphics graphics,
                               Font font,
                               ClientSpellingController controller) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int hotbarLeft = screenWidth / 2 - 91;
        int hotbarTop = screenHeight - 22;
        int slotSize = 20;

        // The vanilla hotbar is hidden in trance (SpellingClientForgeEvents), so its frame is drawn here, empty.
        graphics.blit(WIDGETS, hotbarLeft, hotbarTop, 0, 0, 182, 22);

        List<String> slots = controller.repertoireSlots();
        for (int i = 0; i < slots.size(); i++) {
            int x = hotbarLeft + i * slotSize;
            int y = hotbarTop;
            boolean highlighted = controller.isSlotHighlighted(i);
            int overlayColor = highlighted ? 0xB34C9DFF : 0x66000000;
            graphics.fill(x + 1, y + 1, x + slotSize - 1, y + slotSize - 1, overlayColor);
            Component label = RuneSgaMapper.runeComponent(slots.get(i));
            int textColor = highlighted ? 0xFFFFFF : 0xE0E4FF;
            graphics.drawCenteredString(font, label, x + slotSize / 2, y + 6, textColor);
        }
    }

    /** The trance being recited: runes in the old script, large, with their names below to learn them by. */
    private void renderRecitation(GuiGraphics graphics, Font font, List<String> runes, int centerX, int glyphsY, int namesY) {
        if (runes.isEmpty()) {
            graphics.drawCenteredString(font, "· · ·", centerX, glyphsY + 4, 0x80D8B8FF);
            return;
        }
        Component glyphs = RuneSgaMapper.sequenceComponent(runes);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, glyphsY, 0.0F);
        graphics.pose().scale(RECITATION_SCALE, RECITATION_SCALE, 1.0F);
        graphics.drawString(font, glyphs, -font.width(glyphs) / 2, 0, 0xD8B8FF, true);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, String.join(" ", runes), centerX, namesY, 0xA8A0B8);
    }

    /** How much of the bottom of the screen the hotbar and the bars above it take, as Forge's HUD tracks it. */
    private static int usedAboveBottom(Minecraft minecraft) {
        if (minecraft.gui instanceof ForgeGui gui) {
            return Math.max(Math.max(gui.leftHeight, gui.rightHeight), 22);
        }
        return 39;
    }

    private void renderSpellLogs(GuiGraphics graphics,
                                 Font font,
                                 ClientSpellingController controller,
                                 int centerX,
                                 int startY) {
        List<ClientSpellingController.SpellLogEntry> logs = controller.overlayLogs();
        int lineY = startY;
        for (ClientSpellingController.SpellLogEntry entry : logs) {
            Component text = entry.message();
            if (text == null) {
                continue;
            }
            int color = colorForLog(entry.kind());
            graphics.drawCenteredString(font, text, centerX, lineY, color);
            lineY -= font.lineHeight;
        }
    }

    private int colorForLog(ClientSpellingController.SpellLogKind kind) {
        return switch (kind) {
            case SUCCESS -> 0x9CFFBE;
            case WARNING -> 0xFFE5A3;
            case FAILURE -> 0xFFB1A6;
        };
    }
}

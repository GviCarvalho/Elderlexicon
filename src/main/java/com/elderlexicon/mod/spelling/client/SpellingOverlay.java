package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spelling.config.SpellingClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight HUD overlay shown while the player records rune inputs.
 */
@SuppressWarnings("null")
public final class SpellingOverlay {

    private static final SpellingOverlay INSTANCE = new SpellingOverlay();

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

    private void renderRecordingState(GuiGraphics graphics,
                                      Font font,
                                      ClientSpellingController controller,
                                      int centerX) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int hotbarLeft = screenWidth / 2 - 91;
        int hotbarTop = screenHeight - 22;
        int titleY = Math.max(4, hotbarTop - 26);

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

        renderSpellLogs(graphics, font, controller, centerX, titleY + font.lineHeight + 2);

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
            lineY += font.lineHeight;
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

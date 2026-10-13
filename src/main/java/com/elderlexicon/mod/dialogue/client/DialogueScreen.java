package com.elderlexicon.mod.dialogue.client;

import com.elderlexicon.mod.dialogue.DialogueNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialogue screen, as in the games of roleplay (docs/mago-design.md, 4.1): a panel at the foot of the screen, who
 * speaks drawn at its side, their name, their words written out a few at a time, and the answers below, to be clicked
 * or picked by their number. The world goes on behind it.
 */
public final class DialogueScreen extends Screen {

    private static final int MARGIN = 16;
    private static final int PADDING = 8;
    private static final int PORTRAIT = 64;
    private static final int CHOICE_HEIGHT = 14;
    /** How many pixels of the words are written out each tick. */
    private static final float WRITTEN_PER_TICK = 14.0F;
    private static final int PANEL = 0xE0100C14;
    private static final int EDGE = 0xFF6B5A86;
    private static final int HOVERED = 0x40B49CFF;

    private DialogueNetwork.LinePacket line;
    private List<FormattedCharSequence> lines = List.of();
    private final List<List<FormattedCharSequence>> choices = new ArrayList<>();
    private float written;
    private int total;
    private boolean ended;
    /** An answer was given, and the next line has not come yet. */
    private boolean waiting;

    DialogueScreen(DialogueNetwork.LinePacket line) {
        super(line.name());
        this.line = line;
    }

    int speaker() {
        return line.speaker();
    }

    /** A new line is said: written out from the start. */
    void say(DialogueNetwork.LinePacket next) {
        line = next;
        written = 0.0F;
        waiting = false;
        layout();
    }

    /** The server ended the talk: the screen closes without answering. */
    void ended() {
        ended = true;
        onClose();
    }

    @Override
    protected void init() {
        layout();
    }

    private int textLeft() {
        return MARGIN + PADDING + PORTRAIT + PADDING;
    }

    private int textWidth() {
        return width - textLeft() - MARGIN - PADDING;
    }

    private void layout() {
        if (font == null) {
            return;
        }
        lines = font.split(line.text(), textWidth());
        total = lines.stream().mapToInt(font::width).sum();
        choices.clear();
        for (int i = 0; i < line.choices().size(); i++) {
            Component label = Component.literal((i + 1) + ". ").withStyle(ChatFormatting.GRAY)
                    .append(line.choices().get(i));
            choices.add(font.split(label, textWidth() - 6));
        }
    }

    private boolean writtenOut() {
        return !waiting && written >= total;
    }

    private int choiceHeight(List<FormattedCharSequence> choice) {
        return choice.size() * 10 + (CHOICE_HEIGHT - 10);
    }

    private int panelTop() {
        int height = PADDING + 12 + lines.size() * 10 + PADDING;
        for (List<FormattedCharSequence> choice : choices) {
            height += choiceHeight(choice);
        }
        height = Math.max(height + PADDING, PORTRAIT + 2 * PADDING);
        return this.height - MARGIN - height;
    }

    @Override
    public void tick() {
        if (written < total) {
            written += WRITTEN_PER_TICK;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = panelTop();
        int bottom = height - MARGIN;
        graphics.fillGradient(0, Math.max(0, top - 40), width, height, 0x00000000, 0x90000000);
        graphics.fill(MARGIN, top, width - MARGIN, bottom, PANEL);
        graphics.renderOutline(MARGIN, top, width - 2 * MARGIN, bottom - top, EDGE);
        portrait(graphics, top, mouseX, mouseY);
        int x = textLeft();
        int y = top + PADDING;
        graphics.drawString(font, line.name().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y,
                0xFFFFFF);
        y += 12;
        float left = written + (writtenOut() ? 0.0F : partialTick * WRITTEN_PER_TICK);
        for (FormattedCharSequence text : lines) {
            if (left <= 0.0F) {
                break;
            }
            int lineWidth = font.width(text);
            if (left < lineWidth) {
                graphics.enableScissor(x, y - 1, x + Mth.ceil(left), y + 10);
                graphics.drawString(font, text, x, y, 0xE8E0F0, false);
                graphics.disableScissor();
            } else {
                graphics.drawString(font, text, x, y, 0xE8E0F0, false);
            }
            left -= lineWidth;
            y += 10;
        }
        if (writtenOut()) {
            y = top + PADDING + 12 + lines.size() * 10 + PADDING;
            int hovered = choiceAt(mouseX, mouseY);
            for (int i = 0; i < choices.size(); i++) {
                List<FormattedCharSequence> choice = choices.get(i);
                int h = choiceHeight(choice);
                if (i == hovered) {
                    graphics.fill(x - 3, y - 2, x + textWidth(), y + h - 2, HOVERED);
                }
                int ly = y;
                for (FormattedCharSequence part : choice) {
                    graphics.drawString(font, part, ly == y ? x : x + 12, ly, i == hovered ? 0xFFFFA0 : 0xD0C8E0,
                            false);
                    ly += 10;
                }
                y += h;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Who speaks, drawn in a frame at the side of the panel, looking at the pointer. */
    private void portrait(GuiGraphics graphics, int top, int mouseX, int mouseY) {
        int x = MARGIN + PADDING;
        int y = top + PADDING;
        graphics.fill(x, y, x + PORTRAIT, y + PORTRAIT, 0xFF1C1626);
        graphics.renderOutline(x, y, PORTRAIT, PORTRAIT, EDGE);
        Entity entity = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(line.speaker());
        if (entity instanceof LivingEntity living) {
            // The head and shoulders: drawn large, the feet below the frame.
            int scale = (int) (PORTRAIT * 1.3F / Math.max(1.0F, living.getBbHeight()));
            int feet = y + (int) (living.getBbHeight() * scale) - 4;
            graphics.enableScissor(x + 1, y + 1, x + PORTRAIT - 1, y + PORTRAIT - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + PORTRAIT / 2, feet, scale,
                    x + PORTRAIT / 2 - mouseX, y + PORTRAIT / 4 - mouseY, living);
            graphics.disableScissor();
        }
    }

    /** The choice under the pointer, or -1. */
    private int choiceAt(double mouseX, double mouseY) {
        if (!writtenOut()) {
            return -1;
        }
        int x = textLeft();
        int y = panelTop() + PADDING + 12 + lines.size() * 10 + PADDING;
        for (int i = 0; i < choices.size(); i++) {
            int h = choiceHeight(choices.get(i));
            if (mouseX >= x - 3 && mouseX < x + textWidth() && mouseY >= y - 2 && mouseY < y + h - 2) {
                return i;
            }
            y += h;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (waiting) {
            return true;
        }
        if (!writtenOut()) {
            written = total;
            return true;
        }
        int chosen = choiceAt(mouseX, mouseY);
        if (chosen >= 0) {
            choose(chosen);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (waiting && key != GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (!writtenOut()) {
                written = total;
            } else if (choices.size() == 1) {
                choose(0);
            }
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && writtenOut()) {
            int chosen = key - GLFW.GLFW_KEY_1;
            if (chosen < choices.size()) {
                choose(chosen);
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void choose(int index) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, 0.6F));
        }
        waiting = true;
        DialogueNetwork.sendChoice(index);
    }

    @Override
    public void onClose() {
        if (!ended) {
            ended = true;
            DialogueNetwork.sendChoice(-1);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

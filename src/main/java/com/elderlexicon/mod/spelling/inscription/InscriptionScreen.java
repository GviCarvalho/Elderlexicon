package com.elderlexicon.mod.spelling.inscription;

import com.elderlexicon.mod.spelling.client.SgaFont;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Writing on a block face with a quill: four short lines, in the old glyphs as they are typed. Each line is a spell,
 * read like a page (the column is the clock). Writing nothing wipes the face.
 */
@OnlyIn(Dist.CLIENT)
public class InscriptionScreen extends Screen {

    private static final Style SGA = SgaFont.STYLE;
    private static final int WIDTH = 200;

    private final BlockPos pos;
    private final Direction face;
    private final String existing;
    private final List<EditBox> lines = new ArrayList<>();

    public InscriptionScreen(BlockPos pos, Direction face, String existing) {
        super(Component.literal("Inscrição"));
        this.pos = pos;
        this.face = face;
        this.existing = existing == null ? "" : existing;
    }

    @Override
    protected void init() {
        lines.clear();
        String[] written = existing.split("\n");
        int top = height / 2 - 50;
        for (int i = 0; i < Inscription.MAX_LINES; i++) {
            EditBox line = new EditBox(font, (width - WIDTH) / 2, top + i * 22, WIDTH, 18, Component.literal("Linha"));
            line.setMaxLength(Inscription.MAX_CHARS / Inscription.MAX_LINES);
            line.setFormatter((text, start) -> FormattedCharSequence.forward(text, SGA));
            line.setValue(i < written.length ? written[i] : "");
            addRenderableWidget(line);
            lines.add(line);
        }
        setInitialFocus(lines.get(0));
        addRenderableWidget(Button.builder(Component.literal("Escrever"), button -> finish())
                .bounds(width / 2 - 100, top + Inscription.MAX_LINES * 22 + 8, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 2, top + Inscription.MAX_LINES * 22 + 8, 98, 20).build());
    }

    private void finish() {
        List<String> text = new ArrayList<>();
        lines.forEach(line -> text.add(line.getValue()));
        SpellingNetwork.sendInscribe(new InscribePacket(pos, face, String.join("\n", text)));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            // Enter goes to the next line; on the last, it writes.
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isFocused()) {
                    if (i + 1 < lines.size()) {
                        setFocused(lines.get(i + 1));
                    } else {
                        finish();
                    }
                    return true;
                }
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, Component.literal("Escrever no bloco"), width / 2, height / 2 - 72, 0xFFE8D8B0);
        graphics.drawCenteredString(font, Component.literal("Cada linha é um feitiço · a coluna é o tempo"), width / 2,
                height / 2 - 62, 0xFF9A8A70);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

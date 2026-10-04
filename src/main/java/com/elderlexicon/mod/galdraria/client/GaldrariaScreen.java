package com.elderlexicon.mod.galdraria.client;

import com.elderlexicon.mod.spelling.client.SgaFont;
import com.elderlexicon.mod.galdraria.EngravePacket;
import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.galdraria.GaldrariaMenu;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The galdraria table: the line of runes to carve, typed in the old glyphs, with what the spirit will read under it;
 * the burin and the thing to engrave below. Putting an engraved thing on the table shows what is carved in it.
 */
@OnlyIn(Dist.CLIENT)
public final class GaldrariaScreen extends AbstractContainerScreen<GaldrariaMenu> {

    private static final Style SGA = SgaFont.STYLE;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int EDGE = 0xFF000000;
    private static final int TEXT = 0xFF404040;
    private static final int WRONG = 0xFFAA2222;

    private EditBox runes;
    private ItemStack shown = ItemStack.EMPTY;

    public GaldrariaScreen(GaldrariaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 176;
        inventoryLabelY = GaldrariaMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        runes = new EditBox(font, leftPos + 8, topPos + 18, 160, 16,
                Component.translatable("gui.elderlexicon.galdraria.runes"));
        runes.setMaxLength(Engravings.MAX_CHARS);
        runes.setFormatter((text, start) -> FormattedCharSequence.forward(text, SGA));
        addRenderableWidget(runes);
        setInitialFocus(runes);
        addRenderableWidget(Button.builder(Component.translatable("gui.elderlexicon.galdraria.engrave"),
                        button -> engrave())
                .bounds(leftPos + 100, topPos + GaldrariaMenu.SLOTS_Y - 1, 68, 20).build());
    }

    private void engrave() {
        SpellingNetwork.sendEngrave(new EngravePacket(runes.getValue()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack item = menu.item();
        // A different thing put on the table shows what is carved in it, to be carved over.
        if (item.getItem() != shown.getItem() || item.isEmpty() != shown.isEmpty()) {
            runes.setValue(Engravings.of(item).orElse(""));
        }
        shown = item.copy();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == InputConstants.KEY_ESCAPE) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (runes.isFocused()) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                engrave();
                return true;
            }
            return runes.keyPressed(key, scanCode, modifiers) || runes.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, PANEL);
        slot(graphics, x + GaldrariaMenu.BURIN_X - 1, y + GaldrariaMenu.SLOTS_Y - 1);
        slot(graphics, x + GaldrariaMenu.ITEM_X - 1, y + GaldrariaMenu.SLOTS_Y - 1);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + 7 + column * 18, y + GaldrariaMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + 7 + column * 18, y + GaldrariaMenu.INVENTORY_Y + 57);
        }
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        List<String> words = Engravings.words(runes.getValue());
        List<String> read = Engravings.read(Engravings.written(words));
        int color = words.size() > Engravings.MAX_COLUMNS || read.contains("") ? WRONG : TEXT;
        String readable = String.join(" ", read.stream().map(word -> word.isEmpty() ? "?" : word).toList());
        graphics.drawString(font, font.plainSubstrByWidth(readable, 160), 8, 38, color, false);
        graphics.drawString(font, words.size() + "/" + Engravings.MAX_COLUMNS, 48, GaldrariaMenu.SLOTS_Y + 5, color,
                false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index <= GaldrariaMenu.BURIN_SLOT) {
            graphics.renderTooltip(font, Component.translatable(hoveredSlot.index == GaldrariaMenu.ITEM_SLOT
                    ? "gui.elderlexicon.galdraria.item_slot" : "gui.elderlexicon.galdraria.burin_slot"), mouseX, mouseY);
        }
    }
}

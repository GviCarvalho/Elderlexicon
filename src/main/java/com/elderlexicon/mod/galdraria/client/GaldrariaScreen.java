package com.elderlexicon.mod.galdraria.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.galdraria.EngravePacket;
import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.galdraria.GaldrariaMenu;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.spell.block.SpellReading;
import com.elderlexicon.mod.spelling.client.SgaFont;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The galdraria table (docs/galdraria-design.md), as the anvil's screen: the line of ten cells to carve on top, one
 * word per cell and each column a step of the reading, typed as on a grimoire page (space goes to the next cell, an
 * empty cell is a pause); under it, the thing to engrave + the burin, and the thing engraved, to be taken. The arrow
 * is crossed out while the line cannot be carved. A thing put on the table that is already engraved shows its runes.
 */
@OnlyIn(Dist.CLIENT)
public final class GaldrariaScreen extends ItemCombinerScreen<GaldrariaMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "textures/gui/galdraria_table.png");
    private static final Style SGA = SgaFont.STYLE;
    private static final ParserDictionary DICTIONARY = ParserDictionary.load();
    private static final SpellReading READING = new SpellReading(DICTIONARY);

    private static final int CELL = 16;
    private static final int MAX_WORD = 16;
    /** One column is one step of the spirit's reading: 5 ticks. */
    private static final double SECONDS_PER_COLUMN = 0.25D;
    private static final float SMALL = 0.75F;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int READ_Y = 37;

    private static final int LABEL = 0xFF404040;
    private static final int MARK = 0xFF6A2E9A;
    private static final int PAUSE = 0xFF5A5A5A;
    private static final int CURSOR = 0x80FFFFFF;

    /** The line being carved, one word per cell ("" is a pause). */
    private final List<String> cells = new ArrayList<>();
    private int cursor;
    private int frame;
    private String sent = "";
    private ItemStack lastItem = ItemStack.EMPTY;
    private List<SpellReading.Word> words = List.of();

    public GaldrariaScreen(GaldrariaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
    }

    // ------------------------------------------------------------------ the line

    /** Reads the line again (what each word does, for the tooltips) and sends it to the table to be carved. */
    private void refresh() {
        List<String> ids = new ArrayList<>();
        for (String cell : cells) {
            ids.add(RuneTokens.normalize(cell));
        }
        words = READING.read(ids);
        String text = text();
        if (!text.equals(sent)) {
            sent = text;
            menu.setText(text);
            SpellingNetwork.sendEngrave(new EngravePacket(text));
        }
    }

    /** The line as the table reads it: each word as the page writes it, pauses as the page writes them. */
    private String text() {
        List<String> kept = new ArrayList<>();
        for (String cell : cells) {
            String word = written(cell);
            kept.add(word.isEmpty() ? Engravings.PAUSE : word);
        }
        return String.join(" ", kept);
    }

    /** What is carved in the thing put on the table, cell by cell. */
    private void load(ItemStack item) {
        cells.clear();
        for (String word : Engravings.words(Engravings.of(item).orElse(""))) {
            cells.add(Engravings.isPause(word) ? "" : word);
        }
        cursor = Math.min(cells.size(), Engravings.MAX_COLUMNS - 1);
        refresh();
    }

    /** A typed word as the page keeps it: a rune's name as its glyph, a number's digits as glyphs. */
    private static String written(String typed) {
        String written = RuneTokens.written(typed);
        if (written.length() == 1 && Character.isLetter(written.charAt(0))) {
            written = written.toUpperCase(Locale.ROOT);
        }
        return written;
    }

    private void commitCell() {
        if (cursor < cells.size()) {
            cells.set(cursor, written(cells.get(cursor)));
        }
    }

    private void typeCharacter(char character) {
        if (cursor >= cells.size()) {
            if (cells.size() >= Engravings.MAX_COLUMNS) {
                return;
            }
            cells.add(String.valueOf(character));
            cursor = cells.size() - 1;
        } else if (cells.get(cursor).length() < MAX_WORD) {
            cells.set(cursor, cells.get(cursor) + character);
        }
        scratch();
        refresh();
    }

    /** Space: on to the next cell; past the end, a pause (an empty cell). */
    private void nextCell() {
        commitCell();
        if (cursor < cells.size()) {
            cursor = Math.min(cursor + 1, Engravings.MAX_COLUMNS - 1);
        } else if (cells.size() < Engravings.MAX_COLUMNS - 1) {
            cells.add("");
            cursor = cells.size();
        }
        refresh();
    }

    private void backspace() {
        if (cells.isEmpty()) {
            return;
        }
        if (cursor >= cells.size()) {
            cursor = cells.size() - 1;
        }
        String word = cells.get(cursor);
        if (!word.isEmpty()) {
            cells.set(cursor, word.substring(0, word.length() - 1));
        } else {
            cells.remove(cursor);
            cursor = Math.max(0, cursor - 1);
        }
        refresh();
    }

    private void deleteCell() {
        if (cursor < cells.size()) {
            cells.remove(cursor);
            refresh();
        }
    }

    private void move(int by) {
        commitCell();
        cursor = Math.max(0, Math.min(Math.min(cells.size(), Engravings.MAX_COLUMNS - 1), cursor + by));
        refresh();
    }

    /** The scratch of the burin on the stone. */
    private void scratch() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GRINDSTONE_USE,
                    1.8F + (float) Math.random() * 0.3F, 0.12F));
        }
    }

    // ------------------------------------------------------------------ input

    /** A thing put on the table that is already engraved shows its runes, to be carved over. */
    @Override
    public void slotChanged(AbstractContainerMenu changed, int slot, ItemStack stack) {
        if (slot == GaldrariaMenu.ITEM_SLOT) {
            if (!stack.isEmpty() && Engravings.has(stack) && !ItemStack.isSameItemSameTags(stack, lastItem)) {
                load(stack);
            }
            lastItem = stack.copy();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        frame++;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (Character.isLetterOrDigit(character) || character == '_' || character == '-') {
            typeCharacter(character);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case InputConstants.KEY_ESCAPE -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
            case InputConstants.KEY_SPACE -> nextCell();
            case InputConstants.KEY_BACKSPACE -> backspace();
            case InputConstants.KEY_DELETE -> deleteCell();
            case InputConstants.KEY_LEFT -> move(-1);
            case InputConstants.KEY_RIGHT -> move(1);
            case InputConstants.KEY_HOME -> move(-Engravings.MAX_COLUMNS);
            case InputConstants.KEY_END -> move(Engravings.MAX_COLUMNS);
            default -> {
                // Letters and digits are runes being written here, not the keys that close the pack, drop an item or
                // swap one with the hotbar.
                if ((key >= InputConstants.KEY_0 && key <= InputConstants.KEY_Z) || (minecraft != null
                        && (minecraft.options.keyInventory.matches(key, scanCode)
                        || minecraft.options.keyDrop.matches(key, scanCode)))) {
                    return true;
                }
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            commitCell();
            cursor = Math.min(cell, Math.min(cells.size(), Engravings.MAX_COLUMNS - 1));
            refresh();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int cellAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - (leftPos + GRID_X);
        int y = (int) Math.floor(mouseY) - (topPos + GRID_Y);
        if (x < 0 || y < 0 || x >= Engravings.MAX_COLUMNS * CELL || y >= CELL) {
            return -1;
        }
        return x / CELL;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        drawCells(graphics);
    }

    /** The arrow crossed out, as the anvil's, while there is something on the table but nothing can be carved. */
    @Override
    protected void renderErrorIcon(GuiGraphics graphics, int x, int y) {
        if ((menu.getSlot(GaldrariaMenu.ITEM_SLOT).hasItem() || menu.getSlot(GaldrariaMenu.BURIN_SLOT).hasItem())
                && !menu.getSlot(menu.getResultSlot()).hasItem()) {
            graphics.blit(TEXTURE, x + 98, y + GaldrariaMenu.SLOTS_Y - 2, 176, 0, 28, 24);
        }
    }

    /** The line: each word in its cell in the old glyphs, the cursor lit as a slot is under the pointer. */
    private void drawCells(GuiGraphics graphics) {
        boolean blink = frame / 10 % 2 == 0;
        for (int i = 0; i < Engravings.MAX_COLUMNS; i++) {
            int x = leftPos + GRID_X + i * CELL;
            int y = topPos + GRID_Y;
            if (i == cursor) {
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, CURSOR);
                if (blink) {
                    graphics.fill(x + 3, y + CELL - 3, x + CELL - 3, y + CELL - 2, LABEL);
                }
            }
            if (i < cells.size()) {
                String word = cells.get(i);
                if (word.isEmpty()) {
                    graphics.fill(x + 7, y + 7, x + 9, y + 9, PAUSE);
                } else {
                    drawWord(graphics, word, x, y);
                }
            }
        }
    }

    /** A word in its cell, shrunk to fit; a mark is framed in violet. */
    private void drawWord(GuiGraphics graphics, String word, int x, int y) {
        String id = RuneTokens.normalize(word);
        boolean mark = !id.isEmpty() && !id.matches("-?\\d+") && DICTIONARY.lookup(id).isEmpty();
        Component text = Component.literal(word).withStyle(SGA);
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(1.0F, 12.0F / textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(x + CELL / 2.0F + 0.5F, y + CELL / 2.0F, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth / 2, -4, mark ? MARK : LABEL, false);
        graphics.pose().popPose();
        if (mark) {
            graphics.renderOutline(x + 2, y + 2, CELL - 3, CELL - 3, MARK);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // The line in the common letters, under the cells.
        if (cells.stream().anyMatch(cell -> !cell.isEmpty())) {
            List<String> read = new ArrayList<>();
            for (String cell : cells) {
                String id = RuneTokens.normalize(cell);
                read.add(id.isEmpty() ? "·" : id);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(GRID_X, READ_Y, 0);
            graphics.pose().scale(SMALL, SMALL, 1.0F);
            graphics.drawString(font, font.plainSubstrByWidth(String.join(" ", read), (int) (160 / SMALL)), 0, 0, LABEL,
                    false);
            graphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCellTooltip(graphics, mouseX, mouseY);
    }

    /** Over a cell: the word, what the rune is and what it does in this line, and when it is read. */
    private void drawCellTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int cell = cellAt(mouseX, mouseY);
        if (cell < 0) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (cell < cells.size() && cell < words.size()) {
            SpellReading.Word word = words.get(cell);
            MutableComponent title = Component.literal("");
            if (!cells.get(cell).isEmpty()) {
                title.append(Component.literal(cells.get(cell)).withStyle(SGA)).append("  ");
            }
            title.append(Component.literal(word.title()).withStyle(ChatFormatting.GOLD));
            lines.add(title.getVisualOrderText());
            if (!word.rune().isEmpty() && DICTIONARY.lookup(word.rune()).isPresent()) {
                wrap(lines, SpellReading.lore(word.rune()), ChatFormatting.GRAY);
            }
            wrap(lines, word.role(), ChatFormatting.WHITE);
        }
        lines.add(Component.literal(timeOf(cell)).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        graphics.renderTooltip(font, lines, mouseX, mouseY);
    }

    private void wrap(List<FormattedCharSequence> lines, String text, ChatFormatting color) {
        lines.addAll(font.split(Component.literal(text).withStyle(color), 220));
    }

    private static String timeOf(int column) {
        return String.format(Locale.ROOT, "Coluna %d · %.2f s", column, column * SECONDS_PER_COLUMN).replace('.', ',');
    }
}

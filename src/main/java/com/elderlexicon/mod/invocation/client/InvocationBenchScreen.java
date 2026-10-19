package com.elderlexicon.mod.invocation.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.invocation.BenchMarkPacket;
import com.elderlexicon.mod.invocation.Burins;
import com.elderlexicon.mod.invocation.Formats;
import com.elderlexicon.mod.invocation.InvocationBenchBlockEntity;
import com.elderlexicon.mod.invocation.InvocationBenchMenu;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.spelling.client.SgaFont;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The invocation bench (docs/invocacao-design.md): a crafting table, wider. The mark of each square is carved in its
 * floor, in low relief, under what lies there; after the result, the line the bench reads with what it made as the
 * subject, carved the same way in ten cells, as a grimoire page writes it (docs/grimorio-design.md): a cell is a word
 * and a step of the reading, an empty cell a pause, a rune's name turns to its glyph as the cell is left, a mark is
 * framed as a seal. With the burin at hand (either hand or the hotbar), clicking an empty square carves its mark and
 * clicking the line writes it; without it, both are only read.
 */
@OnlyIn(Dist.CLIENT)
public final class InvocationBenchScreen extends AbstractContainerScreen<InvocationBenchMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "textures/gui/invocation_bench.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int GRID_X = 30;
    private static final int GRID_Y = 17;
    private static final int CELL = 18;
    /** The line: ten cells after the result, a word in each. */
    private static final int LINE_X = 151;
    private static final int LINE_Y = 33;
    private static final int LINE_CELL = 12;
    private static final int LINE_HEIGHT = 20;
    private static final int MAX_WORD = 16;
    private static final int SQUARE_WIDTH = 56;
    private static final ParserDictionary DICTIONARY = ParserDictionary.load();

    /** The carving in a square's floor: the cut, and the light on its lower edge. */
    private static final int CUT = 0xFF5E5E5E;
    private static final int EDGE = 0xFFDCDCDC;
    private static final int EDITING = 0x60B48CFF;
    private static final int CURSOR = 0x50FFFFFF;
    private static final int HOVER = 0x20FFFFFF;
    private static final float MARK_SCALE = 0.5F;

    private EditBox square;
    /** The line as it is being written, a word per cell ("" is a pause); null while it is only read. */
    private List<String> cells;
    private int cursor;
    private int frame;
    /** The square whose mark is being carved, or -1. */
    private int editing = -1;

    public InvocationBenchScreen(InvocationBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
        imageHeight = 166;
        titleLabelX = 29;
        inventoryLabelX = InvocationBenchMenu.INVENTORY_X;
        inventoryLabelY = imageHeight - 94;
    }

    // ------------------------------------------------------------------ what the bench has

    private Optional<InvocationBenchBlockEntity> bench() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof InvocationBenchBlockEntity bench
                ? Optional.of(bench) : Optional.empty();
    }

    private String[] marks() {
        return bench().map(InvocationBenchBlockEntity::marks).orElseGet(() -> {
            String[] none = new String[Formats.SIDE * Formats.SIDE];
            Arrays.fill(none, "");
            return none;
        });
    }

    /** The line carved in the bench, a word per cell as the page writes it ("" is a pause). */
    private List<String> carvedCells() {
        List<String> words = new ArrayList<>();
        for (String word : Engravings.words(bench().map(InvocationBenchBlockEntity::engraving).orElse(""))) {
            words.add(Engravings.isPause(word) ? "" : word);
        }
        return words;
    }

    /** The cells shown: those being written, or those carved. */
    private List<String> shownCells() {
        return cells != null ? cells : carvedCells();
    }

    private boolean burin() {
        return minecraft != null && minecraft.player != null && Burins.atHand(minecraft.player).isPresent();
    }

    // ------------------------------------------------------------------ setting up

    @Override
    protected void init() {
        super.init();
        square = new EditBox(font, 0, 0, SQUARE_WIDTH, 12,
                Component.translatable("screen.elderlexicon.invocation_bench.square", 0));
        square.setMaxLength(32);
        square.visible = false;
        addRenderableWidget(square);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        frame++;
        if (!burin()) {
            stopCarving(false);
            stopWriting(false);
        }
    }

    // ------------------------------------------------------------------ carving

    /** The square under the pointer, or -1. */
    private int squareAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - (leftPos + GRID_X - 1);
        int y = (int) Math.floor(mouseY) - (topPos + GRID_Y - 1);
        if (x < 0 || y < 0 || x >= CELL * Formats.SIDE || y >= CELL * Formats.SIDE) {
            return -1;
        }
        return x / CELL + Formats.SIDE * (y / CELL);
    }

    private void startCarving(int index) {
        stopCarving(true);
        editing = index;
        int x = leftPos + GRID_X + (index % Formats.SIDE) * CELL + 8 - SQUARE_WIDTH / 2;
        int y = topPos + GRID_Y + (index / Formats.SIDE) * CELL + 2;
        square.setX(x);
        square.setY(y);
        square.setValue(marks()[index]);
        square.visible = true;
        setFocused(square);
        square.setFocused(true);
        stopWriting(true);
    }

    /** Ends carving a square's mark, keeping what was written or not. */
    private void stopCarving(boolean keep) {
        if (editing < 0) {
            return;
        }
        if (keep) {
            String[] marks = marks();
            String written = square.getValue().trim();
            if (!written.equals(marks[editing])) {
                marks[editing] = written;
                send(marks, text(carvedCells()));
            }
        }
        editing = -1;
        square.visible = false;
        square.setFocused(false);
    }

    // ------------------------------------------------------------------ writing the line

    /** Starts writing the line at cell {@code at}. */
    private void startWriting(int at) {
        stopCarving(true);
        if (cells == null) {
            cells = new ArrayList<>(carvedCells());
        }
        cursor = Math.min(at, Math.min(cells.size(), Engravings.MAX_COLUMNS - 1));
    }

    /** Ends writing the line, keeping what was written or not. */
    private void stopWriting(boolean keep) {
        if (cells == null) {
            return;
        }
        commitCell();
        String written = text(cells);
        cells = null;
        if (keep && !written.equals(text(carvedCells()))) {
            send(marks(), written);
        }
    }

    /** The line as the bench keeps it: each word as the page writes it, pauses as the page writes them. */
    private static String text(List<String> words) {
        List<String> kept = new ArrayList<>(words);
        while (!kept.isEmpty() && kept.get(kept.size() - 1).isEmpty()) {
            kept.remove(kept.size() - 1);
        }
        List<String> written = new ArrayList<>();
        for (String word : kept) {
            String glyph = written(word);
            written.add(glyph.isEmpty() ? Engravings.PAUSE : glyph);
        }
        return String.join(" ", written);
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
        if (cells != null && cursor < cells.size()) {
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
    }

    private void moveCursor(int by) {
        commitCell();
        cursor = Math.max(0, Math.min(Math.min(cells.size(), Engravings.MAX_COLUMNS - 1), cursor + by));
    }

    /** The scratch of the burin. */
    private void scratch() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GRINDSTONE_USE,
                    1.8F + (float) Math.random() * 0.3F, 0.12F));
        }
    }

    /** The cell of the line under the pointer, or -1. */
    private int lineCellAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - (leftPos + LINE_X);
        int y = (int) Math.floor(mouseY) - (topPos + LINE_Y);
        if (x < 0 || y < 0 || x >= Engravings.MAX_COLUMNS * LINE_CELL || y >= LINE_HEIGHT) {
            return -1;
        }
        return x / LINE_CELL;
    }

    private void send(String[] marks, String text) {
        SpellingNetwork.sendBenchMarks(new BenchMarkPacket(menu.pos(), List.of(marks), text));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editing >= 0 && !square.isMouseOver(mouseX, mouseY)) {
            stopCarving(true);
        }
        int cell = lineCellAt(mouseX, mouseY);
        if (cell >= 0 && button == 0 && burin()) {
            startWriting(cell);
            return true;
        }
        stopWriting(true);
        int index = squareAt(mouseX, mouseY);
        if (index >= 0 && button == 0 && burin() && menu.getCarried().isEmpty()
                && !menu.getSlot(1 + index).hasItem()) {
            startCarving(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editing >= 0) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                stopCarving(true);
            } else if (key == InputConstants.KEY_ESCAPE) {
                stopCarving(false);
            } else {
                square.keyPressed(key, scanCode, modifiers);
            }
            return true;
        }
        if (cells != null) {
            switch (key) {
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> stopWriting(true);
                case InputConstants.KEY_ESCAPE -> stopWriting(false);
                case InputConstants.KEY_SPACE -> nextCell();
                case InputConstants.KEY_BACKSPACE -> backspace();
                case InputConstants.KEY_DELETE -> {
                    if (cursor < cells.size()) {
                        cells.remove(cursor);
                    }
                }
                case InputConstants.KEY_LEFT -> moveCursor(-1);
                case InputConstants.KEY_RIGHT -> moveCursor(1);
                case InputConstants.KEY_HOME -> moveCursor(-Engravings.MAX_COLUMNS);
                case InputConstants.KEY_END -> moveCursor(Engravings.MAX_COLUMNS);
                default -> {
                    // Letters and digits are runes being written, not the keys that close or drop.
                }
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (editing >= 0) {
            return square.charTyped(character, modifiers);
        }
        if (cells != null) {
            if (Character.isLetterOrDigit(character) || character == '_' || character == '-') {
                typeCharacter(character);
            }
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void removed() {
        if (editing >= 0) {
            stopCarving(true);
        }
        stopWriting(true);
        super.removed();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        String[] marks = marks();
        for (int i = 0; i < marks.length; i++) {
            int x = leftPos + GRID_X + (i % Formats.SIDE) * CELL;
            int y = topPos + GRID_Y + (i / Formats.SIDE) * CELL;
            if (i == editing) {
                graphics.fill(x, y, x + 16, y + 16, EDITING);
            }
            if (!marks[i].isEmpty()) {
                carved(graphics, Component.literal(marks[i]), x + 8.0F, y + 8.0F, MARK_SCALE, 15.0F);
            }
        }
        drawLine(graphics, mouseX, mouseY);
    }

    /** The line, carved cell by cell: glyphs for runes and numbers, a mark in a seal's frame, a dot for a pause. */
    private void drawLine(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> shown = shownCells();
        int hovered = lineCellAt(mouseX, mouseY);
        for (int i = 0; i < Engravings.MAX_COLUMNS; i++) {
            int x = leftPos + LINE_X + i * LINE_CELL;
            int y = topPos + LINE_Y;
            if (cells != null && i == cursor) {
                graphics.fill(x, y + 1, x + LINE_CELL, y + LINE_HEIGHT - 1, CURSOR);
                if (frame / 10 % 2 == 0) {
                    graphics.fill(x + 2, y + LINE_HEIGHT - 4, x + LINE_CELL - 2, y + LINE_HEIGHT - 3, CUT);
                }
            } else if (i == hovered && burin()) {
                graphics.fill(x, y + 1, x + LINE_CELL, y + LINE_HEIGHT - 1, HOVER);
            }
            if (i >= shown.size()) {
                continue;
            }
            String word = shown.get(i);
            float cx = x + LINE_CELL / 2.0F;
            float cy = y + LINE_HEIGHT / 2.0F;
            if (word.isEmpty()) {
                graphics.fill(x + 5, y + 10, x + 7, y + 12, CUT);
                continue;
            }
            String id = RuneTokens.normalize(word);
            boolean mark = !id.isEmpty() && !id.matches("-?\\d+") && DICTIONARY.lookup(id).isEmpty();
            if (mark) {
                carved(graphics, Component.literal(word), cx, cy, 0.5F, LINE_CELL - 3.0F);
                frame(graphics, x + 1, y + 3, LINE_CELL - 2, LINE_HEIGHT - 6);
            } else {
                carved(graphics, Component.literal(word).withStyle(SgaFont.STYLE), cx, cy, 1.0F, LINE_CELL - 2.0F);
            }
        }
    }

    /** A seal's frame, carved: a cut line with the light on its lower and right edges. */
    private void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.renderOutline(x + 1, y + 1, width, height, EDGE);
        graphics.renderOutline(x, y, width, height, CUT);
    }

    /**
     * Text carved in the floor, centred on {@code cx, cy}: the cut, and the light caught by its lower edge, no larger
     * than {@code scale} and shrunk to fit {@code room}.
     */
    private void carved(GuiGraphics graphics, Component text, float cx, float cy, float scale, float room) {
        int width = Math.max(1, font.width(text));
        float fitted = Math.min(scale, room / width);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().scale(fitted, fitted, 1.0F);
        int left = -width / 2;
        graphics.drawString(font, text, left + 1, -3, EDGE, false);
        graphics.drawString(font, text, left, -4, CUT, false);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        squareTooltip(graphics, mouseX, mouseY);
    }

    /** Over an empty square: its mark, and how to carve it. */
    private void squareTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = squareAt(mouseX, mouseY);
        if (index < 0 || index == editing || menu.getSlot(1 + index).hasItem() || !menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        String mark = marks()[index];
        lines.add(mark.isEmpty()
                ? Component.translatable("screen.elderlexicon.invocation_bench.unmarked").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.elderlexicon.mark", mark).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable(burin() ? "screen.elderlexicon.invocation_bench.carve_hint"
                : "screen.elderlexicon.invocation_bench.burin_hint").withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
}

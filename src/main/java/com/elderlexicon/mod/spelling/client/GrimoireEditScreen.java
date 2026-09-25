package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Book-like editor for the Runic Grimoire. Mirrors the vanilla writable book UI,
 * swaps signing for a "detach page" action, and remembers the last open page.
 */
@OnlyIn(Dist.CLIENT)
public class GrimoireEditScreen extends Screen {
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_HEIGHT = 128;
    private static final int IMAGE_WIDTH = 192;
    private static final int IMAGE_HEIGHT = 192;
    private static final Component DETACH_LABEL = Component.translatable("gui.elderlexicon.grimoire.detach");
    private static final FormattedCharSequence BLACK_CURSOR = FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.BLACK));
    private static final FormattedCharSequence GRAY_CURSOR = FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.GRAY));

    private final Player owner;
    private final ItemStack book;
    private final InteractionHand hand;
    /**
     * Whether the book contents have changed since opening.
     */
    private boolean isModified;
    /**
     * Whether the page index needs to be persisted.
     */
    private boolean pageDirty;
    private int frameTick;
    private int currentPage;
    private final List<String> pages = Lists.newArrayList();
    private static final Pattern RUNE_WORD = Pattern.compile("\\b([A-Za-z0-9_]+)\\b");
    private static final ResourceLocation SGA_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "alt");
    private static final Style SGA_STYLE = Style.EMPTY.withFont(SGA_FONT);
    private final TextFieldHelper pageEdit = new TextFieldHelper(
            this::getCurrentPageText,
            this::setCurrentPageText,
            this::getClipboard,
            this::setClipboard,
            text -> text.length() < 1024 && this.font.wordWrapHeight(text, TEXT_WIDTH) <= TEXT_HEIGHT
    );
    private long lastClickTime;
    private int lastIndex = -1;
    private PageButton forwardButton;
    private PageButton backButton;
    private Button doneButton;
    private Button detachButton;
    @Nullable
    private GrimoireEditScreen.DisplayCache displayCache = GrimoireEditScreen.DisplayCache.EMPTY;
    private Component pageMsg = CommonComponents.EMPTY;

    public GrimoireEditScreen(Player owner, ItemStack book, InteractionHand hand, int startPage) {
        super(GameNarrator.NO_TITLE);
        this.owner = owner;
        this.book = book;
        this.hand = hand;

        CompoundTag tag = book.getTag();
        if (tag != null && tag.contains("pages", 9)) {
            ListTag listtag = tag.getList("pages", 8);
            for (int i = 0; i < listtag.size(); ++i) {
                this.pages.add(listtag.getString(i));
            }
        }

        if (this.pages.isEmpty()) {
            this.pages.add("");
        }

        this.currentPage = Mth.clamp(startPage, 0, this.getNumPages() - 1);
        // Ensure the last page gets persisted even if no edits occur.
        this.pageDirty = true;
    }

    private void setClipboard(String value) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, value);
        }
    }

    private String getClipboard() {
        return this.minecraft != null ? TextFieldHelper.getClipboardContents(this.minecraft) : "";
    }

    private int getNumPages() {
        return this.pages.size();
    }

    @Override
    public void tick() {
        super.tick();
        ++this.frameTick;
    }

    @Override
    protected void init() {
        this.clearDisplayCache();
        this.detachButton = this.addRenderableWidget(Button.builder(DETACH_LABEL, button -> this.detachCurrentPage())
                .bounds(this.width / 2 - 100, 196, 98, 20).build());
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.minecraft.setScreen(null);
            this.saveChanges(false, "", -1);
        }).bounds(this.width / 2 + 2, 196, 98, 20).build());
        int i = (this.width - IMAGE_WIDTH) / 2;
        int j = 2;
        this.forwardButton = this.addRenderableWidget(new PageButton(i + 116, 159, true, p -> this.pageForward(), true));
        this.backButton = this.addRenderableWidget(new PageButton(i + 43, 159, false, p -> this.pageBack(), true));
        this.updateButtonVisibility();
    }

    private void pageBack() {
        if (this.currentPage > 0) {
            --this.currentPage;
            this.pageDirty = true;
        }

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void pageForward() {
        if (this.currentPage < this.getNumPages() - 1) {
            ++this.currentPage;
            this.pageDirty = true;
        } else {
            this.appendPageToBook();
            if (this.currentPage < this.getNumPages() - 1) {
                ++this.currentPage;
                this.pageDirty = true;
            }
        }

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void updateButtonVisibility() {
        this.backButton.visible = this.currentPage > 0;
        this.forwardButton.visible = true;
        this.doneButton.visible = true;
        this.detachButton.visible = true;
        this.detachButton.active = this.getNumPages() > 0;
    }

    private void eraseEmptyTrailingPages() {
        ListIterator<String> listiterator = this.pages.listIterator(this.pages.size());

        while (listiterator.hasPrevious() && listiterator.previous().isEmpty()) {
            listiterator.remove();
        }

        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
    }

    private void saveChanges(boolean detach, String detachedText, int detachedIndex) {
        boolean shouldSend = this.isModified || this.pageDirty || detach;
        if (!shouldSend) {
            return;
        }
        this.eraseEmptyTrailingPages();
        this.updateLocalCopy();
        SpellingNetwork.sendGrimoireUpdate(
                this.hand,
                List.copyOf(this.pages),
                this.currentPage,
                detach,
                detachedText == null ? "" : detachedText,
                detachedIndex
        );
        this.isModified = false;
        this.pageDirty = false;
    }

    private void updateLocalCopy() {
        ListTag listtag = new ListTag();
        this.pages.stream().map(StringTag::valueOf).forEach(listtag::add);
        this.book.addTagElement("pages", listtag);
        GrimoireItem.storeLastPage(this.book, this.currentPage);
    }

    private void appendPageToBook() {
        if (this.getNumPages() < 100) {
            this.pages.add("");
            this.isModified = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else {
            boolean flag = this.bookKeyPressed(keyCode, scanCode, modifiers);
            if (flag) {
                this.clearDisplayCache();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        } else if (SharedConstants.isAllowedChatCharacter(codePoint)) {
            this.pageEdit.insertText(Character.toString(codePoint));
            // When a rune collapses to a single glyph, cursor positions can drift past text end; clamp them.
            this.clampCursorToPage();
            this.clearDisplayCache();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Handles keypresses, clipboard functions, and page turning
     */
    private boolean bookKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.isSelectAll(keyCode)) {
            this.pageEdit.selectAll();
            return true;
        } else if (Screen.isCopy(keyCode)) {
            this.pageEdit.copy();
            return true;
        } else if (Screen.isPaste(keyCode)) {
            this.pageEdit.paste();
            return true;
        } else if (Screen.isCut(keyCode)) {
            this.pageEdit.cut();
            return true;
        } else {
            TextFieldHelper.CursorStep cursorStep = Screen.hasControlDown()
                    ? TextFieldHelper.CursorStep.WORD
                    : TextFieldHelper.CursorStep.CHARACTER;
            switch (keyCode) {
                case 257:
                case 335:
                    this.pageEdit.insertText("\n");
                    return true;
                case 259:
                    this.pageEdit.removeFromCursor(-1, cursorStep);
                    return true;
                case 261:
                    this.pageEdit.removeFromCursor(1, cursorStep);
                    return true;
                case 262:
                    this.pageEdit.moveBy(1, Screen.hasShiftDown(), cursorStep);
                    return true;
                case 263:
                    this.pageEdit.moveBy(-1, Screen.hasShiftDown(), cursorStep);
                    return true;
                case 264:
                    this.keyDown();
                    return true;
                case 265:
                    this.keyUp();
                    return true;
                case 266:
                    this.backButton.onPress();
                    return true;
                case 267:
                    this.forwardButton.onPress();
                    return true;
                case 268:
                    this.keyHome();
                    return true;
                case 269:
                    this.keyEnd();
                    return true;
                default:
                    return false;
            }
        }
    }

    private void keyUp() {
        this.changeLine(-1);
    }

    private void keyDown() {
        this.changeLine(1);
    }

    private void changeLine(int yChange) {
        int i = this.pageEdit.getCursorPos();
        int j = this.getDisplayCache().changeLine(i, yChange);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
    }

    private void keyHome() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToStart(Screen.hasShiftDown());
        } else {
            int i = this.pageEdit.getCursorPos();
            int j = this.getDisplayCache().findLineStart(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private void keyEnd() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToEnd(Screen.hasShiftDown());
        } else {
            GrimoireEditScreen.DisplayCache displayCache = this.getDisplayCache();
            int i = this.pageEdit.getCursorPos();
            int j = displayCache.findLineEnd(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private String getCurrentPageText() {
        return this.currentPage >= 0 && this.currentPage < this.pages.size() ? this.pages.get(this.currentPage) : "";
    }

    private void setCurrentPageText(String text) {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            String translated = translateRunes(text);
            this.pages.set(this.currentPage, translated);
            this.clampCursorToPage();
            this.isModified = true;
            this.clearDisplayCache();
        }
    }

    private static String translateRunes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        Matcher matcher = RUNE_WORD.matcher(raw);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            // A number is written one glyph per digit, so "20" becomes "SQ" even when typed a digit at a time.
            String replacement = RuneSgaMapper.glyphForRune(token)
                    .map(Object::toString)
                    .orElseGet(() -> NumberGlyphs.toGlyphs(token));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void clampCursorToPage() {
        int maxIndex = this.getCurrentPageText().length();
        this.pageEdit.setCursorPos(Math.min(this.pageEdit.getCursorPos(), maxIndex), this.pageEdit.isSelecting());
        this.pageEdit.setSelectionPos(Math.min(this.pageEdit.getSelectionPos(), maxIndex));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.setFocused((GuiEventListener) null);
        int i = (this.width - IMAGE_WIDTH) / 2;
        int j = 2;
        guiGraphics.blit(BookViewScreen.BOOK_LOCATION, i, 2, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        int j1 = this.font.width(this.pageMsg);
        guiGraphics.drawString(this.font, this.pageMsg, i - j1 + IMAGE_WIDTH - 44, 18, 0, false);
        GrimoireEditScreen.DisplayCache displayCache = this.getDisplayCache();

        for (GrimoireEditScreen.LineInfo lineInfo : displayCache.lines) {
            guiGraphics.drawString(this.font, lineInfo.asComponent, lineInfo.x, lineInfo.y, -16777216, false);
        }

        this.renderHighlight(guiGraphics, displayCache.selection);
        this.renderCursor(guiGraphics, displayCache.cursor, displayCache.cursorAtEnd);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderCursor(GuiGraphics guiGraphics, GrimoireEditScreen.Pos2i cursorPos, boolean isEndOfText) {
        if (this.frameTick / 6 % 2 == 0) {
            cursorPos = this.convertLocalToScreen(cursorPos);
            if (!isEndOfText) {
                guiGraphics.fill(cursorPos.x, cursorPos.y - 1, cursorPos.x + 1, cursorPos.y + 9, -16777216);
            } else {
                guiGraphics.drawString(this.font, "_", cursorPos.x, cursorPos.y, 0, false);
            }
        }
    }

    private void renderHighlight(GuiGraphics guiGraphics, Rect2i[] highlightAreas) {
        for (Rect2i rect2i : highlightAreas) {
            int i = rect2i.getX();
            int j = rect2i.getY();
            int k = i + rect2i.getWidth();
            int l = j + rect2i.getHeight();
            guiGraphics.fill(RenderType.guiTextHighlight(), i, j, k, l, -16776961);
        }
    }

    private GrimoireEditScreen.Pos2i convertScreenToLocal(GrimoireEditScreen.Pos2i screenPos) {
        return new GrimoireEditScreen.Pos2i(screenPos.x - (this.width - IMAGE_WIDTH) / 2 - 36, screenPos.y - 32);
    }

    private GrimoireEditScreen.Pos2i convertLocalToScreen(GrimoireEditScreen.Pos2i localScreenPos) {
        return new GrimoireEditScreen.Pos2i(localScreenPos.x + (this.width - IMAGE_WIDTH) / 2 + 36, localScreenPos.y + 32);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else {
            if (button == 0) {
                long i = Util.getMillis();
                GrimoireEditScreen.DisplayCache displayCache = this.getDisplayCache();
                int j = displayCache.getIndexAtPosition(
                        this.font, this.convertScreenToLocal(new GrimoireEditScreen.Pos2i((int) mouseX, (int) mouseY))
                );
                if (j >= 0) {
                    if (j != this.lastIndex || i - this.lastClickTime >= 250L) {
                        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
                    } else if (!this.pageEdit.isSelecting()) {
                        this.selectWord(j);
                    } else {
                        this.pageEdit.selectAll();
                    }

                    this.clearDisplayCache();
                }

                this.lastIndex = j;
                this.lastClickTime = i;
            }

            return true;
        }
    }

    private void selectWord(int index) {
        String s = this.getCurrentPageText();
        this.pageEdit.setSelectionRange(StringSplitter.getWordPosition(s, -1, index, false), StringSplitter.getWordPosition(s, 1, index, false));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        } else {
            if (button == 0) {
                GrimoireEditScreen.DisplayCache displayCache = this.getDisplayCache();
                int i = displayCache.getIndexAtPosition(
                        this.font, this.convertScreenToLocal(new GrimoireEditScreen.Pos2i((int) mouseX, (int) mouseY))
                );
                this.pageEdit.setCursorPos(i, true);
                this.clearDisplayCache();
            }

            return true;
        }
    }

    private GrimoireEditScreen.DisplayCache getDisplayCache() {
        if (this.displayCache == null) {
            // Typing a rune word turns it into one glyph, so the text shrinks right after the text helper moved the
            // cursor past what was typed; the cursor has to be pulled back inside the page before it is used.
            this.clampCursorToPage();
            this.displayCache = this.rebuildDisplayCache();
            this.pageMsg = Component.translatable("book.pageIndicator", this.currentPage + 1, this.getNumPages());
        }

        return this.displayCache;
    }

    private void clearDisplayCache() {
        this.displayCache = null;
    }

    private void clearDisplayCacheAfterPageChange() {
        this.pageEdit.setCursorToEnd();
        this.clearDisplayCache();
    }

    private GrimoireEditScreen.DisplayCache rebuildDisplayCache() {
        String s = this.getCurrentPageText();
        if (s.isEmpty()) {
            return GrimoireEditScreen.DisplayCache.EMPTY;
        } else {
            int i = this.pageEdit.getCursorPos();
            int j = this.pageEdit.getSelectionPos();
            IntList intList = new IntArrayList();
            List<GrimoireEditScreen.LineInfo> list = Lists.newArrayList();
            MutableInt mutableInt = new MutableInt();
            MutableBoolean mutableBoolean = new MutableBoolean();
            StringSplitter stringSplitter = this.font.getSplitter();
            stringSplitter.splitLines(s, TEXT_WIDTH, SGA_STYLE, true, (style, start, end) -> {
                int lineIndex = mutableInt.getAndIncrement();
                String lineRaw = s.substring(start, end);
                mutableBoolean.setValue(lineRaw.endsWith("\n"));
                String line = StringUtils.stripEnd(lineRaw, " \n");
                int y = lineIndex * 9;
                GrimoireEditScreen.Pos2i screenPos = this.convertLocalToScreen(new GrimoireEditScreen.Pos2i(0, y));
                intList.add(start);
                list.add(new GrimoireEditScreen.LineInfo(style, line, screenPos.x, screenPos.y));
            });
            int[] starts = intList.toIntArray();
            boolean cursorAtTextEnd = i == s.length();
            GrimoireEditScreen.Pos2i cursor;
            if (cursorAtTextEnd && mutableBoolean.isTrue()) {
                cursor = new GrimoireEditScreen.Pos2i(0, list.size() * 9);
            } else {
                int lineIndex = findLineFromPos(starts, i);
                int xOffset = this.font.width(s.substring(starts[lineIndex], i));
                cursor = new GrimoireEditScreen.Pos2i(xOffset, lineIndex * 9);
            }

            List<Rect2i> selectionRects = Lists.newArrayList();
            if (i != j) {
                int min = Math.min(i, j);
                int max = Math.max(i, j);
                int minLine = findLineFromPos(starts, min);
                int maxLine = findLineFromPos(starts, max);
                if (minLine == maxLine) {
                    int y = minLine * 9;
                    int lineStart = starts[minLine];
                    selectionRects.add(this.createPartialLineSelection(s, stringSplitter, min, max, y, lineStart));
                } else {
                    int lineEnd = minLine + 1 > starts.length ? s.length() : starts[minLine + 1];
                    selectionRects.add(this.createPartialLineSelection(s, stringSplitter, min, lineEnd, minLine * 9, starts[minLine]));

                    for (int line = minLine + 1; line < maxLine; line++) {
                        int y = line * 9;
                        String lineContent = s.substring(starts[line], starts[line + 1]);
                        int width = (int) stringSplitter.stringWidth(lineContent);
                        selectionRects.add(this.createSelection(new GrimoireEditScreen.Pos2i(0, y), new GrimoireEditScreen.Pos2i(width, y + 9)));
                    }

                    selectionRects.add(this.createPartialLineSelection(s, stringSplitter, starts[maxLine], max, maxLine * 9, starts[maxLine]));
                }
            }

            return new GrimoireEditScreen.DisplayCache(
                    s, cursor, cursorAtTextEnd, starts, list.toArray(new GrimoireEditScreen.LineInfo[0]), selectionRects.toArray(new Rect2i[0])
            );
        }
    }

    static int findLineFromPos(int[] lineStarts, int find) {
        int i = Arrays.binarySearch(lineStarts, find);
        return i < 0 ? -(i + 2) : i;
    }

    private Rect2i createPartialLineSelection(String input, StringSplitter splitter, int startPos, int endPos, int y, int lineStart) {
        String s = input.substring(lineStart, startPos);
        String s1 = input.substring(lineStart, endPos);
        GrimoireEditScreen.Pos2i start = new GrimoireEditScreen.Pos2i((int) splitter.stringWidth(s), y);
        GrimoireEditScreen.Pos2i end = new GrimoireEditScreen.Pos2i((int) splitter.stringWidth(s1), y + 9);
        return this.createSelection(start, end);
    }

    private Rect2i createSelection(GrimoireEditScreen.Pos2i corner1, GrimoireEditScreen.Pos2i corner2) {
        GrimoireEditScreen.Pos2i start = this.convertLocalToScreen(corner1);
        GrimoireEditScreen.Pos2i end = this.convertLocalToScreen(corner2);
        int i = Math.min(start.x, end.x);
        int j = Math.max(start.x, end.x);
        int k = Math.min(start.y, end.y);
        int l = Math.max(start.y, end.y);
        return new Rect2i(i, k, j - i, l - k);
    }

    private void detachCurrentPage() {
        if (this.pages.isEmpty()) {
            return;
        }
        String detachedText = this.getCurrentPageText();
        int detachedIndex = this.currentPage;
        this.pages.remove(detachedIndex);
        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
        this.currentPage = Mth.clamp(detachedIndex, 0, this.getNumPages() - 1);
        this.isModified = true;
        this.pageDirty = true;
        this.clearDisplayCacheAfterPageChange();
        this.updateButtonVisibility();
        this.updateLocalCopy();
        this.saveChanges(true, detachedText, detachedIndex);
    }

    @Override
    public void removed() {
        this.saveChanges(false, "", -1);
        super.removed();
    }

    @OnlyIn(Dist.CLIENT)
    static class DisplayCache {
        static final GrimoireEditScreen.DisplayCache EMPTY = new GrimoireEditScreen.DisplayCache(
                "",
                new GrimoireEditScreen.Pos2i(0, 0),
                true,
                new int[]{0},
                new GrimoireEditScreen.LineInfo[]{new GrimoireEditScreen.LineInfo(Style.EMPTY, "", 0, 0)},
                new Rect2i[0]
        );
        private final String fullText;
        final GrimoireEditScreen.Pos2i cursor;
        final boolean cursorAtEnd;
        private final int[] lineStarts;
        final GrimoireEditScreen.LineInfo[] lines;
        final Rect2i[] selection;

        public DisplayCache(
                String fullText, GrimoireEditScreen.Pos2i cursor, boolean cursorAtEnd, int[] lineStarts, GrimoireEditScreen.LineInfo[] lines, Rect2i[] selection
        ) {
            this.fullText = fullText;
            this.cursor = cursor;
            this.cursorAtEnd = cursorAtEnd;
            this.lineStarts = lineStarts;
            this.lines = lines;
            this.selection = selection;
        }

        public int getIndexAtPosition(Font font, GrimoireEditScreen.Pos2i cursorPosition) {
            int i = cursorPosition.y / 9;
            if (i < 0) {
                return 0;
            } else if (i >= this.lines.length) {
                return this.fullText.length();
            } else {
                GrimoireEditScreen.LineInfo lineInfo = this.lines[i];
                return this.lineStarts[i]
                        + font.getSplitter().plainIndexAtWidth(lineInfo.contents, cursorPosition.x, lineInfo.style);
            }
        }

        public int changeLine(int xChange, int yChange) {
            int i = GrimoireEditScreen.findLineFromPos(this.lineStarts, xChange);
            int j = i + yChange;
            int k;
            if (0 <= j && j < this.lineStarts.length) {
                int l = xChange - this.lineStarts[i];
                int i1 = this.lines[j].contents.length();
                k = this.lineStarts[j] + Math.min(l, i1);
            } else {
                k = xChange;
            }

            return k;
        }

        public int findLineStart(int line) {
            int i = GrimoireEditScreen.findLineFromPos(this.lineStarts, line);
            return this.lineStarts[i];
        }

        public int findLineEnd(int line) {
            int i = GrimoireEditScreen.findLineFromPos(this.lineStarts, line);
            return this.lineStarts[i] + this.lines[i].contents.length();
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class LineInfo {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x;
        final int y;

        public LineInfo(Style style, String contents, int x, int y) {
            this.style = style;
            this.contents = contents;
            this.x = x;
            this.y = y;
            this.asComponent = Component.literal(contents).setStyle(style);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Pos2i {
        public final int x;
        public final int y;

        Pos2i(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}

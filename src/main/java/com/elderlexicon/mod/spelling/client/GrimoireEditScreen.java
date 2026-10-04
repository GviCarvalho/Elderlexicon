package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spell.block.WrittenTexts;
import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.action.SpellActionEngine;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.block.Glossary;
import com.elderlexicon.mod.spell.block.GrimoirePage;
import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.spell.block.SpellReading;
import com.elderlexicon.mod.spell.block.SpellDescription;
import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Runic Grimoire (docs/grimorio-design.md). Closed, it shows its cover. Its first pages are the glossary: every
 * rune its maker has written in it, in the order first written, and every rune named in it with reframe. After them,
 * each page of spells opens on two sides: on the left the spell, ten ruled lines, each an independent spell as long as
 * its words go, one word per cell, each column a step of time; on the right the grimoire's own description of it (a
 * name, what it does, what it cost, notes on what its elements may do where they meet), written only once the spirit
 * has cast it. The name can be changed by clicking it. The margin is inked with a gold seal where the spirit reads the
 * line and a blot where it does not; Tab reads the page ahead in its real time. The page is kept as the same text the
 * spirit reads.
 */
@OnlyIn(Dist.CLIENT)
public class GrimoireEditScreen extends Screen {

    private static final ResourceLocation BOOK_TEXTURE =
            new ResourceLocation(ElderLexicon.MODID, "textures/gui/grimoire_book.png");
    private static final ResourceLocation COVER_TEXTURE =
            new ResourceLocation(ElderLexicon.MODID, "textures/gui/grimoire_cover.png");
    private static final Style SGA_STYLE = SgaFont.STYLE;
    private static final Component DETACH_LABEL = Component.translatable("gui.elderlexicon.grimoire.detach");

    private static final int BOOK_W = 400;
    private static final int BOOK_H = 232;
    private static final int COVER_W = 200;
    private static final int LEFT_PAGE_X = 10;
    private static final int RIGHT_PAGE_X = 202;
    private static final int PAGE_W = 188;
    private static final int SPINE_X = 200;
    private static final int CELL = 16;
    private static final int COLUMNS = 10;
    private static final int GRID_X = 16;
    private static final int GRID_Y = 30;
    private static final int MARGIN_X = 178;
    private static final int SEAL_X = 187;
    private static final int TEXT_X = 212;
    private static final int TEXT_W = 170;
    private static final int TITLE_Y = 14;
    private static final float TEXT_SCALE = 0.75F;
    private static final int CURL = 18;
    private static final int CURL_Y = BOOK_H - 27;
    private static final int ENTRIES_PER_PAGE = 6;
    private static final int ENTRY_H = 31;
    private static final int MAX_CHARS = 1024;
    private static final int MAX_WORD = 16;
    private static final int MAX_PAGES = 100;
    /** One column of the page is one step of the spirit's reading: 5 ticks. */
    private static final double SECONDS_PER_COLUMN = 0.25D;
    private static final long TURN_MS = 320L;

    private static final int INK = 0xFF2E1C10;
    private static final int FAINT_INK = 0xFF8A7050;
    private static final int RULE = 0xFFD8C8A0;
    private static final int MARGIN = 0xFFD89080;
    private static final int MARK_INK = 0xFF7A2418;
    private static final int NAMED_INK = 0xFF8A5A10;
    private static final int RUST = 0xFF8E3A1A;
    private static final int GOLD = 0xFFC8963C;
    private static final int GOLD_LIGHT = 0xFFF0CC7A;
    private static final int GOLD_DARK = 0xFF7E5624;
    private static final int COVER_GOLD = 0xFFD8A850;
    private static final int CROSSING = 0x30D8A020;
    private static final int CURSOR = 0x403A6FC8;

    private static final int[] RIBBONS = {0xFF8E1B1B, 0xFF1F4E8C, 0xFF2E6B2E, 0xFF7A4A9A, 0xFFB07818, 0xFF1D6E6E};
    private static final int RIBBON_W = 18;
    private static final int RIBBON_H = 14;

    private static final ParserDictionary DICTIONARY = ParserDictionary.load();
    private static final SpellActionEngine ENGINE = new SpellActionEngine(DICTIONARY);
    private static final SpellReading READING = new SpellReading(DICTIONARY);

    private enum View { COVER, GLOSSARY, SPELL }

    /** What the margin says about a row: whether it reads, why not, where it starts and is released, each word. */
    private record RowInfo(boolean empty, boolean reads, String issue, int start, int release,
                           List<SpellReading.Word> words) {
        static final RowInfo NONE = new RowInfo(true, false, "", -1, -1, List.of());
    }

    /** A ribbon at the book's edge for a rune named with reframe, at the page where it was named. */
    private record Bookmark(String name, List<String> spell, int page) {
    }

    /** An entry of the glossary: a rune of the language, or one named in this grimoire (with the spell it holds). */
    private record Entry(String id, List<String> spell) {
        boolean isNamed() {
            return spell != null;
        }
    }

    private final ItemStack book;
    private final InteractionHand hand;
    private final String author;
    private final boolean viewerIsAuthor;
    private final List<String> pages = new ArrayList<>();
    private final List<String> recorded = new ArrayList<>();
    /** The runes this grimoire named with reframe, each with the spell it holds. */
    private final Map<String, List<String>> named = new LinkedHashMap<>();
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private SpellReading reading = READING;
    private SpellDescription describer;
    /** The name the mage gave each page ("" where the grimoire names it itself). */
    private final List<String> names = new ArrayList<>();
    /** What each page read by the spirit cost, by its text. */
    private final Map<String, Double> readings = new LinkedHashMap<>();
    private EditBox renaming;
    private List<List<String>> rows = new ArrayList<>();
    private final List<RowInfo> infos = new ArrayList<>();
    private View view;
    private int glossaryPage;
    private int currentPage;
    private int cursorRow;
    private int cursorCol;
    private int scroll;
    private int frame;
    private boolean modified;
    private boolean pageDirty = true;
    private int left;
    private int top;
    private Button detachButton;
    private long turnStart = -1L;
    private boolean turnForward;
    private long previewStart = -1L;
    private int previewColumn = -1;

    public GrimoireEditScreen(Player owner, ItemStack book, InteractionHand hand, int startPage) {
        super(GameNarrator.NO_TITLE);
        this.book = book;
        this.hand = hand;
        CompoundTag tag = book.getTag();
        WrittenTexts.upgrade(tag);
        String viewer = owner == null ? "" : owner.getGameProfile().getName();
        UUID viewerId = owner == null ? null : owner.getUUID();
        if (tag != null && tag.hasUUID(GrimoireItem.AUTHOR_ID_TAG)) {
            author = tag.getString(GrimoireItem.AUTHOR_TAG);
            viewerIsAuthor = tag.getUUID(GrimoireItem.AUTHOR_ID_TAG).equals(viewerId);
        } else {
            author = viewer; // the first to write in it will be its maker
            viewerIsAuthor = true;
        }
        if (tag != null && tag.contains("pages", 9)) {
            ListTag list = tag.getList("pages", 8);
            for (int i = 0; i < list.size(); i++) {
                pages.add(list.getString(i));
            }
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        if (tag != null && tag.contains(GrimoireItem.GLOSSARY_TAG, 9)) {
            ListTag list = tag.getList(GrimoireItem.GLOSSARY_TAG, 8);
            for (int i = 0; i < list.size(); i++) {
                recorded.add(list.getString(i));
            }
        }
        if (tag != null && tag.contains("customRunes", 9)) {
            ListTag runes = tag.getList("customRunes", 10);
            for (int i = 0; i < runes.size(); i++) {
                CompoundTag rune = runes.getCompound(i);
                String spell = rune.getString("spell");
                // A name that is a rune of the language (or a number) is no name: it is not read as one.
                if (com.elderlexicon.mod.spelling.custom.CustomRuneHelper.isNameable(rune.getString("id"))
                        && !spell.isBlank()) {
                    named.put(rune.getString("id"), List.of(spell.trim().split("\\s+")));
                }
            }
        }
        if (tag != null && tag.contains(GrimoireItem.PAGE_NAMES_TAG, 9)) {
            ListTag list = tag.getList(GrimoireItem.PAGE_NAMES_TAG, 8);
            for (int i = 0; i < list.size(); i++) {
                names.add(list.getString(i));
            }
        }
        while (names.size() < pages.size()) {
            names.add("");
        }
        if (tag != null && tag.contains(GrimoireItem.READINGS_TAG, 9)) {
            ListTag list = tag.getList(GrimoireItem.READINGS_TAG, 10);
            for (int i = 0; i < list.size(); i++) {
                readings.put(list.getCompound(i).getString("Text"), list.getCompound(i).getDouble("Cost"));
            }
        }
        reading = READING.withNamed(named);
        describer = new SpellDescription(DICTIONARY, named);
        currentPage = Mth.clamp(startPage, 0, pages.size() - 1);
        // A grimoire with nothing written yet opens on its cover.
        view = pages.stream().allMatch(String::isBlank) ? View.COVER : View.SPELL;
        loadPage();
    }

    // ------------------------------------------------------------------ layout and views

    @Override
    protected void init() {
        left = (width - BOOK_W) / 2;
        top = Math.max(2, (height - (BOOK_H + 26)) / 2);
        int buttonsY = top + BOOK_H + 4;
        detachButton = addRenderableWidget(Button.builder(DETACH_LABEL, button -> detachCurrentPage())
                .bounds(width / 2 - 100, buttonsY, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 + 2, buttonsY, 98, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (detachButton != null) {
            detachButton.active = view == View.SPELL;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        frame++;
    }

    private int glossaryPages() {
        return Math.max(1, (entries.size() + 2 * ENTRIES_PER_PAGE - 1) / (2 * ENTRIES_PER_PAGE));
    }

    /** The glossary: the runes recorded as its maker wrote them (and those they are writing now), then the named. */
    private void listEntries() {
        entries.clear();
        List<String> runes = viewerIsAuthor
                ? Glossary.grow(recorded, pagesWithCurrent(), id -> DICTIONARY.lookup(id).isPresent())
                : recorded;
        for (String rune : runes) {
            entries.add(new Entry(rune, null));
        }
        named.forEach((name, spell) -> entries.add(new Entry(name, spell)));
    }

    private List<String> pagesWithCurrent() {
        List<String> all = new ArrayList<>(pages);
        if (currentPage < all.size()) {
            all.set(currentPage, GrimoirePage.write(rows));
        }
        return all;
    }

    // ------------------------------------------------------------------ pages

    private void loadPage() {
        rows = GrimoirePage.read(pages.get(currentPage));
        cursorRow = Mth.clamp(cursorRow, 0, GrimoirePage.ROWS - 1);
        cursorCol = Math.min(cursorCol, rows.get(cursorRow).size());
        scroll = 0;
        previewStart = -1L;
        refresh();
    }

    private void storePage() {
        pages.set(currentPage, GrimoirePage.write(rows));
    }

    /** Turns forward (+1) or back (-1): cover, the glossary's pages, then the pages of spells. */
    private void turnPage(int by) {
        if (view == View.SPELL) {
            commitCell();
            storePage();
        }
        boolean forward = by > 0;
        switch (view) {
            case COVER -> {
                if (!forward) {
                    return;
                }
                view = View.GLOSSARY;
                glossaryPage = 0;
            }
            case GLOSSARY -> {
                if (forward) {
                    if (glossaryPage + 1 < glossaryPages()) {
                        glossaryPage++;
                    } else {
                        view = View.SPELL;
                        currentPage = 0;
                        cursorRow = 0;
                        cursorCol = 0;
                        loadPage();
                    }
                } else if (glossaryPage > 0) {
                    glossaryPage--;
                } else {
                    view = View.COVER;
                }
            }
            case SPELL -> {
                int next = currentPage + by;
                if (next < 0) {
                    view = View.GLOSSARY;
                    glossaryPage = glossaryPages() - 1;
                } else {
                    if (next >= pages.size()) {
                        if (pages.size() >= MAX_PAGES) {
                            return;
                        }
                        pages.add("");
                        names.add("");
                        modified = true;
                    }
                    currentPage = next;
                    pageDirty = true;
                    cursorRow = 0;
                    cursorCol = 0;
                    loadPage();
                }
            }
        }
        startTurn(forward);
        updateButtons();
    }

    private void openSpellPage(int page) {
        if (view == View.SPELL) {
            commitCell();
            storePage();
        }
        boolean forward = view != View.SPELL || page > currentPage;
        view = View.SPELL;
        currentPage = Mth.clamp(page, 0, pages.size() - 1);
        pageDirty = true;
        cursorRow = 0;
        cursorCol = 0;
        loadPage();
        startTurn(forward);
        updateButtons();
    }

    private void startTurn(boolean forward) {
        turnStart = Util.getMillis();
        turnForward = forward;
        play(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
    }

    private void detachCurrentPage() {
        if (view != View.SPELL) {
            return;
        }
        commitCell();
        storePage();
        String detached = pages.get(currentPage);
        int index = currentPage;
        pages.remove(index);
        if (index < names.size()) {
            names.remove(index);
        }
        if (pages.isEmpty()) {
            pages.add("");
            names.add("");
        }
        currentPage = Mth.clamp(index, 0, pages.size() - 1);
        modified = true;
        loadPage();
        updateButtons();
        save(true, detached, index);
    }

    @Override
    public void onClose() {
        if (view == View.SPELL) {
            commitCell();
            storePage();
        }
        save(false, "", -1);
        super.onClose();
    }

    private void save(boolean detach, String detachedText, int detachedIndex) {
        if (!modified && !pageDirty && !detach) {
            return;
        }
        while (pages.size() > 1 && pages.get(pages.size() - 1).isEmpty()) {
            pages.remove(pages.size() - 1);
        }
        while (names.size() > pages.size()) {
            names.remove(names.size() - 1);
        }
        currentPage = Mth.clamp(currentPage, 0, pages.size() - 1);
        ListTag list = new ListTag();
        pages.stream().map(StringTag::valueOf).forEach(list::add);
        book.addTagElement("pages", list);
        WrittenTexts.mark(book);
        GrimoireItem.storeLastPage(book, currentPage);
        ListTag nameList = new ListTag();
        names.stream().map(StringTag::valueOf).forEach(nameList::add);
        book.addTagElement(GrimoireItem.PAGE_NAMES_TAG, nameList);
        SpellingNetwork.sendGrimoireUpdate(hand, List.copyOf(pages), currentPage, detach,
                detachedText == null ? "" : detachedText, detachedIndex, List.copyOf(names));
        modified = false;
        pageDirty = false;
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    /** The scratch of the quill on the page. */
    private void scratch() {
        play(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.5F + (float) Math.random() * 0.4F, 0.25F);
    }

    // ------------------------------------------------------------------ reading the rows

    /** Reads every row again: whether it reads, where it starts and is released, and what each word does. */
    private void refresh() {
        infos.clear();
        for (List<String> row : rows) {
            infos.add(readRow(row));
        }
        placeBookmarks();
        listEntries();
    }

    private RowInfo readRow(List<String> row) {
        List<String> ids = new ArrayList<>(row.size());
        List<String> spoken = new ArrayList<>();
        int start = -1;
        int release = -1;
        for (int i = 0; i < row.size(); i++) {
            String id = RuneTokens.normalize(row.get(i));
            ids.add(id);
            if (!id.isEmpty()) {
                // A named rune is read as the spell it holds, as the spirit does.
                spoken.addAll(named.getOrDefault(id, List.of(id)));
                if (start < 0) {
                    start = i;
                }
                release = i;
            }
        }
        if (spoken.isEmpty()) {
            return RowInfo.NONE;
        }
        List<SpellReading.Word> words = reading.read(ids);
        try {
            SpellActionResult result = ENGINE.generateActions(spoken);
            if (result.hasIssues()) {
                return new RowInfo(false, false, String.join("; ", result.issues()), start, release, words);
            }
            if (result.actions().isEmpty()) {
                return new RowInfo(false, false, "Nenhuma função para o espírito executar.", start, release, words);
            }
            return new RowInfo(false, true, "", start, release, words);
        } catch (RuntimeException unreadable) {
            return new RowInfo(false, false, "O espírito não entende esta linha.", start, release, words);
        }
    }

    /** A ribbon for every named rune, at the first page where its name is written (where it was named). */
    private void placeBookmarks() {
        bookmarks.clear();
        List<String> all = pagesWithCurrent();
        for (Map.Entry<String, List<String>> rune : named.entrySet()) {
            for (int page = 0; page < all.size(); page++) {
                boolean written = false;
                for (String word : all.get(page).split("\\s+")) {
                    if (rune.getKey().equals(RuneTokens.normalize(word))) {
                        written = true;
                        break;
                    }
                }
                if (written) {
                    bookmarks.add(new Bookmark(rune.getKey(), rune.getValue(), page));
                    break;
                }
            }
        }
    }

    private int pageOfNamed(String name) {
        for (Bookmark mark : bookmarks) {
            if (mark.name().equals(name)) {
                return mark.page();
            }
        }
        return -1;
    }

    /** How many spells on the page read something in {@code column}. */
    private int spokenAt(int column) {
        int spoken = 0;
        for (List<String> row : rows) {
            if (column < row.size() && !RuneTokens.normalize(row.get(column)).isEmpty()) {
                spoken++;
            }
        }
        return spoken;
    }

    private int lastRelease() {
        int last = -1;
        for (RowInfo info : infos) {
            last = Math.max(last, info.release());
        }
        return last;
    }

    // ------------------------------------------------------------------ editing

    private List<String> row() {
        return rows.get(cursorRow);
    }

    /** The word being typed is kept as the page writes it: a rune's name as its glyph, a number's digits as glyphs. */
    private void commitCell() {
        List<String> row = row();
        if (cursorCol < row.size()) {
            String word = row.get(cursorCol);
            String written = RuneTokens.written(word);
            if (written.length() == 1 && Character.isLetter(written.charAt(0))) {
                written = written.toUpperCase(Locale.ROOT);
            }
            row.set(cursorCol, written);
        }
    }

    /** Applies an edit if the page still fits in what a grimoire can hold. */
    private boolean edit(Runnable change) {
        List<List<String>> before = copyRows();
        change.run();
        if (GrimoirePage.write(rows).length() > MAX_CHARS) {
            rows = before;
            return false;
        }
        modified = true;
        storePage();
        refresh();
        keepCursorInView();
        return true;
    }

    private List<List<String>> copyRows() {
        List<List<String>> copy = new ArrayList<>();
        for (List<String> row : rows) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }

    private void keepCursorInView() {
        if (cursorCol < scroll) {
            scroll = cursorCol;
        } else if (cursorCol >= scroll + COLUMNS) {
            scroll = cursorCol - COLUMNS + 1;
        }
    }

    private void typeCharacter(char character) {
        boolean done = edit(() -> {
            List<String> row = row();
            if (cursorCol >= row.size()) {
                row.add(String.valueOf(character));
                cursorCol = row.size() - 1;
            } else if (row.get(cursorCol).length() < MAX_WORD) {
                row.set(cursorCol, row.get(cursorCol) + character);
            }
        });
        if (done) {
            scratch();
        }
    }

    /** Space: on to the next cell; past the end, a pause (an empty cell). */
    private void nextCell() {
        commitCell();
        edit(() -> {
            List<String> row = row();
            if (cursorCol < row.size()) {
                cursorCol++;
            } else {
                row.add("");
                cursorCol = row.size();
            }
        });
    }

    private void insertPause() {
        commitCell();
        edit(() -> {
            List<String> row = row();
            row.add(Math.min(cursorCol, row.size()), "");
        });
    }

    private void backspace() {
        edit(() -> {
            List<String> row = row();
            if (row.isEmpty()) {
                // An empty line at the cursor: it goes, and the cursor goes to the end of the line above.
                if (cursorRow > 0) {
                    rows.remove(cursorRow);
                    rows.add(new ArrayList<>());
                    cursorRow--;
                    cursorCol = row().size();
                }
                return;
            }
            if (cursorCol >= row.size()) {
                cursorCol = row.size() - 1;
            }
            String word = row.get(cursorCol);
            if (!word.isEmpty()) {
                row.set(cursorCol, word.substring(0, word.length() - 1));
            } else {
                row.remove(cursorCol);
                cursorCol = Math.max(0, cursorCol - 1);
            }
        });
    }

    private void deleteCell() {
        edit(() -> {
            List<String> row = row();
            if (cursorCol < row.size()) {
                row.remove(cursorCol);
            }
        });
    }

    /** Enter: the line breaks at the cursor, what comes after it starting a new line below. */
    private void breakLine() {
        commitCell();
        if (!rows.get(GrimoirePage.ROWS - 1).isEmpty() || cursorRow >= GrimoirePage.ROWS - 1) {
            if (cursorRow < GrimoirePage.ROWS - 1) {
                cursorRow++;
                cursorCol = 0;
                scroll = 0;
            }
            return;
        }
        edit(() -> {
            List<String> row = row();
            int from = Math.min(row.size(), cursorCol + (cursorCol < row.size() && !row.get(cursorCol).isEmpty() ? 1 : 0));
            List<String> rest = new ArrayList<>(row.subList(from, row.size()));
            row.subList(from, row.size()).clear();
            rows.remove(GrimoirePage.ROWS - 1);
            rows.add(cursorRow + 1, rest);
            cursorRow++;
            cursorCol = 0;
        });
        scroll = 0;
    }

    private void moveCursor(int rowsBy, int colsBy) {
        commitCell();
        cursorRow = Mth.clamp(cursorRow + rowsBy, 0, GrimoirePage.ROWS - 1);
        cursorCol = Mth.clamp(cursorCol + colsBy, 0, row().size());
        refresh();
        keepCursorInView();
    }

    private void paste(String text) {
        commitCell();
        List<String> words = new ArrayList<>();
        for (String word : text.trim().split("\\s+")) {
            if (!word.isBlank()) {
                words.add(RuneTokens.written(word));
            }
        }
        if (words.isEmpty()) {
            return;
        }
        edit(() -> {
            List<String> row = row();
            int at = Math.min(cursorCol, row.size());
            row.addAll(at, words);
            cursorCol = at + words.size();
        });
    }

    /** Tab: the page read ahead, a light running through the columns at the pace the spirit reads them. */
    private void readAhead() {
        commitCell();
        refresh();
        previewStart = Util.getMillis();
        previewColumn = -1;
        scroll = 0;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (renaming != null) {
            return renaming.charTyped(character, modifiers);
        }
        if (super.charTyped(character, modifiers)) {
            return true;
        }
        if (view == View.SPELL && (Character.isLetterOrDigit(character) || character == '_' || character == '-')) {
            typeCharacter(character);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (renaming != null) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                finishRenaming(true);
            } else if (key == InputConstants.KEY_ESCAPE) {
                finishRenaming(false);
            } else {
                renaming.keyPressed(key, scanCode, modifiers);
            }
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (view != View.SPELL) {
            switch (key) {
                case InputConstants.KEY_PAGEDOWN, InputConstants.KEY_RIGHT, InputConstants.KEY_RETURN,
                        InputConstants.KEY_SPACE -> turnPage(1);
                case InputConstants.KEY_PAGEUP, InputConstants.KEY_LEFT -> turnPage(-1);
                default -> {
                    return super.keyPressed(key, scanCode, modifiers);
                }
            }
            return true;
        }
        if (Screen.isCopy(key)) {
            commitCell();
            storePage();
            if (minecraft != null) {
                TextFieldHelper.setClipboardContents(minecraft, pages.get(currentPage));
            }
            return true;
        }
        if (Screen.isPaste(key)) {
            if (minecraft != null) {
                paste(TextFieldHelper.getClipboardContents(minecraft));
            }
            return true;
        }
        boolean shift = Screen.hasShiftDown();
        switch (key) {
            case InputConstants.KEY_TAB -> readAhead();
            case InputConstants.KEY_SPACE -> {
                if (shift) {
                    insertPause();
                } else {
                    nextCell();
                }
            }
            case InputConstants.KEY_INSERT -> insertPause();
            case InputConstants.KEY_BACKSPACE -> backspace();
            case InputConstants.KEY_DELETE -> deleteCell();
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> breakLine();
            case InputConstants.KEY_LEFT -> moveCursor(0, -1);
            case InputConstants.KEY_RIGHT -> moveCursor(0, 1);
            case InputConstants.KEY_UP -> moveCursor(-1, 0);
            case InputConstants.KEY_DOWN -> moveCursor(1, 0);
            case InputConstants.KEY_HOME -> moveCursor(0, -cursorCol);
            case InputConstants.KEY_END -> moveCursor(0, row().size() - cursorCol);
            case InputConstants.KEY_PAGEUP -> turnPage(-1);
            case InputConstants.KEY_PAGEDOWN -> turnPage(1);
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (view == View.COVER) {
            if (mouseX >= coverLeft() && mouseX < coverLeft() + COVER_W && mouseY >= top && mouseY < top + BOOK_H) {
                turnPage(1);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inBox(mouseX, mouseY, LEFT_PAGE_X + 1, CURL_Y, CURL, CURL)) {
            turnPage(-1);
            return true;
        }
        if (inBox(mouseX, mouseY, RIGHT_PAGE_X + PAGE_W - CURL - 1, CURL_Y, CURL, CURL)) {
            turnPage(1);
            return true;
        }
        int ribbon = bookmarkAt(mouseX, mouseY);
        if (ribbon >= 0) {
            openSpellPage(bookmarks.get(ribbon).page());
            return true;
        }
        if (view == View.GLOSSARY) {
            Entry entry = entryAt(mouseX, mouseY);
            if (entry != null && entry.isNamed() && pageOfNamed(entry.id()) >= 0) {
                openSpellPage(pageOfNamed(entry.id()));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (renaming != null) {
            if (renaming.isMouseOver(mouseX, mouseY)) {
                return renaming.mouseClicked(mouseX, mouseY, button);
            }
            finishRenaming(true);
        }
        if (inBox(mouseX, mouseY, TEXT_X, TITLE_Y - 2, TEXT_W, 12)) {
            startRenaming();
            return true;
        }
        int[] cell = cellAt(mouseX, mouseY);
        if (cell != null) {
            commitCell();
            cursorRow = cell[0];
            cursorCol = Math.min(cell[1], rows.get(cursorRow).size());
            refresh();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (view != View.SPELL) {
            return false;
        }
        int longest = 0;
        for (List<String> row : rows) {
            longest = Math.max(longest, row.size());
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, longest + 1 - COLUMNS));
        return true;
    }

    private boolean inBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= left + x && mouseX < left + x + w && mouseY >= top + y && mouseY < top + y + h;
    }

    /** The row and column under the pointer, or null off the written page. */
    private int[] cellAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - (left + GRID_X);
        int y = (int) Math.floor(mouseY) - (top + GRID_Y);
        if (view != View.SPELL || x < 0 || y < 0 || x >= COLUMNS * CELL || y >= GrimoirePage.ROWS * CELL) {
            return null;
        }
        return new int[]{y / CELL, scroll + x / CELL};
    }

    /** The name of the page on show: the one the mage gave it, or the grimoire's own once the spirit has read it. */
    private String pageName() {
        String given = currentPage < names.size() ? names.get(currentPage) : "";
        if (!given.isBlank()) {
            return given;
        }
        return readings.containsKey(pages.get(currentPage)) ? description().name() : "Feitiço sem nome";
    }

    private SpellDescription.Text description() {
        List<List<String>> ids = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> line = new ArrayList<>();
            for (String word : row) {
                line.add(RuneTokens.normalize(word));
            }
            ids.add(line);
        }
        return describer.describe(ids);
    }

    private void startRenaming() {
        commitCell();
        storePage();
        renaming = new EditBox(font, left + TEXT_X, top + TITLE_Y - 2, TEXT_W, 12, Component.literal("Nome"));
        renaming.setMaxLength(40);
        renaming.setValue(pageName());
        renaming.setFocused(true);
        addRenderableWidget(renaming);
        setFocused(renaming);
    }

    private void finishRenaming(boolean keep) {
        if (renaming == null) {
            return;
        }
        if (keep) {
            String name = renaming.getValue().trim();
            while (names.size() <= currentPage) {
                names.add("");
            }
            // Written back as the grimoire's own name, it is not a name given.
            names.set(currentPage, readings.containsKey(pages.get(currentPage)) && name.equals(description().name())
                    ? "" : name);
            modified = true;
        }
        removeWidget(renaming);
        renaming = null;
    }

    /** The glossary entry under the pointer, or null. */
    private Entry entryAt(double mouseX, double mouseY) {
        if (view != View.GLOSSARY) {
            return null;
        }
        for (int side = 0; side < 2; side++) {
            int x0 = left + (side == 0 ? LEFT_PAGE_X : RIGHT_PAGE_X);
            if (mouseX < x0 + 8 || mouseX >= x0 + PAGE_W - 8) {
                continue;
            }
            int slot = (int) Math.floor((mouseY - (top + 22)) / ENTRY_H);
            if (slot < 0 || slot >= ENTRIES_PER_PAGE) {
                return null;
            }
            int index = glossaryPage * 2 * ENTRIES_PER_PAGE + side * ENTRIES_PER_PAGE + slot;
            return index < entries.size() ? entries.get(index) : null;
        }
        return null;
    }

    /** Where the {@code index}th ribbon is drawn: left, top, right, bottom. */
    private int[] ribbonBox(int index, Bookmark mark) {
        int out = view == View.SPELL && mark.page() == currentPage ? RIBBON_W + 4 : RIBBON_W;
        int x0 = left + BOOK_W - 6;
        int y0 = top + 26 + index * (RIBBON_H + 3);
        return new int[]{x0, y0, x0 + out, y0 + RIBBON_H};
    }

    private int bookmarkAt(double mouseX, double mouseY) {
        if (view == View.COVER) {
            return -1;
        }
        for (int i = 0; i < bookmarks.size() && i < GrimoirePage.ROWS; i++) {
            int[] box = ribbonBox(i, bookmarks.get(i));
            if (mouseX >= box[0] + 6 && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                return i;
            }
        }
        return -1;
    }

    private int coverLeft() {
        return (width - COVER_W) / 2;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (view == View.COVER) {
            drawCover(graphics);
            drawTurning(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        drawBookmarks(graphics);
        graphics.blit(BOOK_TEXTURE, left, top, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
        if (view == View.GLOSSARY) {
            drawGlossary(graphics, mouseX, mouseY);
        } else {
            drawRules(graphics);
            drawInstant(graphics, mouseX, mouseY);
            drawReadingAhead(graphics);
            drawCells(graphics);
            drawMargin(graphics);
            drawDescription(graphics, mouseX, mouseY);
            drawPageNumber(graphics);
        }
        drawTurning(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTooltip(graphics, mouseX, mouseY);
    }

    /** The cover, after EVL's grimoire: the sigil, and its maker's name in the old glyphs. */
    private void drawCover(GuiGraphics graphics) {
        int x = coverLeft();
        graphics.blit(COVER_TEXTURE, x, top, 0, 0, COVER_W, BOOK_H, COVER_W, BOOK_H);
        drawScaled(graphics, Component.literal(author.toLowerCase(Locale.ROOT)).withStyle(SGA_STYLE),
                x + COVER_W / 2.0F, top + 134, 1.5F, COVER_GOLD);
        drawScaled(graphics, Component.literal("grimorio").withStyle(SGA_STYLE), x + COVER_W / 2.0F, top + 160, 1.0F,
                0xFFA07A38);
    }

    private void drawScaled(GuiGraphics graphics, Component text, float centerX, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawLeft(GuiGraphics graphics, Component text, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** The glossary: each rune its maker wrote, with its glyph, name and what it is; then the runes named here. */
    private void drawGlossary(GuiGraphics graphics, int mouseX, int mouseY) {
        if (glossaryPage == 0) {
            drawScaled(graphics, Component.literal("Glossário"), left + LEFT_PAGE_X + PAGE_W / 2.0F, top + 11, 1.0F,
                    INK);
        }
        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(
                    "Nada registrado ainda. Cada runa entra aqui quando o criador a escreve pela primeira vez."), 300);
            for (int i = 0; i < lines.size(); i++) {
                graphics.pose().pushPose();
                graphics.pose().translate(left + LEFT_PAGE_X + 14, top + 40 + i * 6, 0);
                graphics.pose().scale(0.5F, 0.5F, 1.0F);
                graphics.drawString(font, lines.get(i), 0, 0, FAINT_INK, false);
                graphics.pose().popPose();
            }
        }
        Entry hovered = entryAt(mouseX, mouseY);
        for (int side = 0; side < 2; side++) {
            int x0 = left + (side == 0 ? LEFT_PAGE_X : RIGHT_PAGE_X);
            for (int slot = 0; slot < ENTRIES_PER_PAGE; slot++) {
                int index = glossaryPage * 2 * ENTRIES_PER_PAGE + side * ENTRIES_PER_PAGE + slot;
                if (index >= entries.size()) {
                    break;
                }
                Entry entry = entries.get(index);
                int y = top + 22 + slot * ENTRY_H;
                if (entry == hovered) {
                    graphics.fill(x0 + 8, y, x0 + PAGE_W - 8, y + ENTRY_H - 2, 0x14603A10);
                }
                drawEntry(graphics, entry, x0 + 10, y);
                graphics.fill(x0 + 12, y + ENTRY_H - 2, x0 + PAGE_W - 12, y + ENTRY_H - 1, RULE);
            }
        }
        int onRight = entries.size() - glossaryPage * 2 * ENTRIES_PER_PAGE - ENTRIES_PER_PAGE;
        if (glossaryPage == glossaryPages() - 1 && onRight <= ENTRIES_PER_PAGE - 2) {
            String[] hints = {"Espaço · próxima célula   Shift+Espaço · pausa", "Enter · nova linha   Tab · ler adiante",
                    "PgUp / PgDn · virar a página"};
            for (int i = 0; i < hints.length; i++) {
                drawScaled(graphics, Component.literal(hints[i]), left + RIGHT_PAGE_X + PAGE_W / 2.0F,
                        top + BOOK_H - 44 + i * 7, 0.5F, FAINT_INK);
            }
        }
    }

    private void drawEntry(GuiGraphics graphics, Entry entry, int x, int y) {
        String glyph = entry.isNamed() ? entry.id() : RuneSgaMapper.glyphForRune(entry.id()).map(String::valueOf)
                .orElse(entry.id());
        Component glyphText = Component.literal(glyph).withStyle(SGA_STYLE);
        float scale = Math.min(2.0F, 22.0F / Math.max(1, font.width(glyphText)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 12, y + 14, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, glyphText, -font.width(glyphText) / 2, -4, entry.isNamed() ? NAMED_INK : INK, false);
        graphics.pose().popPose();
        String title = entry.isNamed() ? "“" + entry.id() + "” · nomeada" : READING.title(entry.id());
        drawLeft(graphics, Component.literal(title), x + 28, y + 3, 0.75F, entry.isNamed() ? NAMED_INK : INK);
        String about = entry.isNamed() ? "Guarda: " + String.join(" ", entry.spell())
                + (pageOfNamed(entry.id()) >= 0 ? " · pág. " + (pageOfNamed(entry.id()) + 1) : "")
                : SpellReading.lore(entry.id());
        List<FormattedCharSequence> lines = font.split(Component.literal(about), 280);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 28, y + 13 + i * 6, 0);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(font, lines.get(i), 0, 0, FAINT_INK, false);
            graphics.pose().popPose();
        }
    }

    /** The spell page is ruled like a notebook: ten lines and the margin. */
    private void drawRules(GuiGraphics graphics) {
        for (int r = 0; r < GrimoirePage.ROWS; r++) {
            int y = top + GRID_Y + r * CELL + CELL - 1;
            graphics.fill(left + GRID_X - 2, y, left + MARGIN_X, y + 1, RULE);
        }
        graphics.fill(left + MARGIN_X, top + 12, left + MARGIN_X + 1, top + BOOK_H - 30, MARGIN);
    }

    /** The instant under the pointer: a faint band down that column, golden where two or more spells meet. */
    private void drawInstant(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return;
        }
        int x = left + GRID_X + (cell[1] - scroll) * CELL;
        graphics.fill(x + 1, top + GRID_Y, x + CELL, top + GRID_Y + GrimoirePage.ROWS * CELL,
                spokenAt(cell[1]) >= 2 ? CROSSING : 0x142E1C10);
    }

    /** Reading ahead: the light runs through the columns in their real time; each spell chimes as it is released. */
    private void drawReadingAhead(GuiGraphics graphics) {
        if (previewStart < 0L) {
            return;
        }
        double column = (Util.getMillis() - previewStart) / (SECONDS_PER_COLUMN * 1000.0D);
        int reached = (int) Math.floor(column);
        if (reached > lastRelease() + 1) {
            previewStart = -1L;
            return;
        }
        if (reached != previewColumn) {
            previewColumn = reached;
            if (reached >= scroll + COLUMNS) {
                scroll = reached - COLUMNS + 1;
            }
            for (RowInfo info : infos) {
                if (info.release() == reached) {
                    play(info.reads() ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_BASS.value(),
                            1.0F + 0.05F * reached, 0.8F);
                }
            }
        }
        float x = (float) (left + GRID_X + (column - scroll) * CELL);
        if (x < left + GRID_X || x > left + GRID_X + COLUMNS * CELL) {
            return;
        }
        int x0 = (int) x;
        graphics.fill(x0, top + GRID_Y, x0 + CELL, top + GRID_Y + GrimoirePage.ROWS * CELL, 0x40F0C060);
        graphics.fill(x0 + CELL - 1, top + GRID_Y, x0 + CELL, top + GRID_Y + GrimoirePage.ROWS * CELL, 0x90F0C060);
    }

    private void drawCells(GuiGraphics graphics) {
        boolean blink = frame / 10 % 2 == 0;
        for (int r = 0; r < GrimoirePage.ROWS; r++) {
            List<String> row = rows.get(r);
            RowInfo info = infos.get(r);
            int y = top + GRID_Y + r * CELL;
            for (int i = 0; i < COLUMNS; i++) {
                int column = scroll + i;
                int x = left + GRID_X + i * CELL;
                if (r == cursorRow && column == cursorCol) {
                    graphics.fill(x + 1, y + 1, x + CELL, y + CELL - 1, CURSOR);
                    if (blink) {
                        graphics.fill(x + 3, y + CELL - 3, x + CELL - 2, y + CELL - 2, INK);
                    }
                }
                if (column >= row.size()) {
                    continue;
                }
                String word = row.get(column);
                if (word.isEmpty()) {
                    graphics.fill(x + 7, y + 7, x + 9, y + 9, 0x502E1C10); // a pause
                } else {
                    drawWord(graphics, word, x, y, !info.empty() && !info.reads());
                }
            }
        }
    }

    /**
     * A word in its cell, in the old glyphs, shrunk to fit: a mark is framed like a seal, a named rune is in gold ink,
     * and the words of a line the spirit cannot read are in rust.
     */
    private void drawWord(GuiGraphics graphics, String word, int x, int y, boolean unread) {
        String id = RuneTokens.normalize(word);
        boolean isNamed = named.containsKey(id);
        boolean mark = !id.isEmpty() && !isNamed && !id.matches("-?\\d+") && DICTIONARY.lookup(id).isEmpty();
        Component text = Component.literal(word).withStyle(SGA_STYLE);
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(1.0F, 13.0F / textWidth);
        int color = unread ? RUST : isNamed ? NAMED_INK : mark ? MARK_INK : INK;
        graphics.pose().pushPose();
        graphics.pose().translate(x + CELL / 2.0F + 0.5F, y + CELL / 2.0F, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth / 2, -4, color, false);
        graphics.pose().popPose();
        if (mark) {
            graphics.renderOutline(x + 1, y + 1, CELL - 1, CELL - 1, MARK_INK);
        }
    }

    /** The margin, inked: a small gold seal beside a line the spirit reads, a blot beside one it does not. */
    private void drawMargin(GuiGraphics graphics) {
        boolean reading = previewStart >= 0L;
        for (int r = 0; r < GrimoirePage.ROWS; r++) {
            RowInfo info = infos.get(r);
            if (info.empty()) {
                continue;
            }
            int cx = left + SEAL_X;
            int cy = top + GRID_Y + r * CELL + 7;
            if (info.reads()) {
                boolean released = reading && previewColumn >= info.release();
                int body = released ? GOLD_LIGHT : GOLD;
                graphics.fill(cx - 2, cy - 3, cx + 3, cy + 4, body);
                graphics.fill(cx - 3, cy - 2, cx + 4, cy + 3, body);
                graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD_DARK);
                graphics.fill(cx, cy, cx + 1, cy + 1, GOLD_LIGHT);
            } else {
                graphics.fill(cx - 2, cy - 2, cx + 3, cy + 2, RUST);
                graphics.fill(cx - 3, cy - 1, cx + 1, cy + 3, RUST);
                graphics.fill(cx + 1, cy + 2, cx + 3, cy + 4, 0xC08E3A1A);
                graphics.fill(cx - 4, cy - 3, cx - 3, cy - 2, 0x908E3A1A);
            }
        }
    }

    /**
     * The right page: the grimoire's own description of the spell, written once the spirit has cast it. Its name (click
     * to change it), what each spell does and when, what the page cost when it was read, and notes on what its
     * elements may do where they meet.
     */
    private void drawDescription(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + TEXT_X;
        int bottom = top + BOOK_H - 30;
        if (renaming == null) {
            boolean hovered = inBox(mouseX, mouseY, TEXT_X, TITLE_Y - 2, TEXT_W, 12);
            Component title = Component.literal(pageName());
            drawScaled(graphics, title, x + TEXT_W / 2.0F, top + TITLE_Y, 1.0F, hovered ? GOLD : NAMED_INK);
        }
        graphics.fill(x + 30, top + TITLE_Y + 11, x + TEXT_W - 30, top + TITLE_Y + 12, RULE);
        float y = top + TITLE_Y + 18;
        if (infos.stream().allMatch(RowInfo::empty)) {
            paragraph(graphics, "Página em branco. Escreva o feitiço à esquerda.", x, y, bottom, FAINT_INK);
            return;
        }
        Double cost = readings.get(pages.get(currentPage));
        if (cost == null) {
            y = paragraph(graphics, "O grimório ainda não conhece este feitiço.", x, y, bottom, FAINT_INK);
            paragraph(graphics, "Ele só o descreve depois que o espírito o lê de verdade (surgit com esta página "
                    + "aberta). Se a página mudar, será preciso lê-la de novo.", x, y + 3, bottom, FAINT_INK);
            return;
        }
        SpellDescription.Text text = description();
        for (String line : text.paragraphs()) {
            y = paragraph(graphics, line, x, y, bottom, INK) + 2;
        }
        y = paragraph(graphics, String.format(Locale.ROOT, "Custo: %.1f UMU", cost).replace('.', ','), x, y + 2, bottom,
                NAMED_INK) + 2;
        if (!text.notes().isEmpty() && y < bottom) {
            y = paragraph(graphics, "Notas", x, y + 2, bottom, FAINT_INK);
            for (String note : text.notes()) {
                y = paragraph(graphics, note, x, y, bottom, FAINT_INK) + 2;
            }
        }
    }

    /** A paragraph wrapped to the page, from {@code y}; returns where the next one starts. Past the page, it is cut. */
    private float paragraph(GuiGraphics graphics, String text, int x, float y, int bottom, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) (TEXT_W / TEXT_SCALE));
        float lineHeight = 9 * TEXT_SCALE;
        for (FormattedCharSequence line : lines) {
            if (y + lineHeight > bottom) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y - lineHeight, 0);
                graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
                graphics.drawString(font, "\u2026", (int) (TEXT_W / TEXT_SCALE) - 6, 0, color, false);
                graphics.pose().popPose();
                return bottom;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            graphics.drawString(font, line, 0, 0, color, false);
            graphics.pose().popPose();
            y += lineHeight;
        }
        return y;
    }

    /** The page's number, written in the old digits. */
    private void drawPageNumber(GuiGraphics graphics) {
        Component number = Component.literal(NumberGlyphs.toGlyphs(String.valueOf(currentPage + 1))).withStyle(SGA_STYLE);
        drawScaled(graphics, number, left + LEFT_PAGE_X + PAGE_W / 2.0F, top + BOOK_H - 22, 1.0F, FAINT_INK);
    }

    /** The ribbons of the named runes, sticking out of the book's right edge; the one of this page sticks out more. */
    private void drawBookmarks(GuiGraphics graphics) {
        for (int i = 0; i < bookmarks.size() && i < GrimoirePage.ROWS; i++) {
            Bookmark mark = bookmarks.get(i);
            int[] box = ribbonBox(i, mark);
            int color = RIBBONS[i % RIBBONS.length];
            graphics.fill(box[0], box[1], box[2], box[3], color);
            graphics.fill(box[0], box[3] - 1, box[2], box[3], 0x40000000);
            graphics.fill(box[2] - 2, box[1] + RIBBON_H / 2 - 1, box[2], box[1] + RIBBON_H / 2 + 1, 0xFF140C08);
            Component label = Component.literal(mark.name()).withStyle(SGA_STYLE);
            float scale = Math.min(1.0F, 12.0F / Math.max(1, font.width(label)));
            graphics.pose().pushPose();
            graphics.pose().translate(box[2] - 10.0F, box[1] + RIBBON_H / 2.0F, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, label, -font.width(label) / 2, -4, 0xFFF4E6C0, false);
            graphics.pose().popPose();
        }
    }

    /** A page turning: a leaf sweeping from one side of the spine to the other. */
    private void drawTurning(GuiGraphics graphics) {
        if (turnStart < 0L) {
            return;
        }
        float t = (Util.getMillis() - turnStart) / (float) TURN_MS;
        if (t >= 1.0F) {
            turnStart = -1L;
            return;
        }
        if (view == View.COVER) {
            return;
        }
        int spine = left + SPINE_X;
        int y0 = top + 8;
        int y1 = top + BOOK_H - 8;
        boolean firstHalf = t < 0.5F;
        float width = PAGE_W * (firstHalf ? 1.0F - 2.0F * t : 2.0F * t - 1.0F);
        boolean onRight = firstHalf == turnForward;
        int x0 = onRight ? spine : (int) (spine - width);
        int x1 = onRight ? (int) (spine + width) : spine;
        if (x1 - x0 < 1) {
            return;
        }
        graphics.fill(x0, y0, x1, y1, 0xFFF7F0DC);
        int edge = onRight ? x1 - 1 : x0;
        graphics.fill(edge, y0, edge + 1, y1, 0xFFC8B890);
        int shadow = onRight ? x0 : x1 - 3;
        graphics.fill(shadow, y0, shadow + 3, y1, 0x30000000);
    }

    private void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        int[] cell = cellAt(mouseX, mouseY);
        int ribbon = bookmarkAt(mouseX, mouseY);
        Entry entry = entryAt(mouseX, mouseY);
        if (cell != null) {
            List<String> row = rows.get(cell[0]);
            RowInfo info = infos.get(cell[0]);
            if (cell[1] < row.size() && cell[1] < info.words().size()) {
                SpellReading.Word word = info.words().get(cell[1]);
                MutableComponent title = Component.literal("");
                if (!row.get(cell[1]).isEmpty()) {
                    title.append(Component.literal(row.get(cell[1])).withStyle(SGA_STYLE)).append("  ");
                }
                title.append(Component.literal(word.title()).withStyle(ChatFormatting.GOLD));
                lines.add(title.getVisualOrderText());
                if (!word.rune().isEmpty() && DICTIONARY.lookup(word.rune()).isPresent()) {
                    wrap(lines, SpellReading.lore(word.rune()), ChatFormatting.GRAY);
                }
                wrap(lines, word.role(), ChatFormatting.WHITE);
                int together = spokenAt(cell[1]);
                String instant = timeOf(cell[1]) + (together >= 2 ? " · " + together + " feitiços neste instante" : "");
                lines.add(Component.literal(instant).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            }
        } else if (ribbon >= 0) {
            Bookmark mark = bookmarks.get(ribbon);
            StringBuilder glyphs = new StringBuilder();
            for (String rune : mark.spell()) {
                glyphs.append(RuneTokens.written(rune)).append(' ');
            }
            lines.add(Component.literal("Marcador · ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(mark.name()).withStyle(SGA_STYLE))
                    .append(Component.literal("  " + mark.name()).withStyle(ChatFormatting.GOLD)).getVisualOrderText());
            lines.add(Component.literal("Guarda: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(glyphs.toString().trim()).withStyle(SGA_STYLE)).getVisualOrderText());
            wrap(lines, String.join(" ", mark.spell()), ChatFormatting.DARK_GRAY);
            lines.add(Component.literal("Nomeada na página " + (mark.page() + 1)).withStyle(ChatFormatting.DARK_GRAY)
                    .getVisualOrderText());
        } else if (renaming == null && inBox(mouseX, mouseY, TEXT_X, TITLE_Y - 2, TEXT_W, 12)) {
            lines.add(Component.literal("Clique para dar outro nome a este feitiço").withStyle(ChatFormatting.GRAY)
                    .getVisualOrderText());
        } else if (entry != null) {
            if (entry.isNamed()) {
                lines.add(Component.literal("“" + entry.id() + "”, runa nomeada").withStyle(ChatFormatting.GOLD)
                        .getVisualOrderText());
                wrap(lines, "Guarda: " + String.join(" ", entry.spell()), ChatFormatting.GRAY);
                if (pageOfNamed(entry.id()) >= 0) {
                    lines.add(Component.literal("Clique para ir à página " + (pageOfNamed(entry.id()) + 1))
                            .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                }
            } else {
                lines.add(Component.literal(READING.title(entry.id())).withStyle(ChatFormatting.GOLD).getVisualOrderText());
                wrap(lines, SpellReading.lore(entry.id()), ChatFormatting.GRAY);
                RuneSgaMapper.glyphForRune(entry.id()).ifPresent(glyph -> lines.add(Component.literal("Tecla: " + glyph)
                        .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()));
            }
        } else if (view == View.SPELL && mouseX >= left + SEAL_X - 6 && mouseX < left + SEAL_X + 7
                && mouseY >= top + GRID_Y && mouseY < top + GRID_Y + GrimoirePage.ROWS * CELL) {
            RowInfo info = infos.get((mouseY - top - GRID_Y) / CELL);
            if (!info.empty()) {
                if (info.reads()) {
                    wrap(lines, "O espírito lê este feitiço. Solto na " + timeOf(info.release()).toLowerCase(Locale.ROOT)
                            + ".", ChatFormatting.GREEN);
                } else {
                    wrap(lines, "O espírito não lê: " + info.issue(), ChatFormatting.RED);
                }
            }
        }
        if (!lines.isEmpty()) {
            graphics.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void wrap(List<FormattedCharSequence> lines, String text, ChatFormatting color) {
        lines.addAll(font.split(Component.literal(text).withStyle(color), 220));
    }

    private static String timeOf(int column) {
        return String.format(Locale.ROOT, "Coluna %d · %.2f s", column, column * SECONDS_PER_COLUMN)
                .replace('.', ',');
    }
}

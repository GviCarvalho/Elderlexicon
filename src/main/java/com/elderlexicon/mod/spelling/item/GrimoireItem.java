package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.spelling.client.RuneSgaMapper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Spell notebook that stores sequences as SGA glyphs.
 */
public final class GrimoireItem extends WritableBookItem {

    private static final String SPELLS_TAG = "Spells";
    static final String LAST_PAGE_TAG = "LastPage";

    public GrimoireItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Vanilla openItemGui only accepts the default writable book, so open the editor directly.
        if (level.isClientSide) {
            openClientScreen(player, stack, hand, getStoredPage(stack));
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClientScreen(Player player, ItemStack stack, InteractionHand hand, int startPage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.setScreen(new com.elderlexicon.mod.spelling.client.GrimoireEditScreen(player, stack, hand, startPage));
    }

    public static int getStoredPage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LAST_PAGE_TAG)) {
            return 0;
        }
        return Math.max(0, tag.getInt(LAST_PAGE_TAG));
    }

    public static void storeLastPage(ItemStack stack, int page) {
        stack.getOrCreateTag().putInt(LAST_PAGE_TAG, Math.max(0, page));
    }

    /** The pages the spirit has read (their text and what they cost), and the names the mage gave the pages. */
    public static final String READINGS_TAG = "Readings";
    public static final String PAGE_NAMES_TAG = "PageNames";
    private static final int MAX_READINGS = 100;

    /**
     * The spirit read a page with this {@code text} and it cost {@code spent} UMU (added to what it cost so far when
     * {@code more}: spells of the page released later). The grimoire describes a page only once it has been read.
     */
    public static void recordReading(ItemStack stack, String text, double spent, boolean more) {
        if (!(stack.getItem() instanceof GrimoireItem) || text == null) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        ListTag readings = tag.getList(READINGS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < readings.size(); i++) {
            CompoundTag reading = readings.getCompound(i);
            if (text.equals(reading.getString("Text"))) {
                reading.putDouble("Cost", (more ? reading.getDouble("Cost") : 0.0D) + Math.max(0.0D, spent));
                tag.put(READINGS_TAG, readings);
                return;
            }
        }
        CompoundTag reading = new CompoundTag();
        reading.putString("Text", text);
        reading.putDouble("Cost", Math.max(0.0D, spent));
        readings.add(reading);
        while (readings.size() > MAX_READINGS) {
            readings.remove(0);
        }
        tag.put(READINGS_TAG, readings);
    }

    /** Who made the grimoire (the first to write in it), and the runes they have written in it so far. */
    public static final String AUTHOR_TAG = "Author";
    public static final String AUTHOR_ID_TAG = "AuthorId";
    public static final String GLOSSARY_TAG = "Glossary";
    private static final com.elderlexicon.mod.parser.ParserDictionary DICTIONARY =
            com.elderlexicon.mod.parser.ParserDictionary.load();

    /**
     * {@code writer} saved {@code pages}: the first to write in a grimoire becomes its maker, and every rune of the
     * language its maker writes joins the glossary at the front of the book, in the order first written.
     */
    public static void recordWriting(ItemStack stack, Player writer, List<String> pages) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(AUTHOR_ID_TAG)) {
            tag.putUUID(AUTHOR_ID_TAG, writer.getUUID());
            tag.putString(AUTHOR_TAG, writer.getGameProfile().getName());
        }
        if (!writer.getUUID().equals(tag.getUUID(AUTHOR_ID_TAG))) {
            return;
        }
        List<String> known = new ArrayList<>();
        ListTag stored = tag.getList(GLOSSARY_TAG, Tag.TAG_STRING);
        for (int i = 0; i < stored.size(); i++) {
            known.add(stored.getString(i));
        }
        ListTag glossary = new ListTag();
        com.elderlexicon.mod.spell.block.Glossary.grow(known, pages, id -> DICTIONARY.lookup(id).isPresent())
                .forEach(id -> glossary.add(StringTag.valueOf(id)));
        tag.put(GLOSSARY_TAG, glossary);
    }

    /**
     * Stores a rune sequence into the grimoire, sanitizing the identifiers.
     */
    public static void appendSpell(ItemStack stack, List<String> runes) {
        if (!(stack.getItem() instanceof GrimoireItem) || runes == null || runes.isEmpty()) {
            return;
        }
        List<String> sanitized = runes.stream()
                .filter(rune -> rune != null && !rune.isBlank())
                .map(rune -> rune.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (sanitized.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        ListTag entries = getSpellList(tag);
        entries.add(StringTag.valueOf(String.join(" ", sanitized)));
        tag.put(SPELLS_TAG, entries);
    }

    /**
     * Reads all recorded sequences.
     */
    public static List<List<String>> readSpells(ItemStack stack) {
        if (!(stack.getItem() instanceof GrimoireItem)) {
            return List.of();
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(SPELLS_TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag entries = tag.getList(SPELLS_TAG, Tag.TAG_STRING);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<List<String>> spells = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            String raw = entries.getString(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            List<String> tokens = Arrays.stream(raw.split("\\s+"))
                    .filter(token -> token != null && !token.isBlank())
                    .map(token -> token.trim().toLowerCase(Locale.ROOT))
                    .toList();
            if (!tokens.isEmpty()) {
                spells.add(tokens);
            }
        }
        return Collections.unmodifiableList(spells);
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level level,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.elderlexicon.grimoire.summary")
                .withStyle(ChatFormatting.DARK_AQUA));

        List<List<String>> spells = readSpells(stack);
        if (spells.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.elderlexicon.grimoire.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        int displayed = Math.min(4, spells.size());
        for (int i = 0; i < displayed; i++) {
            Component sequenceComponent = RuneSgaMapper.sequenceComponent(spells.get(i));
            tooltip.add(Component.translatable("tooltip.elderlexicon.grimoire.entry", i + 1, sequenceComponent)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (spells.size() > displayed) {
            tooltip.add(Component.translatable("tooltip.elderlexicon.grimoire.more", spells.size() - displayed)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static ListTag getSpellList(CompoundTag tag) {
        if (tag.contains(SPELLS_TAG, Tag.TAG_LIST)) {
            return tag.getList(SPELLS_TAG, Tag.TAG_STRING);
        }
        ListTag list = new ListTag();
        tag.put(SPELLS_TAG, list);
        return list;
    }
}

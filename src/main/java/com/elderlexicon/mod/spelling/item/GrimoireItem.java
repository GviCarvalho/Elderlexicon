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

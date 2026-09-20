package com.elderlexicon.mod.spelling.custom;

import com.elderlexicon.mod.spelling.client.RuneSgaMapper;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores and resolves player-authored rune shortcuts inside grimoires.
 */
public final class CustomRuneHelper {

    private static final String CUSTOM_RUNES_TAG = "customRunes";
    private static final String ID_TAG = "id";
    private static final String SPELL_TAG = "spell";
    private static final int MAX_RECURSION = 4;
    private static final Pattern RUNE_TOKEN = Pattern.compile("\\b([A-Za-z0-9_]+)\\b");

    private CustomRuneHelper() {
    }

    public static String normalizeRuneId(String runeId) {
        if (runeId == null) {
            return null;
        }
        String normalized = runeId.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean saveCustomRune(ItemStack stack, String runeId, List<String> runes) {
        String normalizedId = normalizeRuneId(runeId);
        if (!(stack.getItem() instanceof GrimoireItem) || normalizedId == null || runes == null || runes.isEmpty()) {
            return false;
        }
        List<String> sanitized = sanitizeRunes(runes);
        if (sanitized.isEmpty()) {
            return false;
        }

        CompoundTag tag = stack.getOrCreateTag();
        ListTag entries = tag.getList(CUSTOM_RUNES_TAG, Tag.TAG_COMPOUND);
        CompoundTag payload = new CompoundTag();
        payload.putString(ID_TAG, normalizedId);
        payload.putString(SPELL_TAG, String.join(" ", sanitized));

        boolean replaced = false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag existing = entries.getCompound(i);
            if (normalizedId.equals(existing.getString(ID_TAG))) {
                entries.set(i, payload);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            entries.add(payload);
        }
        tag.put(CUSTOM_RUNES_TAG, entries);
        return true;
    }

    public static Optional<List<String>> findCustomRune(Player player, String runeId) {
        String normalizedId = normalizeRuneId(runeId);
        if (player == null || normalizedId == null) {
            return Optional.empty();
        }
        for (ItemStack stack : player.getInventory().items) {
            Optional<List<String>> resolved = findCustomRune(stack, normalizedId);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        Optional<List<String>> main = findCustomRune(player.getMainHandItem(), normalizedId);
        if (main.isPresent()) {
            return main;
        }
        return findCustomRune(player.getOffhandItem(), normalizedId);
    }

    private static Optional<List<String>> findCustomRune(ItemStack stack, String runeId) {
        if (!(stack.getItem() instanceof GrimoireItem) || runeId == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CUSTOM_RUNES_TAG, Tag.TAG_LIST)) {
            return Optional.empty();
        }
        ListTag entries = tag.getList(CUSTOM_RUNES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!runeId.equals(entry.getString(ID_TAG))) {
                continue;
            }
            String spell = entry.getString(SPELL_TAG);
            if (spell == null || spell.isBlank()) {
                continue;
            }
            List<String> tokens = Arrays.stream(spell.split("\\s+"))
                    .filter(token -> token != null && !token.isBlank())
                    .map(token -> token.trim().toLowerCase(Locale.ROOT))
                    .toList();
            if (!tokens.isEmpty()) {
                return Optional.of(tokens);
            }
        }
        return Optional.empty();
    }

    public static List<String> expandRunes(Player player, List<String> runes) {
        if (player == null || runes == null || runes.isEmpty()) {
            return runes == null ? List.of() : List.copyOf(runes);
        }
        List<String> output = new ArrayList<>();
        for (String rune : runes) {
            expandInto(player, normalizeRuneId(rune), output, new HashSet<>(), 0);
        }
        return output;
    }

    private static void expandInto(Player player,
                                   String runeId,
                                   List<String> output,
                                   Set<String> stack,
                                   int depth) {
        if (runeId == null) {
            return;
        }
        if (depth >= MAX_RECURSION || stack.contains(runeId)) {
            output.add(runeId);
            return;
        }
        Optional<List<String>> replacement = findCustomRune(player, runeId);
        if (replacement.isEmpty()) {
            output.add(runeId);
            return;
        }
        stack.add(runeId);
        for (String token : replacement.get()) {
            expandInto(player, normalizeRuneId(token), output, stack, depth + 1);
        }
        stack.remove(runeId);
    }

    private static List<String> sanitizeRunes(List<String> runes) {
        List<String> sanitized = new ArrayList<>(runes.size());
        for (String rune : runes) {
            String normalized = normalizeRuneId(rune);
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    /**
     * Removes a custom rune from the provided grimoire stack.
     *
     * @param stack  the grimoire
     * @param runeId the identifier to remove
     * @return true if the rune was found and removed
     */
    public static boolean removeCustomRune(ItemStack stack, String runeId) {
        String normalizedId = normalizeRuneId(runeId);
        if (!(stack.getItem() instanceof GrimoireItem) || stack.isEmpty() || normalizedId == null) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CUSTOM_RUNES_TAG, Tag.TAG_LIST)) {
            return false;
        }
        ListTag entries = tag.getList(CUSTOM_RUNES_TAG, Tag.TAG_COMPOUND);
        boolean removed = false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (normalizedId.equals(entry.getString(ID_TAG))) {
                entries.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            if (entries.isEmpty()) {
                tag.remove(CUSTOM_RUNES_TAG);
            } else {
                tag.put(CUSTOM_RUNES_TAG, entries);
            }
        }
        return removed;
    }

    /**
     * Removes any stored custom rune whose identifier no longer appears in any grimoire page.
     *
     * @param stack the grimoire
     * @param pages sanitized page contents
     * @return true if at least one entry was pruned
     */
    public static boolean pruneAbsentCustomRunes(ItemStack stack, List<String> pages) {
        if (!(stack.getItem() instanceof GrimoireItem) || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CUSTOM_RUNES_TAG, Tag.TAG_LIST)) {
            return false;
        }
        Set<String> present = extractRunesFromPages(pages);
        ListTag entries = tag.getList(CUSTOM_RUNES_TAG, Tag.TAG_COMPOUND);
        boolean removed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            CompoundTag entry = entries.getCompound(i);
            String id = entry.getString(ID_TAG);
            if (!present.contains(id)) {
                entries.remove(i);
                removed = true;
            }
        }
        if (removed) {
            if (entries.isEmpty()) {
                tag.remove(CUSTOM_RUNES_TAG);
            } else {
                tag.put(CUSTOM_RUNES_TAG, entries);
            }
        }
        return removed;
    }

    private static Set<String> extractRunesFromPages(List<String> pages) {
        Set<String> runes = new HashSet<>();
        if (pages == null || pages.isEmpty()) {
            return runes;
        }
        for (String page : pages) {
            if (page == null || page.isBlank()) {
                continue;
            }
            Matcher matcher = RUNE_TOKEN.matcher(page);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (token == null || token.isBlank()) {
                    continue;
                }
                String normalized = normalizeRuneId(resolveRuneToken(token));
                if (normalized != null) {
                    runes.add(normalized);
                }
            }
        }
        return runes;
    }

    private static String resolveRuneToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.length() == 1) {
            return RuneSgaMapper.runeForGlyph(token.charAt(0)).orElse(token);
        }
        return token;
    }
}

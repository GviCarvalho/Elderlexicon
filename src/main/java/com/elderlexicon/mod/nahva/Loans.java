package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.spelling.data.RuneUnlocks;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Runes the tower lends a climber for a trial (the speech, {@link TowerRoom#SPEECH}): known for as long as the climb
 * lasts, and put in an empty slot so they can be said. When the climb ends (won, given up or fallen) they are given
 * back: what the climber did not know before is forgotten, and the slots the tower filled are emptied. What they knew
 * already is never touched.
 */
final class Loans {

    private static final String RUNES = "lentRunes";
    private static final String SLOTS = "lentSlots";

    private Loans() {
    }

    static void lend(ServerPlayer player, CompoundTag dream, List<String> runes) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        ListTag lentRunes = dream.getList(RUNES, Tag.TAG_STRING);
        ListTag lentSlots = dream.getList(SLOTS, Tag.TAG_STRING);
        boolean changed = false;
        for (String rune : runes) {
            if (!repertoire.isUnlocked(rune) && RuneUnlocks.unlock(player, rune)) {
                lentRunes.add(StringTag.valueOf(rune));
            }
            if (!repertoire.slots().contains(rune)) {
                int empty = repertoire.slots().indexOf("");
                if (empty >= 0 && repertoire.assignSlot(empty, rune)) {
                    lentSlots.add(StringTag.valueOf(empty + ":" + rune));
                    changed = true;
                }
            }
        }
        dream.put(RUNES, lentRunes);
        dream.put(SLOTS, lentSlots);
        if (changed) {
            SpellingNetwork.syncToPlayer(player, repertoire);
        }
        player.displayClientMessage(Component.translatable("tower.elderlexicon.lent", String.join(", ", runes))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
    }

    /** The climb is over: what was lent goes back. */
    static void repay(ServerPlayer player, CompoundTag dream) {
        if (!dream.contains(RUNES) && !dream.contains(SLOTS)) {
            return;
        }
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        for (Tag slot : dream.getList(SLOTS, Tag.TAG_STRING)) {
            String[] parts = slot.getAsString().split(":", 2);
            int index = Integer.parseInt(parts[0]);
            if (parts[1].equals(repertoire.slots().get(index))) {
                repertoire.assignSlot(index, "");
            }
        }
        for (Tag rune : dream.getList(RUNES, Tag.TAG_STRING)) {
            RuneUnlocks.lock(player, rune.getAsString());
        }
        dream.remove(RUNES);
        dream.remove(SLOTS);
        SpellingNetwork.syncToPlayer(player, repertoire);
    }
}

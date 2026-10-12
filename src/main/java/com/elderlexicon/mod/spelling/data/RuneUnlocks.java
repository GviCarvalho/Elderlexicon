package com.elderlexicon.mod.spelling.data;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Learning runes (docs/repertorio-design.md). Runes have no levels and need no other rune first: each is unlocked on its
 * own, under the circumstances that teach it. In creative every rune counts as unlocked without being learned.
 */
public final class RuneUnlocks {

    private RuneUnlocks() {
    }

    /** Teaches {@code runeId} to {@code player}; true when they did not know it. They are told, and their client too. */
    public static boolean unlock(ServerPlayer player, String runeId) {
        if (player == null || !Lexicons.get().isRune(runeId)) {
            return false;
        }
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        if (!repertoire.unlock(runeId)) {
            return false;
        }
        player.displayClientMessage(Component.translatable("message.elderlexicon.rune_unlocked", runeId)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                1.0F, 1.2F);
        SpellingNetwork.syncToPlayer(player, repertoire);
        return true;
    }

    /** Makes {@code player} forget {@code runeId}, taking it out of their slots; true when they knew it. */
    public static boolean lock(ServerPlayer player, String runeId) {
        if (player == null) {
            return false;
        }
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        if (!repertoire.lock(runeId)) {
            return false;
        }
        SpellingNetwork.syncToPlayer(player, repertoire);
        return true;
    }
}

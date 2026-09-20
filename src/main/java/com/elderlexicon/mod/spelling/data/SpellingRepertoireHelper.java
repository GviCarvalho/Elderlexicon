package com.elderlexicon.mod.spelling.data;

import net.minecraft.world.entity.player.Player;

public final class SpellingRepertoireHelper {

    private SpellingRepertoireHelper() {
    }

    public static SpellingRepertoire get(Player player) {
        return SpellingRepertoireCapability.get(player).orElseThrow(() ->
                new IllegalStateException("Spelling repertoire missing for player " + player.getScoreboardName()));
    }
}

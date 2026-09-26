package com.elderlexicon.mod.spelling.server;

import com.elderlexicon.mod.spell.SpellCastingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a cast tells its caster. By default a spell that works speaks through its effect alone, and one that fails
 * says why on the action bar; the transcription, cost and scene notes of every spell go to chat only for players who
 * asked for them with {@code /spell debug}.
 */
public final class SpellFeedback {

    private static final Set<UUID> DETAILED = ConcurrentHashMap.newKeySet();

    private SpellFeedback() {
    }

    public static boolean detailed(ServerPlayer player) {
        return player != null && DETAILED.contains(player.getUUID());
    }

    /** Turns the details on or off for {@code player}; returns whether they are now on. */
    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (DETAILED.remove(id)) {
            return false;
        }
        DETAILED.add(id);
        return true;
    }

    /** The response sent back for a spell cast in trance, stripped of details unless the player asked for them. */
    static ServerSpellingController.SpellCastResponse forPlayer(ServerPlayer player,
                                                                ServerSpellingController.SpellCastResponse response) {
        if (!response.success() || detailed(player)) {
            return response;
        }
        return new ServerSpellingController.SpellCastResponse(true, Component.empty(), List.of(),
                response.cooldownMs(), response.runes());
    }

    /** Reports a spell that fired on its own (a later line of a page, a ritual step). */
    static void later(ServerPlayer player, SpellCastingService.Result result, List<Component> warnings, String prefix) {
        if (detailed(player)) {
            player.sendSystemMessage(prefix.isEmpty() ? result.message() : Component.literal(prefix).append(result.message()));
            warnings.forEach(player::sendSystemMessage);
        } else if (result.failed()) {
            player.displayClientMessage(prefix.isEmpty() ? result.message() : Component.literal(prefix).append(result.message()), true);
        }
    }
}

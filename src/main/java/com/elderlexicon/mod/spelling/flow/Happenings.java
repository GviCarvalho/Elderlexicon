package com.elderlexicon.mod.spelling.flow;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What just happened to each mage's body ({@code attack}, {@code hurt}, {@code kill}), in flow or not
 * (docs/fluxo-design.md). A condition rune is true while its happening is recent: the spirit reads it as an "if" at the
 * instant it reads the line, whoever made it read (the trance, surgit, a mark, a ritual, the flow).
 */
public final class Happenings {

    /** How long a happening still counts as now, in ticks: one second. */
    public static final int RECENT_TICKS = 20;

    private static final Map<UUID, Map<String, Long>> LAST = new ConcurrentHashMap<>();

    private Happenings() {
    }

    /** {@code trigger} happens to {@code player} now. */
    public static void mark(ServerPlayer player, String trigger) {
        LAST.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(trigger, player.serverLevel().getGameTime());
    }

    /** Whether {@code trigger} happened to {@code player} within the last second. */
    public static boolean recent(ServerPlayer player, String trigger) {
        Map<String, Long> last = LAST.get(player.getUUID());
        Long when = last == null ? null : last.get(trigger);
        return when != null && player.serverLevel().getGameTime() - when <= RECENT_TICKS;
    }

    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }
}

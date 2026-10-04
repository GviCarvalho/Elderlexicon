package com.elderlexicon.mod.spelling.flow;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * What is happening to each mage's body, in flow or not (docs/fluxo-design.md). A condition rune is an "if" the spirit
 * reads at the instant it reads the line, whoever made it read (the trance, surgit, a mark, a ritual, the flow):
 * <ul>
 *   <li>a <b>happening</b> ({@code attack}, {@code jump}, {@code break}…) holds while it is recent, for a second;</li>
 *   <li>a <b>state</b> ({@code sneak}, {@code burning}, {@code underwater}…) holds while it lasts, and in flow it wakes
 *       the spirit the moment it begins.</li>
 * </ul>
 */
public final class Happenings {

    /** How long a happening still counts as now, in ticks: one second. */
    public static final int RECENT_TICKS = 20;
    /** Below this share of its health, the mage languishes. */
    public static final float LOW_HEALTH = 0.3F;

    public static final String ATTACK = "attack";
    public static final String HURT = "hurt";
    public static final String KILL = "kill";
    public static final String JUMP = "jump";
    public static final String LAND = "land";
    public static final String BREAK = "break";
    public static final String USE = "use";
    public static final String SNEAK = "sneak";
    public static final String SPRINT = "sprint";
    public static final String LOW = "low_health";
    public static final String BURNING = "burning";
    public static final String UNDERWATER = "underwater";

    /** The states of the body, each with how the world tells it holds. */
    private static final Map<String, Predicate<ServerPlayer>> STATES = new LinkedHashMap<>();

    static {
        STATES.put(SNEAK, ServerPlayer::isShiftKeyDown);
        STATES.put(SPRINT, ServerPlayer::isSprinting);
        STATES.put(LOW, player -> player.getHealth() <= player.getMaxHealth() * LOW_HEALTH);
        STATES.put(BURNING, ServerPlayer::isOnFire);
        STATES.put(UNDERWATER, ServerPlayer::isUnderWater);
    }

    private static final Map<UUID, Map<String, Long>> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> HOLDING = new ConcurrentHashMap<>();

    private Happenings() {
    }

    /** {@code trigger} happens to {@code player} now. */
    public static void mark(ServerPlayer player, String trigger) {
        LAST.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(trigger, player.serverLevel().getGameTime());
    }

    /** Whether {@code trigger} holds for {@code player} now: a state that lasts, or a happening of the last second. */
    public static boolean recent(ServerPlayer player, String trigger) {
        Predicate<ServerPlayer> state = STATES.get(trigger);
        if (state != null) {
            return state.test(player);
        }
        Map<String, Long> last = LAST.get(player.getUUID());
        Long when = last == null ? null : last.get(trigger);
        return when != null && player.serverLevel().getGameTime() - when <= RECENT_TICKS;
    }

    /** Looks at the states of {@code player}'s body this tick; returns those that have just begun. */
    public static List<String> begun(ServerPlayer player) {
        Set<String> held = HOLDING.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        List<String> begun = new ArrayList<>();
        STATES.forEach((trigger, state) -> {
            if (state.test(player)) {
                if (held.add(trigger)) {
                    begun.add(trigger);
                }
            } else {
                held.remove(trigger);
            }
        });
        return begun;
    }

    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
        HOLDING.remove(player.getUUID());
    }
}

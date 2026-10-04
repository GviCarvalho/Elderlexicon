package com.elderlexicon.mod.spelling.flow;

import com.elderlexicon.mod.spell.Mana;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The state of flow (docs/fluxo-design.md): a trance held for as long as the mage wants and can pay for. The spirit
 * keeps listening, and when a happening of the mage's body comes ({@code attack}, {@code hurt}, {@code kill}), it reads
 * all that surgit would read, and the armor worn, and casts the lines whose conditions speak of that happening.
 * <p>
 * Holding it is an open tap: it costs {@link #BASE_UMU_PER_SECOND} UMU of Vis a second at first, and more the longer it
 * lasts. When the Vis runs out, the flow comes undone; the spirit takes nothing from the flesh for it, since holding the
 * channel open is no order.
 */
public final class FlowState {

    /** What the first second of flow costs, in UMU of Vis. */
    public static final double BASE_UMU_PER_SECOND = 0.5D;
    /** How much the cost of a second grows with each second held: one UMU more every fifty seconds. */
    public static final double GROWTH_PER_SECOND = 0.02D;
    /** How long a happening must wait to wake the same lines again, so a spell that wakes them cannot spin forever. */
    private static final int REFRACTORY_TICKS = 10;
    /** The woken lines are cast on the next tick, out of the happening that woke them. */
    private static final int WAKE_DELAY_TICKS = 1;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private FlowState() {
    }

    /** What a second of flow costs after {@code seconds} held, in UMU. */
    public static double umuPerSecond(double seconds) {
        return BASE_UMU_PER_SECOND + GROWTH_PER_SECOND * Math.max(0.0D, seconds);
    }

    public static boolean flowing(ServerPlayer player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    /** Enters flow, or leaves it when already in it. */
    public static void toggle(ServerPlayer player) {
        if (flowing(player)) {
            leave(player, Component.translatable("message.elderlexicon.flow.leave"));
        } else {
            enter(player);
        }
    }

    public static void enter(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!player.isCreative() && Mana.points(player.experienceLevel, player.experienceProgress) < 1.0D) {
            player.displayClientMessage(Component.translatable("message.elderlexicon.flow.no_mana"), true);
            return;
        }
        SESSIONS.put(player.getUUID(), new Session());
        player.displayClientMessage(Component.translatable("message.elderlexicon.flow.enter"), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                0.4F, 1.6F);
        SpellingNetwork.sendFlowState(player, true);
    }

    /** Leaves flow, telling the mage {@code why} on the action bar (nothing when null). */
    public static void leave(ServerPlayer player, Component why) {
        if (player == null || SESSIONS.remove(player.getUUID()) == null) {
            return;
        }
        if (why != null) {
            player.displayClientMessage(why, true);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                0.4F, 1.6F);
        SpellingNetwork.sendFlowState(player, false);
    }

    /** Forgets the mage without a word (it left the world). */
    public static void forget(ServerPlayer player) {
        if (player != null) {
            SESSIONS.remove(player.getUUID());
        }
    }

    /** One tick of flow: the tap runs, and the mage glows faintly for those around. */
    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            leave(player, null);
            return;
        }
        session.ticks++;
        if (!player.isCreative()) {
            session.owedPoints += umuPerSecond(session.ticks / 20.0D) / 20.0D * Mana.XP_PER_UMU;
            int whole = (int) Math.floor(session.owedPoints);
            if (whole > 0) {
                if (Mana.points(player.experienceLevel, player.experienceProgress) < whole) {
                    leave(player, Component.translatable("message.elderlexicon.flow.exhausted"));
                    return;
                }
                player.giveExperiencePoints(-whole);
                session.owedPoints -= whole;
            }
        }
        if (session.ticks % 10 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 3, 0.4D, 0.6D, 0.4D, 0.4D);
        }
    }

    /**
     * {@code trigger} happened to the mage: in flow, the spirit reads what it listens to and wakes the lines that wait
     * for it. A happening that just woke them must wait a moment before it wakes them again.
     */
    public static void happen(ServerPlayer player, String trigger) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = session.lastWake.get(trigger);
        if (last != null && now - last < REFRACTORY_TICKS) {
            return;
        }
        session.lastWake.put(trigger, now);
        SpellTicks.schedule(player.server, WAKE_DELAY_TICKS, () -> {
            if (player.isAlive() && !player.hasDisconnected() && flowing(player)) {
                ServerSpellingController.getInstance().wake(player, trigger);
            }
        });
    }

    private static final class Session {
        private long ticks;
        /** Points of experience owed for the time held, paid whole as they add up. */
        private double owedPoints;
        private final Map<String, Long> lastWake = new HashMap<>();
    }
}

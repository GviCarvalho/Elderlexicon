package com.elderlexicon.mod.spelling.flow;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.Mana;
import com.elderlexicon.mod.spell.block.LineConditions;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final Map<UUID, Map<String, Other>> OTHERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> HOLDING = new ConcurrentHashMap<>();
    /** How far below each falling mage the ground is, in blocks (none while not falling). */
    private static final Map<UUID, Double> GROUND_BELOW = new ConcurrentHashMap<>();
    /** Which fall each mage is in: a new number each time one begins. */
    private static final Map<UUID, Long> FALLS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_FALL = new AtomicLong();
    /** How far below a falling mage the ground is looked for. */
    public static final int GROUND_REACH = 32;

    private Happenings() {
    }

    /**
     * The other one of a happening, what {@code ille} points at: whom the mage struck, who hurt the mage, the creature
     * that died (where it died), the block broken (where it was), what the mage used the item on.
     */
    public sealed interface Other permits OtherEntity, OtherBlock, OtherPoint {
    }

    public record OtherEntity(Entity entity) implements Other {
    }

    public record OtherBlock(ServerLevel level, BlockPos pos) implements Other {
    }

    public record OtherPoint(ServerLevel level, Vec3 at) implements Other {
    }

    /** {@code trigger} happens to {@code player} now. */
    public static void mark(ServerPlayer player, String trigger) {
        mark(player, trigger, null);
    }

    /** {@code trigger} happens to {@code player} now, with {@code other} as the other one of it (null: no one). */
    public static void mark(ServerPlayer player, String trigger, @Nullable Other other) {
        LAST.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(trigger, player.serverLevel().getGameTime());
        Map<String, Other> others = OTHERS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        if (other == null) {
            others.remove(trigger);
        } else {
            others.put(trigger, other);
        }
    }

    /**
     * The other one of the most recent happening among {@code triggers} (any happening, when empty) that came within
     * the last second and had one.
     */
    public static Optional<Other> recentOther(ServerPlayer player, Collection<String> triggers) {
        Map<String, Long> last = LAST.get(player.getUUID());
        Map<String, Other> others = OTHERS.get(player.getUUID());
        if (last == null || others == null) {
            return Optional.empty();
        }
        long now = player.serverLevel().getGameTime();
        String best = null;
        long bestAt = Long.MIN_VALUE;
        for (Map.Entry<String, Other> entry : others.entrySet()) {
            Long when = last.get(entry.getKey());
            if (when == null || now - when > RECENT_TICKS
                    || !triggers.isEmpty() && !triggers.contains(entry.getKey())) {
                continue;
            }
            if (when > bestAt) {
                best = entry.getKey();
                bestAt = when;
            }
        }
        return best == null ? Optional.empty() : Optional.of(others.get(best));
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

    /**
     * Looks at the mage's fall this tick: while falling (two blocks or more already), how far below the ground is, and
     * the block it is. Returns that distance, or -1 when the mage is not falling or no ground is within reach.
     */
    public static double fall(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean falling = !player.onGround() && !player.isFallFlying() && !player.getAbilities().flying
                && player.getDeltaMovement().y < -0.05D && player.fallDistance >= 2.0F && !player.isInWater();
        if (!falling) {
            GROUND_BELOW.remove(id);
            FALLS.remove(id);
            return -1.0D;
        }
        FALLS.computeIfAbsent(id, key -> NEXT_FALL.incrementAndGet());
        Vec3 feet = player.position();
        BlockHitResult hit = player.level().clip(new ClipContext(feet, feet.subtract(0.0D, GROUND_REACH, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            GROUND_BELOW.remove(id);
            return -1.0D;
        }
        double below = feet.y - hit.getLocation().y;
        GROUND_BELOW.put(id, below);
        mark(player, LineConditions.FALLING, new OtherBlock(player.serverLevel(), hit.getBlockPos()));
        return below;
    }

    /** How far below the falling mage the ground is, in blocks; negative when not falling. */
    public static double groundBelow(ServerPlayer player) {
        return GROUND_BELOW.getOrDefault(player.getUUID(), -1.0D);
    }

    /** Which fall the mage is in, or 0 when not falling. */
    public static long fallOf(ServerPlayer player) {
        return FALLS.getOrDefault(player.getUUID(), 0L);
    }

    /**
     * The scene the conditions of a line are read in for {@code player} (docs/fluxo-design.md): the happenings and
     * states, the fall, and the body: how much of each element it holds (the Vita), and of vis, the mana.
     */
    public static LineConditions.Scene scene(ServerPlayer player) {
        return new LineConditions.Scene() {
            @Override
            public boolean recent(String trigger) {
                return Happenings.recent(player, trigger);
            }

            @Override
            public double amount(String source) {
                VitaElement element = Lexicons.get().elementOf(source);
                if (element.isBalanced()) {
                    return Mana.umuOf(Mana.points(player.experienceLevel, player.experienceProgress));
                }
                return VitaSystem.body(player).getOrDefault(element, 0.0D);
            }

            @Override
            public double groundBelow() {
                return Happenings.groundBelow(player);
            }
        };
    }

    public static void forget(ServerPlayer player) {
        GROUND_BELOW.remove(player.getUUID());
        FALLS.remove(player.getUUID());
        LAST.remove(player.getUUID());
        OTHERS.remove(player.getUUID());
        HOLDING.remove(player.getUUID());
    }
}

package com.elderlexicon.mod.spell.nature;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.physics.Box;
import com.elderlexicon.mod.magic.physics.Field;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.vita.VitaElement;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * The drives in each world (docs/particulas-design.md, stage 9): holds the {@link WorldField} of each world, runs it
 * every tick, and is where spells put what they bring into it. Whatever element or spell brought it, the drives decide
 * what it does once it is there.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class NatureWorld {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, WorldField> FIELDS = new WeakHashMap<>();
    /** How long a vocant's flame lasts by itself: a moment, one second. */
    public static final int FLAME_TICKS = 20;
    /** What holding one flame for that moment takes, in UMU (see {@link #flame}). */
    private static final double FLAME_MOMENT = measureFlame();

    private NatureWorld() {
    }

    private static WorldField fieldOf(ServerLevel level) {
        return FIELDS.computeIfAbsent(level, WorldField::new);
    }

    // ------------------------------------------------------------------ what spells put in

    /**
     * {@code umu} of fire let into the air at {@code pos}: agitation, which the drives make a flame of, or lightning,
     * or nothing much, by how much it is and what is around. Says whether it went in (not into a wall).
     */
    public static boolean fire(ServerLevel level, BlockPos pos, double umu) {
        return fieldOf(level).heat(pos, Particles.ofUmu(umu));
    }

    /** {@code igni} particles of agitation into the block at {@code pos}; negative, taken out of it. */
    public static boolean heat(ServerLevel level, BlockPos pos, long igni) {
        return fieldOf(level).heat(pos, igni);
    }

    /**
     * What holding one flame for a vocant's moment takes, in UMU: what {@code igni vocant} brings by itself (user,
     * 03/10/2026). The drives say how much: worked out once in a box of still air, the fire it takes to make a block of
     * air a flame and to keep it one for {@link #FLAME_TICKS} steps as its heat goes into the air around.
     */
    public static double flame() {
        return FLAME_MOMENT;
    }

    private static double measureFlame() {
        Box box = new Box(5, 5, 5);
        Particles air = new Particles(0L, 0L, Particles.BLOCK, 0L);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    box.blow(x, y, z, air);
                }
            }
        }
        long spent = 0L;
        for (int step = 0; step < FLAME_TICKS; step++) {
            box.step();
            long given = Math.max(0L, box.agitationFor(2, 1, 2, Field.flameTemperature()) - box.heat(2, 1, 2));
            box.heat(2, 1, 2, given);
            spent += given;
        }
        return (double) spent / Particles.PER_UMU;
    }

    /**
     * Holds a flame where {@code where} says for {@code ticks} ticks, feeding it from {@code umu} of fire: after every
     * step its air is brought back up to a flame's agitation. What is not spent goes to {@code left}, in UMU.
     */
    public static void keepFlame(ServerLevel level, Supplier<BlockPos> where, double umu, int ticks,
                                 DoubleConsumer left) {
        fieldOf(level).keep(where, Particles.ofUmu(umu), ticks,
                rest -> left.accept((double) rest / Particles.PER_UMU));
    }

    /** Wakes the block at {@code pos} as it is by nature, even one hot by nature (lava a spell has reached). */
    public static void wake(ServerLevel level, BlockPos pos) {
        fieldOf(level).wake(pos);
    }

    /** Pressed air let out at {@code at}: its particles go into the air there and spread from it, as wind. */
    public static void releaseAir(ServerLevel level, Vec3 at, double pressure, double reach) {
        long air = Particles.ofUmu(pressure);
        if (air > 0L) {
            fieldOf(level).blow(BlockPos.containing(at), new Particles(0L, 0L, air, 0L));
        }
    }

    /** Pressed water let out at {@code at}: it springs back to its own volume and falls as water. */
    public static void releaseWater(ServerLevel level, Vec3 at, double pressure) {
        pour(level, at, pressure);
    }

    /** {@code water} UMU of water thrown into the air around {@code at}: it falls and pools. */
    public static void spray(ServerLevel level, Vec3 at, double reach, double water) {
        pour(level, at, water);
    }

    private static void pour(ServerLevel level, Vec3 at, double water) {
        if (water <= 0.0D) {
            return;
        }
        WorldMatter.place(level, BlockPos.containing(at), Matter.natural(Materials.get().primordial(VitaElement.AQUA),
                water));
    }

    // ------------------------------------------------------------------ what the drives say

    /** Whether the block at {@code pos} is matter the drives know and not air: something heat can go into. */
    public static boolean isMatter(ServerLevel level, BlockPos pos) {
        return fieldOf(level).isMatter(pos);
    }

    /** Whether the drives keep a flame at {@code pos}: its air is awake and glows. */
    public static boolean flameAt(ServerLevel level, BlockPos pos) {
        WorldField field = FIELDS.get(level);
        return field != null && field.flameAt(pos);
    }

    /** How agitated the block at {@code pos} is (1 is the world at rest, about 20 °C). */
    public static double temperature(ServerLevel level, BlockPos pos) {
        return fieldOf(level).temperature(pos);
    }

    /** What the block at {@code pos} holds and what flies in it, while it is awake. */
    public static Optional<Field.Cell> cell(ServerLevel level, BlockPos pos) {
        WorldField field = FIELDS.get(level);
        return field == null ? Optional.empty() : field.cell(pos);
    }

    /** How many blocks are awake in a world. */
    public static int awake(ServerLevel level) {
        WorldField field = FIELDS.get(level);
        return field == null ? 0 : field.size();
    }

    // ------------------------------------------------------------------ the drives, each tick

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        WorldField field = FIELDS.get(level);
        if (field == null || field.isEmpty()) {
            return;
        }
        try {
            field.step();
        } catch (RuntimeException exception) {
            LOGGER.error("The drives failed a step", exception);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FIELDS.remove(level);
        }
    }
}

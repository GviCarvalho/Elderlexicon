package com.elderlexicon.mod.spell.nature;

import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.physics.Field;
import com.elderlexicon.mod.spell.matter.MatterBlocks;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * The four drives at work in one world (docs/particulas-design.md, stage 9): the laws of {@link Field}, run over the
 * blocks awake around whatever stirred them.
 * <ul>
 *   <li>A block wakes as what the table reads it as, at the agitation its nature gives it: the world at rest; lava past
 *       the melting of its stone; ice and snow short of the melting of their water. Air, and the plants that stand in it,
 *       are a block of air; a flame is air that glows. A block the table does not know is a wall.</li>
 *   <li>Around a block that is not at rest the blocks beside it wake, so what it gives has somewhere to go. What is hot
 *       by its nature (a lava lake) is the world as it always is, and only wakes when something wakes it on purpose.</li>
 *   <li>Each step the drives act, and the world shows what came of each block: the block its matter is in its state,
 *       formless matter holding its particles exactly when the table has none, a flame where air glows, lightning
 *       where it is plasma. What someone else changed in the world meanwhile is taken as it is, never written over.</li>
 *   <li>A block back at its nature for a while sleeps again. Formless matter keeps what it holds to the last particle;
 *       a block of the table is its code again, and what little it held besides goes to the world at rest (the open
 *       air takes the smoke and the warmth, the ground the ash): the world is far bigger than what is awake.</li>
 * </ul>
 */
final class WorldField {

    /** The most blocks awake at once in one world; past it, no more wake until some sleep. */
    static final int MOST_AWAKE = 8192;
    /** Steps a block stays at its nature before it sleeps. */
    static final int REST_STEPS = 20;
    /** An agitation this close to a block's nature is at rest (about 6 K). */
    static final double REST = 0.02D;
    /** Gas beyond its nature the open air takes when a block sleeps. */
    static final long LOOSE = Particles.BLOCK / 20L;
    /**
     * Held matter shows as a block once it is at least half of one; less, it is scattered in the air of its block. A
     * block of the table that has gained or lost less than this, and is still what it was, is its code again when it
     * sleeps: near the code, it is the code.
     */
    static final long SHOWN = Particles.BLOCK / 2L;
    /** Steps between two looks at the world under a block, which someone may have changed. */
    static final int REFRESH = 10;
    /** At most this many puffs of vapour and smoke are shown each step. */
    static final int PUFFS = 24;
    /** Ticks between two strikes from the same plasma. */
    static final int STRIKE_TICKS = 10;

    /** The faces of a block in the order of the field's: east, west, up, down, south, north. */
    private static final Direction[] FACES = {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN,
            Direction.SOUTH, Direction.NORTH};
    private static final Particles AIR = new Particles(0L, 0L, Particles.BLOCK, 0L);

    /** What a block is by its nature: the block it was, what it holds and how agitated it is. */
    record Nature(BlockState block, Field.Cell cell, double temperature) {
    }

    /**
     * What a block shows: the block, and the particles it holds when that is formless matter, whose block glows as
     * agitated as it is.
     */
    private record Shown(BlockState block, Particles formless) {

        /** The same block: of the same kind (water flowing to another level is still water), formless glowing alike. */
        boolean same(Shown other) {
            if (other == null || (formless == null) != (other.formless == null)) {
                return false;
            }
            return formless != null ? block.equals(other.block) : block.getBlock() == other.block.getBlock();
        }

        /** The state of the formless matter it shows. */
        State state() {
            return block.is(MatterBlocks.FORMLESS_LIQUID.get()) ? State.LIQUID : State.SOLID;
        }
    }

    /** A flame a spell holds: where it is now, what it may still spend, for how many more steps, who gets the rest. */
    private static final class Kept {
        final Supplier<BlockPos> where;
        long budget;
        int steps;
        final LongConsumer left;

        Kept(Supplier<BlockPos> where, long budget, int steps, LongConsumer left) {
            this.where = where;
            this.budget = budget;
            this.steps = steps;
            this.left = left;
        }
    }

    private final ServerLevel level;
    private final Field field;
    private final List<Kept> kept = new ArrayList<>();
    private final Long2IntOpenHashMap awake = new Long2IntOpenHashMap();
    private long[] where = new long[64];
    private Nature[] nature = new Nature[64];
    private Shown[] shown = new Shown[64];
    private BlockState[] expected = new BlockState[64];
    private int[] quiet = new int[64];
    private int[] age = new int[64];
    private long[] struck = new long[64];

    WorldField(ServerLevel level) {
        this.level = level;
        this.field = new Field(level.getSeed());
        this.awake.defaultReturnValue(-1);
    }

    boolean isEmpty() {
        return field.count() == 0 && kept.isEmpty();
    }

    int size() {
        return field.count();
    }

    // ------------------------------------------------------------------ what spells put in

    /** Puts gas into the block at {@code pos}: dust, vapour, air, and fire as agitation. Says whether it went in. */
    boolean blow(BlockPos pos, Particles gas) {
        int i = stir(pos);
        if (i < 0) {
            return false;
        }
        field.blow(i, gas);
        return true;
    }

    /**
     * Matter nothing holds together any more into the air of the block at {@code pos}: dust carrying its fire, mist,
     * air. Says whether it went in.
     */
    boolean scatter(BlockPos pos, Particles matter) {
        int i = stir(pos);
        if (i < 0) {
            return false;
        }
        field.scatter(i, matter);
        return true;
    }

    /** Agitation into the block at {@code pos}; negative, taken out of it. Says whether it went in. */
    boolean heat(BlockPos pos, long igni) {
        int i = stir(pos);
        if (i < 0) {
            return false;
        }
        field.heat(i, igni);
        return true;
    }

    /**
     * Brings the block at {@code pos}, as the world has it now, to {@code temperature}: what was put there came that
     * agitated. Says whether it could.
     */
    boolean settle(BlockPos pos, double temperature) {
        int i = stir(pos);
        if (i < 0) {
            return false;
        }
        field.heat(i, field.agitationFor(i, temperature) - field.heat(i));
        return true;
    }

    /**
     * Holds a flame where {@code where} says for {@code steps} steps: after every step the air there is brought back up
     * to a flame's agitation, from {@code budget} particles of fire, and what is not spent goes to {@code left}.
     */
    void keep(Supplier<BlockPos> where, long budget, int steps, LongConsumer left) {
        kept.add(new Kept(where, Math.max(0L, budget), Math.max(1, steps), left));
    }

    /** Wakes the block at {@code pos}, even one hot by nature. */
    void wake(BlockPos pos) {
        stir(pos);
    }

    /**
     * Wakes the block at {@code pos} as the world has it now (what someone changed under an awake block is taken as it
     * is), and it is stirring; returns its number, or -1 when it cannot wake.
     */
    private int stir(BlockPos pos) {
        int i = awake.get(pos.asLong());
        if (i >= 0) {
            changedUnder(i);
            i = awake.get(pos.asLong());
        }
        if (i < 0) {
            i = wake(pos, false);
        }
        if (i >= 0) {
            quiet[i] = 0;
        }
        return i;
    }

    /** Whether the block at {@code pos} is awake and its air glows: a flame the drives keep. */
    boolean flameAt(BlockPos pos) {
        int i = awake.get(pos.asLong());
        return i >= 0 && field.held(i).total() < SHOWN && field.temperature(i) >= Field.glow();
    }

    /** How agitated the block at {@code pos} is: its nature when it is asleep. */
    double temperature(BlockPos pos) {
        int i = awake.get(pos.asLong());
        if (i >= 0) {
            return field.temperature(i);
        }
        Nature found = read(pos);
        return found == null ? 1.0D : found.temperature();
    }

    /** What the block at {@code pos} holds and what flies in it, if it is awake. */
    Optional<Field.Cell> cell(BlockPos pos) {
        int i = awake.get(pos.asLong());
        return i < 0 ? Optional.empty() : Optional.of(field.cell(i));
    }

    /** Whether the block at {@code pos} is matter the drives know and not air: something heat can go into. */
    boolean isMatter(BlockPos pos) {
        Nature found = read(pos);
        return found != null && found.cell().held().total() > 0L;
    }

    // ------------------------------------------------------------------ one step

    /** One step of the drives over every block awake, and what came of it shown in the world. */
    void step() {
        if (isEmpty()) {
            return;
        }
        // Around what is stirring, the blocks beside it wake.
        int[] stirring = indices(true);
        for (int i : stirring) {
            BlockPos pos = BlockPos.of(where[i]);
            for (int face = 0; face < FACES.length; face++) {
                if (field.neighbour(i, face) < 0) {
                    wake(pos.relative(FACES[face]), true);
                }
            }
        }
        field.step();
        feed();
        long now = level.getGameTime();
        int puffs = 0;
        for (int i : indices(false)) {
            if (++age[i] % REFRESH == 0 && changedUnder(i)) {
                continue;
            }
            Shown showing = quiet[i] > 0 ? shown[i] : display(i);
            if (!showing.same(shown[i])) {
                if (changedUnder(i)) {
                    continue; // someone changed it since: theirs stands, and the drives go on from it
                }
                show(i, showing);
            } else if (shown[i].formless() != null) {
                WorldMatter.keep(level, BlockPos.of(where[i]), field.held(i));
            }
            if (field.state(i) == State.PLASMA && now - struck[i] >= STRIKE_TICKS) {
                strike(i);
                struck[i] = now;
            }
            if (puffs < PUFFS && quiet[i] == 0) {
                puffs += puff(i);
            }
            if (atRest(i)) {
                if (++quiet[i] >= REST_STEPS) {
                    sleep(i);
                }
            } else {
                quiet[i] = 0;
            }
        }
    }

    /** The flames spells hold are fed: each is brought back up to a flame's agitation, as far as its fire goes. */
    private void feed() {
        Iterator<Kept> iterator = kept.iterator();
        while (iterator.hasNext()) {
            Kept flame = iterator.next();
            BlockPos pos = flame.where.get();
            int i = pos == null ? -1 : wake(pos, false);
            if (i >= 0) {
                long given = Math.max(0L, Math.min(flame.budget,
                        field.agitationFor(i, Field.flameTemperature()) - field.heat(i)));
                field.heat(i, given);
                flame.budget -= given;
                quiet[i] = 0;
            }
            if (--flame.steps <= 0 || flame.budget <= 0L || pos == null) {
                iterator.remove();
                flame.left.accept(flame.budget);
            }
        }
    }

    /** The blocks awake: all of them, or only those stirring (not at rest). */
    private int[] indices(boolean stirring) {
        int[] found = new int[awake.size()];
        int count = 0;
        for (int i : awake.values()) {
            if (!stirring || quiet[i] == 0) {
                found[count++] = i;
            }
        }
        return Arrays.copyOf(found, count);
    }

    // ------------------------------------------------------------------ waking and sleeping

    /**
     * Wakes the block at {@code pos} and joins it to the awake blocks beside it; returns its number, or -1 for a wall,
     * for what is not loaded, and when too much is awake already. Woken {@code around} something stirring, a block hot
     * by its nature stays asleep: it is the world as it always is.
     */
    private int wake(BlockPos pos, boolean around) {
        long key = pos.asLong();
        int i = awake.get(key);
        if (i >= 0) {
            return i;
        }
        if (field.count() >= MOST_AWAKE) {
            return -1;
        }
        Nature found = read(pos);
        if (found == null || around && found.temperature() > 1.0D + REST) {
            return -1;
        }
        i = field.add();
        room(i);
        awake.put(key, i);
        where[i] = key;
        nature[i] = found;
        expected[i] = found.block();
        quiet[i] = 0;
        age[i] = 0;
        struck[i] = Long.MIN_VALUE / 2L;
        field.set(i, found.cell());
        shown[i] = display(i);
        for (int face = 0; face < FACES.length; face++) {
            int j = awake.get(pos.relative(FACES[face]).asLong());
            if (j >= 0) {
                field.link(i, face, j);
            }
        }
        return i;
    }

    private void sleep(int i) {
        BlockPos pos = BlockPos.of(where[i]);
        if (shown[i] != null && shown[i].formless() != null
                && level.getBlockState(pos).getBlock() == expected[i].getBlock()) {
            // Formless matter keeps what the drives left it, to the last particle, and is seen in its colour.
            WorldMatter.showFormless(level, pos, WorldMatter.formless(shown[i].state(), field.temperature(i)),
                    field.held(i));
        }
        awake.remove(where[i]);
        field.remove(i);
        nature[i] = null;
        shown[i] = null;
        expected[i] = null;
    }

    private void room(int i) {
        if (i < where.length) {
            return;
        }
        int size = Math.max(i + 1, where.length * 2);
        where = Arrays.copyOf(where, size);
        nature = Arrays.copyOf(nature, size);
        shown = Arrays.copyOf(shown, size);
        expected = Arrays.copyOf(expected, size);
        quiet = Arrays.copyOf(quiet, size);
        age = Arrays.copyOf(age, size);
        struck = Arrays.copyOf(struck, size);
    }

    /**
     * Whether a block is back at its nature: as agitated as it is by nature, holding what it does, and with little gas
     * besides its own. Formless matter's nature is what it holds now, which it keeps. A block of the table, or of air,
     * may have gained or lost a little matter and still show as it did; the world at rest takes that difference with it.
     */
    private boolean atRest(int i) {
        Nature found = nature[i];
        Field.Cell now = field.cell(i);
        Shown showing = shown[i];
        double natural = showing.formless() != null ? Field.natural(now.held(), showing.state()) : found.temperature();
        if (Math.abs(field.temperature(i) - natural) > REST) {
            return false;
        }
        Field.Cell was = found.cell();
        if (showing.formless() == null && distance(now.held(), was.held()) > SHOWN) {
            return false;
        }
        long air = Math.abs(now.airborne().aura() + now.smoke() - was.airborne().aura() - was.smoke());
        long stray = Math.abs(now.airborne().firmo() - was.airborne().firmo())
                + Math.abs(now.airborne().aqua() - was.airborne().aqua()) + now.aloft() + now.bound();
        return air <= LOOSE && stray <= LOOSE;
    }

    private static long distance(Particles a, Particles b) {
        return Math.abs(a.firmo() - b.firmo()) + Math.abs(a.aqua() - b.aqua()) + Math.abs(a.aura() - b.aura())
                + Math.abs(a.igni() - b.igni());
    }

    /**
     * Someone changed the block under an awake one (built, broke, poured): the block takes what is there now, at its
     * nature, and the change is not undone. Says whether it was so.
     */
    private boolean changedUnder(int i) {
        BlockPos pos = BlockPos.of(where[i]);
        BlockState now = level.getBlockState(pos);
        if (now.getBlock() == expected[i].getBlock()) {
            return false; // the same block (water flowing to another level, a plant growing): still what it was
        }
        Nature found = read(pos);
        if (found == null) {
            sleep(i);
            return true;
        }
        nature[i] = found;
        expected[i] = now;
        field.set(i, found.cell());
        shown[i] = display(i);
        quiet[i] = 0;
        return true;
    }

    // ------------------------------------------------------------------ reading the world

    /** What the block at {@code pos} is by its nature, or null when it is a wall or not loaded. */
    Nature read(BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return gas(state, 1.0D);
        }
        if (state.is(BlockTags.FIRE) || state.is(MatterBlocks.FLAME.get())) {
            return gas(state, Field.flameTemperature());
        }
        FluidState fluid = state.getFluidState();
        Optional<Matter> matter;
        if (!fluid.isEmpty() && state.getBlock() instanceof LiquidBlock) {
            Fluid type = fluid.getType();
            Fluid source = type instanceof FlowingFluid flowing ? flowing.getSource() : type;
            double share = fluid.getAmount() / 8.0D;
            matter = WorldMatter.read(source.defaultFluidState().createLegacyBlock())
                    .map(found -> found.withUmu(found.umu() * share));
        } else {
            matter = WorldMatter.read(level, pos);
        }
        if (matter.isEmpty()) {
            // Plants and what else stands in the air are the air around them; anything else the table does not know is
            // a wall.
            return state.canBeReplaced() && fluid.isEmpty() ? gas(state, 1.0D) : null;
        }
        Particles particles = matter.get().particles();
        if (MaterialTable.floats(matter.get().state())) {
            Particles gas = new Particles(particles.firmo(), particles.aqua(), particles.aura(), 0L);
            long heat = Math.max(0L, particles.igni()) + Field.agitation(Particles.NONE, gas, 1.0D);
            return new Nature(state, new Field.Cell(Particles.NONE, 0L, gas, 0L, 0L, 0L, heat), 1.0D);
        }
        double temperature = Field.natural(particles, matter.get().state());
        long heat = Field.agitation(particles, Particles.NONE, temperature);
        return new Nature(state, new Field.Cell(particles, 0L, Particles.NONE, 0L, 0L, 0L, heat), temperature);
    }

    private static Nature gas(BlockState state, double temperature) {
        long heat = Field.agitation(Particles.NONE, AIR, temperature);
        return new Nature(state, new Field.Cell(Particles.NONE, 0L, AIR, 0L, 0L, 0L, heat), temperature);
    }

    // ------------------------------------------------------------------ showing the world

    /**
     * What a block shows now: the block its held matter is in its state, or formless matter holding exactly its
     * particles, while it holds at least half a block; otherwise its air, glowing as a flame where it is hot enough.
     */
    private Shown display(int i) {
        Particles held = field.held(i);
        State state = field.state(i);
        if (held.total() >= SHOWN && state != State.GAS && state != State.PLASMA) {
            Optional<Composition> composition = held.composition();
            if (composition.isPresent()) {
                Optional<BlockState> block = WorldMatter.blockOf(new Matter(composition.get(), state, held.umu()));
                if (block.isPresent()) {
                    return new Shown(block.get(), null);
                }
                return new Shown(WorldMatter.formless(state, field.temperature(i)), held);
            }
        }
        if (field.temperature(i) >= Field.glow()) {
            return new Shown(MatterBlocks.FLAME.get().defaultBlockState(), null);
        }
        return new Shown(Blocks.AIR.defaultBlockState(), null);
    }

    /** Shows {@code showing} at block {@code i}, and the block's nature is what the world now shows there. */
    private void show(int i, Shown showing) {
        BlockPos pos = BlockPos.of(where[i]);
        if (showing.formless() != null) {
            WorldMatter.showFormless(level, pos, showing.block(), showing.formless());
        } else {
            level.setBlock(pos, showing.block(), Block.UPDATE_ALL);
        }
        shown[i] = showing;
        expected[i] = level.getBlockState(pos);
        Nature now = read(pos);
        if (now != null) {
            nature[i] = new Nature(expected[i], now.cell(), now.temperature());
        }
    }

    /** Plasma strikes: lightning flashes from it, and what stands in it is struck. */
    private void strike(int i) {
        BlockPos pos = BlockPos.of(where[i]);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(0.5D))) {
            living.hurt(level.damageSources().lightningBolt(), 5.0F);
        }
    }

    /** Vapour and smoke rising from a block can be seen. Returns how many puffs it showed. */
    private int puff(int i) {
        Field.Cell cell = field.cell(i);
        Vec3 at = Vec3.atCenterOf(BlockPos.of(where[i]));
        int shownPuffs = 0;
        if (cell.bound() > 0L && cell.airborne().aqua() > Particles.BLOCK / 64L) {
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 2, 0.3D, 0.3D, 0.3D, 0.02D);
            shownPuffs++;
        }
        if (cell.smoke() + cell.airborne().firmo() > Particles.BLOCK / 32L && field.temperature(i) > 1.2D) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, 0.3D, 0.3D, 0.3D, 0.01D);
            shownPuffs++;
        }
        return shownPuffs;
    }
}

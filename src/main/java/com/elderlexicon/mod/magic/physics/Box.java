package com.elderlexicon.mod.magic.physics;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;

import java.util.SplittableRandom;

import static com.elderlexicon.mod.magic.physics.Drives.AQUA;
import static com.elderlexicon.mod.magic.physics.Drives.AURA;
import static com.elderlexicon.mod.magic.physics.Drives.FIRMO;
import static com.elderlexicon.mod.magic.physics.Drives.IGNI;

/**
 * The laboratory of the four drives (docs/particulas-design.md, section 2): a box of blocks where the drives act and
 * nothing else. No law here names a substance or a pair of elements. Each particle has one drive and applies it to any
 * particle near it; how it takes the others' comes from its own ({@link Drives}). What comes out of that (vapour, lava,
 * a fire that eats the air of a closed room, charcoal) is what the tests look at. Pure: no Minecraft.
 * <p>
 * A block holds:
 * <ul>
 *   <li><b>held</b> matter: the particles mass and cohesion keep together (a solid or a liquid), with the fire held in
 *       it (fuel), part of which the mass may have gripped into char, and the air in its pores;</li>
 *   <li><b>airborne</b> particles: the gas in the room the held matter leaves (air, vapour, dust), and the fire the
 *       dust carries (fuel aloft: soot, the vapour of a fuel that boiled), which burns where it meets air;</li>
 *   <li><b>smoke</b>: air the fire took, airborne, which feeds no fire any more;</li>
 *   <li><b>bound</b> fire: what boiling took, held by the vapour and given back when it condenses;</li>
 *   <li><b>heat</b>: free igni, the agitation above the world at rest; owed below it, down to absolute zero.</li>
 * </ul>
 * Every step moves whole particles, so nothing is made or lost (L1). What would move part of a particle moves a whole
 * one with the chance of that part, so small flows still happen, on average as much as they should. The walls of the
 * box are closed.
 */
public final class Box {

    private static final int[][] UP = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    private final int width;
    private final int height;
    private final int depth;
    private final int size;
    private final long[][] held = new long[4][];
    private final long[][] airborne = new long[3][];
    private final long[] charred;
    private final long[] smoke;
    private final long[] aloft;
    private final long[] bound;
    private final long[] heat;
    private final SplittableRandom random;

    public Box(int width, int height, int depth) {
        this(width, height, depth, 0L);
    }

    /** A box whose chances fall as {@code seed} says: the same seed, the same world. */
    public Box(int width, int height, int depth, long seed) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("a box needs room");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.size = width * height * depth;
        for (int element = 0; element < 4; element++) {
            held[element] = new long[size];
        }
        for (int element = 0; element < 3; element++) {
            airborne[element] = new long[size];
        }
        charred = new long[size];
        smoke = new long[size];
        aloft = new long[size];
        bound = new long[size];
        heat = new long[size];
        random = new SplittableRandom(seed);
    }

    // ------------------------------------------------------------------ setting up and reading

    /** Puts matter in a block, held, at rest. What cannot hold together (air, fire with no mass) goes on the first step. */
    public void put(int x, int y, int z, Particles matter) {
        int i = index(x, y, z);
        held[FIRMO][i] += Math.max(0L, matter.firmo());
        held[AQUA][i] += Math.max(0L, matter.aqua());
        held[AURA][i] += Math.max(0L, matter.aura());
        held[IGNI][i] += Math.max(0L, matter.igni());
    }

    /** Puts gas in a block: the earth as dust, the water as mist, the air, and the fire as free agitation. */
    public void blow(int x, int y, int z, Particles gas) {
        int i = index(x, y, z);
        airborne[FIRMO][i] += Math.max(0L, gas.firmo());
        airborne[AQUA][i] += Math.max(0L, gas.aqua());
        airborne[AURA][i] += Math.max(0L, gas.aura());
        heat[i] += Math.max(0L, gas.igni());
    }

    /** Agitation brought into a block from outside (a spell); negative, taken out of it, down to absolute zero. */
    public void heat(int x, int y, int z, long igni) {
        int i = index(x, y, z);
        heat[i] = Math.max(heat[i] + igni, floor(i));
    }

    /** The matter a block holds, the fire held in it among it. */
    public Particles held(int x, int y, int z) {
        int i = index(x, y, z);
        return new Particles(held[FIRMO][i], held[AQUA][i], held[AURA][i], held[IGNI][i]);
    }

    /** How much of the fire a block holds its mass has gripped into char. */
    public long charred(int x, int y, int z) {
        return charred[index(x, y, z)];
    }

    /** What flies in a block: the smoke as air, the fire the vapour holds and the fuel the dust carries as fire. */
    public Particles airborne(int x, int y, int z) {
        int i = index(x, y, z);
        return new Particles(airborne[FIRMO][i], airborne[AQUA][i], airborne[AURA][i] + smoke[i], bound[i] + aloft[i]);
    }

    /** The smoke in a block: air the fire took. */
    public long smoke(int x, int y, int z) {
        return smoke[index(x, y, z)];
    }

    /** The free agitation of a block: igni above the world at rest, or owed below it. */
    public long heat(int x, int y, int z) {
        return heat[index(x, y, z)];
    }

    /** How agitated a block is: 1 at rest, 0 at absolute zero. */
    public double temperature(int x, int y, int z) {
        return temperature(index(x, y, z));
    }

    /** The state of the matter a block holds; with none, a gas, or plasma where the agitation beats the expansion. */
    public State state(int x, int y, int z) {
        int i = index(x, y, z);
        if (heldCount(i) == 0L) {
            return temperature(i) >= Drives.PLASMA ? State.PLASMA : State.GAS;
        }
        return heldState(i);
    }

    /** Whether a block is agitated enough to glow (lava, iron red with heat, flame). */
    public boolean glows(int x, int y, int z) {
        return temperature(index(x, y, z)) >= Drives.GLOW;
    }

    /** Whether a block is flame: gas that glows, short of plasma. */
    public boolean fire(int x, int y, int z) {
        int i = index(x, y, z);
        double t = temperature(i);
        return heldCount(i) == 0L && t >= Drives.GLOW && t < Drives.PLASMA;
    }

    /** Every particle in the box, the free agitation counted as fire: what no step may change. */
    public Particles total() {
        long firmo = 0L;
        long aqua = 0L;
        long aura = 0L;
        long igni = 0L;
        for (int i = 0; i < size; i++) {
            firmo += held[FIRMO][i] + airborne[FIRMO][i];
            aqua += held[AQUA][i] + airborne[AQUA][i];
            aura += held[AURA][i] + airborne[AURA][i] + smoke[i];
            igni += held[IGNI][i] + aloft[i] + bound[i] + heat[i];
        }
        return new Particles(firmo, aqua, aura, igni);
    }

    // ------------------------------------------------------------------ one step

    /**
     * One step of the world: agitation spreads and shines, fire is let go, matter changes, gas evens out and what is
     * light rises.
     */
    public void step() {
        agitate();
        burn();
        burnAloft();
        change();
        flow();
        rise();
    }

    public void steps(int count) {
        for (int step = 0; step < count; step++) {
            step();
        }
    }

    // ------------------------------------------------------------------ agitation

    /**
     * Agitation passes through the faces from the more agitated block to the less: as readily as what both are made of
     * passes it on (earth well, air hardly), and as light, as much more as the brighter one glows (the fourth power of
     * its agitation). Light is given and taken by matter; clear air lets it through, so between two blocks of gas it
     * passes none. Never more than evens them out.
     */
    private void agitate() {
        double[] t = new double[size];
        double[] c = new double[size];
        double[] k = new double[size];
        boolean[] held = new boolean[size];
        for (int i = 0; i < size; i++) {
            t[i] = temperature(i);
            c[i] = capacity(i);
            k[i] = conduction(i);
            held[i] = heldCount(i) > 0L;
        }
        long[] change = new long[size];
        forEachFace((i, j) -> {
            if (c[i] <= 0.0D || c[j] <= 0.0D) {
                return;
            }
            double even = (t[i] - t[j]) * c[i] * c[j] / (c[i] + c[j]);
            double readily = k[i] + k[j] <= 0.0D ? 0.0D : 2.0D * k[i] * k[j] / (k[i] + k[j]);
            double shone = held[i] || held[j] ? Drives.RADIATE * (fourth(t[i]) - fourth(t[j])) : 0.0D;
            double passed = Drives.CONDUCT * readily * even + shone;
            double most = Drives.CONDUCT * Math.abs(even);
            long whole = round(Math.max(-most, Math.min(most, passed)));
            change[i] -= whole;
            change[j] += whole;
        });
        for (int i = 0; i < size; i++) {
            heat[i] += change[i];
        }
    }

    private static double fourth(double t) {
        double square = t * t;
        return square * square;
    }

    // ------------------------------------------------------------------ fire

    /**
     * Held fire is let go as agitation where the matter is agitated past what its mass crowds it with, and free air
     * touches it (in the block or through a face): the further past, the faster, up to twice its ignition, past which
     * the air that reaches the fuel is what limits it. A fire heats itself and burns faster, until what it loses as it
     * glows and as its smoke rises evens what it lets go: that is how hot a flame is. The air opens the fuel, and the
     * fire goes into the air that opened it and agitates it: the flame is that air, and the fuel warms from it. The earth
     * that held the fire crumbles with it into ash, so what is left holds its fire as before and burns to the end. The
     * air it takes is not lost: it stays where it was, as smoke, with the ash in it, and feeds no fire any more.
     */
    private void burn() {
        double[] t = new double[size];
        for (int i = 0; i < size; i++) {
            t[i] = temperature(i);
        }
        long[] wanted = new long[size];
        double[] asked = new double[size];
        double[][] askedFrom = new double[size][];
        int[][] sources = new int[size][];
        for (int i = 0; i < size; i++) {
            long fire = held[IGNI][i];
            if (fire <= 0L || held[FIRMO][i] <= 0L || t[i] <= ignition(i)) {
                continue;
            }
            int[] near = withSelf(i);
            long air = 0L;
            for (int s : near) {
                air += airborne[AURA][s];
            }
            if (air <= 0L) {
                continue;
            }
            double past = Math.min(1.0D, (t[i] - ignition(i)) / ignition(i));
            wanted[i] = Math.min(fire, Math.max(1L, round(Drives.BURNING * past * fire)));
            double need = (double) wanted[i] * Drives.AIR_PER_FIRE;
            sources[i] = near;
            askedFrom[i] = new double[near.length];
            for (int n = 0; n < near.length; n++) {
                double share = need * airborne[AURA][near[n]] / air;
                askedFrom[i][n] = share;
                asked[near[n]] += share;
            }
        }
        long[] airTaken = new long[size];
        long[] smokeMade = new long[size];
        long[] ashMade = new long[size];
        long[] flame = new long[size];
        long[] airLeft = airborne[AURA].clone();
        for (int i = 0; i < size; i++) {
            if (wanted[i] <= 0L) {
                continue;
            }
            long got = 0L;
            long[] from = new long[sources[i].length];
            for (int n = 0; n < sources[i].length; n++) {
                int s = sources[i][n];
                double scale = asked[s] <= airborne[AURA][s] ? 1.0D : airborne[AURA][s] / asked[s];
                from[n] = Math.min(airLeft[s], round(askedFrom[i][n] * scale));
                airLeft[s] -= from[n];
                got += from[n];
            }
            long fire = Math.min(wanted[i], got / Drives.AIR_PER_FIRE);
            if (fire <= 0L) {
                continue;
            }
            long all = held[IGNI][i];
            long ash = fire == all ? held[FIRMO][i] : fire * held[FIRMO][i] / all;
            long gripped = fire == all ? charred[i] : fire * charred[i] / all;
            long air = fire * Drives.AIR_PER_FIRE;
            long ashLeft = ash;
            long fireLeft = fire;
            for (int n = 0; n < sources[i].length && air > 0L; n++) {
                long taken = Math.min(from[n], air);
                boolean last = n == sources[i].length - 1 || taken == air;
                long withIt = last ? ashLeft : ash * taken / (fire * Drives.AIR_PER_FIRE);
                long agitating = last ? fireLeft : taken / Drives.AIR_PER_FIRE;
                airTaken[sources[i][n]] += taken;
                smokeMade[sources[i][n]] += taken;
                ashMade[sources[i][n]] += withIt;
                flame[sources[i][n]] += agitating;
                ashLeft -= withIt;
                fireLeft -= agitating;
                air -= taken;
            }
            held[IGNI][i] -= fire;
            charred[i] -= gripped;
            held[FIRMO][i] -= ash;
        }
        for (int i = 0; i < size; i++) {
            airborne[AURA][i] -= airTaken[i];
            smoke[i] += smokeMade[i];
            airborne[FIRMO][i] += ashMade[i];
            heat[i] += flame[i];
        }
    }

    /**
     * How agitated held matter must be to let its fire go. The fire needs room to flicker, and the mass takes room: the
     * more mass for the room left in the block, the more agitation, as the cube of it (the room is a volume). A small
     * piece in a block of air lets go easily; a block that is all mass never does.
     */
    private double ignition(int i) {
        long mass = held[FIRMO][i];
        long room = Particles.BLOCK - mass;
        if (room <= 0L) {
            return Double.MAX_VALUE;
        }
        double crowded = (double) mass / room;
        return Drives.IGNITION + Drives.CROWDING * crowded * crowded * crowded;
    }

    /**
     * Fuel carried aloft (soot, the vapour of a fuel that boiled) burns where it is mixed with free air, past the ignition
     * of fire that nothing crowds, by the same law as held fuel: it is the flame that dances over a fire. The dust that
     * carried it stays aloft as ash.
     */
    private void burnAloft() {
        for (int i = 0; i < size; i++) {
            long fuel = aloft[i];
            long air = airborne[AURA][i];
            double t = temperature(i);
            if (fuel <= 0L || air <= 0L || t <= Drives.IGNITION) {
                continue;
            }
            double past = Math.min(1.0D, (t - Drives.IGNITION) / Drives.IGNITION);
            long fire = Math.min(Math.min(fuel, air / Drives.AIR_PER_FIRE),
                    Math.max(1L, round(Drives.BURNING * past * fuel)));
            if (fire <= 0L) {
                continue;
            }
            aloft[i] -= fire;
            airborne[AURA][i] -= fire * Drives.AIR_PER_FIRE;
            smoke[i] += fire * Drives.AIR_PER_FIRE;
            heat[i] += fire;
        }
    }

    // ------------------------------------------------------------------ matter changing

    /**
     * What each block's matter does as it is agitated, block by block: fire no mass holds is agitation; a solid keeps
     * the air in its pores unless the air spreads more than it stays, and then it crumbles; a liquid lets its air go;
     * the air in the pores, the water and the earth escape as the agitation passes what holds them, the water taking
     * fire with it as vapour; a solid fuel past its ignition chars; vapour that cools gives its fire back as it
     * condenses; dust settles when it holds more than the air around it spreads it.
     */
    private void change() {
        for (int i = 0; i < size; i++) {
            free(i);
            fly(i);
            escape(i);
            carbonize(i);
            condense(i);
            settle(i);
            charred[i] = Math.min(charred[i], Math.min(held[IGNI][i], (long) (held[FIRMO][i] / Drives.CHAR)));
        }
    }

    /** Fire held where there is no mass to hold it, or carried aloft with no dust to carry it, is free agitation. */
    private void free(int i) {
        if (held[IGNI][i] > 0L && held[FIRMO][i] <= 0L) {
            heat[i] += held[IGNI][i];
            held[IGNI][i] = 0L;
            charred[i] = 0L;
        }
        if (aloft[i] > 0L && airborne[FIRMO][i] <= 0L) {
            heat[i] += aloft[i];
            aloft[i] = 0L;
        }
    }

    /**
     * Held air with nothing to hold it flies. In a solid every particle stays, as mass does, and keeps the air in its
     * pores, unless the air spreads more than that: then the solid crumbles and flies as dust, the fire it held carried
     * aloft with it, still fuel.
     */
    private void fly(int i) {
        long air = held[AURA][i];
        if (air <= 0L) {
            return;
        }
        if (structure(i) <= 0L) {
            held[AURA][i] = 0L;
            airborne[AURA][i] += air;
            return;
        }
        if (heldState(i) != State.SOLID || Drives.SPREAD * air < lock(i)) {
            return;
        }
        for (int element = FIRMO; element <= AURA; element++) {
            airborne[element][i] += held[element][i];
            held[element][i] = 0L;
        }
        aloft[i] += held[IGNI][i];
        held[IGNI][i] = 0L;
        charred[i] = 0L;
    }

    private void escape(int i) {
        if (heldCount(i) <= 0L) {
            return;
        }
        double t = temperature(i);
        long air = held[AURA][i];
        if (air > 0L && t > Drives.BOIL * Drives.PORES * keep(i) / air) {
            long out = Math.min(air, Math.max(1L, round(Drives.ESCAPE * air)));
            held[AURA][i] -= out;
            airborne[AURA][i] += out;
        }
        long water = held[AQUA][i];
        if (water > 0L) {
            double wet = Drives.BOIL * (Drives.HOLD[AQUA] + Drives.WET * Drives.HOLD[FIRMO] * held[FIRMO][i] / water);
            if (t > wet && heat[i] > 0L) {
                double excess = (t - wet) * capacity(i);
                long fire = Math.min(heat[i], Math.max(1L, round(Drives.BOILING * excess)));
                long out = Math.min(water, fire * Drives.VAPOUR);
                fire = ceilDiv(out, Drives.VAPOUR);
                held[AQUA][i] -= out;
                airborne[AQUA][i] += out;
                heat[i] -= fire;
                bound[i] += fire;
            }
        }
        long earth = held[FIRMO][i];
        if (earth > 0L && temperature(i) > Drives.BOIL * hold(i)) {
            long out = Math.min(earth, Math.max(1L, round(Drives.ESCAPE * earth)));
            long fire = out == earth ? held[IGNI][i] : Math.min(held[IGNI][i], round((double) held[IGNI][i] * out / earth));
            long gripped = out == earth ? charred[i] : Math.min(charred[i], round((double) charred[i] * out / earth));
            held[FIRMO][i] -= out;
            airborne[FIRMO][i] += out;
            held[IGNI][i] -= fire;
            charred[i] -= Math.min(gripped, fire);
            aloft[i] += fire;
        }
    }

    /**
     * A solid fuel agitated past its ignition chars, air or not: the agitation shakes it apart, and its mass grips the
     * fire tighter, {@link Drives#CHAR} of earth to each particle of fire. The earth that grips no fire leaves as soot,
     * and the air of its pores leaves with it, spent, as smoke: it feeds no fire, so fuel heated without air keeps its
     * fire. The water it held, however hard, leaves as vapour, taking what boiling takes. Gripped fire holds like the mass
     * around it, so char does not melt where the fuel would; it still burns when air comes. What is heated slowly chars
     * before it reaches its melting; what is heated all at once melts first, and a liquid does not char.
     */
    private void carbonize(int i) {
        long fire = held[IGNI][i];
        if (fire <= 0L || held[FIRMO][i] <= 0L || heldState(i) != State.SOLID || temperature(i) <= ignition(i)) {
            return;
        }
        long loose = Math.min(fire, (long) (held[FIRMO][i] / Drives.CHAR)) - charred[i];
        if (loose > 0L) {
            charred[i] += Math.min(loose, Math.max(1L, round(Drives.CHARRING * loose)));
        }
        long spare = held[FIRMO][i] - (long) Math.ceil(Drives.CHAR * fire);
        if (spare > 0L) {
            long soot = Math.min(spare, Math.max(1L, round(Drives.CHARRING * spare)));
            held[FIRMO][i] -= soot;
            airborne[FIRMO][i] += soot;
        }
        long air = held[AURA][i];
        if (air > 0L) {
            long out = Math.min(air, Math.max(1L, round(Drives.ESCAPE * air)));
            held[AURA][i] -= out;
            smoke[i] += out;
        }
        long water = held[AQUA][i];
        if (water > 0L && heat[i] > 0L) {
            long out = Math.min(water, Math.min(heat[i] * Drives.VAPOUR, Math.max(1L, round(Drives.ESCAPE * water))));
            long boiling = ceilDiv(out, Drives.VAPOUR);
            held[AQUA][i] -= out;
            airborne[AQUA][i] += out;
            heat[i] -= boiling;
            bound[i] += boiling;
        }
    }

    /** Vapour cooler than water boils condenses into water, and the fire it held is agitation again. */
    private void condense(int i) {
        long vapour = airborne[AQUA][i];
        if (vapour > 0L && bound[i] > 0L) {
            double t = temperature(i);
            double dew = Drives.BOIL * Drives.HOLD[AQUA];
            if (t < dew) {
                double lack = (dew - t) * capacity(i);
                long fire = Math.min(bound[i], Math.max(1L, round(Drives.BOILING * lack)));
                long in = Math.min(vapour, fire * Drives.VAPOUR);
                fire = Math.min(bound[i], ceilDiv(in, Drives.VAPOUR));
                airborne[AQUA][i] -= in;
                held[AQUA][i] += in;
                bound[i] -= fire;
                heat[i] += fire;
            }
        }
        long unheld = bound[i] - ceilDiv(airborne[AQUA][i], Drives.VAPOUR);
        if (unheld > 0L) {
            bound[i] -= unheld;
            heat[i] += unheld;
        }
    }

    /**
     * Airborne earth settles when it and the water with it hold more than the air around them spreads them; agitated
     * air spreads more, so hot smoke keeps its soot aloft.
     */
    private void settle(int i) {
        long dust = airborne[FIRMO][i];
        if (dust <= 0L) {
            return;
        }
        double holding = Drives.HOLD[FIRMO] * dust + Drives.HOLD[AQUA] * airborne[AQUA][i];
        double agitated = Math.max(Drives.AT_REST, temperature(i));
        if (holding > Drives.SPREAD * agitated * (airborne[AURA][i] + smoke[i])) {
            airborne[FIRMO][i] = 0L;
            held[FIRMO][i] += dust;
            held[IGNI][i] += aloft[i];
            aloft[i] = 0L;
        }
    }

    // ------------------------------------------------------------------ gas

    /**
     * Gas goes where there is less of it: from the block where it presses more (more of it, more agitated, in less room)
     * to the one where it presses less, carrying its share of the agitation.
     */
    private void flow() {
        long[] n = new long[size];
        double[] p = new double[size];
        double[] t = new double[size];
        long[] room = new long[size];
        for (int i = 0; i < size; i++) {
            n[i] = gasCount(i);
            t[i] = temperature(i);
            room[i] = room(i);
            p[i] = n[i] == 0L ? 0.0D : n[i] * t[i] / Math.max(1L, room[i]);
        }
        Moves moves = new Moves();
        forEachFace((i, j) -> {
            int from = p[i] >= p[j] ? i : j;
            int to = from == i ? j : i;
            if (p[from] <= p[to] || room[to] <= 0L || n[from] <= 0L) {
                return;
            }
            double rFrom = Math.max(1L, room[from]);
            double rTo = room[to];
            double even = (n[from] * t[from] * rTo - n[to] * t[to] * rFrom) / (t[from] * rTo + t[to] * rFrom);
            long count = Math.min(n[from], round(Drives.FLOW * even));
            if (count > 0L) {
                moves.gas(from, to, count, n[from]);
            }
        });
        moves.apply();
    }

    /** Gas lighter than the gas above it rises through it, and the heavier sinks, each carrying its agitation. */
    private void rise() {
        double[] density = new double[size];
        long[] n = new long[size];
        for (int i = 0; i < size; i++) {
            n[i] = gasCount(i);
            long room = room(i);
            density[i] = n[i] == 0L || room <= 0L ? -1.0D : weight(i) / room;
        }
        Moves moves = new Moves();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y + 1 < height; y++) {
                for (int z = 0; z < depth; z++) {
                    int below = index(x, y, z);
                    int above = index(x, y + 1, z);
                    if (density[below] < 0.0D || density[above] < 0.0D || density[below] >= density[above]) {
                        continue;
                    }
                    double share = Math.min(0.25D,
                            Drives.RISE * (density[above] - density[below]) / (density[above] + density[below]));
                    moves.gas(below, above, round(share * n[below]), n[below]);
                    moves.gas(above, below, round(share * n[above]), n[above]);
                }
            }
        }
        moves.apply();
    }

    /**
     * Gas moved between blocks in one step, worked out from the step's start and applied at its end. A block gives
     * through all its faces together no more than it has.
     */
    private final class Moves {
        private static final int HEAT = 6;
        private final long[][] change = new long[7][size];
        private final long[][] left = {airborne[FIRMO].clone(), airborne[AQUA].clone(), airborne[AURA].clone(),
                smoke.clone(), bound.clone(), aloft.clone(), heat.clone()};

        /** {@code count} of the {@code total} gas particles of {@code from} go to {@code to}, each kind in its share. */
        void gas(int from, int to, long count, long total) {
            if (count <= 0L || total <= 0L) {
                return;
            }
            long[] kinds = {airborne[FIRMO][from], airborne[AQUA][from], airborne[AURA][from], smoke[from], bound[from],
                    aloft[from]};
            double moved = 0.0D;
            for (int kind = 0; kind < kinds.length; kind++) {
                long part = kinds[kind] <= 0L ? 0L
                        : Math.min(left[kind][from], round((double) kinds[kind] * count / total));
                if (part <= 0L) {
                    continue;
                }
                left[kind][from] -= part;
                change[kind][from] -= part;
                change[kind][to] += part;
                moved += part * capacityOf(kind);
            }
            double matter = matter(from);
            if (matter <= 0.0D || moved <= 0.0D) {
                return;
            }
            long carried = round(heat[from] * Math.min(1.0D, moved / matter));
            carried = carried > 0L ? Math.min(carried, Math.max(0L, left[HEAT][from]))
                    : Math.max(carried, Math.min(0L, left[HEAT][from]));
            left[HEAT][from] -= carried;
            change[HEAT][from] -= carried;
            change[HEAT][to] += carried;
        }

        void apply() {
            for (int i = 0; i < size; i++) {
                airborne[FIRMO][i] += change[0][i];
                airborne[AQUA][i] += change[1][i];
                airborne[AURA][i] += change[2][i];
                smoke[i] += change[3][i];
                bound[i] += change[4][i];
                aloft[i] += change[5][i];
                heat[i] += change[HEAT][i];
            }
        }

        private double capacityOf(int kind) {
            return switch (kind) {
                case 0 -> Drives.CAPACITY[FIRMO];
                case 1 -> Drives.CAPACITY[AQUA];
                case 2, 3 -> Drives.CAPACITY[AURA];
                default -> Drives.CAPACITY[IGNI];
            };
        }
    }

    // ------------------------------------------------------------------ what a block is like

    private long heldCount(int i) {
        return held[FIRMO][i] + held[AQUA][i] + held[AURA][i] + held[IGNI][i];
    }

    /** The held particles that make the matter: all but the air in its pores, which is room. */
    private long structure(int i) {
        return held[FIRMO][i] + held[AQUA][i] + held[IGNI][i];
    }

    private long gasCount(int i) {
        return airborne[FIRMO][i] + airborne[AQUA][i] + airborne[AURA][i] + smoke[i] + bound[i] + aloft[i];
    }

    /** The room the held matter leaves for gas. */
    private long room(int i) {
        return Math.max(0L, Particles.BLOCK - heldCount(i));
    }

    /**
     * How hard the held matter holds together, per particle of it: the mass and the cohesion in it, and the fire the
     * mass has gripped into char, which holds as the mass does. Loose fire wedges it apart; the pores are room.
     */
    private double hold(int i) {
        long structure = structure(i);
        if (structure <= 0L) {
            return 0.0D;
        }
        return (Drives.HOLD[FIRMO] * (held[FIRMO][i] + charred[i]) + Drives.HOLD[AQUA] * held[AQUA][i]) / structure;
    }

    /** How hard a solid keeps what is in its pores: still, every particle of it stays, as mass does. */
    private double lock(int i) {
        return Drives.HOLD[FIRMO] * structure(i);
    }

    /**
     * How hard held matter keeps the air in it: a solid with all of itself ({@link #lock}); a liquid only with its mass,
     * which stays, while its water joins and pushes the air out. Molten rock keeps its air; melted snow lets it go.
     */
    private double keep(int i) {
        return heldState(i) == State.SOLID ? lock(i) : Drives.HOLD[FIRMO] * held[FIRMO][i];
    }

    private State heldState(int i) {
        double hold = hold(i);
        double t = temperature(i);
        if (t < Drives.MELT * hold) {
            return State.SOLID;
        }
        return t < Drives.BOIL * hold ? State.LIQUID : State.GAS;
    }

    /** How much agitation the matter of a block takes to warm: what it is made of, held and airborne. */
    private double matter(int i) {
        return Drives.CAPACITY[FIRMO] * (held[FIRMO][i] + airborne[FIRMO][i])
                + Drives.CAPACITY[AQUA] * (held[AQUA][i] + airborne[AQUA][i])
                + Drives.CAPACITY[AURA] * (held[AURA][i] + airborne[AURA][i] + smoke[i])
                + Drives.CAPACITY[IGNI] * (held[IGNI][i] + aloft[i] + bound[i]);
    }

    /** How much agitation a block takes to warm: its matter, and the free fire, which agitates itself too. */
    private double capacity(int i) {
        return matter(i) + Drives.CAPACITY[IGNI] * Math.max(0L, heat[i]);
    }

    /** How readily the block passes agitation on: what it is made of, each in its share. */
    private double conduction(int i) {
        long firmo = held[FIRMO][i] + airborne[FIRMO][i];
        long aqua = held[AQUA][i] + airborne[AQUA][i];
        long aura = held[AURA][i] + airborne[AURA][i] + smoke[i];
        long igni = held[IGNI][i] + aloft[i] + bound[i] + Math.max(0L, heat[i]);
        long all = firmo + aqua + aura + igni;
        if (all <= 0L) {
            return 0.0D;
        }
        return (Drives.CONDUCTION[FIRMO] * firmo + Drives.CONDUCTION[AQUA] * aqua + Drives.CONDUCTION[AURA] * aura
                + Drives.CONDUCTION[IGNI] * igni) / all;
    }

    private double weight(int i) {
        return Drives.WEIGHT[FIRMO] * airborne[FIRMO][i] + Drives.WEIGHT[AQUA] * airborne[AQUA][i]
                + Drives.WEIGHT[AURA] * (airborne[AURA][i] + smoke[i]) + Drives.WEIGHT[IGNI] * bound[i];
    }

    private double temperature(int i) {
        double capacity = capacity(i);
        if (capacity <= 0.0D) {
            return Drives.AT_REST;
        }
        return Math.max(0.0D, Drives.AT_REST + heat[i] / capacity);
    }

    /** The least free agitation a block can have: owing all of the world's rest, it is at absolute zero. */
    private long floor(int i) {
        return -(long) Math.ceil(Drives.AT_REST * matter(i));
    }

    /** A whole number of particles for {@code amount}: the part of one moves a whole one with the chance of that part. */
    private long round(double amount) {
        if (amount < 0.0D) {
            return -round(-amount);
        }
        long whole = (long) amount;
        double part = amount - whole;
        return part > 0.0D && random.nextDouble() < part ? whole + 1L : whole;
    }

    private static long ceilDiv(long amount, long by) {
        return (amount + by - 1L) / by;
    }

    // ------------------------------------------------------------------ the grid

    private int index(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ") is outside the box");
        }
        return (y * depth + z) * width + x;
    }

    private interface Face {
        void touch(int i, int j);
    }

    /** Every pair of blocks that share a face, once. */
    private void forEachFace(Face face) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    int i = index(x, y, z);
                    for (int[] up : UP) {
                        int nx = x + up[0];
                        int ny = y + up[1];
                        int nz = z + up[2];
                        if (nx < width && ny < height && nz < depth) {
                            face.touch(i, index(nx, ny, nz));
                        }
                    }
                }
            }
        }
    }

    /** A block and the ones that share a face with it. */
    private int[] withSelf(int i) {
        int x = i % width;
        int z = (i / width) % depth;
        int y = i / (width * depth);
        int[] near = new int[7];
        int count = 0;
        near[count++] = i;
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] face : faces) {
            int nx = x + face[0];
            int ny = y + face[1];
            int nz = z + face[2];
            if (nx >= 0 && ny >= 0 && nz >= 0 && nx < width && ny < height && nz < depth) {
                near[count++] = index(nx, ny, nz);
            }
        }
        return java.util.Arrays.copyOf(near, count);
    }
}

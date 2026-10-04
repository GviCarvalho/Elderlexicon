package com.elderlexicon.mod.magic.physics;

import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The laboratory of the four drives (docs/particulas-design.md, section 2): small boxes of blocks where only the drives
 * act. Each test says what should come out; the laws never name a substance or a pair, so when one fails the drives or
 * their numbers change, never a special case. The table names what comes out, here and only here. The world at rest is
 * 1 (about 20 °C); a step of 1 is about 293 K.
 */
class BoxTest {

    private static final MaterialTable TABLE = Materials.builtIn();
    private static final double KELVIN = 293.0D;

    private static Particles block(String substance) {
        return TABLE.substance(substance).orElseThrow().code(Particles.BLOCK);
    }

    private static String named(Particles particles) {
        return TABLE.identify(particles).map(Substance::id).orElse("formless");
    }

    private static double capacity(Particles code) {
        return Drives.CAPACITY[Drives.FIRMO] * code.firmo() + Drives.CAPACITY[Drives.AQUA] * code.aqua()
                + Drives.CAPACITY[Drives.AURA] * code.aura() + Drives.CAPACITY[Drives.IGNI] * code.igni();
    }

    /** The agitation that brings a block of {@code substance} from rest to {@code temperature}. */
    private static long toHeat(String substance, double temperature) {
        return (long) Math.ceil((temperature - Drives.AT_REST) * capacity(block(substance)));
    }

    /** A box of three by three by three, all {@code substance} but the middle, which is left empty for what is put there. */
    private static void fill(Box box, String substance) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x != 1 || y != 1 || z != 1) {
                        box.put(x, y, z, block(substance));
                    }
                }
            }
        }
    }

    /**
     * A box of {@code n} by {@code n} by {@code n} blocks of air, the air settled, with the blocks from x to {@code toX}
     * along the row at y, z left empty for what is put there.
     */
    private static Box air(int n, int x, int y, int z, int toX) {
        Box box = new Box(n, n, n);
        for (int ax = 0; ax < n; ax++) {
            for (int ay = 0; ay < n; ay++) {
                for (int az = 0; az < n; az++) {
                    if (ay != y || az != z || ax < x || ax > toX) {
                        box.put(ax, ay, az, block("air"));
                    }
                }
            }
        }
        box.steps(3);
        return box;
    }

    /** As {@link #air(int, int, int, int, int)}, one block left empty. */
    private static Box air(int n, int x, int y, int z) {
        return air(n, x, y, z, x);
    }

    /**
     * Holds a flame to a block for {@code steps} steps: whenever it is less agitated than {@code temperature}, some
     * agitation is brought in from outside. A spark does not light a log; a flame held to it does.
     */
    private static void light(Box box, int x, int y, int z, double temperature, int steps) {
        light(box, x, y, z, temperature, steps, 30L);
    }

    /** As {@link #light}, bringing in {@code each} particles of agitation at a time: a gentler flame overshoots less. */
    private static void light(Box box, int x, int y, int z, double temperature, int steps, long each) {
        for (int step = 0; step < steps; step++) {
            if (box.temperature(x, y, z) < temperature) {
                box.heat(x, y, z, each);
            }
            box.step();
        }
    }

    // ------------------------------------------------------------------ air, heat and state

    @Test
    void airLeaksIntoEmptySpace() {
        Box box = new Box(2, 1, 1);
        box.put(0, 0, 0, block("air"));
        box.steps(200);
        long here = box.airborne(0, 0, 0).aura();
        long there = box.airborne(1, 0, 0).aura();
        assertEquals(Particles.BLOCK, here + there, "not one particle of air is lost");
        assertTrue(Math.abs(here - there) <= 64, "the air should spread evenly, " + here + " and " + there);
    }

    @Test
    void hotAirRises() {
        Box column = new Box(1, 5, 1);
        for (int y = 0; y < 5; y++) {
            column.put(0, y, 0, block("air"));
        }
        column.steps(5);
        column.heat(0, 2, 0, 20L);
        column.steps(60);
        double height = 0.0D;
        long all = 0L;
        for (int y = 0; y < 5; y++) {
            height += y * column.heat(0, y, 0);
            all += column.heat(0, y, 0);
        }
        height /= all;
        assertTrue(height > 2.3D, "the heat put in the middle should rise with the air, now at height " + height);
        assertTrue(column.temperature(0, 4, 0) > column.temperature(0, 0, 0), "the top warmer than the bottom");
    }

    @Test
    void stoneMeltsIntoLavaAndCoolsBackIntoStone() {
        Box box = new Box(1, 1, 1);
        box.put(0, 0, 0, block("stone"));
        long melting = toHeat("stone", 5.8D);
        box.heat(0, 0, 0, melting);
        box.steps(3);
        assertEquals(State.LIQUID, box.state(0, 0, 0), "at " + box.temperature(0, 0, 0) * KELVIN + " K");
        assertTrue(box.glows(0, 0, 0), "lava glows");
        assertEquals("stone", named(box.held(0, 0, 0)), "molten stone is still stone, and keeps its air: lava");
        box.heat(0, 0, 0, -box.heat(0, 0, 0));
        box.steps(60);
        assertEquals(State.SOLID, box.state(0, 0, 0));
        assertEquals("stone", named(box.held(0, 0, 0)), "cooled, it is stone again: " + box.held(0, 0, 0));
    }

    @Test
    void waterBoilsIntoSteamAndTheSteamGivesItsFireBackAsItCondenses() {
        Box box = new Box(1, 2, 1);
        box.put(0, 0, 0, block("water"));
        box.heat(0, 0, 0, toHeat("water", 1.27D) + 300L);
        box.steps(60);
        Particles steam = box.airborne(0, 1, 0);
        assertTrue(steam.aqua() > 0L, "vapour should rise from the boiling water: " + box.airborne(0, 0, 0));
        assertEquals("steam", named(steam), "water keeping the fire it boiled with, nine to one, is steam: " + steam);
        long fire = steam.igni();
        box.heat(0, 1, 0, -box.heat(0, 1, 0) - 300L);
        box.heat(0, 0, 0, -box.heat(0, 0, 0) - 300L);
        box.steps(60);
        assertTrue(box.airborne(0, 1, 0).aqua() < steam.aqua(), "cooled, the vapour should condense");
        assertTrue(box.held(0, 1, 0).aqua() + box.held(0, 0, 0).aqua() > block("water").aqua() - steam.aqua(),
                "back into water");
        assertTrue(fire > 0L, "and the fire it held is agitation again");
    }

    @Test
    void earthAndWaterMixedAreMudAndStay() {
        Box box = new Box(1, 1, 1);
        Particles mud = new Particles(2048L, 2048L, 0L, 0L);
        box.put(0, 0, 0, mud);
        box.steps(50);
        assertEquals(mud, box.held(0, 0, 0), "the water holds the earth, and nothing flies");
        assertEquals(State.SOLID, box.state(0, 0, 0));
        assertEquals("mud", named(box.held(0, 0, 0)));
    }

    @Test
    void earthAndAirHalfAndHalfFlyAsDustWhileSandStays() {
        Box dust = new Box(1, 1, 1);
        dust.put(0, 0, 0, new Particles(2048L, 0L, 2048L, 0L));
        dust.step();
        assertEquals(State.GAS, dust.state(0, 0, 0), "the air spreads more than the earth stays");
        assertEquals("dust", named(dust.airborne(0, 0, 0)));

        Box sand = new Box(1, 1, 1);
        sand.put(0, 0, 0, block("sand"));
        sand.steps(50);
        assertEquals(block("sand"), sand.held(0, 0, 0), "the earth keeps the air in its pores");
        assertEquals(State.SOLID, sand.state(0, 0, 0));
    }

    /** User, 03/10/2026: whether snow holds depends on the agitation; the stiller the particles, the colder. */
    @Test
    void snowStaysSnowInTheColdAndMeltsIntoWaterInTheWarm() {
        Box cold = new Box(1, 1, 1);
        cold.put(0, 0, 0, block("snow"));
        cold.heat(0, 0, 0, -(long) Math.ceil(0.1D * capacity(block("snow"))));
        cold.steps(100);
        assertTrue(cold.temperature(0, 0, 0) < Drives.MELT, "kept below the melting of water");
        assertEquals(State.SOLID, cold.state(0, 0, 0));
        assertEquals(block("snow"), cold.held(0, 0, 0), "frozen, the water is still and keeps the air between it");

        Box warm = new Box(1, 1, 1);
        warm.put(0, 0, 0, block("snow"));
        warm.steps(100);
        assertEquals(State.LIQUID, warm.state(0, 0, 0));
        assertEquals("water", named(warm.held(0, 0, 0)), "at rest it melts, and the water joins and lets the air go: "
                + warm.held(0, 0, 0));
        assertEquals(block("snow").aura(), warm.airborne(0, 0, 0).aura(), "the air is out, not carried off as mist");
    }

    // ------------------------------------------------------------------ fire

    /** User, 03/10/2026: close to the real one; a fire must be strong enough to go from log to log. */
    @Test
    void aFlameHeldToOneEndOfARowOfLogsBurnsThemAll() {
        Box row = new Box(7, 4, 3);
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 3; z++) {
                    if (y != 1 || z != 1 || x == 0 || x == 6) {
                        row.put(x, y, z, block("air"));
                    }
                }
            }
        }
        row.steps(3);
        for (int x = 1; x <= 5; x++) {
            row.put(x, 1, 1, block("wood"));
        }
        light(row, 1, 1, 1, 3.0D, 80);
        double hottest = 0.0D;
        boolean flame = false;
        boolean plasma = false;
        for (int step = 0; step < 1500; step++) {
            row.step();
            for (int x = 0; x < 7; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 3; z++) {
                        flame |= row.fire(x, y, z);
                        plasma |= row.state(x, y, z) == State.PLASMA;
                        if (y == 1 && z == 1 && x >= 1 && x <= 5) {
                            hottest = Math.max(hottest, row.temperature(x, y, z));
                        }
                    }
                }
            }
        }
        for (int x = 1; x <= 5; x++) {
            assertTrue(row.held(x, 1, 1).igni() < block("wood").igni() / 2, "log " + x + " should have caught and burnt "
                    + "most of its fire (the closed room's air runs short of the rest): " + row.held(x, 1, 1));
        }
        assertTrue(flame, "the air the fire agitates glows: the flame");
        assertFalse(plasma, "a wood fire is no lightning");
        assertTrue(hottest * KELVIN > 1000.0D && hottest * KELVIN < 1600.0D, "a wood fire burns at about 1000 to 1600 K, "
                + "was " + hottest * KELVIN);
    }

    @Test
    void coalBurnsHotterThanWoodAndTheStoneBesideItDoesNotMelt() {
        String[] fuels = {"wood", "coal", "coal"};
        double[] hottest = new double[3];
        double stone = 0.0D;
        Box forge = null;
        for (int n = 0; n < 3; n++) {
            boolean walled = n == 2;
            forge = walled ? air(5, 2, 1, 2, 3) : air(5, 2, 1, 2);
            forge.put(2, 1, 2, block(fuels[n]));
            if (walled) {
                forge.put(3, 1, 2, block("stone"));
            }
            for (int step = 0; step < 800; step++) {
                if (step < 80 && forge.temperature(2, 1, 2) < 4.0D) {
                    forge.heat(2, 1, 2, 30L);
                }
                forge.step();
                if (step >= 80) {
                    // the flame: the air around the fuel, once the flame held to it is gone
                    hottest[n] = Math.max(hottest[n], Math.max(forge.temperature(2, 2, 2),
                            Math.max(forge.temperature(1, 1, 2), forge.temperature(2, 1, 1))));
                }
                if (walled) {
                    stone = Math.max(stone, forge.temperature(3, 1, 2));
                }
            }
            assertTrue(forge.held(2, 1, 2).igni() < block(fuels[n]).igni() / 10, "the " + fuels[n] + " burns out: "
                    + forge.held(2, 1, 2));
        }
        assertTrue(hottest[0] * KELVIN > 1000.0D, "a wood flame burns at more than 1000 K, was " + hottest[0] * KELVIN);
        assertTrue(hottest[1] > hottest[0], "coal's flame is hotter than wood's: " + hottest[1] * KELVIN + " K to "
                + hottest[0] * KELVIN);
        assertTrue(hottest[1] * KELVIN < 2200.0D, "but no hotter than a real coal fire: " + hottest[1] * KELVIN + " K");
        assertTrue(stone < Drives.MELT * 5.95D, "the stone beside it warms but does not melt: " + stone * KELVIN + " K");
        assertEquals(State.SOLID, forge.state(3, 1, 2));
    }

    @Test
    void coalBurnsWithAirAndKeepsItsFireWithout() {
        Box open = new Box(3, 3, 3);
        fill(open, "air");
        open.steps(3);
        open.put(1, 1, 1, block("coal"));
        light(open, 1, 1, 1, 4.0D, 60);
        open.steps(600);
        long burnt = block("coal").igni() - open.held(1, 1, 1).igni();
        assertTrue(burnt > block("coal").igni() * 3 / 4, "with air around it, the coal's fire should go: "
                + open.held(1, 1, 1));
        long smoke = 0L;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    smoke += open.smoke(x, y, z);
                }
            }
        }
        assertTrue(smoke > 0L && smoke <= burnt * Drives.AIR_PER_FIRE, "the air the fire took is smoke: " + smoke);
        assertTrue(open.held(1, 1, 1).firmo() < block("coal").firmo() / 4, "and the earth that held it crumbled into ash: "
                + open.held(1, 1, 1));

        Box sealed = new Box(1, 1, 1);
        sealed.put(0, 0, 0, block("coal"));
        sealed.heat(0, 0, 0, toHeat("coal", 3.5D));
        sealed.steps(400);
        assertEquals(block("coal").igni(), sealed.held(0, 0, 0).igni(), "with no air it keeps its fire");
        assertEquals(block("coal").igni(), sealed.charred(0, 0, 0), "and, past its ignition, it chars: coke");
    }

    /**
     * User, 03/10/2026: the fire needs room to flicker, and mass takes room, so what is mostly mass resists. The more
     * mass for the room in its block, the more agitation it takes to let the fire go.
     */
    @Test
    void theMoreMassAroundTheFireTheMoreItTakesToLetItGo() {
        Box rest = new Box(3, 3, 3);
        fill(rest, "air");
        rest.steps(3);
        rest.put(1, 1, 1, block("magma"));
        rest.steps(300);
        assertEquals(block("magma").igni(), rest.held(1, 1, 1).igni(), "magma, half earth, keeps its fire at rest in air");

        Particles coal = block("coal");
        Particles piece = new Particles(coal.firmo() / 8, 0L, 0L, coal.igni() / 8);
        String[] names = {"wood", "coal", "a piece of coal"};
        Particles[] fuels = {block("wood"), coal, piece};
        long[] burnt = new long[3];
        for (int n = 0; n < 3; n++) {
            Box box = new Box(3, 3, 3);
            fill(box, "air");
            box.steps(3);
            box.put(1, 1, 1, fuels[n]);
            light(box, 1, 1, 1, 2.5D, 300, 3L);
            burnt[n] = fuels[n].igni() - box.held(1, 1, 1).igni();
        }
        assertTrue(burnt[0] > 0L, names[0] + " lets its fire go at about 460 °C");
        assertEquals(0L, burnt[1], names[1] + ", more mass, does not yet");
        assertTrue(burnt[2] > 0L, names[2] + ", the same coal with room around it, does");

        Box gold = new Box(3, 3, 3);
        fill(gold, "air");
        gold.steps(3);
        gold.put(1, 1, 1, block("gold"));
        light(gold, 1, 1, 1, 4.8D, 200);
        assertEquals(State.LIQUID, gold.state(1, 1, 1), "gold melts");
        assertEquals(block("gold").igni(), gold.held(1, 1, 1).igni(), "before its mass lets its fire go");
    }

    @Test
    void theWaterInWoodDrinksTheHeatThatWouldLightIt() {
        Particles wood = block("wood");
        Particles dried = new Particles(wood.firmo(), 0L, wood.aura(), wood.igni());
        long heat = (long) Math.ceil(4.0D * capacity(dried));
        long[] burnt = new long[2];
        Particles[] woods = {dried, wood};
        for (int n = 0; n < 2; n++) {
            Box box = air(3, 1, 1, 1);
            box.put(1, 1, 1, woods[n]);
            box.heat(1, 1, 1, heat);
            box.steps(600);
            burnt[n] = woods[n].igni() - box.held(1, 1, 1).igni();
        }
        assertTrue(burnt[0] > dried.igni() / 2, "dry wood lit by that much heat burns: " + burnt[0]);
        assertTrue(burnt[1] < wood.igni() / 10, "the same heat in wet wood goes into its water, and it does not light: "
                + burnt[1]);
    }

    @Test
    void aClosedRoomPutsTheFireOut() {
        Box room = new Box(3, 1, 1);
        room.put(0, 0, 0, block("coal"));
        room.put(1, 0, 0, new Particles(0L, 0L, Particles.BLOCK / 2, 0L));
        room.put(2, 0, 0, block("coal"));
        room.steps(2);
        for (int step = 0; step < 60; step++) {
            for (int x = 0; x <= 2; x += 2) {
                if (room.temperature(x, 0, 0) < 4.0D) {
                    room.heat(x, 0, 0, 30L);
                }
            }
            room.step();
        }
        room.steps(800);
        long fireLeft = room.held(0, 0, 0).igni() + room.held(2, 0, 0).igni();
        long air = room.airborne(1, 0, 0).aura() - room.smoke(1, 0, 0);
        assertTrue(air < Drives.AIR_PER_FIRE, "the air of the room is spent, too little left to let one more particle "
                + "of fire go: " + air + " left, smoke " + room.smoke(1, 0, 0));
        assertTrue(fireLeft > 0L, "with fuel still left: " + fireLeft);
        room.steps(100);
        assertEquals(fireLeft, room.held(0, 0, 0).igni() + room.held(2, 0, 0).igni(), "and it burns no more");
    }

    /**
     * Water poured on burning coal drinks the agitation, and the coal cools below its ignition. A burning block takes
     * half a block of water: with less, the flame around it heats it back and it lights again.
     */
    @Test
    void waterPouredOnBurningCoalPutsItOut() {
        long[] left = new long[2];
        long[] before = new long[2];
        for (int n = 0; n < 2; n++) {
            Box box = air(5, 2, 1, 2);
            box.put(2, 1, 2, block("coal"));
            light(box, 2, 1, 2, 4.0D, 60);
            before[n] = box.held(2, 1, 2).igni();
            if (n == 1) {
                box.put(2, 1, 2, new Particles(0L, Particles.BLOCK / 2, 0L, 0L));
            }
            box.steps(800);
            left[n] = box.held(2, 1, 2).igni();
        }
        assertTrue(before[0] < block("coal").igni(), "it was burning: " + before[0]);
        assertTrue(left[0] < block("coal").igni() / 10, "left alone, the coal burns out: " + left[0]);
        assertTrue(left[1] > before[1] * 9 / 10, "the water drinks the agitation and the fire goes out: " + left[1] + " of "
                + before[1]);
    }

    // ------------------------------------------------------------------ fire and lightning

    /**
     * User, 03/10/2026: when agitated air is fire and when it is lightning. Past the glow every thing shines, and gas
     * that shines is flame; past the face of the sun the agitation beats the expansion, and air is plasma: lightning.
     */
    @Test
    void agitatedAirGlowsAsFireAndPastTheSunItIsLightning() {
        Box warm = new Box(1, 1, 1);
        warm.blow(0, 0, 0, block("air"));
        warm.heat(0, 0, 0, 13L);
        assertFalse(warm.fire(0, 0, 0), "air at " + warm.temperature(0, 0, 0) * KELVIN + " K does not glow");

        Box flame = new Box(1, 1, 1);
        flame.blow(0, 0, 0, block("air"));
        flame.heat(0, 0, 0, 27L);
        assertTrue(flame.fire(0, 0, 0), "air at " + flame.temperature(0, 0, 0) * KELVIN + " K glows: fire");
        assertEquals(State.GAS, flame.state(0, 0, 0), "flame is still gas");

        Box bolt = new Box(1, 1, 1);
        bolt.blow(0, 0, 0, block("lightning"));
        assertEquals(State.PLASMA, bolt.state(0, 0, 0), "half air, half free fire, is plasma");
        assertFalse(bolt.fire(0, 0, 0), "past flame");
        double kelvin = bolt.temperature(0, 0, 0) * KELVIN;
        assertTrue(kelvin > 20_000.0D && kelvin < 40_000.0D, "as hot as a real bolt, about 30 000 K: " + kelvin);

        Box fire = new Box(1, 1, 1);
        fire.blow(0, 0, 0, block("fire"));
        assertEquals(State.PLASMA, fire.state(0, 0, 0), "fire with nothing to agitate but itself");
        assertTrue(fire.temperature(0, 0, 0) > bolt.temperature(0, 0, 0), "is the most agitated of all");
    }

    // ------------------------------------------------------------------ charring

    /** User, 03/10/2026: what should char turns into charcoal or melts; heated slowly, it chars. */
    @Test
    void woodHeatedSlowlyWithoutAirBecomesCharcoal() {
        Box retort = new Box(1, 3, 1);
        retort.put(0, 0, 0, block("wood"));
        for (int step = 0; step < 800; step++) {
            if (retort.temperature(0, 0, 0) < 3.2D) {
                retort.heat(0, 0, 0, 2L);
            }
            retort.step();
        }
        Particles charcoal = retort.held(0, 0, 0);
        assertEquals(State.SOLID, retort.state(0, 0, 0));
        assertEquals("coal", named(charcoal), "three of earth gripping two of fire: charcoal, " + charcoal);
        assertEquals(block("wood").igni(), charcoal.igni(), "with no air it keeps all its fire");
        assertEquals(charcoal.igni(), retort.charred(0, 0, 0), "gripped by the earth");
        Particles above = retort.airborne(0, 1, 0);
        assertTrue(above.aqua() > 0L && above.firmo() > 0L, "its water and the earth that gripped no fire went up: " + above);

        retort.heat(0, 0, 0, toHeat("coal", 5.8D));
        retort.steps(20);
        assertEquals(State.SOLID, retort.state(0, 0, 0), "char does not melt where the wood would: at "
                + retort.temperature(0, 0, 0) * KELVIN + " K");
    }

    /** Heated all at once, it reaches its melting before it chars, and a liquid does not char: it melts. */
    @Test
    void woodHeatedAllAtOnceMeltsInstead() {
        Box box = new Box(1, 1, 1);
        box.put(0, 0, 0, block("wood"));
        box.heat(0, 0, 0, toHeat("wood", 6.1D));
        box.steps(40);
        assertEquals(State.LIQUID, box.state(0, 0, 0), "at " + box.temperature(0, 0, 0) * KELVIN + " K: "
                + box.held(0, 0, 0));
        assertEquals(0L, box.charred(0, 0, 0), "it melted before it could char");
    }

    // ------------------------------------------------------------------ the ledger

    @Test
    void nothingIsMadeOrLost() {
        Random random = new Random(3);
        String[] things = {"air", "water", "stone", "wood", "coal", "sand", "mud", "earth", "snow", "leaves", "gold",
                "magma", "steam", "lightning"};
        for (int trial = 0; trial < 20; trial++) {
            Box box = new Box(3, 3, 3, trial);
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 3; z++) {
                        if (random.nextInt(4) > 0) {
                            box.put(x, y, z, block(things[random.nextInt(things.length)]));
                        }
                        if (random.nextInt(3) == 0) {
                            box.heat(x, y, z, random.nextInt(2_000));
                        }
                    }
                }
            }
            Particles before = box.total();
            for (int step = 0; step < 150; step++) {
                box.step();
                assertEquals(before, box.total(), "trial " + trial + ", step " + step);
            }
        }
    }

    @Test
    void nothingIsColderThanAbsoluteZero() {
        Box box = new Box(2, 1, 1);
        box.put(0, 0, 0, block("stone"));
        box.put(1, 0, 0, block("stone"));
        box.heat(0, 0, 0, -1_000_000L);
        assertEquals(0.0D, box.temperature(0, 0, 0), 1.0E-9D, "the lack has a floor");
        Particles before = box.total();
        box.steps(50);
        assertTrue(box.temperature(0, 0, 0) > 0.0D, "the warm stone beside it gives it agitation");
        assertEquals(before, box.total());
    }
}

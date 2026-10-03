package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What particles are, read in the one table (docs/particulas-design.md, section 6): without an anchor, the thing whose
 * code is nearest; with one, the being whose proportion is nearest.
 */
class IdentityTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    private static Particles block(String substance) {
        return TABLE.substance(substance).orElseThrow().code(Particles.BLOCK);
    }

    private static Particles body(String being) {
        return TABLE.being(being).orElseThrow().code(Particles.BLOCK);
    }

    /** The table with only these kinds of being, for what the choice between them shows. */
    private static MaterialTable onlyKinds(String... kept) {
        MaterialTableBuilder builder = MaterialTableBuilder.from(TABLE);
        java.util.Set<String> keep = java.util.Set.of(kept);
        TABLE.beings().stream().map(Being::id).filter(id -> !keep.contains(id)).forEach(builder::remove);
        return builder.build();
    }

    @Test
    void whatIsHeldIsTheThingWhoseCodeIsNearest() {
        assertEquals(new Identity.Thing(TABLE.substance("stone").orElseThrow()), TABLE.identify(block("stone"), false));
        Particles halfEarthHalfFire = new Particles(2048, 0, 0, 2048);
        assertEquals(new Identity.Thing(TABLE.substance("magma").orElseThrow()),
                TABLE.identify(halfEarthHalfFire, false), "terra + fogo, the book's fusus");
        Particles coldWater = block("water").minus(Particles.of(VitaElement.IGNI, 300));
        assertEquals(new Identity.Thing(TABLE.primordial(VitaElement.AQUA)), TABLE.identify(coldWater, false),
                "water owing fire is cold water, still water");
    }

    @Test
    void matterNearNoCodeIsFormless() {
        Particles mixed = new Particles(1843, 0, 410, 1843);
        Identity identity = TABLE.identify(mixed, false);
        Identity.Formless formless = assertInstanceOf(Identity.Formless.class, identity, "terra 45, ar 10, fogo 45");
        assertEquals(0.45D, formless.composition().share(VitaElement.FIRMO), 0.001D);
    }

    @Test
    void nothingHeldIsNothing() {
        assertEquals(new Identity.Nothing(), TABLE.identify(Particles.NONE, false), "a vacuum");
        assertEquals(new Identity.Nothing(), TABLE.identify(Particles.of(VitaElement.IGNI, -50), true),
                "a debt alone is nothing, anchored or not");
    }

    @Test
    void withAnAnchorTheSameMatterIsABeing() {
        Particles snow = block("snow");
        assertEquals(new Identity.Thing(TABLE.substance("snow").orElseThrow()), TABLE.identify(snow, false));
        Identity.Creature golem = assertInstanceOf(Identity.Creature.class, TABLE.identify(snow, true));
        assertEquals("snow_golem", golem.being().id(), "snow with an anchor is a snow golem");
        assertFalse(golem.sound(), "a little off");
        assertEquals(0.06D, golem.deviation().get(VitaElement.AURA), 0.001D, "too much air");
        assertEquals(-0.04D, golem.deviation().get(VitaElement.FIRMO), 0.001D, "too little earth");
    }

    @Test
    void aHomunculusIsAQuarterFleshAThirdAirAndTwoFifthsWater() {
        Particles flesh = TABLE.substance("flesh").orElseThrow().code(1024);
        Particles made = flesh.plus(Particles.of(VitaElement.AURA, 1454)).plus(Particles.of(VitaElement.AQUA, 1638));
        Identity.Creature being = assertInstanceOf(Identity.Creature.class, TABLE.identify(made, true));
        assertEquals("homunculus", being.being().id());
        assertTrue(being.sound(), "the proportion of a person, within the sound");
    }

    @Test
    void fleshAloneIsNoPersonItLacksTheBreath() {
        Identity.Creature dead = assertInstanceOf(Identity.Creature.class, TABLE.identify(block("flesh"), true));
        assertEquals("zombie", dead.being().id(), "flesh with an anchor and no breath is dead flesh walking");
        Identity.Creature lump = assertInstanceOf(Identity.Creature.class,
                onlyKinds("homunculus", "slime").identify(block("flesh"), true));
        assertEquals("slime", lump.being().id(), "between a person and a slime, flesh with no air is the slime");
    }

    @Test
    void neighbouringKindsPassFromOneToTheOtherSoundly() {
        Particles pig = body("pig");
        Particles cow = body("cow");
        Particles way = cow.minus(pig);
        for (int step = 0; step <= 10; step++) {
            Particles along = pig.plus(new Particles(Math.floorDiv(way.firmo() * step, 10),
                    Math.floorDiv(way.aqua() * step, 10), Math.floorDiv(way.aura() * step, 10),
                    Math.floorDiv(way.igni() * step, 10)));
            Identity.Creature being = assertInstanceOf(Identity.Creature.class, TABLE.identify(along, true));
            assertTrue(being.being().id().equals("pig") || being.being().id().equals("cow"), "step " + step);
            assertTrue(being.sound(), "no element strays three points from the nearest kind, step " + step);
        }
        assertEquals("pig", ((Identity.Creature) TABLE.identify(pig, true)).being().id());
        assertEquals("cow", ((Identity.Creature) TABLE.identify(cow, true)).being().id());
    }

    @Test
    void aBodyAsNearTwoBeingsIsTheOneListedFirst() {
        // 30 firmo, 105 aqua, 48 aura and 27 igni are 72/210 from a cow and from a pig alike; rounding alone would say pig.
        Identity.Creature tie = assertInstanceOf(Identity.Creature.class,
                onlyKinds("cow", "pig").identify(new Particles(30, 105, 48, 27), true));
        assertEquals("cow", tie.being().id(), "the cow comes first in the table");
    }

    @Test
    void aCreatureOfTheGameShowsItsBeing() {
        assertEquals("cow", TABLE.beingShownBy("minecraft:cow").map(Being::id).orElseThrow());
        assertEquals("homunculus", TABLE.beingShownBy("elderlexicon:homunculus").map(Being::id).orElseThrow());
        assertEquals("zombie", TABLE.beingShownBy("minecraft:zombie").map(Being::id).orElseThrow());
        assertTrue(TABLE.beingShownBy("minecraft:ender_dragon").isEmpty(), "a creature the table lacks");
    }
}

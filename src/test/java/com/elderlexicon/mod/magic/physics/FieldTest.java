package com.elderlexicon.mod.magic.physics;

import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the drives say of matter as it comes into the world (docs/particulas-design.md, stage 9): how agitated it is by
 * its nature, the state that leaves it in, whether it can stay as it is, what mixing does, and how what nothing holds
 * any more goes into the air.
 */
class FieldTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    private static Particles block(String id) {
        return Particles.in(TABLE.substance(id).orElseThrow().recipe(), Particles.BLOCK);
    }

    private static Particles blocks(String id, int count) {
        Particles all = Particles.NONE;
        for (int n = 0; n < count; n++) {
            all = all.plus(block(id));
        }
        return all;
    }

    // ------------------------------------------------------------------ by nature

    @Test
    void matterIsAsAgitatedAsItsStateNeeds() {
        assertEquals(1.0D, Field.natural(block("water"), State.LIQUID), 1.0E-9, "water rests as the world does");
        assertEquals(1.0D, Field.natural(block("stone"), State.SOLID), 1.0E-9);
        assertEquals(Field.melting(block("water")) * 0.97D, Field.natural(block("water"), State.SOLID), 1.0E-9,
                "ice is just short of its melting");
        double lava = Field.natural(block("stone"), State.LIQUID);
        assertEquals(Field.melting(block("stone")) * 1.03D, lava, 1.0E-9, "lava just past it");
        assertTrue(lava > Field.glow(), "and it glows");
    }

    @Test
    void theStateIsWhatTheAgitationLeavesIt() {
        assertEquals(State.LIQUID, Field.state(block("water"), 1.0D));
        assertEquals(State.SOLID, Field.state(block("water"), 0.5D));
        assertEquals(State.GAS, Field.state(block("water"), 2.0D));
        assertEquals(State.SOLID, Field.state(block("stone"), 1.0D));
        assertEquals(State.LIQUID, Field.state(block("stone"), Field.natural(block("stone"), State.LIQUID)));
        assertEquals(State.PLASMA, Field.state(block("air"), Field.plasma() + 1.0D));
    }

    @Test
    void earthHoldsBackWhatWadesInItAndWaterHardly() {
        assertEquals(1.0D, Field.holding(block("earth")), 1.0E-9);
        assertEquals(1.0D / 7.0D, Field.holding(block("water")), 1.0E-9);
        assertEquals(0.0D, Field.holding(block("air")), 1.0E-9);
        assertEquals(0.0D, Field.holding(Particles.NONE), 1.0E-9);
        assertEquals(8.0D / 14.0D, Field.holding(new Particles(2048L, 2048L, 0L, 0L)), 1.0E-9);
    }

    // ------------------------------------------------------------------ what can stay as it is

    @Test
    void whatCanStayAsItIsRests() {
        assertFalse(Field.restless(block("stone"), State.SOLID));
        assertFalse(Field.restless(block("sand"), State.SOLID), "sand holds the air in its pores");
        assertFalse(Field.restless(block("snow"), State.SOLID), "snow in the cold holds its air");
        assertFalse(Field.restless(block("earth"), State.LIQUID), "molten earth stays molten, as lava does");
        assertFalse(Field.restless(block("water"), State.LIQUID));
    }

    @Test
    void whatCannotStayAsItIsStirs() {
        assertTrue(Field.restless(new Particles(0L, 2731L, 0L, 1365L), State.LIQUID),
                "fire with no mass to hold it is agitation: the water with it boils");
        assertTrue(Field.restless(block("snow"), State.LIQUID), "melted snow lets its air go");
        assertTrue(Field.restless(new Particles(2048L, 0L, 2048L, 0L), State.SOLID),
                "earth and air half and half fly as dust");
        assertFalse(Field.restless(Particles.NONE, State.SOLID), "nothing is still");
    }

    // ------------------------------------------------------------------ mixing (L4)

    @Test
    void waterPouredIntoWaterIsWaterAsItWas() {
        Field.Mixture mixture = Field.mix(List.of(new Field.Portion(block("water"), 1.0D),
                new Field.Portion(blocks("water", 3), 1.0D)));
        assertEquals(blocks("water", 4), mixture.held());
        assertEquals(1.0D, mixture.temperature(), 1.0E-9);
        assertEquals(State.LIQUID, mixture.state());
    }

    @Test
    void moltenEarthInMuchWaterIsQuenched() {
        Field.Mixture mixture = Field.mix(List.of(
                new Field.Portion(block("earth"), Field.natural(block("earth"), State.LIQUID)),
                new Field.Portion(blocks("water", 4), 1.0D)));
        assertEquals(State.SOLID, mixture.state(), "the water quenches it");
        assertTrue(mixture.temperature() > 1.0D && mixture.temperature() < 1.5D,
                "a little warmer than the world: " + mixture.temperature());
    }

    @Test
    void waterPouredOnLavaSetsItWarmAndSteaming() {
        Particles lava = blocks("stone", 2);
        Field.Mixture mixture = Field.mix(List.of(new Field.Portion(lava, Field.natural(lava, State.LIQUID)),
                new Field.Portion(blocks("water", 2), 1.0D)));
        assertEquals(State.SOLID, mixture.state());
        assertTrue(mixture.temperature() > 1.27D, "warm enough that its water boils off: " + mixture.temperature());
    }

    @Test
    void mixingMakesAndLosesNoAgitationNorParticle() {
        SplittableRandom random = new SplittableRandom(11L);
        for (int run = 0; run < 500; run++) {
            List<Field.Portion> portions = new ArrayList<>();
            Particles all = Particles.NONE;
            long agitation = 0L;
            int count = 1 + random.nextInt(4);
            for (int n = 0; n < count; n++) {
                Particles held = new Particles(random.nextLong(0L, 5000L), random.nextLong(0L, 5000L),
                        random.nextLong(0L, 2000L), random.nextLong(0L, 2000L));
                double temperature = 0.2D + random.nextDouble() * 6.0D;
                portions.add(new Field.Portion(held, temperature));
                all = all.plus(held);
                agitation += Field.agitation(held, Particles.NONE, temperature);
            }
            Field.Mixture mixture = Field.mix(portions);
            assertEquals(all, mixture.held(), "every particle, to the last");
            if (all.total() > 0L) {
                long after = Field.agitation(mixture.held(), Particles.NONE, mixture.temperature());
                assertEquals(agitation, after, count + 1.0D, "the agitation each brought, no more, no less");
            }
        }
    }

    // ------------------------------------------------------------------ into the air

    @Test
    void whatNothingHoldsFliesWithItsFireAloft() {
        Field field = new Field(0L);
        int dusty = field.add();
        field.scatter(dusty, new Particles(100L, 0L, 0L, 50L));
        Field.Cell cell = field.cell(dusty);
        assertEquals(100L, cell.airborne().firmo(), "its earth as dust");
        assertEquals(50L, cell.aloft(), "carrying its fire, still fuel");
        assertEquals(0L, cell.heat());
        int wet = field.add();
        field.scatter(wet, new Particles(0L, 30L, 0L, 20L));
        assertEquals(30L, field.cell(wet).airborne().aqua(), "its water as mist");
        assertEquals(20L, field.heat(wet), "fire with no dust to carry it is agitation");
    }

    @Test
    void vapourBlownInKeepsTheFireBoilingTook() {
        Field field = new Field(0L);
        int i = field.add();
        field.blow(i, new Particles(0L, 900L, 0L, 150L));
        assertEquals(100L, field.cell(i).bound(), "one of fire to nine of water is the vapour's");
        assertEquals(50L, field.heat(i), "the rest is agitation");
        assertEquals(new Particles(0L, 900L, 0L, 150L), field.total(), "nothing made or lost");
    }
}

package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/** A portion of matter, and the particles it is to the last one. */
class MatterTest {

    @Test
    void matterOfParticlesIsThoseParticlesAgain() {
        SplittableRandom random = new SplittableRandom(7L);
        for (int run = 0; run < 2000; run++) {
            Particles held = new Particles(random.nextLong(0L, 9000L), random.nextLong(0L, 9000L),
                    random.nextLong(0L, 9000L), random.nextLong(0L, 9000L));
            if (held.total() <= 0L) {
                continue;
            }
            Matter matter = Matter.of(held, State.LIQUID);
            assertEquals(held, matter.particles(), "read back to the last particle");
            assertEquals((double) held.total() / Particles.PER_UMU, matter.umu(), 0.0D);
        }
    }

    @Test
    void whatIsOwedIsNoPartOfMatter() {
        Matter matter = Matter.of(new Particles(100L, -40L, 0L, 0L), State.SOLID);
        assertEquals(new Particles(100L, 0L, 0L, 0L), matter.particles());
        assertThrows(IllegalArgumentException.class, () -> Matter.of(new Particles(0L, -5L, 0L, 0L), State.SOLID),
                "nothing held is no matter");
    }

    @Test
    void theSpiritTellsItsStateAndWhatItIsMadeOf() {
        Matter molten = new Matter(Composition.of(Map.of(VitaElement.FIRMO, 0.7D, VitaElement.IGNI, 0.3D)),
                State.LIQUID, 5.0D);
        assertEquals("líquida (terra 70%, fogo 30%)", molten.describe());
        Matter mist = new Matter(Composition.of(Map.of(VitaElement.AQUA, 1.0D, VitaElement.AURA, 1.0D)), State.GAS,
                1.0D);
        assertEquals("gasosa (água 50%, ar 50%)", mist.describe());
    }
}

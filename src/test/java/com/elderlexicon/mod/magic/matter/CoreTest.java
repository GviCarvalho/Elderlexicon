package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static com.elderlexicon.mod.vita.VitaElement.AQUA;
import static com.elderlexicon.mod.vita.VitaElement.AURA;
import static com.elderlexicon.mod.vita.VitaElement.FIRMO;
import static com.elderlexicon.mod.vita.VitaElement.IGNI;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The core (docs/particulas-design.md, section 6): a hundred parts, changed by converting what the thing already holds.
 */
class CoreTest {

    private static final MaterialTable TABLE = Materials.builtIn();
    private static final double CLOSE = 1.0E-9D;

    private static Substance substance(String id) {
        return TABLE.substance(id).orElseThrow();
    }

    private static Composition code(String id) {
        return substance(id).recipe();
    }

    private static Map<VitaElement, Double> asked(Object... pairs) {
        Map<VitaElement, Double> asked = new EnumMap<>(VitaElement.class);
        for (int i = 0; i < pairs.length; i += 2) {
            asked.put((VitaElement) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return asked;
    }

    @Test
    void oneAspectAskedTakesItsPartsAndTheOthersKeepTheirMix() {
        // A person's proportion (55/38/2/5) with water made sixteen parts: the 39 parts of water it gives up go to the
        // others in the proportion they had among themselves.
        Composition person = TABLE.being("homunculus").orElseThrow().recipe();
        Composition core = Core.reshape(person, asked(AQUA, 16)).orElseThrow();
        assertEquals(0.16D, core.share(AQUA), CLOSE);
        double rest = 0.84D / 0.45D;
        assertEquals(0.05D * rest, core.share(FIRMO), CLOSE);
        assertEquals(0.38D * rest, core.share(AURA), CLOSE);
        assertEquals(0.02D * rest, core.share(IGNI), CLOSE);
        assertEquals(core.share(AURA) / core.share(FIRMO), 0.38D / 0.05D, 1.0E-6D, "air and earth keep their mix");
    }

    @Test
    void aWholeCodeWrittenIsThatCode() {
        Composition core = Core.reshape(code("stone"), asked(FIRMO, 70, AURA, 30)).orElseThrow();
        assertEquals(code("sand"), core, "seventy of earth and thirty of air leave nothing for water and fire");
        assertEquals("sand", TABLE.identify(core).map(Substance::id).orElse(null));
    }

    @Test
    void thePartsAskedAreExactlyThatMuchOfTheHundred() {
        // A person: 2 of fire, 55 of water, 38 of air and 5 of earth, a hundred in all.
        Composition core = Core.reshape(code("stone"), asked(IGNI, 2, AQUA, 55, AURA, 38, FIRMO, 5)).orElseThrow();
        assertEquals(0.02D, core.share(IGNI), CLOSE);
        assertEquals(0.55D, core.share(AQUA), CLOSE);
        assertEquals(0.38D, core.share(AURA), CLOSE);
        assertEquals(0.05D, core.share(FIRMO), CLOSE);
    }

    @Test
    void partsThatCannotMakeAHundredMakeNoCore() {
        assertTrue(Core.reshape(code("stone"), asked(IGNI, 1, AQUA, 2, AURA, 5, FIRMO, 7)).isEmpty(),
                "all four written come to fifteen: nothing is left to make up the hundred");
        assertTrue(Core.reshape(code("stone"), asked(FIRMO, 80, AQUA, 80)).isEmpty(), "a hundred and sixty");
        assertTrue(Core.reshape(code("stone"), asked(FIRMO, 250)).isEmpty(), "more than the whole");
        assertTrue(Core.reshape(code("stone"), asked(FIRMO, -20)).isEmpty(), "less than nothing");
        assertTrue(Core.reshape(code("stone"), asked(FIRMO, 0, AQUA, 0, AURA, 0, IGNI, 0)).isEmpty(), "nothing at all");
        assertEquals(165.0D, Core.asked(asked(FIRMO, 80, AQUA, 85)), CLOSE);
    }

    @Test
    void theOnesWrittenWithoutANumberShareWhatIsLeftEvenly() {
        // The user's rule (01/10/2026): aqua vertere m1 makes m1 all water; aqua and aura make it half each.
        assertEquals(code("water"), Core.reshape(code("stone"), asked(), Set.of(AQUA)).orElseThrow());
        assertEquals(code("mist"), Core.reshape(code("stone"), asked(), Set.of(AQUA, AURA)).orElseThrow(),
                "half water and half air is mist's code");
        Composition mixed = Core.reshape(code("stone"), asked(FIRMO, 40), Set.of(AQUA, AURA)).orElseThrow();
        assertEquals(0.40D, mixed.share(FIRMO), CLOSE);
        assertEquals(0.30D, mixed.share(AQUA), CLOSE, "the sixty the number leaves, half each");
        assertEquals(0.30D, mixed.share(AURA), CLOSE);
        assertEquals(0.0D, mixed.share(IGNI), CLOSE, "what is not written at all keeps nothing");
        assertEquals(code("earth"), Core.reshape(code("stone"), asked(FIRMO, 100), Set.of(AQUA)).orElseThrow(),
                "a hundred numbered leaves nothing to share");
        assertTrue(Core.reshape(code("stone"), asked(FIRMO, 120), Set.of(AQUA)).isEmpty());
    }

    @Test
    void whatHeldNothingSharesTheRestEvenly() {
        Composition core = Core.reshape(code("water"), asked(AQUA, 40)).orElseThrow();
        assertEquals(0.40D, core.share(AQUA), CLOSE);
        for (VitaElement aspect : new VitaElement[] {FIRMO, AURA, IGNI}) {
            assertEquals(0.20D, core.share(aspect), CLOSE, aspect.runeId());
        }
        Composition hot = Core.reshape(code("water"), asked(IGNI, 30)).orElseThrow();
        assertEquals(0.30D, hot.share(IGNI), CLOSE);
        assertEquals(0.70D, hot.share(AQUA), CLOSE, "what was all water gives up the thirty parts");
    }

    @Test
    void nothingAskedLeavesItAsItIs() {
        assertEquals(Optional.of(code("stone")), Core.reshape(code("stone"), asked()));
        assertEquals(Optional.of(code("earth")), Core.reshape(code("stone"), asked(FIRMO, 100)),
                "the whole hundred of one leaves nothing for the rest");
    }

    @Test
    void convertingKeepsEveryParticle() {
        Particles stone = substance("stone").code(Particles.BLOCK);
        Particles earth = Core.convert(stone, code("earth"));
        assertEquals(Particles.of(FIRMO, Particles.BLOCK), earth, "a block of stone made earth is a block of earth");
        Random random = new Random(1);
        for (int trial = 0; trial < 500; trial++) {
            Particles held = new Particles(random.nextInt(5000), random.nextInt(5000), random.nextInt(5000),
                    random.nextInt(5000) - 2500);
            Composition core = Core.reshape(code("wood"), asked(Particles.ASPECTS.get(random.nextInt(4)),
                    random.nextInt(101))).orElseThrow();
            Particles converted = Core.convert(held, core);
            assertEquals(held.total(), converted.total(), "as many in all, what is owed included");
        }
        Particles owing = stone.minus(Particles.of(AQUA, 4000));
        Particles made = Core.convert(owing, code("earth"));
        assertEquals(owing.total(), made.total());
    }

    @Test
    void theWorkIsTheShortestClimbOnTheLadder() {
        Particles stone = substance("stone").code(Particles.BLOCK);
        Particles earth = Core.convert(stone, code("earth"));
        // 409 fire three rungs down, 205 air two, 205 water one: 1842 rungs of a particle, five hundredths of a UMU
        // each.
        assertEquals(1842.0D * MatterLaws.WORK_PER_STEP / Particles.PER_UMU, Core.work(stone, earth), CLOSE);
        assertEquals(Core.work(stone, earth), Core.work(earth, stone), CLOSE, "back is as far");
        Particles water = substance("water").code(Particles.BLOCK);
        Particles fire = Core.convert(water, code("fire"));
        assertEquals(16.0D * 2 * MatterLaws.WORK_PER_STEP, Core.work(water, fire), CLOSE,
                "a block of water made fire climbs two rungs, sixteen UMU of it");
        assertEquals(0.0D, Core.work(stone, stone), CLOSE);
    }

    @Test
    void theStateComesFromWhatItBecomes() {
        Optional<Substance> stone = Optional.of(substance("stone"));
        Optional<Substance> water = Optional.of(substance("water"));
        Optional<Substance> earth = Optional.of(substance("earth"));
        Optional<Substance> steam = Optional.of(substance("steam"));
        assertEquals(State.LIQUID, Core.state(State.SOLID, stone, water), "stone made water is water, not ice");
        assertEquals(State.SOLID, Core.state(State.LIQUID, water, stone), "water made stone is stone");
        assertEquals(State.LIQUID, Core.state(State.LIQUID, stone, water), "lava made all water is water");
        assertEquals(State.GAS, Core.state(State.LIQUID, stone, steam), "lava with its fire kept is vapour");
        assertEquals(State.SOLID, Core.state(State.LIQUID, stone, earth), "lava made earth is soil");
        assertEquals(State.LIQUID, Core.state(State.LIQUID, stone, stone), "lava still stone stays lava");
        assertEquals(State.SOLID, Core.state(State.LIQUID, Optional.empty(), stone),
                "formless matter in stone's code is stone");
        assertEquals(State.SOLID, Core.state(State.SOLID, stone, Optional.empty()), "what becomes formless keeps it");
    }
}

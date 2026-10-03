package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Particles (docs/particulas-design.md): whole, signed, and kept to the last one wherever they go. */
class ParticlesTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    private static Particles block(String substance) {
        return TABLE.substance(substance).orElseThrow().code(Particles.BLOCK);
    }

    @Test
    void aCodeIsWholeParticlesThatAddUpToTheBlockOrTheItem() {
        assertEquals(new Particles(3277, 205, 205, 409), block("stone"), "stone, 80/5/5/10 of a block");
        assertEquals(new Particles(154, 51, 0, 51), TABLE.substance("bone").orElseThrow().code(Particles.ITEM),
                "a bone, 60/20/20 of an item, one layer of 256");
        assertEquals(Particles.of(VitaElement.AQUA, 4096), block("water"), "4096 aqua is water");
        assertEquals(Particles.of(VitaElement.FIRMO, 4096), block("earth"), "4096 firmo is earth");
        assertEquals(Particles.of(VitaElement.AURA, 4096), block("air"), "4096 aura is air");
        assertEquals(Particles.of(VitaElement.IGNI, 4096), block("fire"), "4096 igni is fire");
    }

    @Test
    void everyCodeInAnyNumberAddsUpExactly() {
        List<Composition> codes = new ArrayList<>();
        TABLE.substances().forEach(substance -> codes.add(substance.recipe()));
        TABLE.beings().forEach(being -> codes.add(being.recipe()));
        for (Composition code : codes) {
            for (long total : new long[] {0, 1, 3, 7, 255, 256, 4095, 4096, 4097, 65_536, 1_000_003}) {
                Particles particles = Particles.in(code, total);
                assertEquals(total, particles.total(), code + " in " + total);
                assertFalse(particles.owes());
                for (VitaElement aspect : Particles.ASPECTS) {
                    assertTrue(Math.abs(particles.count(aspect) - code.share(aspect) * total) < 1.0D,
                            aspect.runeId() + " of " + code + " in " + total + " is off by a particle or more");
                }
            }
        }
    }

    @Test
    void takingMoreThanIsThereLeavesADebt() {
        Particles water = block("water");
        Particles cold = water.minus(Particles.of(VitaElement.IGNI, 200));
        assertEquals(-200, cold.igni(), "water has no igni to give, but it can owe it");
        assertTrue(cold.owes());
        assertEquals(Particles.of(VitaElement.IGNI, 200), cold.owed());
        assertEquals(water, cold.present(), "what it holds is still all water");
        assertEquals(water.composition(), cold.composition(), "a debt is no part of what it is made of");
    }

    @Test
    void aDebtAndTheSameParticlesCancel() {
        Particles water = block("water");
        Particles iron = block("iron");
        Particles coldWater = water.minus(Particles.of(VitaElement.IGNI, 200));
        Particles hotIron = iron.plus(Particles.of(VitaElement.IGNI, 200));
        Particles heat = Particles.of(VitaElement.IGNI, 200);
        assertEquals(water, coldWater.plus(heat), "the water is paid back");
        assertEquals(iron, hotIron.minus(heat), "by the heat the iron had too much of");
        assertEquals(water.plus(iron), coldWater.plus(hotIron), "and between them nothing was made or lost");
    }

    @Test
    void pullingHeatFromTheAirLeavesItOwingFire() {
        // Book 9.2: warming the hands pulls heat from around, where there is no flame to take it from.
        Particles air = block("air");
        Particles pulled = Particles.of(VitaElement.IGNI, 3);
        Particles airAfter = air.minus(pulled);
        Particles hands = Particles.NONE.plus(pulled);
        assertEquals(-3, airAfter.igni(), "the air is left owing fire: it is colder");
        assertEquals(3, hands.igni());
        assertEquals(air, airAfter.plus(hands), "nothing made, nothing lost");
    }

    @Test
    void whatPassesBetweenHoldersIsKeptToTheParticle() {
        Random random = new Random(4096L);
        Particles[] holders = new Particles[8];
        Particles before = Particles.NONE;
        for (int i = 0; i < holders.length; i++) {
            holders[i] = new Particles(random.nextInt(5000), random.nextInt(5000), random.nextInt(5000),
                    random.nextInt(5000));
            before = before.plus(holders[i]);
        }
        for (int move = 0; move < 10_000; move++) {
            int from = random.nextInt(holders.length);
            int to = random.nextInt(holders.length);
            VitaElement aspect = Particles.ASPECTS.get(random.nextInt(Particles.ASPECTS.size()));
            Particles moved = Particles.of(aspect, random.nextInt(3000));
            holders[from] = holders[from].minus(moved);
            holders[to] = holders[to].plus(moved);
        }
        Particles after = Particles.NONE;
        boolean someoneOwes = false;
        for (Particles holder : holders) {
            after = after.plus(holder);
            someoneOwes |= holder.owes();
        }
        assertTrue(someoneOwes, "moving more than a holder has leaves it owing");
        assertEquals(before, after, "ten thousand moves later, every particle is still there");
    }

    @Test
    void aBlockSplitsIntoSixteenItemsThatAddBackUpToIt() {
        Particles stone = block("stone");
        List<Particles> items = stone.split(16);
        assertEquals(16, items.size());
        Particles sum = Particles.NONE;
        for (Particles item : items) {
            assertEquals(Particles.ITEM, item.total(), "each is one layer");
            assertEquals(Optional.of("stone"), TABLE.identify(item).map(Substance::id), "and each is stone");
            sum = sum.plus(item);
        }
        assertEquals(stone, sum, "the items are the block, particle by particle");
        Particles owing = new Particles(10, -5, 0, 3);
        Particles back = Particles.NONE;
        for (Particles part : owing.split(4)) {
            back = back.plus(part);
        }
        assertEquals(owing, back, "a debt splits too");
    }

    @Test
    void aBlockAsNatureFillsItIsAtOnePressure() {
        assertEquals(1.0D, block("air").pressure(), 1.0E-12);
        assertEquals(0.5D, Particles.of(VitaElement.AURA, 2048).pressure(), 1.0E-12, "fewer is rarefied");
        assertEquals(2.0D, Particles.of(VitaElement.AURA, 8192).pressure(), 1.0E-12, "more is compressed");
        assertEquals(0.0D, Particles.NONE.pressure(), 1.0E-12, "none is a vacuum");
        Particles coldWater = block("water").minus(Particles.of(VitaElement.IGNI, 200));
        Particles coldStone = block("stone").minus(Particles.of(VitaElement.IGNI, 200));
        assertEquals(coldStone.pressure(), coldWater.pressure(), 1.0E-12,
                "water owing igni is as rarefied as stone that lost as much of its own: cold contracts");
    }

    @Test
    void visIsNoParticleOfMatter() {
        assertThrows(IllegalArgumentException.class, () -> Particles.of(VitaElement.BALANCED, 1));
        assertThrows(IllegalArgumentException.class, () -> Particles.NONE.count(VitaElement.BALANCED));
    }

    @Test
    void aCountThatWouldOverflowIsRefusedNotWrapped() {
        Particles most = Particles.of(VitaElement.FIRMO, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> most.plus(Particles.of(VitaElement.FIRMO, 1)));
        assertThrows(ArithmeticException.class, () -> Particles.of(VitaElement.FIRMO, Long.MIN_VALUE).owed());
    }
}

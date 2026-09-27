package com.elderlexicon.mod.spell.nature;

import com.elderlexicon.mod.spell.AirPressure;
import com.elderlexicon.mod.spell.Pressure;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.elderlexicon.mod.spell.nature.NatureField.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatureFieldTest {

    /** A small world: air by default, with the blocks it was given, changing as the field says. */
    private static final class World implements NatureField.Matter {
        private final Map<Long, Material> blocks = new HashMap<>();
        private final Material rest;

        World(Material rest) {
            this.rest = rest;
        }

        World fill(int x0, int y0, int z0, int x1, int y1, int z1, Material material) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        blocks.put(key(x, y, z), material);
                    }
                }
            }
            return this;
        }

        @Override
        public Material at(long key) {
            return blocks.getOrDefault(key, rest);
        }

        /** Runs {@code steps} steps, keeping the world in step with the field; returns everything that happened. */
        List<NatureField.Step> run(NatureField field, int steps) {
            List<NatureField.Step> all = new ArrayList<>();
            for (int i = 0; i < steps; i++) {
                NatureField.Step step = field.step(this);
                for (NatureField.Change change : step.changes()) {
                    blocks.put(change.key(), change.to());
                }
                all.add(step);
            }
            return all;
        }
    }

    private static int bursts(List<NatureField.Step> steps) {
        return steps.stream().mapToInt(step -> step.bursts().size()).sum();
    }

    private static boolean changed(List<NatureField.Step> steps, Material from, Material to) {
        return steps.stream().flatMap(step -> step.changes().stream())
                .anyMatch(change -> change.from() == from && change.to() == to);
    }

    /** A pond: water one block deep in a stone basin, open to the air above. */
    private static World pond() {
        return new World(Material.AIR).fill(-4, -3, -4, 4, -1, 4, Material.STONE).fill(-2, -1, -2, 2, -1, 2, Material.WATER);
    }

    @Test
    void keysKeepTheirCoordinates() {
        long key = key(-1234, -64, 98765);
        assertEquals(-1234, NatureField.x(key));
        assertEquals(-64, NatureField.y(key));
        assertEquals(98765, NatureField.z(key));
        assertEquals(key(-1233, -63, 98764), NatureField.offset(key, 1, 1, -1));
    }

    @Test
    void aGentleHeatBoilsWaterAwayWithoutBursting() {
        World world = pond();
        NatureField field = new NatureField();
        List<NatureField.Step> steps = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            field.warm(world, key(0, -1, 0), 1.0D, 0.5D);
            steps.addAll(world.run(field, 1));
        }
        assertTrue(changed(steps, Material.WATER, Material.AIR), "the water boils away");
        assertTrue(steps.stream().anyMatch(step -> !step.steams().isEmpty()), "and steams while it does");
        assertEquals(0, bursts(steps), "the vapor gets out as it forms");
    }

    @Test
    void aSuddenGreatHeatInWaterBursts() {
        World world = new World(Material.STONE).fill(-3, -3, -3, 3, -1, 3, Material.WATER).fill(-10, 0, -10, 10, 10, 10,
                Material.AIR);
        NatureField field = new NatureField();
        double budget = 200.0D;
        for (int x = -1; x <= 1; x++) {
            for (int y = -2; y <= -1; y++) {
                for (int z = -1; z <= 1; z++) {
                    budget -= field.warm(world, key(x, y, z), 100.0D, budget / 10.0D);
                }
            }
        }
        List<NatureField.Step> steps = world.run(field, 5);
        assertTrue(bursts(steps) >= 1, "the water flashes to vapor faster than it can get out");
    }

    /** A condensed fire striking a lake's surface, as the fire spot does: half its heat into what it touches. */
    private static List<NatureField.Step> strikeLake(double heat) {
        World world = new World(Material.STONE).fill(-6, -4, -6, 6, -1, 6, Material.WATER).fill(-20, 0, -20, 20, 20, 20,
                Material.AIR);
        NatureField field = new NatureField();
        double reach = 1.5D;
        List<long[]> touched = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        double total = 0.0D;
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    double dx = x + 0.5D - 0.5D;
                    double dy = y + 0.5D - 0.0D;
                    double dz = z + 0.5D - 0.5D;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance <= reach + 0.5D && world.at(key(x, y, z)) == Material.WATER) {
                        double share = 1.0D - distance / (reach + 1.0D);
                        touched.add(new long[]{key(x, y, z)});
                        shares.add(share);
                        total += share;
                    }
                }
            }
        }
        for (int i = 0; i < touched.size(); i++) {
            field.warm(world, touched.get(i)[0], heat * shares.get(i), heat * 0.5D * shares.get(i) / total);
        }
        return world.run(field, 5);
    }

    @Test
    void aStrongCondensedFireOnALakeBurstsAndAWeakOneOnlySteams() {
        assertEquals(0, bursts(strikeLake(20.0D)), "a little condensed fire only steams");
        assertTrue(bursts(strikeLake(80.0D)) >= 1, "a strong one flashes the water it touches to vapor");
    }

    @Test
    void aLittleHeatInMuchWaterIsSimplySwallowed() {
        World world = new World(Material.WATER);
        NatureField field = new NatureField();
        field.heat(world, key(0, 0, 0), 0.5D);
        List<NatureField.Step> steps = world.run(field, 300);
        assertTrue(steps.stream().allMatch(step -> step.changes().isEmpty()), "the water around takes it all in");
        assertEquals(0, bursts(steps));
        assertTrue(field.isEmpty(), "and it all settles back");
    }

    @Test
    void lavaQuenchedByWaterSetsAsObsidian() {
        World world = new World(Material.WATER).fill(0, 0, 0, 0, 0, 0, Material.LAVA);
        NatureField field = new NatureField();
        field.wake(world, key(0, 0, 0));
        List<NatureField.Step> steps = world.run(field, 40);
        assertTrue(changed(steps, Material.LAVA, Material.OBSIDIAN));
        assertFalse(changed(steps, Material.LAVA, Material.BASALT));
    }

    @Test
    void lavaLeftInTheAirSetsSlowlyAsBasalt() {
        World world = new World(Material.AIR).fill(0, 0, 0, 0, 0, 0, Material.LAVA);
        NatureField field = new NatureField();
        field.wake(world, key(0, 0, 0));
        assertTrue(world.run(field, 40).stream().allMatch(step -> step.changes().isEmpty()), "it stays molten a while");
        List<NatureField.Step> steps = world.run(field, 3000);
        assertTrue(changed(steps, Material.LAVA, Material.BASALT));
    }

    @Test
    void aLavaLakeAtRestIsNotWokenByOneTouch() {
        World world = new World(Material.LAVA).fill(-20, 1, -20, 20, 5, 20, Material.AIR);
        NatureField field = new NatureField();
        field.heat(world, key(0, 1, 0), 0.1D);
        world.run(field, 400);
        assertTrue(field.isEmpty(), "the lake does not keep the air above it in the field");
    }

    @Test
    void iceMeltsWhenWarmed() {
        World world = new World(Material.AIR).fill(0, 0, 0, 0, 0, 0, Material.ICE);
        NatureField field = new NatureField();
        field.warm(world, key(0, 0, 0), 3.0D, 10.0D);
        assertTrue(changed(world.run(field, 5), Material.ICE, Material.WATER));
    }

    @Test
    void hotAirRises() {
        World world = new World(Material.AIR);
        NatureField field = new NatureField();
        field.heat(world, key(0, 0, 0), 1.0D);
        world.run(field, 5);
        assertTrue(field.temperature(key(0, 1, 0)) > field.temperature(key(0, -1, 0)));
    }

    @Test
    void heatIsNeverMadeFromNothing() {
        World world = pond();
        NatureField field = new NatureField();
        field.heat(world, key(0, -1, 0), 20.0D);
        double last = field.energy();
        for (int i = 0; i < 100; i++) {
            world.run(field, 1);
            double now = field.energy();
            assertTrue(now <= last + 1.0E-9, "step " + i + ": " + now + " > " + last);
            last = now;
        }
    }

    @Test
    void waterBoilsColdInAVacuumAndHotterUnderPressure() {
        assertTrue(NatureField.boilingPoint(-0.5D) < 0.0D, "in a vacuum it boils below the air around");
        assertEquals(NatureField.BOIL, NatureField.boilingPoint(0.0D), 1.0E-9);
        assertTrue(NatureField.boilingPoint(30.0D) > NatureField.BOIL);
    }

    @Test
    void waterThrownIntoTheAirFallsAndPools() {
        World world = new World(Material.AIR).fill(-10, -5, -10, 10, -1, 10, Material.STONE);
        NatureField field = new NatureField();
        field.spray(world, key(0, 6, 0), 1.0D, 12.0D);
        List<NatureField.Step> steps = world.run(field, 200);
        long pooled = steps.stream().flatMap(step -> step.changes().stream())
                .filter(change -> change.to() == Material.WATER).count();
        assertTrue(pooled >= 1, "it lands as water");
        assertTrue(steps.stream().flatMap(step -> step.changes().stream())
                .allMatch(change -> NatureField.y(change.key()) == 0), "on the ground, not in the air");
    }

    @Test
    void waterThrownIntoFreezingAirFallsAsSnow() {
        World world = new World(Material.AIR).fill(-10, -5, -10, 10, -1, 10, Material.STONE);
        NatureField field = new NatureField();
        field.heatAir(world, key(0, 3, 0), 6.0D, -20.0D);
        field.spray(world, key(0, 3, 0), 1.0D, 12.0D);
        assertTrue(changed(world.run(field, 100), Material.AIR, Material.SNOW));
    }

    /** Ice VII bursting, pressed air let out, or both, at the same point, as their spots do in the world. */
    private static List<NatureField.Step> storm(boolean ice, boolean air, double pressure) {
        World world = new World(Material.AIR).fill(-30, -5, -30, 30, -1, 30, Material.STONE);
        NatureField field = new NatureField();
        long center = key(0, 8, 0);
        if (ice) {
            field.spray(world, center, Pressure.burstReach(pressure) / 2.0D, pressure);
            field.stir(world, center, Pressure.burstReach(pressure), pressure);
        }
        if (air) {
            field.stir(world, center, AirPressure.reach(pressure), pressure);
            field.heatAir(world, center, AirPressure.reach(pressure), -0.5D * pressure);
        }
        return world.run(field, 60);
    }

    private static int strikes(List<NatureField.Step> steps) {
        return steps.stream().mapToInt(step -> step.discharges().size()).sum();
    }

    @Test
    void lightningTakesWaterColdAndStirredAirTogether() {
        assertEquals(0, strikes(storm(true, false, 40.0D)), "a warm spray alone makes no storm");
        assertEquals(0, strikes(storm(false, true, 40.0D)), "cold stirred air with no water neither");
        assertTrue(strikes(storm(true, true, 40.0D)) >= 1, "together, the droplets freeze and rub: lightning");
        assertEquals(0, strikes(storm(true, true, 10.0D)), "too little of them only makes a flurry");
    }

    @Test
    void aGreatHeatInTheAirBurstsOnceAndThenOnlySpreads() {
        World world = new World(Material.AIR).fill(-30, -5, -30, 30, -1, 30, Material.STONE);
        NatureField field = new NatureField();
        field.heat(world, key(0, 5, 0), 1.0E6D);
        List<NatureField.Step> steps = world.run(field, 100);
        assertEquals(1, bursts(steps), "the sudden heat expands at once; after that it only spreads");
    }

    @Test
    void burstsComeOneAtATime() {
        World world = new World(Material.AIR);
        NatureField field = new NatureField();
        for (int i = 0; i < 5; i++) {
            field.press(world, key(i * 20, 0, 0), 100.0D);
        }
        List<NatureField.Step> steps = world.run(field, 3);
        assertTrue(steps.stream().allMatch(step -> step.bursts().size() <= 1));
        assertEquals(1, bursts(steps), "the others wait (and in the open air, meanwhile, they spread out)");
    }

    @Test
    void theFieldNeverGrowsPastItsLimit() {
        World world = new World(Material.WATER);
        NatureField field = new NatureField(20);
        field.heat(world, key(0, 0, 0), 1000.0D);
        world.run(field, 20);
        assertTrue(field.size() <= 20);
    }
}

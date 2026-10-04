package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.magic.physics.Field;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlock;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlockEntity;
import com.elderlexicon.mod.spell.matter.MatterBlocks;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The world read as matter and matter put into it, in a running game (docs/plano-materia-e-forca.md, stage 3, and
 * docs/particulas-design.md, stage 9: formless matter in particles, doing what its particles do). Run with
 * {@code ./gradlew runGameTestServer}, or {@code /test runall} in a world.
 */
@GameTestHolder(ElderLexicon.MODID)
@PrefixGameTestTemplate(false)
public final class MatterGameTests {

    private static final String EMPTY = "empty";

    private MatterGameTests() {
    }

    private static Substance substance(String id) {
        return Materials.get().substance(id).orElseThrow();
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    private static void close(GameTestHelper helper, double expected, double actual, String what) {
        check(helper, Math.abs(expected - actual) < 1.0E-6D, what + ": expected " + expected + ", was " + actual);
    }

    @GameTest(template = EMPTY)
    public static void blocksReadAsWhatTheyAre(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.LAVA);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.ICE);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));

        Matter stone = WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))).orElseThrow();
        check(helper, stone.substance(Materials.get()).map(Substance::id).equals(Optional.of("stone")), "stone is stone");
        close(helper, 16.0D, stone.umu(), "a block of stone, as any block");
        Matter lava = WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1))).orElseThrow();
        check(helper, lava.state() == State.LIQUID
                && lava.substance(Materials.get()).map(Substance::id).equals(Optional.of("stone")), "lava is molten stone");
        Matter ice = WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1))).orElseThrow();
        check(helper, ice.state() == State.SOLID, "ice is solid water");
        close(helper, 16.0D, ice.umu(), "a block of ice holds a source of water");
        check(helper, WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 3))).isEmpty(),
                "a chest is made, no natural matter");
        check(helper, WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))).isEmpty(),
                "flowing water is no whole block");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemsReadAsTheMatterTheyHold(GameTestHelper helper) {
        close(helper, 9.0D * 455.0D / 256.0D, WorldMatter.read(new ItemStack(Items.RAW_IRON, 9)).orElseThrow().umu(),
                "nine raw irons are a block of iron, but for a particle");
        close(helper, 32.0D, WorldMatter.read(new ItemStack(Items.STONE, 2)).orElseThrow().umu(),
                "a block's item is the block");
        check(helper, WorldMatter.read(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "a sword is made");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void moltenStoneIsPlacedAsLava(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), at,
                Matter.of(substance("stone"), State.LIQUID, 40.0D));
        check(helper, placed.blocks().size() == 2, "forty UMU of molten stone are two blocks of lava, were "
                + placed.blocks().size());
        for (BlockPos pos : placed.blocks()) {
            check(helper, helper.getLevel().getBlockState(pos).is(Blocks.LAVA), "lava at " + pos);
        }
        close(helper, 32.0D, placed.placed(), "placed");
        close(helper, 8.0D, placed.leftover(), "left over, not lost");
        helper.succeed();
    }

    private static long count(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        long found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO),
                helper.absolutePos(new BlockPos(4, 4, 4)))) {
            if (helper.getLevel().getBlockState(pos).is(block)
                    && (block != Blocks.WATER || helper.getLevel().getFluidState(pos).isSource())) {
                found++;
            }
        }
        return found;
    }

    private static double formlessUmu(GameTestHelper helper) {
        double held = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO),
                helper.absolutePos(new BlockPos(4, 4, 4)))) {
            if (helper.getLevel().getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless) {
                held += formless.matter().map(Matter::umu).orElse(0.0D);
            }
        }
        return held;
    }

    private static void glassFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
    }

    /**
     * Mixing keeps the agitation each portion brought (L4, docs/particulas-design.md, stage 9): sixteen of molten earth
     * in sixty-four of water are quenched. Near no natural thing's code it is still matter, all of it, in five blocks
     * of sixteen UMU, solid now; and it holds as it is, but for the little water its warmth boils off as it cools.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void moltenEarthMixedIntoMuchWaterIsQuenched(GameTestHelper helper) {
        glassFloor(helper);
        MaterialTable table = Materials.get();
        WorldMatter.Mixed mixed = WorldMatter.mix(List.of(
                Matter.of(table.primordial(VitaElement.FIRMO), State.LIQUID, 16.0D),
                Matter.of(table.primordial(VitaElement.AQUA), State.LIQUID, 64.0D))).orElseThrow();
        check(helper, mixed.matter().state() == State.SOLID, "quenched, it is solid, was " + mixed.matter().state());
        check(helper, mixed.temperature() > 1.0D && mixed.temperature() < Field.glow(),
                "warmer than the world, far from glowing: " + mixed.temperature());
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)),
                mixed.matter(), mixed.temperature());
        check(helper, placed.blocks().size() == 5, "five blocks, were " + placed.blocks().size());
        close(helper, 80.0D, formlessUmu(helper), "held as it is");
        helper.runAtTickTime(150, () -> {
            double held = formlessUmu(helper);
            check(helper, held >= 79.0D && held <= 80.0D + 1.0E-6D, "all there but a little water, was " + held);
            helper.succeed();
        });
    }

    /**
     * Fire held in a liquid with no mass to hold it is agitation (docs/particulas-design.md, section 2): the water
     * with it boils, and the formless liquid is gone into the air, nothing of it lost on the way in.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void fireAndWaterInOneLiquidBoil(GameTestHelper helper) {
        glassFloor(helper);
        Matter hot = new Matter(Composition.of(Map.of(VitaElement.AQUA, 2.0D, VitaElement.IGNI, 1.0D)), State.LIQUID,
                48.0D);
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), hot);
        close(helper, 48.0D, placed.placed() + placed.leftover(), "nothing is lost");
        check(helper, formlessUmu(helper) > 0.0D, "it comes into the world as it is");
        helper.runAfterDelay(20, () -> helper.succeedWhen(() -> check(helper, formlessUmu(helper) <= 0.0D,
                "the water should have boiled with the fire, " + formlessUmu(helper) + " UMU left")));
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aHotMixtureBurnsWhatWadesIntoIt(GameTestHelper helper) {
        glassFloor(helper);
        Matter molten = new Matter(com.elderlexicon.mod.magic.matter.Composition.of(java.util.Map.of(
                VitaElement.FIRMO, 0.45D, VitaElement.IGNI, 0.45D, VitaElement.AURA, 0.1D)), State.LIQUID, 16.0D);
        check(helper, molten.unnamed(Materials.get()), "a mixture with no name");
        WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), molten);
        check(helper, helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 1, 2)))
                .getValue(FormlessMatterBlock.GLOW) > 0, "it glows with its heat");
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG,
                new BlockPos(2, 1, 2));
        float health = pig.getHealth();
        helper.succeedWhen(() -> check(helper, pig.isOnFire() || pig.getHealth() < health,
                "no one wrote magma, but it is hot: it burns"));
    }

    /**
     * A liquid short of the agitation that lets fire go cuts the air from what burns in it, and the fire goes out: a
     * quarter earth and the rest fire, molten (a liquid fuel, about 220 °C, short of its own ignition).
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aLiquidThatDoesNotBurnPutsOutWhatBurnsInIt(GameTestHelper helper) {
        glassFloor(helper);
        Matter fuel = new Matter(Composition.of(Map.of(VitaElement.FIRMO, 1.0D, VitaElement.IGNI, 3.0D)),
                State.LIQUID, 16.0D);
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        WorldMatter.place(helper.getLevel(), at, fuel);
        check(helper, helper.getLevel().getBlockState(at).is(MatterBlocks.FORMLESS_LIQUID.get()), "a formless liquid");
        double temperature = NatureWorld.temperature(helper.getLevel(), at);
        check(helper, temperature < Field.ignition(), "short of the ignition, was " + temperature);
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG,
                new BlockPos(2, 1, 2));
        pig.setSecondsOnFire(10);
        helper.succeedWhen(() -> check(helper, !pig.isOnFire(), "in it, the fire has no air and goes out"));
    }

    @GameTest(template = EMPTY)
    public static void moltenEarthIsFormlessMatterThatHolds(GameTestHelper helper) {
        Matter molten = Matter.of(Materials.get().primordial(VitaElement.FIRMO), State.LIQUID, 64.0D);
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)),
                molten);
        check(helper, placed.blocks().size() == 4, "64 UMU of earth fill four blocks, were " + placed.blocks().size());
        for (BlockPos pos : placed.blocks()) {
            check(helper, helper.getLevel().getBlockState(pos).is(MatterBlocks.FORMLESS_LIQUID.get()), "formless");
            // No fire is in its code, but molten earth is agitated past its melting: it glows, as lava does.
            check(helper, helper.getLevel().getBlockState(pos).getValue(FormlessMatterBlock.GLOW) > 0,
                    "molten, it glows with its agitation");
            check(helper, helper.getLevel().getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless
                            && formless.particles().equals(Optional.of(new Particles(Particles.BLOCK, 0L, 0L, 0L))),
                    "each block holds its 4096 particles of earth exactly");
        }
        Matter read = WorldMatter.read(helper.getLevel(), placed.blocks().get(0)).orElseThrow();
        check(helper, read.state() == State.LIQUID
                && read.substance(Materials.get()).map(Substance::id).equals(Optional.of("earth")), "read back as it is");
        close(helper, 16.0D, read.umu(), "sixteen UMU a block");
        // It can stay as it is, so it rests as the world does, as a lake of lava does: still molten a while later.
        helper.runAfterDelay(40, () -> {
            for (BlockPos pos : placed.blocks()) {
                check(helper, helper.getLevel().getBlockState(pos).is(MatterBlocks.FORMLESS_LIQUID.get()),
                        "still molten earth at " + pos);
            }
            helper.succeed();
        });
    }

    /**
     * Formless matter a world saved before the particles kept as a proportion and an amount loads as the whole particles
     * nearest them.
     */
    @GameTest(template = EMPTY)
    public static void formlessMatterSavedBeforeTheParticlesLoadsAsThem(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        WorldMatter.showFormless(helper.getLevel(), at, WorldMatter.formless(State.SOLID, 1.0D),
                new Particles(1L, 0L, 0L, 0L));
        FormlessMatterBlockEntity formless = (FormlessMatterBlockEntity) helper.getLevel().getBlockEntity(at);
        CompoundTag old = new CompoundTag();
        CompoundTag shares = new CompoundTag();
        shares.putDouble("firmo", 0.5D);
        shares.putDouble("aqua", 0.5D);
        old.put("shares", shares);
        old.putString("state", "solid");
        old.putDouble("umu", 16.0D);
        formless.load(old);
        check(helper, formless.particles().equals(Optional.of(new Particles(2048L, 2048L, 0L, 0L))),
                "half earth and half water, sixteen UMU: 2048 of each, were " + formless.particles());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void waterPouredIntoLavaMixesWithIt(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.LAVA);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.LAVA);
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        Matter water = Matter.of(Materials.get().primordial(VitaElement.AQUA), State.LIQUID, 32.0D);
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), at, water);
        // Two blocks of molten stone and two of water are half water: near no natural thing's code, formless now, and
        // the water quenches the stone: solid, though warm, and its water boils off as it cools.
        Matter mixture = placed.mixed().orElseThrow();
        check(helper, mixture.unnamed(Materials.get()), "a mixture with no name");
        check(helper, mixture.state() == State.SOLID, "the water quenched it, was " + mixture.state());
        close(helper, 64.0D, mixture.umu(), "all of the lava and the water");
        check(helper, count(helper, Blocks.LAVA) == 0, "the lava was taken into it");
        close(helper, 64.0D, formlessUmu(helper), "held as formless matter");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBlockTakenLeavesItsPlaceEmpty(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, Blocks.SAND);
        Matter sand = WorldMatter.take(helper.getLevel(), helper.absolutePos(rel)).orElseThrow();
        check(helper, sand.substance(Materials.get()).map(Substance::id).equals(Optional.of("sand")), "sand is sand");
        helper.assertBlockPresent(Blocks.AIR, rel);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aGasGoesIntoTheAirWhole(GameTestHelper helper) {
        Matter vapour = Matter.of(Materials.get().primordial(VitaElement.AQUA), State.GAS, 2.5D);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), at, vapour);
        check(helper, placed.blocks().isEmpty(), "a gas lays no blocks");
        close(helper, 2.5D, placed.placed(), "it disperses, all of it");
        // Into the air there, where the drives take it: its 640 particles of water fly in that block.
        long flying = NatureWorld.cell(helper.getLevel(), at).map(cell -> cell.airborne().aqua()).orElse(0L);
        check(helper, flying == 640L, "640 particles of water in the air there, were " + flying);
        helper.succeed();
    }
}

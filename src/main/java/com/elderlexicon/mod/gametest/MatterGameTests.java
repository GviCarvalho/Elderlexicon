package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlock;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlockEntity;
import com.elderlexicon.mod.spell.matter.MatterBlocks;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * The world read as matter and matter put into it, in a running game (docs/plano-materia-e-forca.md, stage 3). Run with
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
        close(helper, 1.5D, stone.umu(), "a block of stone");
        Matter lava = WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1))).orElseThrow();
        check(helper, lava.state() == State.LIQUID
                && lava.substance(Materials.get()).map(Substance::id).equals(Optional.of("stone")), "lava is molten stone");
        Matter ice = WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1))).orElseThrow();
        check(helper, ice.state() == State.SOLID, "ice is solid water");
        close(helper, 3.0D, ice.umu(), "a block of ice holds a source of water");
        check(helper, WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 3))).isEmpty(),
                "a chest is made, no natural matter");
        check(helper, WorldMatter.read(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))).isEmpty(),
                "flowing water is no whole block");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemsReadAsTheMatterTheyHold(GameTestHelper helper) {
        close(helper, 5.0D, WorldMatter.read(new ItemStack(Items.RAW_IRON, 9)).orElseThrow().umu(),
                "nine raw irons are a block of iron");
        close(helper, 3.0D, WorldMatter.read(new ItemStack(Items.STONE, 2)).orElseThrow().umu(),
                "a block's item is the block");
        check(helper, WorldMatter.read(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "a sword is made");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void moltenStoneIsPlacedAsLava(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), at,
                Matter.of(substance("stone"), State.LIQUID, 4.0D));
        check(helper, placed.blocks().size() == 2, "four UMU of molten stone are two blocks of lava, were "
                + placed.blocks().size());
        for (BlockPos pos : placed.blocks()) {
            check(helper, helper.getLevel().getBlockState(pos).is(Blocks.LAVA), "lava at " + pos);
        }
        close(helper, 3.0D, placed.placed(), "placed");
        close(helper, 1.0D, placed.leftover(), "left over, not lost");
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

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void anAmalgamHoldsAWhileAndThenFallsApartIntoItsParts(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        MaterialTable table = Materials.get();
        Matter amalgam = MatterLaws.mix(List.of(
                Matter.of(table.primordial(VitaElement.FIRMO), State.LIQUID, 1.0D),
                Matter.of(table.primordial(VitaElement.AQUA), State.LIQUID, 9.0D))).orElseThrow();
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)),
                amalgam);
        // L5: it matches no recipe, so it is formless matter that holds for a while: all of it, in five blocks (two of
        // molten earth and three of water fill five together).
        check(helper, placed.blocks().size() == 5, "five blocks of amalgam, were " + placed.blocks().size());
        close(helper, 10.0D, placed.placed(), "all of it placed");
        close(helper, 10.0D, formlessUmu(helper), "held as it is");
        check(helper, placed.blocks().stream().allMatch(pos ->
                helper.getLevel().getBlockState(pos).getValue(FormlessMatterBlock.UNSTABLE)), "and unstable");
        // Halfway through its time it still holds, all of it.
        int halfway = WorldMatter.amalgamTicks() / 2;
        helper.runAtTickTime(halfway, () -> close(helper, 10.0D, formlessUmu(helper), "halfway, it should still hold"));
        // When its time is up it falls apart, all at once: into two blocks of soil and three sources of water.
        helper.succeedWhen(() -> {
            check(helper, helper.getTick() >= WorldMatter.amalgamTicks(), "its time is not up yet");
            check(helper, formlessUmu(helper) < 1.0E-6D, "the amalgam still holds");
            check(helper, count(helper, Blocks.DIRT) == 2 && count(helper, Blocks.WATER) == 3,
                    "two blocks of soil and three of water, were " + count(helper, Blocks.DIRT) + " and "
                            + count(helper, Blocks.WATER));
        });
    }

    @GameTest(template = EMPTY)
    public static void moltenEarthIsFormlessMatterThatHolds(GameTestHelper helper) {
        Matter molten = Matter.of(Materials.get().primordial(VitaElement.FIRMO), State.LIQUID, 2.0D);
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)),
                molten);
        check(helper, placed.blocks().size() == 4, "two UMU of earth fill four blocks, were " + placed.blocks().size());
        for (BlockPos pos : placed.blocks()) {
            check(helper, helper.getLevel().getBlockState(pos).is(MatterBlocks.FORMLESS_LIQUID.get()), "formless");
            check(helper, !helper.getLevel().getBlockState(pos).getValue(FormlessMatterBlock.UNSTABLE),
                    "molten earth is earth: it holds together");
        }
        Matter read = WorldMatter.read(helper.getLevel(), placed.blocks().get(0)).orElseThrow();
        check(helper, read.state() == State.LIQUID
                && read.substance(Materials.get()).map(Substance::id).equals(Optional.of("earth")), "read back as it is");
        close(helper, 0.5D, read.umu(), "half a UMU a block");
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
        Matter water = Matter.of(Materials.get().primordial(VitaElement.AQUA), State.LIQUID, 3.0D);
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), at, water);
        // Three UMU of stone and three of water are half water: no recipe is that, so the pool is an amalgam now.
        Matter mixture = placed.mixed().orElseThrow();
        check(helper, mixture.amalgam(Materials.get()), "an amalgam");
        close(helper, 6.0D, mixture.umu(), "all of the lava and the water");
        check(helper, count(helper, Blocks.LAVA) == 0, "the lava was taken into it");
        close(helper, 6.0D, formlessUmu(helper), "held as formless matter");
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
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), vapour);
        check(helper, placed.blocks().isEmpty(), "a gas lays no blocks");
        close(helper, 2.5D, placed.placed(), "it disperses, all of it");
        helper.succeed();
    }
}

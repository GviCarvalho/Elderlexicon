package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
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

    @GameTest(template = EMPTY)
    public static void anAmalgamSettlesAsItsParts(GameTestHelper helper) {
        MaterialTable table = Materials.get();
        Matter amalgam = MatterLaws.mix(List.of(
                Matter.of(table.primordial(VitaElement.FIRMO), State.LIQUID, 1.0D),
                Matter.of(table.primordial(VitaElement.AQUA), State.LIQUID, 9.0D))).orElseThrow();
        WorldMatter.Placed placed = WorldMatter.place(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)),
                amalgam);
        long dirt = placed.blocks().stream().filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.DIRT)).count();
        long water = placed.blocks().stream().filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.WATER)).count();
        check(helper, dirt == 2 && water == 3, "two blocks of soil and three of water, were " + dirt + " and " + water);
        close(helper, 10.0D, placed.placed() + placed.leftover(), "nothing is lost");
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

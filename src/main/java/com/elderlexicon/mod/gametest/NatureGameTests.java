package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.matter.MatterBlocks;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The drives in a running world (docs/particulas-design.md, stage 9): what fire let into the air does, as the
 * laboratory found it, in blocks of the game. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ElderLexicon.MODID)
@PrefixGameTestTemplate(false)
public final class NatureGameTests {

    private static final String EMPTY = "empty";

    private NatureGameTests() {
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    /** A floor of glass under the test: made, so the drives take it for a wall. */
    private static void glassFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
    }

    private static boolean flame(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(MatterBlocks.FLAME.get());
    }

    /** Whether there is a flame in the column above {@code pos}, from it up. */
    private static boolean flameAbove(GameTestHelper helper, BlockPos pos) {
        for (int up = 0; up < 4; up++) {
            if (flame(helper, pos.above(up))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A flame is air agitated until it glows: a flame held for a moment shows as one, and once nothing feeds it, with
     * nothing to burn, it cools into the air around and goes out.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFlameIsAirThatGlowsAndGoesOutWithNothingToBurn(GameTestHelper helper) {
        glassFloor(helper);
        BlockPos spot = new BlockPos(2, 1, 2);
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(spot);
        double[] left = {-1.0D};
        NatureWorld.keepFlame(level, () -> at, NatureWorld.flame(), NatureWorld.FLAME_TICKS, rest -> left[0] = rest);
        boolean[] seen = {false};
        helper.onEachTick(() -> seen[0] |= flame(helper, spot));
        helper.runAfterDelay(3, () -> check(helper, seen[0], "the air should glow as a flame"));
        helper.runAfterDelay(NatureWorld.FLAME_TICKS + 2, () -> helper.succeedWhen(() -> {
            check(helper, left[0] >= 0.0D, "the flame should have been let go");
            check(helper, !flameAbove(helper, spot), "with nothing feeding it or to burn, the flame goes out");
            check(helper, helper.getBlockState(spot).isAir(), "and leaves air, was " + helper.getBlockState(spot));
        }));
    }

    /**
     * A flame held beside a log dries it and lights it, and the fire goes on alone: the log burns away, its fire let
     * into the air around it as flames.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1600)
    public static void aFlameHeldBesideALogLightsItAndItBurnsAway(GameTestHelper helper) {
        glassFloor(helper);
        BlockPos log = new BlockPos(2, 1, 2);
        BlockPos beside = new BlockPos(3, 1, 2);
        helper.setBlock(log, Blocks.OAK_LOG);
        ServerLevel level = helper.getLevel();
        for (int tick = 0; tick < 80; tick++) {
            helper.runAfterDelay(tick, () -> NatureWorld.fire(level, helper.absolutePos(beside), 0.25D));
        }
        boolean[] flames = {false};
        helper.onEachTick(() -> {
            for (BlockPos near : new BlockPos[]{log.above(), log.west(), log.north(), log.south()}) {
                flames[0] |= flame(helper, near);
            }
        });
        helper.runAfterDelay(100, () -> helper.succeedWhen(() -> {
            BlockState now = helper.getBlockState(log);
            check(helper, !now.is(Blocks.OAK_LOG), "the log should have burnt away, is still " + now);
            check(helper, flames[0], "flames should have risen around it as it burnt");
        }));
    }

    /** Fire enough to melt stone, let into it: it is lava, and as the heat leaves it, stone again. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void stoneHeatedPastItsMeltingIsLavaAndSetsAgain(GameTestHelper helper) {
        glassFloor(helper);
        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        // Short of its boiling: what it loses in the first step (its water boiling off, carrying heat with it) still
        // leaves it past its melting.
        check(helper, NatureWorld.heat(level, helper.absolutePos(stone), 360L), "the heat should go into the stone");
        boolean[] molten = {false};
        helper.onEachTick(() -> molten[0] |= helper.getBlockState(stone).is(Blocks.LAVA));
        helper.runAfterDelay(5, () -> check(helper, molten[0], "past its melting, stone is lava"));
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> check(helper,
                helper.getBlockState(stone).is(Blocks.STONE), "cooled, it sets into stone, is "
                        + helper.getBlockState(stone))));
    }

    /** Water heated past its boiling leaves as vapour, and the basin is left dry. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void waterHeatedPastItsBoilingLeavesAsVapour(GameTestHelper helper) {
        glassFloor(helper);
        BlockPos water = new BlockPos(2, 1, 2);
        for (BlockPos wall : new BlockPos[]{water.east(), water.west(), water.north(), water.south()}) {
            helper.setBlock(wall, Blocks.GLASS);
        }
        helper.setBlock(water, Blocks.WATER);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(1, () -> check(helper, NatureWorld.heat(level, helper.absolutePos(water), 1200L),
                "the heat should go into the water"));
        helper.runAfterDelay(2, () -> helper.succeedWhen(() -> check(helper,
                !helper.getBlockState(water).is(Blocks.WATER), "the water should have boiled away, is "
                        + helper.getBlockState(water))));
    }

    /** A block of pure fire let into the air is plasma, past the face of the sun: lightning. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aBlockOfPureFireInTheAirIsLightning(GameTestHelper helper) {
        glassFloor(helper);
        BlockPos spot = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        check(helper, NatureWorld.fire(level, helper.absolutePos(spot), 16.0D), "the fire should go into the air");
        AABB around = new AABB(helper.absolutePos(spot)).inflate(3.0D);
        helper.succeedWhen(() -> check(helper, !level.getEntitiesOfClass(LightningBolt.class, around).isEmpty(),
                "plasma should strike as lightning"));
    }
}

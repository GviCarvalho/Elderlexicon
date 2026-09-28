package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Spells cast by a player in a running game, to see the grammar's rules act in the world
 * (docs/plano-materia-e-forca.md, stage 4). Run with {@code ./gradlew runGameTestServer}, or {@code /test runall}.
 */
@GameTestHolder(ElderLexicon.MODID)
@PrefixGameTestTemplate(false)
public final class SpellGameTests {

    private static final String EMPTY = "empty";

    private SpellGameTests() {
    }

    /**
     * A floor under the room and a wall of stone at its far end (z = 4); a mage standing at the near end (z = 0),
     * looking at the wall.
     */
    private static ServerPlayer mageBeforeAWall(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.0D, 0.5D));
        // Yaw 0 looks south (+z), toward the wall, whichever way the test is rotated in the world.
        mage.moveTo(at.x, at.y, at.z, facingWall(helper), 0.0F);
        return mage;
    }

    /**
     * A mage logged into the test's level. {@link GameTestHelper#makeMockServerPlayerInLevel} gives its player a
     * connection with no channel, and the mod talks to its players (their Vita, their marks): this one has a channel
     * that takes what it is sent and goes nowhere.
     */
    private static ServerPlayer mage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer mage = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mage"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, mage);
        return mage;
    }

    /** The yaw that looks from the near end of the room to the wall, in world terms. */
    private static float facingWall(GameTestHelper helper) {
        Vec3 near = helper.absoluteVec(new Vec3(2.5D, 1.0D, 0.5D));
        Vec3 far = helper.absoluteVec(new Vec3(2.5D, 1.0D, 3.5D));
        Vec3 way = far.subtract(near);
        return (float) Math.toDegrees(Math.atan2(-way.x, way.z));
    }

    private static SpellCastingService.Result cast(ServerPlayer mage, String spell) {
        try {
            return new SpellCastingService().cast(mage, List.of(spell.split(" ")));
        } catch (RuntimeException exception) {
            StringBuilder trace = new StringBuilder(exception.toString());
            for (StackTraceElement frame : exception.getStackTrace()) {
                trace.append("\n  at ").append(frame);
            }
            throw new IllegalStateException(trace.toString(), exception);
        }
    }

    private static boolean anyIn(GameTestHelper helper, Block block, int fromZ, int toZ) {
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y < 5; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String where(GameTestHelper helper, Block block) {
        StringBuilder found = new StringBuilder();
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        found.append(' ').append(x).append(',').append(y).append(',').append(z);
                    }
                }
            }
        }
        return found.toString();
    }

    @GameTest(template = EMPTY)
    public static void earthSummonedAlonePilesWhereTheMageAims(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        SpellCastingService.Result result = cast(mage, "firmo vocant");
        if (result.failed()) {
            helper.fail("firmo vocant failed: " + result.message().getString());
        }
        helper.succeedWhen(() -> {
            if (!anyIn(helper, Blocks.DIRT, 3, 3)) {
                helper.fail("no earth before the wall");
            }
        });
    }

    @GameTest(template = EMPTY)
    public static void earthSummonedAlongTheWayStaysWhereItWasSummoned(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        SpellCastingService.Result result = cast(mage, "firmo 2 ubis vocant");
        if (result.failed()) {
            helper.fail("firmo 2 ubis vocant failed: " + result.message().getString());
        }
        helper.succeedWhen(() -> {
            if (!anyIn(helper, Blocks.DIRT, 2, 2)) {
                helper.fail("no earth two blocks ahead");
            }
        });
    }

    @GameTest(template = EMPTY)
    public static void earthSummonedAndPushedFliesFromWhereItWasMadeToTheAim(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        // R2: the iactare acts on what the vocant made. The earth is made two blocks ahead and pushed toward the
        // aim, the wall: it lands against the wall, not where it was made.
        SpellCastingService.Result result = cast(mage, "firmo 2 ubis vocant iactare");
        if (result.failed()) {
            helper.fail("firmo 2 ubis vocant iactare failed: " + result.message().getString());
        }
        helper.succeedWhen(() -> {
            if (anyIn(helper, Blocks.DIRT, 0, 2)) {
                helper.fail("the earth stayed where it was made: " + where(helper, Blocks.DIRT));
            }
            if (!anyIn(helper, Blocks.DIRT, 3, 3)) {
                helper.fail("no earth against the wall yet");
            }
        });
    }

    @GameTest(template = EMPTY)
    public static void solidMatterTakenFromTheWorldIsPutWhereTheMageAims(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        // The floor is the solid matter nearest the mage: it is taken, stone as stone, and put before the wall.
        SpellCastingService.Result result = cast(mage, "firmo tenet vocant");
        if (result.failed()) {
            helper.fail("firmo tenet vocant failed: " + result.message().getString());
        }
        int holes = 0;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                holes += helper.getBlockState(new BlockPos(x, 0, z)).isAir() ? 1 : 0;
            }
        }
        if (holes == 0) {
            helper.fail("no floor was taken");
        }
        if (!anyIn(helper, Blocks.STONE, 2, 3)) {
            helper.fail("no stone was put before the wall");
        }
        helper.succeed();
    }

    private static boolean anyInFloor(GameTestHelper helper, Block block) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (helper.getBlockState(new BlockPos(x, 0, z)).is(block)) {
                    return true;
                }
            }
        }
        return false;
    }

    @GameTest(template = EMPTY)
    public static void stoneTakenToTheLiquidRungIsLava(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        // Vertere on matter of the world climbs the ladder and keeps what it is: solid stone, one rung up, is liquid
        // stone, which the game shows as lava.
        SpellCastingService.Result result = cast(mage, "firmo tenet vertere aqua");
        if (result.failed()) {
            helper.fail("firmo tenet vertere aqua failed: " + result.message().getString());
        }
        if (!anyInFloor(helper, Blocks.LAVA)) {
            helper.fail("the floor did not melt");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void waterTakenToTheSolidRungIsIce(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 0, 2), Blocks.WATER);
        }
        SpellCastingService.Result result = cast(mage, "aqua tenet vertere firmo");
        if (result.failed()) {
            helper.fail("aqua tenet vertere firmo failed: " + result.message().getString());
        }
        if (!anyInFloor(helper, Blocks.ICE)) {
            helper.fail("the water did not freeze");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void impediuntTurnedAroundDrawsThingsIn(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        Vec3 start = helper.absoluteVec(new Vec3(0.5D, 1.0D, 3.5D));
        ItemEntity stone = new ItemEntity(helper.getLevel(), start.x, start.y, start.z, new ItemStack(Items.COBBLESTONE));
        stone.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(stone);
        double before = stone.position().distanceTo(mage.position());
        // R4 on impediunt: the force over the area points in. Air carries what is loose in it: a vortex.
        SpellCastingService.Result result = cast(mage, "aura quantum -30 chronos 3 impediunt");
        if (result.failed()) {
            helper.fail("aura quantum -30 chronos 3 impediunt failed: " + result.message().getString());
        }
        helper.succeedWhen(() -> {
            double now = stone.position().distanceTo(mage.position());
            if (now > before - 1.5D) {
                helper.fail("the stone was not drawn in: " + before + " -> " + now);
            }
        });
    }

    /** The absolute coordinates of a block of the test, as a spell writes them before a place filter. */
    private static String at(GameTestHelper helper, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static int countIn(GameTestHelper helper, Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO),
                helper.absolutePos(new BlockPos(4, 4, 4)))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(block) && (state.getFluidState().isEmpty() || state.getFluidState().isSource())) {
                found++;
            }
        }
        return found;
    }

    /** The formless matter in the test and just around it, where what is put at its edge spills. */
    private static List<Matter> formlessIn(GameTestHelper helper) {
        List<Matter> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-2, 0, -2)),
                helper.absolutePos(new BlockPos(6, 6, 6)))) {
            if (helper.getLevel().getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless) {
                formless.matter().ifPresent(found::add);
            }
        }
        return found;
    }

    private static void castOrFail(GameTestHelper helper, ServerPlayer mage, String spell) {
        SpellCastingService.Result result = cast(mage, spell);
        if (result.failed()) {
            helper.fail(spell + " failed: " + result.message().getString());
        }
        if (!result.warnings().isEmpty()) {
            helper.fail(spell + " was misread: " + result.warnings().get(0).getString());
        }
    }

    /**
     * The chain the plan ends on (docs/plano-materia-e-forca.md, stage 5): stone made by mixing. Earth is melted, and
     * into the molten earth go water, air and fire, a little of each, until the proportion is stone's (eight parts earth,
     * half a part water, half a part air, one part fire): the mixture is molten stone, lava, which cools into stone.
     */
    @GameTest(template = EMPTY)
    public static void stoneIsMadeByMixingThePrimordials(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS); // glass is made: no matter to melt
            }
        }
        for (int x = 2; x <= 3; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 4; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);

        // Sixteen UMU of earth (thirty-two blocks of soil) melted where they are: molten earth, formless matter.
        castOrFail(helper, mage, "firmo quantum 16 " + at(helper, 2, 3, 2) + " tenet vertere aqua");
        List<Matter> molten = formlessIn(helper);
        check(helper, molten.size() == 32 && countIn(helper, Blocks.DIRT) == 0,
                "the soil did not melt: " + molten.size() + " formless, " + countIn(helper, Blocks.DIRT) + " dirt left");

        String pool = at(helper, 2, 1, 2);
        // One UMU of water: sixteen of earth and one of water are deepslate, molten.
        castOrFail(helper, mage, "aqua quantum 1 " + pool + " ubis vocant");
        check(helper, formlessIn(helper).stream().allMatch(matter -> named(matter, "deepslate")),
                "earth and a little water should be molten deepslate");
        // One of air: that matches nothing, an amalgam.
        castOrFail(helper, mage, "aura quantum 1 " + pool + " ubis vocant");
        check(helper, !formlessIn(helper).isEmpty()
                && formlessIn(helper).stream().allMatch(matter -> matter.amalgam(Materials.get())),
                "with air too it should be an amalgam");
        // Two of fire: now it is stone's proportion. Molten stone is lava.
        castOrFail(helper, mage, "igni quantum 2 " + pool + " ubis vocant");
        check(helper, formlessIn(helper).isEmpty(), "no formless matter should be left: " + formlessIn(helper));
        int lava = countIn(helper, Blocks.LAVA);
        check(helper, lava == 13, "twenty UMU of molten stone are thirteen sources of lava, were " + lava);

        // Cooled, it is stone.
        castOrFail(helper, mage, "aqua quantum 19 " + pool + " tenet vertere firmo");
        int stone = countIn(helper, Blocks.STONE);
        check(helper, stone == 13, "the lava should cool into thirteen blocks of stone, were " + stone);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void fluidsBroughtTogetherMix(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.LAVA);
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        // The liquids nearest (2, 1, 2), four UMU of them, taken and put at (2, 1, 4): a source of water and one of
        // lava, brought to one place, mix (L4). Half stone and half water is no recipe: an amalgam.
        castOrFail(helper, mage, "aqua quantum 4 " + at(helper, 2, 1, 2) + " tenet " + at(helper, 2, 1, 4) + " ubis vocant");
        check(helper, countIn(helper, Blocks.WATER) == 0 && countIn(helper, Blocks.LAVA) == 0,
                "the water and the lava should have been taken");
        List<Matter> brought = formlessIn(helper);
        check(helper, !brought.isEmpty() && brought.stream().allMatch(matter -> matter.amalgam(Materials.get())),
                "they should be one amalgam now: " + brought);
        double held = brought.stream().mapToDouble(Matter::umu).sum();
        check(helper, Math.abs(held - 4.5D) < 1.0E-6D, "all of both, 4.5 UMU, were " + held);
        helper.succeed();
    }

    private static boolean named(Matter matter, String substance) {
        return matter.substance(Materials.get()).map(Substance::id).filter(substance::equals).isPresent();
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    @GameTest(template = EMPTY)
    public static void aNegativeQuantityOnAVerbThatCannotTurnIsRefused(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        SpellCastingService.Result result = cast(mage, "igni quantum -10 vertere aqua");
        boolean warned = result.warnings().stream().map(warning -> warning.getString())
                .anyMatch(text -> text.contains("Quantidade negativa"));
        if (!warned) {
            helper.fail("the spirit should refuse the sign: " + Arrays.toString(result.warnings().toArray()));
        }
        helper.succeed();
    }
}

package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.item.ArcaneCompassItem;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.function.Dials;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The arcane compass (docs/bussola-arcana-design.md): a measure shown on a nugget, and nothing taken from the body; a
 * nugget pushed toward a mark points at it; an iron one is drawn by a lodestone, a gold one is not; a nugget pulled by
 * an aura bond strains as fast as what pulls it. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ElderLexicon.MODID)
@PrefixGameTestTemplate(false)
public final class DialGameTests {

    private static final String EMPTY = "empty";

    private DialGameTests() {
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    /** A mage with a dial in hand, its nuggets bearing {@code marks}, and mana to spare. */
    private static ServerPlayer mageWithDial(GameTestHelper helper, ArcaneCompassItem.Metal metal, String... marks) {
        ServerLevel level = helper.getLevel();
        ServerPlayer mage = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-dial"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, mage);
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D));
        mage.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        mage.giveExperienceLevels(30);
        ItemStack dial = new ItemStack(metal == ArcaneCompassItem.Metal.GOLD
                ? ElderLexicon.GOLD_ARCANE_COMPASS.get() : ElderLexicon.IRON_ARCANE_COMPASS.get());
        for (String mark : marks) {
            ArcaneCompassItem.addNugget(dial, mark);
        }
        mage.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dial);
        return mage;
    }

    private static void leave(ServerPlayer mage) {
        mage.level().getServer().getPlayerList().remove(mage);
    }

    private static SpellCastingService.Result cast(ServerPlayer mage, String spell) {
        return new SpellCastingService().cast(mage, List.of(spell.split(" ")));
    }

    private static ArcaneCompassItem.Reading reading(ServerPlayer mage, int slot) {
        return ArcaneCompassItem.reading(mage.getMainHandItem(), slot, mage.level().getGameTime());
    }

    /**
     * A nugget renamed at the anvil bears the mark of its name, and set in a dial at the anvil it is one more nugget;
     * the dial keeps no mark of its own name.
     */
    @GameTest(template = EMPTY)
    public static void aNuggetRenamedAtTheAnvilIsSetInTheDial(GameTestHelper helper) {
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.IRON, "primeira");
        ItemStack dial = mage.getMainHandItem().copy();

        net.minecraft.world.inventory.AnvilMenu naming = new net.minecraft.world.inventory.AnvilMenu(1, mage.getInventory());
        naming.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 4));
        naming.setItemName("Agulha");
        check(helper, !naming.getSlot(2).getItem().isEmpty(), "the anvil does not rename the nugget");
        // Taken with shift-click, as a player does: the stack is copied into the inventory after the anvil's event.
        naming.quickMoveStack(mage, 2);
        ItemStack renamed = ItemStack.EMPTY;
        for (ItemStack carried : mage.getInventory().items) {
            if (carried.is(net.minecraft.world.item.Items.IRON_NUGGET) && carried.hasCustomHoverName()) {
                renamed = carried;
            }
        }
        check(helper, !renamed.isEmpty(), "the renamed nugget did not come into the inventory");
        check(helper, MarkHelper.markForItem(renamed).filter("agulha"::equals).isPresent(),
                "the renamed nugget bears no mark: " + MarkHelper.markForItem(renamed));

        net.minecraft.world.inventory.AnvilMenu setting = new net.minecraft.world.inventory.AnvilMenu(2, mage.getInventory());
        setting.getSlot(0).set(dial);
        setting.getSlot(1).set(renamed.copy());
        setting.createResult();
        ItemStack set = setting.getSlot(2).getItem();
        check(helper, ArcaneCompassItem.nuggets(set).equals(List.of("primeira", "agulha")),
                "the anvil did not set the nugget: " + ArcaneCompassItem.nuggets(set));
        check(helper, MarkHelper.markForItem(set).isEmpty(), "the dial took a mark: " + MarkHelper.markForItem(set));

        // A gold nugget goes into an iron dial too, and stays gold.
        setting.getSlot(1).set(goldNugget("agulha_de_ouro"));
        setting.createResult();
        ItemStack mixed = setting.getSlot(2).getItem();
        check(helper, ArcaneCompassItem.nuggets(mixed).contains("agulha_de_ouro")
                && ArcaneCompassItem.nuggetMetal(mixed, ArcaneCompassItem.nuggets(mixed).indexOf("agulha_de_ouro"))
                == ArcaneCompassItem.Metal.GOLD, "an iron dial did not take a gold nugget: " + mixed);
        leave(mage);
        helper.succeed();
    }

    /** At the crafting table: the diamond with a marked middle makes a dial, and a dial with a marked nugget takes it. */
    @GameTest(template = EMPTY)
    public static void theCraftingTableMakesADialAndSetsNuggets(GameTestHelper helper) {
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.GOLD);
        com.elderlexicon.mod.item.ArcaneCompassRecipe recipe = new com.elderlexicon.mod.item.ArcaneCompassRecipe(
                new net.minecraft.resources.ResourceLocation(ElderLexicon.MODID, "arcane_compass"),
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC);

        net.minecraft.world.inventory.CraftingContainer grid = new net.minecraft.world.inventory.TransientCraftingContainer(
                new net.minecraft.world.inventory.CraftingMenu(3, mage.getInventory()), 3, 3);
        for (int slot : new int[] {1, 3, 5, 7}) {
            grid.setItem(slot, new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET));
        }
        grid.setItem(4, goldNugget("centro"));
        check(helper, recipe.matches(grid, helper.getLevel()), "the diamond of gold nuggets makes nothing");
        ItemStack made = recipe.assemble(grid, helper.getLevel().registryAccess());
        check(helper, made.is(ElderLexicon.GOLD_ARCANE_COMPASS.get())
                && ArcaneCompassItem.nuggets(made).equals(List.of("centro")), "the dial made is not right: " + made);

        net.minecraft.world.inventory.CraftingContainer pair = new net.minecraft.world.inventory.TransientCraftingContainer(
                new net.minecraft.world.inventory.CraftingMenu(4, mage.getInventory()), 3, 3);
        pair.setItem(0, made);
        pair.setItem(8, goldNugget("borda"));
        check(helper, recipe.matches(pair, helper.getLevel()), "a dial and a marked nugget make nothing");
        ItemStack set = recipe.assemble(pair, helper.getLevel().registryAccess());
        check(helper, ArcaneCompassItem.nuggets(set).equals(List.of("centro", "borda")),
                "the table did not set the nugget: " + ArcaneCompassItem.nuggets(set));

        // The case is the diamond's metal; the middle may be of the other.
        for (int slot : new int[] {1, 3, 5, 7}) {
            grid.setItem(slot, new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET));
        }
        check(helper, recipe.matches(grid, helper.getLevel()), "an iron diamond with a gold middle makes nothing");
        ItemStack mixed = recipe.assemble(grid, helper.getLevel().registryAccess());
        check(helper, mixed.is(ElderLexicon.IRON_ARCANE_COMPASS.get())
                && ArcaneCompassItem.nuggetMetal(mixed, 0) == ArcaneCompassItem.Metal.GOLD,
                "the mixed dial is not an iron case with a gold nugget: " + mixed);

        // An unmarked nugget is no anchor.
        pair.setItem(8, new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET));
        check(helper, !recipe.matches(pair, helper.getLevel()), "a dial took a nugget with no mark");
        leave(mage);
        helper.succeed();
    }

    private static ItemStack goldNugget(String mark) {
        ItemStack nugget = new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET);
        MarkHelper.applyMark(nugget, mark);
        return nugget;
    }

    /** The image of how much fire the mage holds lands on the nugget, and the fire stays in the body. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aMeasureLandsOnTheNuggetAndTakesNothing(GameTestHelper helper) {
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.IRON, "fogo", "agua");
        double fire = VitaSystem.holds(mage, VitaElement.IGNI);
        SpellCastingService.Result fireResult = cast(mage, "igni quantum ego surgit fogo ubis vocant");
        SpellCastingService.Result waterResult = cast(mage, "aqua quantum ego surgit agua ubis vocant");
        helper.runAtTickTime(2, () -> {
            check(helper, fireResult.success(), "the measure of fire was not cast: " + fireResult.message().getString());
            check(helper, waterResult.success(), "the measure of water was not cast: " + waterResult.message().getString());
            ArcaneCompassItem.Reading shown = reading(mage, 0);
            check(helper, shown != null && shown.kind() == ArcaneCompassItem.Reading.Kind.IMAGE
                    && "igni".equals(shown.element()), "the nugget shows no image of fire: " + shown);
            check(helper, Math.abs(shown.amount() - fire) < 1.0E-6D,
                    "the image shows " + shown.amount() + " of fire, the body holds " + fire);
            ArcaneCompassItem.Reading water = reading(mage, 1);
            check(helper, water != null && "aqua".equals(water.element())
                    && Math.abs(water.amount() - VitaSystem.holds(mage, VitaElement.AQUA)) < 1.0E-6D,
                    "the second nugget does not show the water: " + water);
            check(helper, Math.abs(VitaSystem.holds(mage, VitaElement.IGNI) - fire) < 1.0E-6D,
                    "the measure took fire from the body: " + VitaSystem.holds(mage, VitaElement.IGNI));
            leave(mage);
            helper.succeed();
        });
    }

    /** A nugget pushed toward a marked thing points at it, as a needle. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aPushedNuggetPointsAtTheMark(GameTestHelper helper) {
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.GOLD, "agulha");
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5D, 1.0D, 2.5D).add(4.0D, 0.0D, 0.0D));
        pig.setNoAi(true);
        MarkHelper.applyMark(pig, "porco");
        helper.runAtTickTime(2, () -> {
            SpellCastingService.Result result = cast(mage, "agulha porco ubis iactare");
            check(helper, result.success(), "the push was not cast: " + result.message().getString());
            ArcaneCompassItem.Reading shown = reading(mage, 0);
            check(helper, shown != null && shown.kind() == ArcaneCompassItem.Reading.Kind.NEEDLE,
                    "the nugget is no needle: " + shown);
            Vec3 way = pig.position().subtract(mage.position());
            float expected = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
            check(helper, Math.abs(net.minecraft.util.Mth.wrapDegrees(shown.yaw() - expected)) < 1.0F,
                    "the needle points at " + shown.yaw() + ", the pig is at " + expected);
            check(helper, pig.position().distanceTo(helper.absoluteVec(new Vec3(6.5D, 1.0D, 2.5D))) < 0.5D,
                    "pushing the nugget moved the pig");
            pig.discard();
            leave(mage);
            helper.succeed();
        });
    }

    /** By a lodestone, an iron needle is drawn from where it was pushed; a gold one is not. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aLodestoneDrawsIronAndNotGold(GameTestHelper helper) {
        ServerPlayer iron = mageWithDial(helper, ArcaneCompassItem.Metal.IRON, "ferro_n");
        ServerPlayer gold = mageWithDial(helper, ArcaneCompassItem.Metal.GOLD, "ouro_n");
        // The lodestone beside the mages, at a right angle to the pig.
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.LODESTONE);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(6.5D, 1.0D, 2.5D));
        pig.setNoAi(true);
        MarkHelper.applyMark(pig, "norte");
        helper.runAtTickTime(2, () -> {
            cast(iron, "ferro_n norte ubis iactare");
            cast(gold, "ouro_n norte ubis iactare");
            Vec3 way = pig.position().subtract(iron.position());
            float expected = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
            float ironYaw = reading(iron, 0).yaw();
            float goldYaw = reading(gold, 0).yaw();
            check(helper, Math.abs(net.minecraft.util.Mth.wrapDegrees(goldYaw - expected)) < 1.0F,
                    "the gold needle was drawn: " + goldYaw + " instead of " + expected);
            check(helper, Math.abs(net.minecraft.util.Mth.wrapDegrees(ironYaw - expected)) > 5.0F,
                    "the iron needle was not drawn by the lodestone: " + ironYaw);
            pig.discard();
            leave(iron);
            leave(gold);
            helper.succeed();
        });
    }

    /**
     * A dial takes nuggets of both metals, whatever its case, and each moves as its own metal: in a gold dial, a
     * lodestone draws the iron nugget and not the gold one.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void eachNuggetMovesAsItsOwnMetal(GameTestHelper helper) {
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.GOLD);
        ItemStack ironNugget = new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET);
        ironNugget.setHoverName(net.minecraft.network.chat.Component.literal("ferro_m"));
        ItemStack dial = ArcaneCompassItem.withNugget(mage.getMainHandItem(), ironNugget).orElse(ItemStack.EMPTY);
        check(helper, !dial.isEmpty(), "a gold dial did not take an iron nugget");
        dial = ArcaneCompassItem.withNugget(dial, goldNugget("ouro_m")).orElse(ItemStack.EMPTY);
        check(helper, ArcaneCompassItem.nuggets(dial).equals(List.of("ferro_m", "ouro_m")),
                "the dial did not take both nuggets: " + ArcaneCompassItem.nuggets(dial));
        check(helper, ArcaneCompassItem.nuggetMetal(dial, 0) == ArcaneCompassItem.Metal.IRON
                && ArcaneCompassItem.nuggetMetal(dial, 1) == ArcaneCompassItem.Metal.GOLD,
                "the nuggets did not keep their metals");
        mage.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dial);

        helper.setBlock(new BlockPos(2, 1, 4), Blocks.LODESTONE);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(6.5D, 1.0D, 2.5D));
        pig.setNoAi(true);
        MarkHelper.applyMark(pig, "leste_m");
        helper.runAtTickTime(2, () -> {
            cast(mage, "ferro_m leste_m ubis iactare");
            cast(mage, "ouro_m leste_m ubis iactare");
            Vec3 way = pig.position().subtract(mage.position());
            float expected = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
            float ironYaw = reading(mage, 0).yaw();
            float goldYaw = reading(mage, 1).yaw();
            check(helper, Math.abs(net.minecraft.util.Mth.wrapDegrees(goldYaw - expected)) < 1.0F,
                    "the gold nugget was drawn: " + goldYaw + " instead of " + expected);
            check(helper, Math.abs(net.minecraft.util.Mth.wrapDegrees(ironYaw - expected)) > 5.0F,
                    "the iron nugget in a gold dial was not drawn by the lodestone: " + ironYaw);
            pig.discard();
            leave(mage);
            helper.succeed();
        });
    }

    /** A nugget bound by an aura bond to a pig strains as fast as the pig goes, and not when it stands. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aPulledNuggetStrainsAsFastAsItsPuller(GameTestHelper helper) {
        // The test world is kept between runs, and its bonds with it: marks of this run only.
        String run = Long.toString(System.nanoTime() % 1000000L, 36);
        String nugget = "veloz" + run;
        String runner = "corredor" + run;
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.IRON, nugget);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(1.5D, 1.0D, 1.5D));
        pig.setNoAi(true);
        MarkHelper.applyMark(pig, runner);
        helper.runAtTickTime(2, () -> {
            SpellCastingService.Result bond = cast(mage, "aura " + runner + " ligabis " + nugget);
            check(helper, bond.success(), "the bond was not cast: " + bond.message().getString());
            check(helper, !nugget.equals(MarkHelper.markForEntity(pig).orElse("")),
                    "the bond marked the pig with the nugget's mark");
        });
        // The pig is carried a block every tick, then stops; the dial is read each tick, as the inventory would.
        for (int tick = 3; tick <= 70; tick++) {
            int now = tick;
            helper.runAtTickTime(tick, () -> {
                if (now < 40) {
                    pig.setPos(pig.getX(), pig.getY(), pig.getZ() + 0.25D);
                }
                Dials.pull(mage, mage.getMainHandItem());
            });
        }
        double[] fastest = {0.0D};
        helper.runAtTickTime(40, () -> {
            ArcaneCompassItem.Reading shown = reading(mage, 0);
            com.elderlexicon.mod.ligabis.world.LigabisManager manager =
                    com.elderlexicon.mod.ligabis.world.LigabisManager.get();
            check(helper, shown != null && shown.kind() == ArcaneCompassItem.Reading.Kind.SPEED,
                    "the nugget shows no speed: " + shown + " (bonds into it: "
                            + (manager == null ? "no manager" : manager.auraParentsOf(nugget).size())
                            + ", mage alive " + mage.isAlive() + ", in hand " + mage.getMainHandItem()
                            + ", nuggets " + ArcaneCompassItem.nuggets(mage.getMainHandItem()) + ")");
            fastest[0] = shown.speed();
            check(helper, shown.speed() > 3.0D, "the pig goes 5 blocks a second, the dial says " + shown.speed());
        });
        helper.runAtTickTime(70, () -> {
            ArcaneCompassItem.Reading shown = reading(mage, 0);
            check(helper, shown == null || shown.speed() < fastest[0] / 4.0D,
                    "the pig stopped, the dial still says " + (shown == null ? 0 : shown.speed()));
            pig.discard();
            leave(mage);
            helper.succeed();
        });
    }

    /**
     * A dial named at the anvil with the mark of a page carried: surgit with the dial in hand reads the page, and the
     * page is not spent.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void surgitWithTheMarkedDialInHandReadsItsPage(GameTestHelper helper) {
        String run = Long.toString(System.nanoTime() % 1000000L, 36);
        String nugget = "fogo" + run;
        ServerPlayer mage = mageWithProgram(helper, nugget, "programa" + run);
        helper.runAtTickTime(2, () -> com.elderlexicon.mod.spelling.server.ServerSpellingController.getInstance()
                .handleSpellCast(mage, List.of("surgit"), net.minecraft.Util.getMillis()));
        helper.runAtTickTime(20, () -> {
            ArcaneCompassItem.Reading shown = reading(mage, 0);
            check(helper, shown != null && shown.kind() == ArcaneCompassItem.Reading.Kind.IMAGE
                    && "igni".equals(shown.element()), "surgit with the dial in hand read no page: " + shown);
            check(helper, mage.getInventory().countItem(ElderLexicon.DETACHED_PAGE.get()) == 1,
                    "the page read by its mark was spent");
            leave(mage);
            helper.succeed();
        });
    }

    /** In flow, a dial in hand is read again each second: what it shows lasts as long as it is held. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void inFlowTheDialInHandKeepsShowing(GameTestHelper helper) {
        String run = Long.toString(System.nanoTime() % 1000000L, 36);
        ServerPlayer mage = mageWithProgram(helper, "fogo" + run, "fluxo" + run);
        com.elderlexicon.mod.spelling.data.MageAbilities.grantFlow(mage);
        com.elderlexicon.mod.spelling.flow.FlowState.enter(mage);
        // A test player is not ticked by its connection: the flow is ticked here, as the player tick would.
        for (int tick = 2; tick <= 100; tick++) {
            helper.runAtTickTime(tick, () -> com.elderlexicon.mod.spelling.flow.FlowState.tick(mage));
        }
        for (int tick : new int[] {40, 70, 100}) {
            helper.runAtTickTime(tick, () -> {
                ArcaneCompassItem.Reading shown = reading(mage, 0);
                check(helper, shown != null && shown.kind() == ArcaneCompassItem.Reading.Kind.IMAGE,
                        "in flow the dial went dark at tick " + helper.getTick() + ": " + shown);
            });
        }
        helper.runAtTickTime(101, () -> {
            com.elderlexicon.mod.spelling.flow.FlowState.leave(mage, null);
            leave(mage);
            helper.succeed();
        });
    }

    /**
     * A mage in trance with a dial named {@code program} in hand, its nugget bearing {@code nugget}, and a page marked
     * {@code program} carried, that measures the fire of the body onto the nugget.
     */
    private static ServerPlayer mageWithProgram(GameTestHelper helper, String nugget, String program) {
        ServerPlayer mage = mageWithDial(helper, ArcaneCompassItem.Metal.IRON, nugget);
        com.elderlexicon.mod.spelling.data.MageAbilities.grantTrance(mage);
        mage.getMainHandItem().setHoverName(net.minecraft.network.chat.Component.literal(program));
        ItemStack page = new ItemStack(ElderLexicon.DETACHED_PAGE.get());
        page.getOrCreateTag().putString("DetachedPageText", "igni quantum ego surgit " + nugget + " ubis vocant");
        com.elderlexicon.mod.spell.block.WrittenTexts.mark(page);
        MarkHelper.applyMark(page, program);
        mage.getInventory().add(page);
        return mage;
    }
}

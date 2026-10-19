package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.galdraria.Galdraria;
import com.elderlexicon.mod.invocation.BenchCrafting;
import com.elderlexicon.mod.invocation.BenchMarkPacket;
import com.elderlexicon.mod.invocation.BenchMatter;
import com.elderlexicon.mod.invocation.Conjured;
import com.elderlexicon.mod.invocation.InvocationBenchBlockEntity;
import com.elderlexicon.mod.invocation.Invocations;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.vita.VitaElement;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The invocation bench (docs/invocacao-design.md): a crafting table marked with the burin; matter invoked into a
 * marked square becomes the thing whose code it has, or stays matter; the grid makes what a recipe of the game makes,
 * else a format's conjured thing; volatile matter comes apart two seconds out of the bench; an engraved bench sends off
 * what it made, or everything on it when a square was put wrong. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ElderLexicon.MODID)
@PrefixGameTestTemplate(false)
public final class InvocationGameTests {

    private static final String EMPTY = "empty";
    private static final BlockPos BENCH = new BlockPos(1, 1, 1);

    private InvocationGameTests() {
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    private static ServerPlayer mage(GameTestHelper helper, Vec3 where) {
        ServerLevel level = helper.getLevel();
        ServerPlayer mage = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-bench"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, mage);
        Vec3 at = helper.absoluteVec(where);
        mage.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        mage.giveExperienceLevels(60);
        return mage;
    }

    private static void leave(ServerPlayer mage) {
        mage.level().getServer().getPlayerList().remove(mage);
    }

    /** A bench whose squares bear {@code prefix}1 to {@code prefix}9, with {@code engraving} carved in it. */
    private static InvocationBenchBlockEntity bench(GameTestHelper helper, String prefix, String engraving) {
        helper.setBlock(BENCH, Invocations.BENCH.get());
        InvocationBenchBlockEntity bench = (InvocationBenchBlockEntity) helper.getBlockEntity(BENCH);
        String[] marks = new String[9];
        for (int i = 0; i < 9; i++) {
            marks[i] = prefix + (i + 1);
        }
        List<String> words = Engravings.words(engraving);
        bench.setMarks(marks, words.isEmpty() ? "" : Engravings.written(words));
        return bench;
    }

    private static Particles code(String substance, long particles) {
        return Materials.get().substance(substance).orElseThrow().code(particles);
    }

    /** How many particles one of an item holds, as the table reads it (an ingot of iron is packed as its block). */
    private static long one(String item) {
        return Materials.get().read(com.elderlexicon.mod.magic.matter.Form.Kind.ITEM, item)
                .map(reading -> Particles.ofUmu(reading.umu())).orElseThrow();
    }

    private static long ingot() {
        return one("minecraft:iron_ingot");
    }

    private static long stick() {
        return one("minecraft:stick");
    }

    private static ItemStack fire(double umu) {
        return BenchMatter.become(BenchMatter.of(VitaElement.IGNI, umu), 0);
    }

    private static SpellCastingService.Result cast(ServerPlayer mage, String spell) {
        return new SpellCastingService().cast(mage, List.of(spell.split(" ")));
    }

    /** Whether {@code item} lies within {@code reach} of {@code at}, or is carried by {@code mage}. */
    private static boolean near(GameTestHelper helper, ServerPlayer mage, Item item, double reach) {
        if (mage.getInventory().contains(new ItemStack(item))) {
            return true;
        }
        return !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(mage.blockPosition()).inflate(reach),
                lying -> lying.getItem().is(item)).isEmpty();
    }

    /** Where the items around the test lie, and what the mage carries, for a failure to say. */
    private static String whereItemsAre(GameTestHelper helper, ServerPlayer mage) {
        StringBuilder found = new StringBuilder("mage at ").append(mage.blockPosition()).append(" carries");
        mage.getInventory().items.stream().filter(stack -> !stack.isEmpty())
                .forEach(stack -> found.append(' ').append(stack));
        found.append("; lying:");
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BENCH)).inflate(12.0D))
                .forEach(lying -> found.append(' ').append(lying.getItem()).append('@').append(lying.blockPosition()));
        return found.toString();
    }

    /**
     * With the burin in hand, a crafting table whose squares were given marks is an invocation bench; each mark and each
     * rune of the line wears the burin by one; with every mark taken away it is blank, a crafting table again when its
     * screen is closed.
     */
    @GameTest(template = EMPTY)
    public static void aCraftingTableMarkedWithTheBurinIsABench(GameTestHelper helper) {
        helper.setBlock(BENCH, Blocks.CRAFTING_TABLE);
        ServerPlayer mage = mage(helper, new Vec3(2.5D, 1.0D, 2.5D));
        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Galdraria.BURIN.get()));
        BlockPos at = helper.absolutePos(BENCH);
        new BenchMarkPacket(at, List.of("ma1", "", "ma3", "", "", "", "", "", ""), "ego ubis vocant").apply(mage);
        check(helper, helper.getBlockState(BENCH).is(Invocations.BENCH.get()), "the table is no bench");
        InvocationBenchBlockEntity bench = (InvocationBenchBlockEntity) helper.getBlockEntity(BENCH);
        check(helper, bench.marks()[0].equals("ma1") && bench.marks()[2].equals("ma3") && bench.marks()[1].isEmpty(),
                "marks " + Arrays.toString(bench.marks()));
        check(helper, !bench.engraving().isBlank(), "no line carved");
        int worn = mage.getMainHandItem().getDamageValue();
        check(helper, worn == 5, "the burin wore by " + worn + ", not 2 marks and 3 runes");

        new BenchMarkPacket(at, List.of("ego", "", "", "", "", "", "", "", ""), "").apply(mage);
        check(helper, helper.getBlockState(BENCH).is(Invocations.BENCH.get())
                && ((InvocationBenchBlockEntity) helper.getBlockEntity(BENCH)).marks()[0].equals("ma1"),
                "a rune was taken for a mark");

        new BenchMarkPacket(at, List.of("", "", "", "", "", "", "", "", ""), "").apply(mage);
        check(helper, ((InvocationBenchBlockEntity) helper.getBlockEntity(BENCH)).blank(),
                "with every mark and the line taken away, it is not blank (a crafting table again once closed)");
        leave(mage);
        helper.succeed();
    }

    /** The particles of iron, an ingot's worth, are an ingot; fire is fire, matter with no item of the game. */
    @GameTest(template = EMPTY)
    public static void matterInASquareIsTheThingWhoseCodeItHas(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mb", "");
        check(helper, bench.receive(0, code("iron", ingot()), 0, null), "the square took no iron");
        check(helper, bench.grid().getItem(0).is(Items.IRON_INGOT), "iron is " + bench.grid().getItem(0));
        check(helper, bench.receive(1, code("iron", ingot() * 440L / 455L), 0, null), "the square took no iron");
        check(helper, bench.grid().getItem(1).is(Items.IRON_INGOT), "iron near an ingot is " + bench.grid().getItem(1));
        check(helper, bench.receive(2, code("iron", ingot() * 300L / 455L), 0, null), "the square took no iron");
        check(helper, bench.grid().getItem(2).is(Invocations.MATTER.get()), "too little iron is " + bench.grid().getItem(2));
        check(helper, bench.receive(3, code("wood", stick()), 0, null), "the square took no wood");
        check(helper, bench.grid().getItem(3).is(Items.STICK), "wood is " + bench.grid().getItem(3));

        check(helper, bench.receive(4, BenchMatter.of(VitaElement.IGNI, 30.0D), 0, null), "the square took no fire");
        ItemStack fire = bench.grid().getItem(4);
        check(helper, fire.is(Invocations.MATTER.get()) && Conjured.matter(fire).igni() == 30L * Particles.PER_UMU,
                "fire is " + fire + " " + Conjured.matter(fire));
        check(helper, Conjured.isVolatile(fire), "fire is not volatile");

        bench.grid().setItem(5, new ItemStack(Items.DIAMOND_SWORD));
        check(helper, !bench.receive(5, BenchMatter.of(VitaElement.IGNI, 1.0D), 0, null), "a sword took matter");
        helper.succeed();
    }

    /** The author's own spell: 30 UMU of vis turned to fire, invoked into a marked square, fills it. */
    @GameTest(template = EMPTY)
    public static void aVocantIntoAMarkedSquareFillsIt(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mc", "");
        ServerPlayer mage = mage(helper, new Vec3(3.5D, 1.0D, 3.5D));
        SpellCastingService.Result result = cast(mage, "vis quantum 30 vertere igni mc2 ubis vocant");
        check(helper, !result.failed(), "the spell failed: " + result.message().getString());
        helper.succeedWhen(() -> {
            ItemStack there = bench.grid().getItem(1);
            check(helper, there.is(Invocations.MATTER.get()), "square 2 holds " + there);
            check(helper, Conjured.matter(there).igni() >= 29L * Particles.PER_UMU,
                    "square 2 holds " + Conjured.matter(there));
            leave(mage);
        });
    }

    /** Two ingots and a stick are the game's sword, as at any crafting table. */
    @GameTest(template = EMPTY)
    public static void aRecipeOfTheGameIsMadeAsAtACraftingTable(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "md", "");
        bench.receive(1, code("iron", ingot()), 0, null);
        bench.receive(4, code("iron", ingot()), 0, null);
        bench.grid().setItem(7, new ItemStack(Items.STICK));
        ItemStack made = BenchCrafting.made(helper.getLevel(), bench.grid()).map(BenchCrafting.Made::thing)
                .orElse(ItemStack.EMPTY);
        check(helper, made.is(Items.IRON_SWORD), "made " + made);
        helper.succeed();
    }

    /** Fire where the iron would be is a sword of fire: the sword's format, of fire, volatile, with its handle. */
    @GameTest(template = EMPTY)
    public static void fireWhereTheIronWouldBeIsASwordOfFire(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "me", "");
        bench.grid().setItem(1, fire(30.0D));
        bench.grid().setItem(4, fire(30.0D));
        bench.grid().setItem(7, new ItemStack(Items.STICK));
        ItemStack made = BenchCrafting.made(helper.getLevel(), bench.grid()).map(BenchCrafting.Made::thing)
                .orElse(ItemStack.EMPTY);
        check(helper, made.is(Invocations.SWORD.get()), "made " + made);
        check(helper, Conjured.matter(made).igni() == 60L * Particles.PER_UMU, "of " + Conjured.matter(made));
        check(helper, Conjured.format(made).orElse("").equals("sword"), "format " + Conjured.format(made));
        check(helper, Conjured.isVolatile(made), "a sword of fire is not volatile");
        check(helper, Conjured.parts(made).size() == 1 && Conjured.parts(made).get(0).is(Items.STICK),
                "parts " + Conjured.parts(made));

        // Mirrored and moved, the drawing is the same; fire and water in the blade are no sword.
        bench.grid().clearContent();
        bench.grid().setItem(2, fire(10.0D));
        bench.grid().setItem(5, fire(10.0D));
        bench.grid().setItem(8, new ItemStack(Items.STICK));
        check(helper, BenchCrafting.made(helper.getLevel(), bench.grid()).isPresent(), "moved, no sword");
        bench.grid().setItem(5, BenchMatter.become(BenchMatter.of(VitaElement.AQUA, 10.0D), 0));
        check(helper, BenchCrafting.made(helper.getLevel(), bench.grid()).isEmpty(), "a blade of two matters made something");
        helper.succeed();
    }

    /** Out of the bench, a sword of fire keeps its form two seconds; then the fire goes and the handle falls. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aSwordOfFireComesApartTwoSecondsOutOfTheBench(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mf", "");
        bench.grid().setItem(1, fire(30.0D));
        bench.grid().setItem(4, fire(30.0D));
        bench.grid().setItem(7, new ItemStack(Items.STICK));
        ItemStack sword = BenchCrafting.made(helper.getLevel(), bench.grid()).orElseThrow().thing();
        Vec3 at = helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D));
        ItemEntity lying = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, sword);
        lying.setNeverPickUp();
        helper.getLevel().addFreshEntity(lying);
        helper.runAfterDelay(20, () -> check(helper, lying.isAlive(), "gone before two seconds"));
        helper.runAfterDelay(60, () -> {
            check(helper, !lying.isAlive(), "still there after three seconds");
            check(helper, !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at))
                    .inflate(2.0D), item -> item.getItem().is(Items.STICK)).isEmpty(), "the handle did not fall");
            helper.succeed();
        });
    }

    /**
     * An engraved bench sends off what it made, once quiet: ego ubis vocant brings the sword to whoever invoked into
     * it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anEngravedBenchSendsWhatItMadeToTheMage(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mg", "ego ubis vocant");
        ServerPlayer mage = mage(helper, new Vec3(4.5D, 1.0D, 4.5D));
        bench.receive(1, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(4, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(7, new ItemStack(Items.STICK), mage.getUUID());
        // Checked as it arrives: an item on the ground of the test world drifts away within a second or two.
        helper.runAfterDelay(InvocationBenchBlockEntity.SETTLE_TICKS + 5, () -> {
            check(helper, bench.grid().isEmpty(), "the grid still holds things");
            check(helper, near(helper, mage, Items.IRON_SWORD, 2.0D), "the sword is not with the mage: "
                    + whereItemsAre(helper, mage));
            leave(mage);
            helper.succeed();
        });
    }

    /** A pig that stays where it is put, on a floor of stone, to be struck. */
    private static net.minecraft.world.entity.animal.Pig pig(GameTestHelper helper, String mark) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        net.minecraft.world.entity.animal.Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 1, 4));
        com.elderlexicon.mod.spell.function.MarkHelper.applyMark(pig, mark);
        return pig;
    }

    /** A marked sword, iactared at a marked pig, is thrown point first as a blow and hurts it. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aMarkedSwordThrownStrikesAsABlow(GameTestHelper helper) {
        ServerPlayer mage = mage(helper, new Vec3(0.5D, 1.0D, 4.5D));
        net.minecraft.world.entity.animal.Pig pig = pig(helper, "mipig");
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1.5D, 1.5D));
        ItemEntity sword = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.IRON_SWORD));
        sword.setNoGravity(true);
        helper.getLevel().addFreshEntity(sword);
        com.elderlexicon.mod.spell.function.MarkHelper.applyMark(sword, "misword");
        SpellCastingService.Result result = cast(mage, "misword mipig ubis iactare");
        check(helper, !result.failed(), "the spell failed: " + result.message().getString());
        check(helper, !sword.isAlive(), "the sword was not thrown");
        helper.succeedWhen(() -> {
            check(helper, pig.getHealth() < pig.getMaxHealth(), "the pig was not struck");
            leave(mage);
        });
    }

    /** Two marked swords carried in the pack fly out together, in one blow, and both strike the marked pig. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void markedSwordsInThePackFlyOutTogether(GameTestHelper helper) {
        ServerPlayer mage = mage(helper, new Vec3(0.5D, 1.0D, 0.5D));
        net.minecraft.world.entity.animal.Pig pig = pig(helper, "mnpig");
        for (int i = 0; i < 2; i++) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            com.elderlexicon.mod.spell.function.MarkHelper.applyMark(sword, "mnsword");
            mage.getInventory().add(sword);
        }
        SpellCastingService.Result result = cast(mage, "mnsword mnpig ubis iactare");
        check(helper, !result.failed(), "the spell failed: " + result.message().getString());
        check(helper, !mage.getInventory().contains(new ItemStack(Items.IRON_SWORD))
                && mage.getInventory().items.stream().noneMatch(stack -> stack.is(Items.IRON_SWORD)),
                "a sword stayed in the pack");
        int flying = helper.getLevel().getEntitiesOfClass(com.elderlexicon.mod.invocation.ThrownThing.class,
                new AABB(mage.blockPosition()).inflate(4.0D)).size();
        check(helper, flying == 2, flying + " swords flew, not 2");
        helper.succeedWhen(() -> {
            // Two blows of 6, each its own: no moment of grace turns the second aside, and a pig has 10.
            check(helper, !pig.isAlive(), "the pig lives with " + pig.getHealth());
            leave(mage);
        });
    }

    /** A marked sword kept in a chest flies out of it at the marked pig. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aMarkedSwordInAChestFliesOutOfIt(GameTestHelper helper) {
        ServerPlayer mage = mage(helper, new Vec3(0.5D, 1.0D, 0.5D));
        net.minecraft.world.entity.animal.Pig pig = pig(helper, "mopig");
        helper.setBlock(BENCH, Blocks.CHEST);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(BENCH);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        com.elderlexicon.mod.spell.function.MarkHelper.applyMark(sword, "mosword");
        chest.setItem(5, sword);
        SpellCastingService.Result result = cast(mage, "mosword mopig ubis iactare");
        check(helper, !result.failed(), "the spell failed: " + result.message().getString());
        check(helper, chest.isEmpty(), "the sword stayed in the chest");
        helper.succeedWhen(() -> {
            check(helper, pig.getHealth() < pig.getMaxHealth(), "the pig was not struck");
            leave(mage);
        });
    }

    /**
     * {@code ferit ille ubis iactare} engraved: what was made waits on the bench until the mage strikes, then flies at
     * the one struck.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aBenchLineOfABlowWaitsForTheBlow(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mj", "ferit ille ubis iactare");
        ServerPlayer mage = mage(helper, new Vec3(0.5D, 1.0D, 4.5D));
        net.minecraft.world.entity.animal.Pig pig = pig(helper, "mjpig");
        bench.receive(1, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(4, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(7, new ItemStack(Items.STICK), mage.getUUID());
        helper.runAfterDelay(InvocationBenchBlockEntity.SETTLE_TICKS + 10, () -> {
            check(helper, bench.grid().isEmpty(), "the grid still holds things");
            check(helper, bench.ready().size() == 1 && bench.ready().get(0).is(Items.IRON_SWORD),
                    "nothing waits: " + bench.ready());
            check(helper, pig.getHealth() == pig.getMaxHealth(), "the pig was struck before any blow");
            com.elderlexicon.mod.spelling.flow.Happenings.mark(mage, com.elderlexicon.mod.spelling.flow.Happenings.ATTACK,
                    new com.elderlexicon.mod.spelling.flow.Happenings.OtherEntity(pig));
        });
        helper.runAfterDelay(InvocationBenchBlockEntity.SETTLE_TICKS + 40, () -> {
            check(helper, bench.ready().isEmpty(), "it still waits after the blow");
            check(helper, pig.getHealth() < pig.getMaxHealth(), "the pig was not struck by the sword");
            leave(mage);
            helper.succeed();
        });
    }

    /** A subject already named reads as if written: "ego ubis vocant" with a marked sword brings the sword. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aSubjectAlreadyNamedIsTheLinesSubject(GameTestHelper helper) {
        ServerPlayer mage = mage(helper, new Vec3(4.5D, 1.0D, 4.5D));
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1.5D, 1.5D));
        ItemEntity sword = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.IRON_SWORD));
        sword.setNoGravity(true);
        helper.getLevel().addFreshEntity(sword);
        com.elderlexicon.mod.spell.function.MarkHelper.applyMark(sword, "mlsword");
        SpellCastingService.Result result = com.elderlexicon.mod.magic.grammar.SpellGrammar.withSubject("mlsword",
                () -> cast(mage, "ego ubis vocant"));
        check(helper, !result.failed(), "the spell failed: " + result.message().getString());
        helper.succeedWhen(() -> {
            check(helper, sword.position().distanceTo(mage.position()) < 2.5D, "the sword stayed at "
                    + helper.relativeVec(sword.position()));
            leave(mage);
        });
    }

    /** The meteor: what the bench made appears high above the mage, and is thrown from there at the one marked. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void whatTheBenchMadeFallsFromTheSkyAtTheMarked(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mk", "ego 20 ubis vocant mkpig ubis iactare");
        ServerPlayer mage = mage(helper, new Vec3(0.5D, 1.0D, 4.5D));
        net.minecraft.world.entity.animal.Pig pig = pig(helper, "mkpig");
        bench.receive(1, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(4, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(7, new ItemStack(Items.STICK), mage.getUUID());
        helper.runAfterDelay(InvocationBenchBlockEntity.SETTLE_TICKS + 80, () -> {
            check(helper, pig.getHealth() < pig.getMaxHealth(), "the pig was not struck from the sky");
            leave(mage);
            helper.succeed();
        });
    }

    /** A square put wrong makes nothing: the engraved line sends everything on the grid, as it is. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aSquarePutWrongSendsEverythingOnTheGrid(GameTestHelper helper) {
        InvocationBenchBlockEntity bench = bench(helper, "mh", "ego ubis vocant");
        ServerPlayer mage = mage(helper, new Vec3(4.5D, 1.0D, 4.5D));
        bench.receive(0, code("iron", ingot()), 0, mage.getUUID());
        bench.receive(8, code("wood", stick()), 0, mage.getUUID());
        helper.runAfterDelay(InvocationBenchBlockEntity.SETTLE_TICKS + 5, () -> {
            check(helper, bench.grid().isEmpty(), "the grid still holds things");
            check(helper, near(helper, mage, Items.IRON_INGOT, 2.0D), "the ingot is not with the mage");
            check(helper, near(helper, mage, Items.STICK, 2.0D), "the stick is not with the mage");
            leave(mage);
            helper.succeed();
        });
    }
}

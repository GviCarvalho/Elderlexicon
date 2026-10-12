package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spell.life.Homunculus;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.npc.Villager;
import com.elderlexicon.mod.illager.Sorcerer;
import com.elderlexicon.mod.illager.SorcererGrimoire;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spell.Casters;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
        roomWithAWall(helper);
        ServerPlayer mage = mage(helper);
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.0D, 0.5D));
        // Yaw 0 looks south (+z), toward the wall, whichever way the test is rotated in the world.
        mage.moveTo(at.x, at.y, at.z, facingWall(helper), 0.0F);
        return mage;
    }

    /** A floor under the room and a wall of stone at its far end (z = 4). */
    private static void roomWithAWall(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }
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

    /**
     * A living body aimed at is a source (docs/feiticeiro-design.md, 7.4): water drawn out of a cow is a lack in its body,
     * and the life follows the element most lacking, so the cow is hurt.
     */
    @GameTest(template = EMPTY)
    public static void waterDrawnOutOfABodyHurtsIt(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        cow.setNoAi(true);
        Vec3 body = cow.position().add(0.0D, cow.getBbHeight() * 0.5D, 0.0D);
        mage.lookAt(EntityAnchorArgument.Anchor.EYES, body);
        float before = cow.getHealth();
        castOrFail(helper, mage, "aqua quantum -10 vocant");
        helper.succeedWhen(() -> {
            if (!(cow.getHealth() < before)) {
                helper.fail("the cow lost no life: " + cow.getHealth() + " of " + before);
            }
        });
    }

    /**
     * Any living being casts by the same rules (docs/feiticeiro-design.md, 8): an evoker throws earth at the wall as a
     * player would, and pays from its Anchor of the Hundred, not from experience it does not have.
     */
    @GameTest(template = EMPTY)
    public static void aCreatureCastsAndPaysFromItsAnchor(GameTestHelper helper) {
        roomWithAWall(helper);
        Evoker evoker = helper.spawn(EntityType.EVOKER, new Vec3(2.5D, 1.0D, 0.5D));
        evoker.setNoAi(true);
        evoker.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5D, 1.5D, 3.5D)));
        SpellCastingService.Result result = new SpellCastingService().cast(evoker, List.of("firmo", "vocant"));
        if (result.failed()) {
            helper.fail("firmo vocant failed: " + result.message().getString());
        }
        if (!(Casters.vis(evoker) < Casters.ANCHOR_UMU)) {
            helper.fail("the evoker paid nothing from its anchor: " + Casters.vis(evoker));
        }
        helper.succeedWhen(() -> {
            if (!anyIn(helper, Blocks.DIRT, 3, 3)) {
                helper.fail("no earth before the wall");
            }
        });
    }

    /**
     * The sorcerer reads its grimoire against a villager it hunts (docs/feiticeiro-design.md, stage 2): it stands, the
     * trance lasts two seconds, and the page it reads hurts the villager.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theSorcererReadsAPageAgainstItsPrey(GameTestHelper helper) {
        roomWithAWall(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 3.5D));
        villager.setNoAi(true);
        Sorcerer evoker = huntingOnly(helper, villager);
        float before = villager.getHealth();
        helper.succeedWhen(() -> {
            if (!(villager.getHealth() < before)) {
                helper.fail("the villager was not hurt");
            }
        });
    }

    /**
     * A sorcerer that hunts {@code prey} alone: the other tests' mages share the world, and an evoker would turn on
     * the nearest player.
     */
    private static Sorcerer huntingOnly(GameTestHelper helper, Villager prey) {
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 0.5D));
        evoker.targetSelector.removeAllGoals(goal -> true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.ELDER, com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                com.elderlexicon.mod.mind.Degree.ELDER.draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(),
                        helper.getLevel().getRandom()));
        evoker.setTarget(prey);
        return evoker;
    }

    /**
     * Out of a fight, with nothing to hunt, the sorcerer writes (docs/feiticeiro-design.md, 2.1): its pages are
     * written in a while, and kept with it when it is saved and loaded again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 2000)
    public static void aSorcererAloneWritesItsPages(GameTestHelper helper) {
        roomWithAWall(helper);
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 1.5D));
        evoker.targetSelector.removeAllGoals(goal -> true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.ELDER, com.elderlexicon.mod.mind.Personality.TRICKSTER,
                com.elderlexicon.mod.mind.Degree.ELDER.draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(),
                        helper.getLevel().getRandom()));
        helper.succeedWhen(() -> {
            if (evoker.written().isEmpty()) {
                helper.fail("nothing written yet");
            }
            net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
            evoker.saveWithoutId(saved);
            Sorcerer again = ElderLexicon.SORCERER.get().create(helper.getLevel());
            again.load(saved);
            List<String> before = evoker.written().stream().map(com.elderlexicon.mod.mind.Option::name).toList();
            List<String> after = again.written().stream().map(com.elderlexicon.mod.mind.Option::name).toList();
            if (!before.equals(after)) {
                helper.fail("written " + before + ", loaded " + after);
            }
            if (again.grimoire().getOrCreateTag().getList("pages", net.minecraft.nbt.Tag.TAG_STRING).size()
                    < before.size()) {
                helper.fail("its grimoire does not hold what it wrote");
            }
        });
    }

    /**
     * A sorcerer that sees a mage cast writes the sentence down (docs/feiticeiro-design.md, 4), leaving out the runes
     * it does not know, as its spirit would skip them.
     */
    @GameTest(template = EMPTY)
    public static void aSorcererCopiesWhatItSeesCast(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(1.5D, 1.0D, 2.5D));
        evoker.setNoAi(true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE, com.elderlexicon.mod.mind.Personality.TRICKSTER,
                java.util.Set.of("aqua", "iactare", "vocant"));
        castOrFail(helper, mage, "aqua tenet iactare");
        List<String> learned = evoker.learned().stream().map(com.elderlexicon.mod.mind.Option::name).toList();
        if (!learned.contains("aqua iactare")) {
            helper.fail("it wrote down " + learned + ", not what it could read of 'aqua tenet iactare'");
        }
        if (!evoker.grimoire().getOrCreateTag().getList("pages", net.minecraft.nbt.Tag.TAG_STRING)
                .contains(net.minecraft.nbt.StringTag.valueOf("aqua iactare"))) {
            helper.fail("its grimoire does not keep the copy");
        }
        helper.succeed();
    }

    /**
     * A mansion's evoker may be a sorcerer (docs/feiticeiro-design.md, 7): it takes the evoker's place where it stood,
     * and a corner to study in is put beside it. (The chance is made certain for the test, and given back.)
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aMansionsEvokerMayBeASorcerer(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        double chance = com.elderlexicon.mod.Config.sorcererMansionChance;
        com.elderlexicon.mod.Config.sorcererMansionChance = 1.0D;
        net.minecraft.world.entity.monster.Evoker evoker = EntityType.EVOKER.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D));
        evoker.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        evoker.setPersistenceRequired();
        evoker.setNoAi(true);
        helper.getLevel().addFreshEntity(evoker);
        com.elderlexicon.mod.Config.sorcererMansionChance = chance;
        helper.succeedWhen(() -> {
            if (!evoker.isRemoved()) {
                helper.fail("the evoker is still there");
            }
            List<Sorcerer> sorcerers = helper.getLevel().getEntitiesOfClass(Sorcerer.class,
                    new AABB(at, at).inflate(1.0D));
            if (sorcerers.isEmpty() || !sorcerers.get(0).isPersistenceRequired()) {
                helper.fail("no sorcerer kept where it stood");
            }
            boolean lectern = false;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                lectern |= helper.getLevel().getBlockState(BlockPos.containing(at).relative(side)).is(Blocks.LECTERN);
            }
            if (!lectern) {
                helper.fail("no lectern to study at");
            }
        });
    }

    /**
     * A sorcerer learns as a mage does (docs/aprendizado-runas-design.md): it lives a rune a few times, and in calm it
     * dreams it and knows it; its degree follows, and it writes its grimoire again.
     */
    @GameTest(template = EMPTY)
    public static void aSorcererLearnsARuneItLivedByDreamingIt(GameTestHelper helper) {
        Sorcerer sorcerer = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 2.5D));
        sorcerer.setNoAi(true);
        sorcerer.setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE, com.elderlexicon.mod.mind.Personality.CAUTIOUS,
                java.util.Set.of("igni", "iactare", "vocant"));
        sorcerer.experience("aqua");
        sorcerer.experience("aqua");
        if (sorcerer.dream()) {
            helper.fail("it dreamed what it barely lived");
        }
        // A blow of no energy teaches nothing; water lived once more, and it is enough.
        sorcerer.hurt(helper.getLevel().damageSources().generic(), 1.0F);
        sorcerer.experience("aqua");
        if (!sorcerer.dream() || !sorcerer.known().contains("aqua")) {
            helper.fail("it did not learn aqua: " + sorcerer.known() + " " + sorcerer.understanding());
        }
        helper.succeed();
    }

    /**
     * A sorcerer leads its band (docs/feiticeiro-design.md, 7.2): an aggressive one gives every member one mark, a
     * trickster gives each its own.
     */
    @GameTest(template = EMPTY)
    public static void aSorcererMarksItsBandAsItLikes(GameTestHelper helper) {
        Sorcerer aggressive = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(1.5D, 1.0D, 1.5D));
        aggressive.setNoAi(true);
        aggressive.setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE, com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                java.util.Set.of("igni", "iactare", "vocant"));
        List<net.minecraft.world.entity.monster.Vindicator> band = List.of(
                helper.spawn(EntityType.VINDICATOR, new Vec3(3.5D, 1.0D, 1.5D)),
                helper.spawn(EntityType.VINDICATOR, new Vec3(3.5D, 1.0D, 3.5D)));
        band.forEach(member -> member.setNoAi(true));
        aggressive.markBand();
        List<String> marks = band.stream().map(member -> com.elderlexicon.mod.spell.function.MarkHelper
                .markForEntity(member).orElse("")).toList();
        if (marks.get(0).isEmpty() || !marks.get(0).equals(marks.get(1))) {
            helper.fail("an aggressive sorcerer gave its band " + marks);
        }
        band.forEach(member -> com.elderlexicon.mod.spell.function.MarkHelper.applyMark(member, null));
        Sorcerer trickster = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(1.5D, 1.0D, 3.5D));
        trickster.setNoAi(true);
        trickster.setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE, com.elderlexicon.mod.mind.Personality.TRICKSTER,
                java.util.Set.of("igni", "iactare", "vocant"));
        trickster.markBand();
        List<String> own = band.stream().map(member -> com.elderlexicon.mod.spell.function.MarkHelper
                .markForEntity(member).orElse("")).toList();
        if (own.get(0).isEmpty() || own.get(0).equals(own.get(1))) {
            helper.fail("a trickster gave its band " + own);
        }
        helper.succeed();
    }

    /**
     * The mind weighs what the scene asks (docs/feiticeiro-design.md, 3.3): drawing the water out of a cow is worth more
     * to a wounded evoker, which heals by it, than to a whole one; and it foresees what the draw takes from the cow.
     */
    @GameTest(template = EMPTY)
    public static void aWoundedMindWantsToDrawMore(GameTestHelper helper) {
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 0.5D));
        evoker.setNoAi(true);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 1.0D, 3.5D));
        cow.setNoAi(true);
        com.elderlexicon.mod.mind.Option bite = com.elderlexicon.mod.mind.Option.of(
                com.elderlexicon.mod.mind.PhraseCatalog.imagine(List.of("aqua", "quantum", "-10", "vocant")).orElseThrow());
        com.elderlexicon.mod.mind.Personality trickster = com.elderlexicon.mod.mind.Personality.TRICKSTER;
        double whole = com.elderlexicon.mod.mind.Mind.evaluate(new com.elderlexicon.mod.mind.Mind.Scene(evoker, cow, 3.0D,
                1.0F, 100.0D), trickster, bite).map(com.elderlexicon.mod.mind.Mind.Choice::score).orElse(0.0D);
        evoker.hurt(helper.getLevel().damageSources().generic(), evoker.getMaxHealth() * 0.6F);
        float share = evoker.getHealth() / evoker.getMaxHealth();
        double wounded = com.elderlexicon.mod.mind.Mind.evaluate(new com.elderlexicon.mod.mind.Mind.Scene(evoker, cow,
                3.0D, share, 100.0D), trickster, bite).map(com.elderlexicon.mod.mind.Mind.Choice::score).orElse(0.0D);
        if (!(whole > 0.0D)) {
            helper.fail("drawing out of the cow is worth nothing: " + whole);
        }
        if (!(wounded > whole)) {
            helper.fail("wounded (" + share + ") it is worth " + wounded + ", whole " + whole);
        }
        helper.succeed();
    }

    /**
     * What the sorcerer leaves is a grimoire like any other (docs/feiticeiro-design.md, 5): its pages as the spirit
     * reads them, named, with the evoker as their author and the runes it wrote in the glossary.
     */
    @GameTest(template = EMPTY)
    public static void theSorcerersGrimoireIsAGrimoireLikeAnyOther(GameTestHelper helper) {
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 0.5D));
        // An elder reads every page written by hand.
        evoker.setMind(com.elderlexicon.mod.mind.Degree.ELDER, com.elderlexicon.mod.mind.Personality.CAUTIOUS,
                com.elderlexicon.mod.mind.Degree.ELDER.draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(),
                        helper.getLevel().getRandom()));
        ItemStack book = evoker.grimoire();
        if (!(book.getItem() instanceof GrimoireItem) || book.getTag() == null) {
            helper.fail("not a grimoire: " + book);
        }
        net.minecraft.nbt.ListTag pages = book.getTag().getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
        if (pages.size() != SorcererGrimoire.get().pages().size()) {
            helper.fail("the grimoire holds " + pages.size() + " pages");
        }
        for (int i = 0; i < pages.size(); i++) {
            if (com.elderlexicon.mod.spell.block.SpellBlock.parse(pages.getString(i),
                    com.elderlexicon.mod.spell.block.RuneTokens::normalize).isEmpty()) {
                helper.fail("page " + i + " has nothing the spirit reads");
            }
        }
        if (!book.getTag().getList(GrimoireItem.GLOSSARY_TAG, net.minecraft.nbt.Tag.TAG_STRING).contains(
                net.minecraft.nbt.StringTag.valueOf("igni"))) {
            helper.fail("igni is not in the glossary");
        }
        helper.succeed();
    }

    /** A blow during the trance breaks it (book, cap. 6): the page is not read. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void aBlowBreaksTheSorcerersTrance(GameTestHelper helper) {
        roomWithAWall(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 3.5D));
        villager.setNoAi(true);
        Sorcerer evoker = huntingOnly(helper, villager);
        helper.succeedWhen(() -> {
            if (!evoker.inTrance()) {
                helper.fail("not in trance yet");
            }
            evoker.hurt(helper.getLevel().damageSources().generic(), 1.0F);
            if (evoker.inTrance()) {
                helper.fail("the trance held after a blow");
            }
        });
    }

    /** A creature bites as a mage does: the water of a cow, drawn by an evoker, hurts the cow, and the evoker pays. */
    @GameTest(template = EMPTY)
    public static void aCreatureDrawsOutOfABody(GameTestHelper helper) {
        roomWithAWall(helper);
        Evoker evoker = helper.spawn(EntityType.EVOKER, new Vec3(2.5D, 1.0D, 0.5D));
        evoker.setNoAi(true);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        cow.setNoAi(true);
        evoker.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0.0D, cow.getBbHeight() * 0.5D, 0.0D));
        float before = cow.getHealth();
        SpellCastingService.Result result = new SpellCastingService().cast(evoker, List.of("aqua", "quantum", "-10",
                "vocant"));
        if (result.failed()) {
            helper.fail("the bite failed: " + result.message().getString());
        }
        helper.succeedWhen(() -> {
            if (!(cow.getHealth() < before)) {
                helper.fail("the cow lost no life: " + cow.getHealth() + " of " + before);
            }
            if (cow.getLastHurtByMob() != evoker) {
                helper.fail("the cow does not know the evoker drew its water");
            }
            if (!(Casters.vis(evoker) < Casters.ANCHOR_UMU)) {
                helper.fail("the evoker paid nothing for the life it took");
            }
        });
    }

    /** The hurt a lack makes is the drawer's: the cow turns on the mage who drew its water out. */
    @GameTest(template = EMPTY)
    public static void aBodyDrawnFromKnowsWhoDrewIt(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        cow.setNoAi(true);
        mage.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0.0D, cow.getBbHeight() * 0.5D, 0.0D));
        castOrFail(helper, mage, "aqua quantum -10 vocant");
        helper.succeedWhen(() -> {
            if (cow.getLastHurtByMob() != mage) {
                helper.fail("the cow does not know who drew its water: " + cow.getLastHurtByMob());
            }
        });
    }

    /**
     * The spirit charges for the life a draw takes, five UMU a point, not for the energy it moves: one UMU of fire out
     * of a cow (it has one and a half) takes two thirds of its ten points of health, and costs the mage some thirty UMU.
     */
    @GameTest(template = EMPTY)
    public static void drawingOutOfABodyCostsTheLifeItTakes(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        mage.giveExperiencePoints(1000);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        cow.setNoAi(true);
        mage.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0.0D, cow.getBbHeight() * 0.5D, 0.0D));
        int before = mage.totalExperience;
        castOrFail(helper, mage, "igni quantum -1 vocant");
        int spent = before - mage.totalExperience;
        if (spent < 250) {
            helper.fail("the draw cost only " + spent + " points of experience");
        }
        helper.succeedWhen(() -> {
            if (!(cow.getHealth() < 5.0F)) {
                helper.fail("the cow kept " + cow.getHealth() + " of its life");
            }
        });
    }

    /** Matter moved out of a living body ({@code aqua tenet vocant} at a cow): its water is put down, and it is hurt. */
    @GameTest(template = EMPTY)
    public static void waterMovedOutOfABodyIsPutDown(GameTestHelper helper) {
        ServerPlayer mage = mageBeforeAWall(helper);
        Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        cow.setNoAi(true);
        mage.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0.0D, cow.getBbHeight() * 0.5D, 0.0D));
        float before = cow.getHealth();
        castOrFail(helper, mage, "aqua tenet vocant");
        helper.succeedWhen(() -> {
            if (!(cow.getHealth() < before)) {
                helper.fail("the cow lost no life: " + cow.getHealth() + " of " + before);
            }
            if (!anyIn(helper, Blocks.WATER, 0, 4)) {
                helper.fail("no water was put down");
            }
        });
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
        // What is brought waits for the end of the instant, in case an anchor binds it into a being.
        helper.succeedWhen(() -> {
            if (!anyIn(helper, Blocks.STONE, 2, 3)) {
                helper.fail("no stone was put before the wall");
            }
        });
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
     * Water poured into molten earth quenches it (docs/particulas-design.md, stage 9): mixing keeps the agitation each
     * brought, and earth just past its melting with a little cold water in it is solid. A hundred and twenty-eight of
     * earth and eight of water are deepslate's code, so the melt sets into deepslate, still hot; and hot, it lets its
     * water go as vapour at once, and what is left is earth. (Stone can no longer be mixed out of the primordials this
     * way: the air poured in would bubble out, and the fire would only heat it.)
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void waterPouredIntoMoltenEarthQuenchesIt(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS); // glass is made: no matter to melt
            }
        }
        for (int x = 2; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        String pool = at(helper, 2, 1, 2);
        helper.startSequence()
                // 128 UMU of earth, eight blocks of soil, melted where they are: molten earth, formless matter.
                .thenExecute(() -> {
                    castOrFail(helper, mage, "firmo quantum 128 " + pool + " tenet vertere aqua");
                    List<Matter> molten = formlessIn(helper);
                    check(helper, molten.size() == 8 && countIn(helper, Blocks.DIRT) == 0, "the soil did not melt: "
                            + molten.size() + " formless, " + countIn(helper, Blocks.DIRT) + " dirt left");
                })
                // It can stay as it is: a moment later it is still molten.
                .thenIdle(3)
                .thenExecute(() -> check(helper, formlessIn(helper).size() == 8
                        && formlessIn(helper).stream().allMatch(matter -> matter.state() == State.LIQUID),
                        "the molten earth should stay molten: " + formlessIn(helper)))
                // Eight of water: quenched into eight solid blocks (the half block more goes back to the mage), which
                // lose their water as they cool.
                .thenExecute(() -> castOrFail(helper, mage, "aqua quantum 8 " + pool + " ubis vocant"))
                .thenWaitUntil(() -> {
                    check(helper, formlessIn(helper).isEmpty(), "the melt should have set: " + formlessIn(helper));
                    int set = countIn(helper, Blocks.DEEPSLATE) + countIn(helper, Blocks.DIRT);
                    check(helper, set == 8, "eight blocks of deepslate or earth, were " + set);
                })
                .thenSucceed();
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
        // The liquids nearest (2, 1, 2), 32 UMU of them, taken and put at (2, 1, 4): a source of water and one of lava,
        // brought to one place, mix (L4) with the agitation each brought. Half stone and half water is near no natural
        // thing's code, and the water quenches the stone: a solid with no name, warm, whose water boils off as it cools.
        castOrFail(helper, mage, "aqua quantum 32 " + at(helper, 2, 1, 2) + " tenet " + at(helper, 2, 1, 4)
                + " ubis vocant");
        check(helper, countIn(helper, Blocks.WATER) == 0 && countIn(helper, Blocks.LAVA) == 0,
                "the water and the lava should have been taken");
        helper.runAfterDelay(2, () -> {
            List<Matter> brought = formlessIn(helper);
            check(helper, !brought.isEmpty() && brought.stream().allMatch(matter -> matter.unnamed(Materials.get())
                    && matter.state() == State.SOLID), "they should be one solid with no name now: " + brought);
            double held = brought.stream().mapToDouble(Matter::umu).sum();
            check(helper, held > 24.0D && held <= 32.0D + 1.0E-6D,
                    "both, 32 UMU, but for the water that boils off, were " + held);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void fireThrownIntoAPoolBoilsItAway(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.WATER);
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        // Fire poured into water does not mix with it: it is agitation, and four UMU of it (1024 particles) boil the
        // block of water it went into (docs/particulas-design.md, stage 9).
        castOrFail(helper, mage, "igni quantum 4 " + at(helper, 2, 1, 2) + " ubis vocant");
        helper.runAfterDelay(5, () -> {
            int water = countIn(helper, Blocks.WATER);
            check(helper, water <= 2, "the block the fire went into should boil away, " + water + " sources left at"
                    + where(helper, Blocks.WATER));
            check(helper, formlessIn(helper).isEmpty(), "nothing formless: water is water");
            helper.succeed();
        });
    }

    /**
     * A vocant makes whole blocks of what its source is, one for every 16 UMU (docs/particulas-design.md); what makes no
     * whole block goes back to the body, so ten UMU of earth make none.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aVocantMakesOneBlockForEverySixteenUmu(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        helper.startSequence()
                .thenExecute(() -> castOrFail(helper, mage, "firmo quantum 40 " + at(helper, 2, 1, 2) + " ubis vocant"))
                .thenIdle(2)
                .thenExecute(() -> check(helper, countIn(helper, Blocks.DIRT) == 2,
                        "forty UMU of earth are two blocks, were " + countIn(helper, Blocks.DIRT)))
                .thenExecute(() -> castOrFail(helper, mage, "firmo quantum 10 " + at(helper, 2, 1, 4) + " ubis vocant"))
                .thenIdle(2)
                .thenExecute(() -> check(helper, countIn(helper, Blocks.DIRT) == 2,
                        "ten UMU make no whole block, yet there are " + countIn(helper, Blocks.DIRT)))
                .thenSucceed();
    }

    /**
     * The ingredients of a body brought beside an anchor in one instant bind into a being (docs/vita-design.md and
     * docs/particulas-design.md): a quarter of flesh, a little more than a third of air and two fifths of water, with
     * the hundred of Vis, are a person's proportion, and a homunculus is born of them. The flesh lying loose is taken
     * with tenet, as the water and the air of a spell come from the body.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void ingredientsBroughtBesideAnAnchorBindIntoAHomunculus(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        mage.giveExperiencePoints(1000); // the hundred of Vis: ten points of experience for each UMU
        Vec3 heap = helper.absoluteVec(new Vec3(4.5D, 1.0D, 0.5D));
        ItemEntity flesh = new ItemEntity(helper.getLevel(), heap.x, heap.y, heap.z,
                new ItemStack(Items.ROTTEN_FLESH, 25));
        flesh.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(flesh);
        String there = at(helper, 2, 1, 3);
        // One instant, one place, as the lines of a page in one column.
        castOrFail(helper, mage, "firmo quantum 25 " + at(helper, 4, 1, 0) + " tenet " + there + " ubis vocant");
        castOrFail(helper, mage, "aura quantum 36 " + there + " ubis vocant");
        castOrFail(helper, mage, "aqua quantum 40 " + there + " ubis vocant");
        castOrFail(helper, mage, "vis quantum 100 " + there + " ubis vocant");
        helper.succeedWhen(() -> {
            List<Homunculus> born = helper.getLevel().getEntitiesOfClass(Homunculus.class,
                    new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D));
            check(helper, !born.isEmpty(), "no homunculus was born of the flesh, the air, the water and the anchor");
            check(helper, !flesh.isAlive(), "the flesh should have become its body");
        });
    }

    private static boolean named(Matter matter, String substance) {
        return matter.substance(Materials.get()).map(Substance::id).filter(substance::equals).isPresent();
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    /**
     * Condensation still holds (docs/condensacao-design.md): everything taken from the world in reach and released in
     * one instant appears as one block as dense as all of it, not as more loose soil.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void earthTakenAllAndReleasedAtOnceIsCondensed(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 3; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        castOrFail(helper, mage, "firmo " + at(helper, 2, 2, 4) + " tenet quantum chronos 0 " + at(helper, 2, 1, 1)
                + " ubis vocant");
        helper.succeedWhen(() -> {
            check(helper, countIn(helper, Blocks.DIRT) == 0, "the earth in reach should all be taken");
            // Twenty blocks of soil pressed into one are denser than any rock: a well of gravity (crying obsidian).
            int dense = countIn(helper, Blocks.STONE) + countIn(helper, Blocks.DEEPSLATE)
                    + countIn(helper, Blocks.OBSIDIAN) + countIn(helper, Blocks.CRYING_OBSIDIAN);
            check(helper, dense == 1, "one block as dense as all of it, were " + dense);
        });
    }

    // ------------------------------------------------------------------ the core (docs/particulas-design.md, stage 4)

    /** A floor of glass (made, so no matter to change) and a mage at its near corner. */
    private static ServerPlayer mageOnGlass(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        return mage;
    }

    /**
     * The marks of this run of the tests. Marks are the world's and are saved with it, and the test server keeps its
     * world from one run to the next, where the tests may stand somewhere else: a mark left by the last run could name
     * a block of another test now. Every run marks with names of its own.
     */
    private static final String RUN = Long.toString(System.currentTimeMillis(), 36);

    private static String mark(String name) {
        return name + RUN;
    }

    /** A block of the test, marked (marks are the world's: every test needs its own). */
    private static void markBlock(GameTestHelper helper, int x, int y, int z, String mark) {
        MarkHelper.applyMark(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)), mark);
    }

    private static ItemEntity loose(GameTestHelper helper, ItemStack stack, String mark) {
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(item);
        MarkHelper.applyMark(item, mark);
        return item;
    }

    private static List<ItemEntity> itemsIn(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6.0D));
    }

    private static int countOf(List<ItemEntity> items, net.minecraft.world.item.Item item) {
        return items.stream().filter(entity -> entity.isAlive() && entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /**
     * A block given a new core becomes what the core is, where it was, and keeps its mark: stone all earth is soil, and
     * the same mark then makes the soil all water, a source of water (soil is solid as nature has it, so the water is
     * liquid as nature has it).
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aBlockBecomesWhatItsCoreIsAndKeepsItsMark(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        BlockPos block = new BlockPos(2, 1, 2);
        helper.setBlock(block, Blocks.STONE);
        markBlock(helper, 2, 1, 2, mark("c4bloco"));
        helper.startSequence()
                .thenExecute(() -> castOrFail(helper, mage, "firmo quantum vertere " + mark("c4bloco")))
                .thenIdle(2)
                .thenExecute(() -> check(helper, helper.getBlockState(block).is(Blocks.DIRT),
                        "stone all earth should be soil, was " + helper.getBlockState(block)))
                .thenExecute(() -> castOrFail(helper, mage, "aqua quantum vertere " + mark("c4bloco")))
                .thenIdle(2)
                .thenExecute(() -> check(helper, helper.getBlockState(block).is(Blocks.WATER),
                        "the soil kept the mark, and all water it is water, was " + helper.getBlockState(block)))
                .thenSucceed();
    }

    /**
     * The lines of a column are weighed together: seventy of earth and thirty of air read in one instant are sand's
     * code. The same two an instant apart are not: the first leaves water and fire their share of what is left, and the
     * second takes its air from a mixture that is no longer stone.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void theLinesOfACodeInOneInstantMakeThatCode(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        BlockPos together = new BlockPos(1, 1, 3);
        BlockPos apart = new BlockPos(3, 1, 3);
        helper.setBlock(together, Blocks.STONE);
        helper.setBlock(apart, Blocks.STONE);
        markBlock(helper, 1, 1, 3, mark("c4junto"));
        markBlock(helper, 3, 1, 3, mark("c4passo"));
        castOrFail(helper, mage, "firmo quantum 70 vertere " + mark("c4junto"));
        castOrFail(helper, mage, "aura quantum 30 vertere " + mark("c4junto"));
        castOrFail(helper, mage, "firmo quantum 70 vertere " + mark("c4passo"));
        helper.runAfterDelay(2, () -> castOrFail(helper, mage, "aura quantum 30 vertere " + mark("c4passo")));
        helper.runAfterDelay(5, () -> {
            check(helper, helper.getBlockState(together).is(Blocks.SAND),
                    "seventy of earth and thirty of air together are sand, was " + helper.getBlockState(together));
            check(helper, !helper.getBlockState(apart).is(Blocks.SAND),
                    "an instant apart they should not be sand");
            check(helper, formlessIn(helper).size() == 1, "the one apart is a mixture with no name: "
                    + formlessIn(helper));
            helper.succeed();
        });
    }

    /**
     * What a thing becomes is in the state its particles make (user, 01/10/2026): stone made all water is water, not
     * ice, and so is lava made all water; lava given steam's code (nine of water to one of fire) rises as vapour, which
     * leaves nothing behind.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void theStateComesFromWhatAThingBecomes(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        BlockPos stone = new BlockPos(0, 1, 4);
        BlockPos lava = new BlockPos(4, 1, 4);
        BlockPos hot = new BlockPos(2, 1, 4);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(lava, Blocks.LAVA);
        helper.setBlock(hot, Blocks.LAVA);
        markBlock(helper, 0, 1, 4, mark("c4pedra"));
        markBlock(helper, 4, 1, 4, mark("c4lava"));
        markBlock(helper, 2, 1, 4, mark("c4vapor"));
        castOrFail(helper, mage, "aqua quantum vertere " + mark("c4pedra"));
        castOrFail(helper, mage, "aqua quantum vertere " + mark("c4lava"));
        castOrFail(helper, mage, "aqua quantum 90 vertere " + mark("c4vapor"));
        castOrFail(helper, mage, "igni quantum 10 vertere " + mark("c4vapor"));
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getBlockState(stone).is(Blocks.WATER),
                    "stone made water should be water, was " + helper.getBlockState(stone));
            check(helper, helper.getBlockState(lava).is(Blocks.WATER),
                    "lava made all water should be water, was " + helper.getBlockState(lava));
            check(helper, helper.getBlockState(hot).isAir(),
                    "lava in steam's code should rise as vapour, was " + helper.getBlockState(hot));
            helper.succeed();
        });
    }

    /**
     * Items stay items: sixteen rotten flesh (4096 particles) given bone's code (sixty of earth, twenty of water and
     * twenty of fire; the air goes to nothing) are three bones of 1365, which carry the mark, and the one particle left
     * leaves as light.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void fleshOnTheGroundGivenBonesCodeIsBones(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        ItemEntity flesh = loose(helper, new ItemStack(Items.ROTTEN_FLESH, 16), mark("c4carne"));
        castOrFail(helper, mage, "firmo quantum 60 vertere " + mark("c4carne"));
        castOrFail(helper, mage, "aqua quantum 20 vertere " + mark("c4carne"));
        castOrFail(helper, mage, "igni quantum 20 vertere " + mark("c4carne"));
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> items = itemsIn(helper);
            check(helper, !flesh.isAlive() && countOf(items, Items.ROTTEN_FLESH) == 0, "the flesh should be gone");
            check(helper, countOf(items, Items.BONE) == 3, "three bones, were " + countOf(items, Items.BONE));
            check(helper, items.stream().filter(item -> item.getItem().is(Items.BONE))
                            .allMatch(item -> MarkHelper.markForEntity(item).filter(mark("c4carne")::equals).isPresent()),
                    "the bones should carry the flesh's mark");
            helper.succeed();
        });
    }

    /**
     * Lines with no number share the hundred evenly (user, 01/10/2026): water and air written without one make stone
     * half of each, mist's code, and it rises as mist.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void linesWithoutANumberShareTheHundredEvenly(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        BlockPos block = new BlockPos(2, 1, 2);
        helper.setBlock(block, Blocks.STONE);
        markBlock(helper, 2, 1, 2, mark("c5metade"));
        castOrFail(helper, mage, "aqua vertere " + mark("c5metade"));
        castOrFail(helper, mage, "aura vertere " + mark("c5metade"));
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getBlockState(block).isAir(),
                    "half water and half air should rise as mist, was " + helper.getBlockState(block));
            helper.succeed();
        });
    }

    /** Parts that cannot make a hundred make no core (user, 01/10/2026): eighty of earth and eighty of water. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void partsPastAHundredChangeNothing(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        BlockPos block = new BlockPos(2, 1, 2);
        helper.setBlock(block, Blocks.STONE);
        markBlock(helper, 2, 1, 2, mark("c4demais"));
        castOrFail(helper, mage, "firmo quantum 80 vertere " + mark("c4demais"));
        castOrFail(helper, mage, "aqua quantum 80 vertere " + mark("c4demais"));
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getBlockState(block).is(Blocks.STONE), "a hundred and sixty parts should change nothing");
            helper.succeed();
        });
    }

    /**
     * A creature is the kind its core is nearest (docs/particulas-design.md, stage 5): a chicken given a cow's core (7 of
     * earth, 60 of water, 30 of air, 3 of fire) is a cow, with the chicken's life (its body, 4 points, 20 UMU, converted)
     * and its mark.
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aChickenGivenACowsCoreIsACowWithTheChickensLife(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        net.minecraft.world.entity.animal.Chicken chicken =
                helper.spawn(net.minecraft.world.entity.EntityType.CHICKEN, new BlockPos(2, 1, 3));
        MarkHelper.applyMark(chicken, mark("c5galinha"));
        castOrFail(helper, mage, "firmo quantum 7 vertere " + mark("c5galinha"));
        castOrFail(helper, mage, "aqua quantum 60 vertere " + mark("c5galinha"));
        castOrFail(helper, mage, "aura quantum 30 vertere " + mark("c5galinha"));
        castOrFail(helper, mage, "igni quantum 3 vertere " + mark("c5galinha"));
        helper.runAfterDelay(2, () -> {
            check(helper, !chicken.isAlive(), "the chicken's body should be gone");
            // The cow that carries the chicken's mark: a test beside this one may have cows of its own.
            List<net.minecraft.world.entity.animal.Cow> cows = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.animal.Cow.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6.0D),
                    cow -> MarkHelper.markForEntity(cow).filter(mark("c5galinha")::equals).isPresent());
            check(helper, cows.size() == 1, "one cow with the chicken's mark, were " + cows.size());
            net.minecraft.world.entity.animal.Cow cow = cows.get(0);
            check(helper, Math.abs(cow.getMaxHealth() - 4.0F) < 1.0E-4F && Math.abs(cow.getHealth() - 4.0F) < 1.0E-4F,
                    "the cow should have the chicken's life, 4, had " + cow.getHealth() + " of " + cow.getMaxHealth());
            check(helper, MarkHelper.markForEntity(cow).filter(mark("c5galinha")::equals).isPresent(),
                    "the cow should carry the chicken's mark");
            helper.succeed();
        });
    }

    /** A creature the table has no kind for has no core the spirit knows: an illusioner stays an illusioner. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aCreatureTheTableDoesNotKnowKeepsItsBody(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        net.minecraft.world.entity.monster.Illusioner illusioner =
                helper.spawn(net.minecraft.world.entity.EntityType.ILLUSIONER, new BlockPos(2, 1, 3));
        MarkHelper.applyMark(illusioner, mark("c5ilusionista"));
        castOrFail(helper, mage, "firmo quantum vertere " + mark("c5ilusionista"));
        helper.runAfterDelay(2, () -> {
            check(helper, illusioner.isAlive(), "the illusioner should be left as it was");
            helper.succeed();
        });
    }

    /**
     * A player is a being too (docs/particulas-design.md, stage 6): the mage given a cow's core has a cow's body, the
     * size of a cow, and keeps what they carry; given a person's core back, the body is theirs again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aMageGivenACowsCoreHasACowsBodyUntilTheirOwnCoreComesBack(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        mage.getInventory().add(new ItemStack(Items.DIAMOND));
        String self = mark("c6eu");
        MarkHelper.applyMark(mage, self);
        float person = mage.getBbHeight();
        helper.startSequence()
                .thenExecute(() -> {
                    castOrFail(helper, mage, "firmo quantum 7 vertere " + self);
                    castOrFail(helper, mage, "aqua quantum 60 vertere " + self);
                    castOrFail(helper, mage, "aura quantum 30 vertere " + self);
                    castOrFail(helper, mage, "igni quantum 3 vertere " + self);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper, com.elderlexicon.mod.spell.life.Forms.of(mage).map(kind -> kind.id())
                            .filter("cow"::equals).isPresent(), "the mage's body should be a cow's");
                    float cow = net.minecraft.world.entity.EntityType.COW.getDimensions().height;
                    check(helper, Math.abs(mage.getBbHeight() - cow) < 1.0E-4F,
                            "the mage should be a cow's size, was " + mage.getBbHeight());
                    double water = com.elderlexicon.mod.vita.VitaSystem.core(mage)
                            .get(com.elderlexicon.mod.vita.VitaElement.AQUA);
                    check(helper, Math.abs(water - 0.60D) < 1.0E-6D, "the Vita's core should be the cow's, water was "
                            + water);
                    check(helper, mage.isAlive() && mage.getInventory().contains(new ItemStack(Items.DIAMOND)),
                            "the mage keeps what they carry");
                })
                .thenExecute(() -> {
                    castOrFail(helper, mage, "firmo quantum 5 vertere " + self);
                    castOrFail(helper, mage, "aqua quantum 55 vertere " + self);
                    castOrFail(helper, mage, "aura quantum 38 vertere " + self);
                    castOrFail(helper, mage, "igni quantum 2 vertere " + self);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper, com.elderlexicon.mod.spell.life.Forms.of(mage).isEmpty(),
                            "a person's core is the mage's own body again");
                    check(helper, Math.abs(mage.getBbHeight() - person) < 1.0E-4F,
                            "the mage should be a person's size again, was " + mage.getBbHeight());
                })
                .thenSucceed();
    }

    /** What is made has no code (docs/particulas-design.md, "Forma"): a sword is left as it is. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aMadeThingHasNoCoreToChange(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        ItemEntity sword = loose(helper, new ItemStack(Items.IRON_SWORD), mark("c4espada"));
        castOrFail(helper, mage, "firmo quantum 50 vertere " + mark("c4espada"));
        helper.runAfterDelay(2, () -> {
            check(helper, sword.isAlive() && sword.getItem().is(Items.IRON_SWORD), "the sword should be left alone");
            helper.succeed();
        });
    }

    /**
     * A condensation handed to the force (docs/plano-rosa-dos-elementos.md, section 4): the vocant gathers all the earth
     * in reach before the hand and the iactare throws that very orb; it is released as one dense block where it strikes,
     * breaking the glass it hits. Before, the vocant made the block at the aim and the iactare threw something else.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condensedEarthIsThrownAsItsOrbAndReleasedWhereItStrikes(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
            }
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.GLASS);
            }
        }
        for (int z = 1; z <= 2; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(0, y, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(4, y, z), Blocks.DIRT);
            }
        }
        ServerPlayer mage = mage(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(2.5D, 1.0D, 0.5D));
        mage.moveTo(stand.x, stand.y, stand.z, facingWall(helper), 0.0F);
        int glass = countIn(helper, Blocks.GLASS);
        castOrFail(helper, mage, "firmo tenet quantum chronos 0 vocant iactare");
        boolean[] flew = {false};
        net.minecraft.world.phys.AABB room = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO))
                .inflate(8.0D);
        String[] last = {""};
        helper.onEachTick(() -> helper.getLevel()
                .getEntitiesOfClass(com.elderlexicon.mod.spell.function.ElementOrb.class, room)
                .stream().filter(com.elderlexicon.mod.spell.function.ElementOrb::flying).forEach(orb -> {
                    flew[0] = true;
                    last[0] = String.valueOf(helper.relativeVec(orb.position()));
                }));
        helper.succeedWhen(() -> {
            check(helper, countIn(helper, Blocks.DIRT) == 0, "the earth in reach should all be taken");
            // Whatever it gathered (the world around the room holds earth too), all of it is one dense block, found
            // wherever the blow left it: it breaks the floor it lands on, and falls.
            int dense = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-3, -6, -3)),
                    helper.absolutePos(new BlockPos(7, 6, 7)))) {
                BlockState state = helper.getLevel().getBlockState(pos);
                if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
                    dense++;
                }
            }
            check(helper, dense == 1, "one block as dense as all of it, were " + dense + "; the orb was last at "
                    + last[0]);
            check(helper, flew[0], "the condensation should have flown as its orb");
            check(helper, countIn(helper, Blocks.GLASS) < glass, "the blow should break the glass it struck");
        });
    }

    /**
     * The law of impact in the world: earth thrown hard breaks what it strikes and hurts the creature it hits; with
     * the configuration saying magic never breaks blocks, it only hurts.
     */
    @GameTest(template = EMPTY)
    public static void aBlowBreaksByItsEnergyAndTheConfigurationCanForbidIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        BlockPos first = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos second = helper.absolutePos(new BlockPos(3, 1, 2));
        com.elderlexicon.mod.magic.matter.Qualities earth =
                com.elderlexicon.mod.magic.matter.Qualities.of(com.elderlexicon.mod.vita.VitaElement.FIRMO);
        Vec3 fast = new Vec3(0.0D, 0.0D, 2.0D);
        com.elderlexicon.mod.spell.function.Impacts.strike(level, null, Vec3.atCenterOf(first), null, first, earth,
                10.0D, fast);
        com.elderlexicon.mod.Config.MagicBreaks was = com.elderlexicon.mod.Config.magicBreaksBlocks;
        com.elderlexicon.mod.Config.magicBreaksBlocks = com.elderlexicon.mod.Config.MagicBreaks.NEVER;
        try {
            com.elderlexicon.mod.spell.function.Impacts.strike(level, null, Vec3.atCenterOf(second), null, second,
                    earth, 10.0D, fast);
        } finally {
            com.elderlexicon.mod.Config.magicBreaksBlocks = was;
        }
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG,
                new BlockPos(2, 1, 0));
        float health = pig.getHealth();
        com.elderlexicon.mod.spell.function.Impacts.strike(level, null, pig.position(), pig, null, earth, 4.0D, fast);
        check(helper, level.getBlockState(first).isAir(), "ten UMU of earth thrown fast should break stone");
        check(helper, level.getBlockState(second).is(Blocks.STONE), "the configuration forbade breaking");
        check(helper, !pig.isAlive() || pig.getHealth() < health, "the blow should hurt the pig");
        helper.succeed();
    }

    /**
     * By itself, igni vocant brings what holding one flame for a moment takes (user, 03/10/2026): a flame shows where
     * it lands, burns for the moment, and goes out with nothing to burn (docs/particulas-design.md, stage 9).
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void igniVocantByItselfHoldsAFlameForAMoment(GameTestHelper helper) {
        ServerPlayer mage = mageOnGlass(helper);
        BlockPos spot = new BlockPos(2, 1, 2);
        castOrFail(helper, mage, "igni " + at(helper, 2, 1, 2) + " ubis vocant");
        boolean[] seen = {false};
        helper.onEachTick(() -> {
            for (int up = 0; up < 3; up++) {
                seen[0] |= helper.getBlockState(spot.above(up)).is(com.elderlexicon.mod.spell.matter.MatterBlocks.FLAME.get());
            }
        });
        helper.runAfterDelay(5, () -> check(helper, seen[0], "a flame should burn where the fire landed"));
        helper.runAfterDelay(com.elderlexicon.mod.spell.nature.NatureWorld.FLAME_TICKS + 5,
                () -> helper.succeedWhen(() -> {
                    for (int up = 0; up < 3; up++) {
                        check(helper, !helper.getBlockState(spot.above(up))
                                        .is(com.elderlexicon.mod.spell.matter.MatterBlocks.FLAME.get()),
                                "after its moment, with nothing to burn, the flame goes out");
                    }
                }));
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

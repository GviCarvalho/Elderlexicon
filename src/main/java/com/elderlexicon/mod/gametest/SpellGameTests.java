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
import java.util.Set;
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

    /** A wizard takes up the cause of a villager hurt near it (docs/mago-design.md, 1), as an iron golem would. */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "wizard_defends")
    public static void aWizardDefendsAHurtVillager(GameTestHelper helper) {
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 1.5D));
        villager.setNoAi(true);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        zombie.setNoAi(true);
        villager.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
        helper.succeedWhen(() -> {
            // Whoever hurt the villager last (a creature of another test may wander in and do it).
            if (wizard.getTarget() == null || wizard.getTarget() != zombie
                    && wizard.getTarget() != villager.getLastHurtByMob()) {
                helper.fail("the wizard does not fight the one who hurt the villager: " + wizard.getTarget()
                        + "; villager hurt by " + villager.getLastHurtByMob() + " at " + villager.getLastHurtByMobTimestamp()
                        + " (now " + villager.tickCount + ", wizard " + wizard.tickCount + ", alive " + wizard.isAlive()
                        + ", at " + wizard.blockPosition() + " in " + helper.getLevel().getBlockState(wizard.blockPosition())
                        + "), zombie alive " + zombie.isAlive());
            }
        });
    }

    /** A wizard hunts no one: one who hurt no one near it is let be, and has its wares (docs/mago-design.md, 1 and 2). */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "wizard_trades")
    public static void aWizardLetsBeAndTrades(GameTestHelper helper) {
        ServerPlayer visitor = mageBeforeAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(2.5D, 1.0D, 2.5D));
        if (wizard.getOffers().isEmpty()) {
            helper.fail("the wizard has nothing to offer");
        }
        if (wizard.getOffers().stream().noneMatch(offer -> offer.getResult().is(ElderLexicon.WAND.get()))) {
            helper.fail("the wizard offers no wand");
        }
        helper.runAtTickTime(60, () -> {
            // (Another test's sorcerer may hurt a villager near it: then the wizard rightly fights it.)
            if (wizard.getTarget() == visitor) {
                helper.fail("the wizard turned on the visitor, who did nothing");
            }
            helper.succeed();
        });
    }

    /** Whoever hurts a wizard is its foe. */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "wizard_fights")
    public static void aWizardFightsWhoeverHurtsIt(GameTestHelper helper) {
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        zombie.setNoAi(true);
        // Hurt a few ticks after it came (a hurt in its very first tick is not told apart from none).
        helper.runAtTickTime(5, () -> wizard.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F));
        helper.succeedWhen(() -> {
            if (wizard.getTarget() != zombie) {
                helper.fail("the wizard does not fight the one who hurt it: " + wizard.getTarget());
            }
        });
    }

    /** A long strip of floor, {@code length} blocks along z, for those who must walk or aim far. */
    private static void longFloor(GameTestHelper helper, int length) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < length; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
                for (int y = 1; y < 4; y++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * Takes away what a test on the long floor spawned: it stands beyond the test's bounds, where the test does not
     * clear it, and would wander into the tests after.
     */
    private static void leave(net.minecraft.world.entity.Entity... spawned) {
        for (net.minecraft.world.entity.Entity entity : spawned) {
            entity.discard();
        }
    }

    /** A sorcerer that knows {@code known} and hunts {@code prey} alone, far down the floor. */
    private static Sorcerer farFrom(GameTestHelper helper, Villager prey, String... known) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 0.5D));
        evoker.targetSelector.removeAllGoals(goal -> true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE, com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                Set.of(known));
        evoker.setTarget(prey);
        return evoker;
    }

    /**
     * A caster walks around fire, not through it (docs/feiticeiro-design.md, 3.14): a strip of fire burns across its way
     * to what it hunts, with a gap at one side; it goes by the gap, comes near enough to read, and never catches fire.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "fire_detour")
    public static void aCasterWalksAroundFire(GameTestHelper helper) {
        fireAcrossTheWay(helper, true);
    }

    /**
     * Fire across the whole of its way: it does not walk through, though it cannot come near (3.14). Fire it set at
     * what it hunts is fire like any other.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "fire_wall")
    public static void aCasterDoesNotWalkThroughFire(GameTestHelper helper) {
        fireAcrossTheWay(helper, false);
    }

    /**
     * A caster that finds itself in fire gets out first (docs/feiticeiro-design.md, 3.18): an elder set down in the
     * middle of a patch of everlasting fire, a villager to hunt twelve blocks off, is out of it within two seconds and
     * keeps out.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300, batch = "fire_escape")
    public static void anElderStepsOutOfFire(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 20; z++) {
                boolean patch = x >= 3 && x <= 5 && z >= 3 && z <= 5;
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                        patch ? Blocks.NETHERRACK.defaultBlockState() : Blocks.STONE.defaultBlockState());
                for (int y = 1; y < 4; y++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            y == 1 && patch ? Blocks.FIRE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(4.5D, 1.0D, 16.5D));
        prey.setNoAi(true);
        prey.setInvulnerable(true);
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(4.5D, 1.0D, 4.5D));
        evoker.targetSelector.removeAllGoals(goal -> true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.ELDER, com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                com.elderlexicon.mod.mind.Degree.ELDER.draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(),
                        helper.getLevel().getRandom()));
        evoker.setTarget(prey);
        Runnable putOut = () -> {
            leave(evoker, prey);
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)),
                            Blocks.AIR.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                            Blocks.STONE.defaultBlockState());
                }
            }
        };
        int[] ticks = new int[1];
        int[] outFor = new int[1];
        boolean[] over = new boolean[1];
        helper.onEachTick(() -> {
            if (over[0]) {
                return;
            }
            ticks[0]++;
            boolean in = evoker.casterMind().standingInFire();
            outFor[0] = in ? 0 : outFor[0] + 1;
            String failed = null;
            if (in && ticks[0] > 40) {
                failed = "still in the fire after two seconds, at " + evoker.blockPosition();
            } else if (outFor[0] < 60) {
                return;
            }
            over[0] = true;
            putOut.run();
            if (failed != null) {
                helper.fail(failed);
            } else {
                com.mojang.logging.LogUtils.getLogger().info("ESCAPE out of the fire after {} ticks", ticks[0] - 60);
                helper.succeed();
            }
        });
    }

    /**
     * A sorcerer that throws twenty blocks hunts a villager thirty off, with a strip of everlasting fire across the way:
     * with a gap at one side ({@code gap}), it goes by it and comes near; with none, it waits outside the fire. Either
     * way it never catches fire.
     */
    private static void fireAcrossTheWay(GameTestHelper helper, boolean gap) {
        // Nine wide, thirty-two long; a strip of everlasting fire (on netherrack) across it at twelve, but for the
        // last two blocks of one side when there is a gap.
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 32; z++) {
                boolean strip = z >= 11 && z <= 13 && (x < 7 || !gap);
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                        strip ? Blocks.NETHERRACK.defaultBlockState() : Blocks.STONE.defaultBlockState());
                for (int y = 1; y < 4; y++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            y == 1 && strip ? Blocks.FIRE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 30.5D));
        prey.setNoAi(true);
        prey.setInvulnerable(true);
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(3.5D, 1.0D, 2.5D));
        evoker.targetSelector.removeAllGoals(goal -> true);
        // It throws twenty blocks and cannot write a distance: it must walk past the fire to read.
        evoker.setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE, com.elderlexicon.mod.mind.Personality.CAUTIOUS,
                Set.of("igni", "iactare", "vocant"));
        evoker.setTarget(prey);
        // The fire is put out when the test ends, either way: everlasting, it would burn into the tests after.
        Runnable putOut = () -> {
            leave(evoker, prey);
            for (int x = 0; x < 9; x++) {
                for (int z = 11; z <= 13; z++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)),
                            Blocks.AIR.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                            Blocks.STONE.defaultBlockState());
                }
            }
        };
        int[] ticks = new int[1];
        boolean[] over = new boolean[1];
        helper.onEachTick(() -> {
            if (over[0]) {
                return;
            }
            ticks[0]++;
            String failed = null;
            if (evoker.isOnFire() || helper.getLevel().getBlockState(evoker.blockPosition()).is(Blocks.FIRE)) {
                failed = "it walked into the fire, at " + evoker.blockPosition();
            } else if (ticks[0] >= 590) {
                if (gap) {
                    failed = "it has not come near: " + evoker.distanceTo(prey) + " blocks, at "
                            + evoker.blockPosition();
                }
            } else if (!gap || evoker.distanceTo(prey) > com.elderlexicon.mod.mind.Reach.THROW - 1.0D) {
                return;
            }
            over[0] = true;
            putOut.run();
            if (failed != null) {
                helper.fail(failed);
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * What it throws gets twenty blocks (docs/feiticeiro-design.md, 3.7): one that cannot write a distance walks up to
     * a target farther than that, rather than throwing what would fall short.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "reach_walk")
    public static void aSorcererWalksUpToWhatItCannotReach(GameTestHelper helper) {
        longFloor(helper, 30);
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 27.5D));
        prey.setNoAi(true);
        Sorcerer evoker = farFrom(helper, prey, "igni", "iactare", "vocant");
        helper.succeedWhen(() -> {
            if (evoker.lastChoice().isPresent() && evoker.distanceTo(prey) > com.elderlexicon.mod.mind.Reach.THROW) {
                helper.fail("it read from " + evoker.distanceTo(prey) + " blocks: " + evoker.lastChoice().get().option().name());
            }
            if (evoker.distanceTo(prey) > com.elderlexicon.mod.mind.Reach.THROW - 1.0D) {
                helper.fail("it has not come near yet: " + evoker.distanceTo(prey));
            }
            leave(evoker, prey);
        });
    }

    /**
     * A place far off from every other test, where a fight is set (docs/feiticeiro-design.md, 3.20): a thousand blocks
     * aside of the test's own place, its chunks held loaded while it lasts. Creatures of other tests (a wizard that
     * defends what is hurt near it, a husk left over) do not wander into it, and what fights there does not wander out:
     * whatever stands there is swept away when it opens and when it closes, and what was built is torn down.
     */
    private static final class Isle {

        private static final int AWAY = 1000;
        private static final int MARGIN = 12;

        final GameTestHelper helper;
        final BlockPos origin;
        private final int width;
        private final int length;
        private final List<net.minecraft.world.level.ChunkPos> held = new java.util.ArrayList<>();

        /** An isle {@code width} by {@code length}, its ground at the test's own height. */
        Isle(GameTestHelper helper, int width, int length) {
            this.helper = helper;
            this.width = width;
            this.length = length;
            origin = helper.absolutePos(BlockPos.ZERO).offset(AWAY, 0, 0);
            ServerLevel level = helper.getLevel();
            for (int cx = (origin.getX() - MARGIN) >> 4; cx <= (origin.getX() + width + MARGIN) >> 4; cx++) {
                for (int cz = (origin.getZ() - MARGIN) >> 4; cz <= (origin.getZ() + length + MARGIN) >> 4; cz++) {
                    level.setChunkForced(cx, cz, true);
                    held.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                }
            }
            sweep();
        }

        BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }

        private void set(int x, int y, int z, BlockState state) {
            helper.getLevel().setBlockAndUpdate(at(x, y, z), state);
        }

        /** {@code type}, set down at {@code where} on the isle, kept (no player is near to keep it from despawning). */
        <T extends net.minecraft.world.entity.Entity> T spawn(EntityType<T> type, Vec3 where) {
            T entity = type.create(helper.getLevel());
            entity.moveTo(origin.getX() + where.x, origin.getY() + where.y, origin.getZ() + where.z, 0.0F, 0.0F);
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }
            helper.getLevel().addFreshEntity(entity);
            return entity;
        }

        /** Stone ground over the whole isle, open air above it. */
        Isle floor() {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < length; z++) {
                    set(x, 0, z, Blocks.STONE.defaultBlockState());
                    for (int y = 1; y < 5; y++) {
                        set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            return this;
        }

        /** Walls three high around the isle, its inside from 1 to its side less one; and a roof, if {@code roofed}. */
        Isle walled(boolean roofed) {
            floor();
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < length; z++) {
                    boolean wall = x == 0 || z == 0 || x == width - 1 || z == length - 1;
                    for (int y = 1; y < 5; y++) {
                        if (y < 4 ? wall : roofed) {
                            set(x, y, z, Blocks.STONE.defaultBlockState());
                        }
                    }
                }
            }
            return this;
        }

        private AABB box() {
            return new AABB(at(-MARGIN, -4, -MARGIN), at(width + MARGIN, 12, length + MARGIN));
        }

        /** Every creature, arrow and thing dropped on the isle, taken away (players never). */
        private void sweep() {
            helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box(),
                            entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                    .forEach(net.minecraft.world.entity.Entity::discard);
        }

        /** It ends: swept, what was built torn down to the ground, its chunks let go. */
        void close() {
            sweep();
            for (int x = -1; x <= width; x++) {
                for (int z = -1; z <= length; z++) {
                    for (int y = 1; y < 6; y++) {
                        set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            held.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
        }
    }

    /**
     * A master does not open its trance with a husk at hand (docs/feiticeiro-design.md, 3.9): it backs away until the husk
     * could not strike before the reading is out, reads from there, and is not beaten by a creature that only walks up.
     * On an isle, alone.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "keep_distance")
    public static void aMasterKeepsAHuskOff(GameTestHelper helper) {
        Isle isle = new Isle(helper, 5, 40).floor();
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        Sorcerer evoker = isle.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 10.5D));
        evoker.targetSelector.removeAllGoals(goal -> true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.MASTER, com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                Set.of("firmo", "igni", "iactare", "vocant"));
        net.minecraft.world.entity.monster.Husk husk = isle.spawn(EntityType.HUSK, new Vec3(2.5D, 1.0D, 7.5D));
        husk.targetSelector.removeAllGoals(goal -> true);
        husk.setTarget(evoker);
        evoker.setTarget(husk);
        float before = husk.getHealth();
        int[] ticks = new int[1];
        boolean[] over = new boolean[1];
        helper.onEachTick(() -> {
            if (over[0]) {
                return;
            }
            ticks[0]++;
            String failed = null;
            if (evoker.getHealth() < evoker.getMaxHealth() * 0.5F) {
                failed = "the husk beat it: " + evoker.getHealth() + " left";
            } else if (ticks[0] >= 590) {
                failed = "the husk was not hurt (" + evoker.distanceTo(husk) + " blocks apart)";
            } else if (!(husk.getHealth() < before)) {
                return;
            }
            over[0] = true;
            isle.close();
            if (failed != null) {
                helper.fail(failed);
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * An elder fights a husk by the doctrine the arena taught it (docs/feiticeiro-design.md, 3.10): it asks it what to do,
     * and is not beaten by a creature that only walks up.
     */
    @GameTest(template = EMPTY, timeoutTicks = 900, batch = "doctrine")
    public static void anElderFollowsItsDoctrine(GameTestHelper helper) {
        Isle isle = new Isle(helper, 5, 40).floor();
        net.minecraft.world.entity.monster.Husk husk = isle.spawn(EntityType.HUSK, new Vec3(2.5D, 1.0D, 8.5D));
        anElderFights(isle, husk, new Vec3(2.5D, 1.0D, 14.5D), 900);
    }

    /** An elder fights a spider by its doctrine: one that leaps, and whose blow reaches farther (3.11). */
    @GameTest(template = EMPTY, timeoutTicks = 900, batch = "doctrine_spider")
    public static void anElderFightsASpiderByItsDoctrine(GameTestHelper helper) {
        // A field walled and roofed at forty: a spider knocked back neither falls off a narrow floor nor climbs out.
        Isle isle = new Isle(helper, 42, 42).walled(true);
        net.minecraft.world.entity.monster.Spider spider = isle.spawn(EntityType.SPIDER, new Vec3(20.5D, 1.0D, 14.5D));
        // A spider in daylight loses its taste for a fight now and then: this one keeps it.
        spider.goalSelector.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal);
        spider.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(spider, 1.0D, true));
        anElderFights(isle, spider, new Vec3(20.5D, 1.0D, 22.5D), 900);
    }

    /** An elder fights a skeleton by its doctrine: it reads between its arrows (3.11). */
    @GameTest(template = EMPTY, timeoutTicks = 1200, batch = "doctrine_skeleton")
    public static void anElderFightsASkeletonByItsDoctrine(GameTestHelper helper) {
        Isle isle = new Isle(helper, 5, 40).floor();
        net.minecraft.world.entity.monster.Skeleton skeleton = isle.spawn(EntityType.SKELETON,
                new Vec3(2.5D, 1.0D, 2.5D));
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        anElderFights(isle, skeleton, new Vec3(2.5D, 1.0D, 14.5D), 1200);
    }

    /**
     * An elder fights a husk shut in a hall sixteen blocks a side (docs/feiticeiro-design.md, 3.12): it backs away the
     * way with room, not into the wall behind it. Not required yet: no doctrine wins it in the world (3.12), and this
     * says how far from it they are. (In a hall of ten, the arena gives it one fight in three: a caster with two
     * seconds of trance does not belong there with a zombie.)
     */
    @GameTest(template = EMPTY, timeoutTicks = 900, batch = "doctrine_hall", required = false)
    public static void anElderFightsAHuskInAHall(GameTestHelper helper) {
        Isle isle = new Isle(helper, 18, 18).walled(false);
        net.minecraft.world.entity.monster.Husk husk = isle.spawn(EntityType.HUSK, new Vec3(8.5D, 1.0D, 5.5D));
        anElderFights(isle, husk, new Vec3(8.5D, 1.0D, 11.5D), 900);
    }

    /**
     * An aggressive elder sorcerer and {@code foe} hunt each other alone, on an isle: the elder asks its doctrine, and
     * is not beaten (its life does not fall below a quarter). The fight ends here, whoever wins, a little before
     * {@code timeout}, and the isle is closed, either way.
     */
    private static void anElderFights(Isle isle, net.minecraft.world.entity.Mob foe, Vec3 at, int timeout) {
        anElderFights(isle, foe, at, timeout, com.elderlexicon.mod.mind.Personality.AGGRESSIVE, 0.25F);
    }

    /**
     * As above, the elder of {@code temper}; beaten when its life falls below {@code beaten} of the whole (in a duel,
     * where both bleed, only dead is beaten).
     */
    private static void anElderFights(Isle isle, net.minecraft.world.entity.Mob foe, Vec3 at, int timeout,
                                      com.elderlexicon.mod.mind.Personality temper, float beaten) {
        GameTestHelper helper = isle.helper;
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        Sorcerer evoker = isle.spawn(ElderLexicon.SORCERER.get(), at);
        evoker.targetSelector.removeAllGoals(goal -> true);
        evoker.setMind(com.elderlexicon.mod.mind.Degree.ELDER, temper,
                com.elderlexicon.mod.mind.Degree.ELDER.draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(),
                        helper.getLevel().getRandom()));
        foe.targetSelector.removeAllGoals(goal -> true);
        foe.setTarget(evoker);
        evoker.setTarget(foe);
        String name = foe.getType().getDescriptionId();
        boolean[] followed = new boolean[1];
        int[] ticks = new int[1];
        boolean[] over = new boolean[1];
        boolean[] burning = new boolean[1];
        helper.onEachTick(() -> {
            if (over[0]) {
                return;
            }
            if (evoker.isOnFire() && !burning[0]) {
                // What set it burning: where it stands, the flames near, what it reads, what last hurt it.
                BlockPos feet = evoker.blockPosition();
                long flames = BlockPos.betweenClosedStream(feet.offset(-3, -1, -3), feet.offset(3, 2, 3))
                        .filter(pos -> helper.getLevel().getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE))
                        .count();
                net.minecraft.world.damagesource.DamageSource last = evoker.getLastDamageSource();
                com.mojang.logging.LogUtils.getLogger().info(
                        "BURNING {} t{}: at {} in {}, {} flames within 3, foe {} blocks off and {}, last hurt by {}; "
                                + "read last: {}; {}",
                        name, ticks[0], feet, helper.getLevel().getBlockState(feet), flames, evoker.distanceTo(foe),
                        foe.isOnFire() ? "burning" : "not burning", last == null ? "nothing" : last.getMsgId()
                                + (last.getEntity() == null ? "" : " of " + last.getEntity().getType().getDescriptionId()),
                        evoker.casterMind().lastChoice().map(choice -> "\"" + choice.option().name() + "\" for "
                                + choice.intent() + " looking " + choice.aim()).orElse("nothing"),
                        evoker.casterMind().describeFight());
            }
            burning[0] = evoker.isOnFire();
            followed[0] |= evoker.casterMind().stance().isPresent();
            if (++ticks[0] % 20 == 0 && evoker.isAlive()) {
                com.mojang.logging.LogUtils.getLogger().info("FIGHT {} t{} life {}: {}", name, ticks[0],
                        evoker.getHealth(), evoker.casterMind().describeFight());
            }
            String failed = null;
            if (!evoker.isAlive() || evoker.getHealth() < evoker.getMaxHealth() * beaten) {
                failed = "the " + name + " beat it: " + evoker.getHealth() + " left";
            } else if (!foe.isAlive() && !followed[0]) {
                failed = "it never asked its doctrine";
            } else if (ticks[0] >= timeout - 5 && foe.isAlive()) {
                failed = "the " + name + " lives: " + foe.getHealth() + " (" + evoker.distanceTo(foe) + " blocks apart)";
            }
            if (failed == null && foe.isAlive()) {
                return;
            }
            over[0] = true;
            float kept = evoker.getHealth();
            isle.close();
            if (failed != null) {
                helper.fail(failed);
            } else {
                com.mojang.logging.LogUtils.getLogger().info("DOCTRINE {} beaten, the elder kept {} of {}", name,
                        kept, evoker.getMaxHealth());
                helper.succeed();
            }
        });
    }

    /**
     * What the elder's sentences really do to a husk (docs/feiticeiro-design.md, 3.13): each is read at a husk standing
     * still, and what it lost is written to the log next to what the mind foresaw, so the arena can be set by it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 6000, batch = "damage_husk")
    public static void whatTheElderDoesToAHusk(GameTestHelper helper) {
        measureDamage(helper, EntityType.HUSK, "zombie");
    }

    /** As {@link #whatTheElderDoesToAHusk}, at a spider. */
    @GameTest(template = EMPTY, timeoutTicks = 6000, batch = "damage_spider")
    public static void whatTheElderDoesToASpider(GameTestHelper helper) {
        measureDamage(helper, EntityType.SPIDER, "spider");
    }

    /** As {@link #whatTheElderDoesToAHusk}, at another caster: a sorcerer with no mind of its own (3.17). */
    @GameTest(template = EMPTY, timeoutTicks = 6000, batch = "damage_caster")
    public static void whatTheElderDoesToACaster(GameTestHelper helper) {
        measureDamage(helper, ElderLexicon.SORCERER.get(), "caster");
    }

    /**
     * Whether the environment asks to measure {@code what} ({@code ELDERLEXICON_CALIBRATE}: 1 for everything, or the
     * names, by commas: zombie, spider, skeleton, caster, arrows).
     */
    private static boolean calibrating(String what) {
        String asked = System.getenv("ELDERLEXICON_CALIBRATE");
        return asked != null && (asked.equals("1") || List.of(asked.split(",")).contains(what));
    }

    /** As {@link #whatTheElderDoesToAHusk}, at a skeleton (with a helmet: the sun burns it). */
    @GameTest(template = EMPTY, timeoutTicks = 6000, batch = "damage_skeleton")
    public static void whatTheElderDoesToASkeleton(GameTestHelper helper) {
        measureDamage(helper, EntityType.SKELETON, "skeleton");
    }

    /**
     * Which of the fire it reads sets the caster itself burning, and from how far (docs/feiticeiro-design.md, 3.14):
     * each sentence read at a husk six, twelve and twenty blocks off, by a fresh sorcerer; logged ("SELFBURN ...").
     * The flames it leaves (blocks of light) linger where the tests after are set, and spoil them: it runs only when
     * the environment asks for it ({@code ELDERLEXICON_PROBE}), not even with the calibration.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1400, batch = "self_burn")
    public static void whatFireSetsTheCasterBurning(GameTestHelper helper) {
        if (System.getenv("ELDERLEXICON_PROBE") == null) {
            helper.succeed();
            return;
        }
        longFloor(helper, 24);
        List<String> sentences = List.of("firmo quantum -10 vocant", "aqua quantum -10 vocant",
                "aura quantum -10 vocant", "igni quantum -10 vocant", "firmo quantum -10 vocant quantum 20 iactare");
        double[] distances = {6.0D, 12.0D};
        int at = 1;
        for (String sentence : sentences) {
            for (double distance : distances) {
                int start = at;
                Object[] pair = new Object[2];
                helper.runAfterDelay(start, () -> {
                    for (int x = 0; x < 5; x++) {
                        for (int z = 0; z < 24; z++) {
                            for (int y = 1; y < 4; y++) {
                                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                                        Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    Sorcerer caster = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 1.5D));
                    caster.setNoAi(true);
                    net.minecraft.world.entity.monster.Husk husk = helper.spawn(EntityType.HUSK,
                            new Vec3(2.5D, 1.0D, 1.5D + distance));
                    husk.setNoAi(true);
                    pair[0] = caster;
                    pair[1] = husk;
                    aimFrom(caster, husk);
                    new SpellCastingService().castBlock(caster, List.of(new SpellCastingService.TimedSpell(
                            List.of(sentence.split(" ")), 0)), later -> {
                    });
                });
                boolean[] burned = new boolean[1];
                for (int tick = 1; tick < 60; tick++) {
                    helper.runAfterDelay(start + tick, () -> {
                        if (pair[0] instanceof Sorcerer caster && pair[1] instanceof net.minecraft.world.entity.LivingEntity husk) {
                            burned[0] |= caster.isOnFire();
                            if (husk.isAlive()) {
                                aimFrom(caster, husk);
                            }
                        }
                    });
                }
                helper.runAfterDelay(start + 70, () -> {
                    Sorcerer caster = (Sorcerer) pair[0];
                    net.minecraft.world.entity.LivingEntity husk = (net.minecraft.world.entity.LivingEntity) pair[1];
                    com.mojang.logging.LogUtils.getLogger().info(
                            "SELFBURN sentence={} distance={} casterBurned={} casterLife={} huskLife={} lastHurt={}",
                            sentence, distance, burned[0], caster.getHealth(), husk.isAlive() ? husk.getHealth() : 0.0F,
                            caster.getLastDamageSource() == null ? "nothing" : caster.getLastDamageSource().getMsgId());
                    leave(caster, husk);
                });
                at += 80;
            }
        }
        helper.runAfterDelay(at + 5, helper::succeed);
    }

    /** The ways a caster moves while an arrow comes, as the arena knows them: standing, backing off, walking up, aside. */
    private static final String[] ARROW_MOTIONS = {"still", "away", "toward", "side"};
    /** How far the skeleton shoots from, how many arrows at each, how fast a caster walks (a sorcerer backing off). */
    private static final double[] ARROW_DISTANCES = {5.0D, 10.0D, 15.0D};
    private static final int ARROWS_EACH = 24;
    private static final double WALK = 0.345D;
    /** How long after an arrow is loosed it is looked whether it struck. */
    private static final int ARROW_LANDS = 25;

    /**
     * How often a skeleton's arrow strikes a caster standing, backing off, walking up or walking aside, five, ten and
     * fifteen blocks off (docs/feiticeiro-design.md, 3.15): four lanes at once, a skeleton that cannot walk at the head
     * of each, a sorcerer with no mind of its own moved by hand at a sorcerer's pace from the moment the bow is drawn to
     * when the arrow lands, placed so it stands at the distance when the arrow is loosed. Struck is hurt; it is healed
     * between arrows. Written to {@code arena-calibration/arrows-skeleton.json}, for the arena (src/arena/calibration).
     * It takes minutes: it runs only when the environment asks for it ({@code ELDERLEXICON_CALIBRATE}).
     */
    @GameTest(template = EMPTY, timeoutTicks = 7000, batch = "arrows")
    public static void howOftenAnArrowStrikes(GameTestHelper helper) {
        if (!calibrating("arrows")) {
            helper.succeed();
            return;
        }
        int lanes = ARROW_MOTIONS.length;
        int width = 16;
        int length = 32;
        for (int x = 0; x < lanes * width; x++) {
            for (int z = 0; z < length; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                        Blocks.STONE.defaultBlockState());
                for (int y = 1; y < 5; y++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        Runnable tearDown = () -> helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(lanes * width, 6, length).inflate(2.0D),
                entity -> entity instanceof net.minecraft.world.entity.projectile.AbstractArrow
                        || entity instanceof net.minecraft.world.entity.monster.Skeleton
                        || entity instanceof Sorcerer).forEach(net.minecraft.world.entity.Entity::discard);
        com.google.gson.JsonArray shotsTaken = new com.google.gson.JsonArray();
        class Lane {
            final String motion;
            final double x;
            final net.minecraft.world.entity.monster.Skeleton skeleton;
            final Sorcerer target;
            int stage;
            int shots;
            final int[][] tally = new int[ARROW_DISTANCES.length][2];
            boolean drawing;
            int moving;
            int landsIn = -1;
            float before;
            double vx;
            double vz;
            int sideSign = 1;
            double loosedAt;

            Lane(int index) {
                motion = ARROW_MOTIONS[index];
                x = index * width + width / 2.0D;
                skeleton = helper.spawn(EntityType.SKELETON, new Vec3(x, 1.0D, 1.5D));
                skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                // The sun wears its helmet through in a few minutes, and then burns it: it does not burn.
                skeleton.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 1000000, 0, false, false));
                skeleton.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                        .setBaseValue(0.0D);
                skeleton.targetSelector.removeAllGoals(goal -> true);
                target = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(x, 1.0D, 1.5D + ARROW_DISTANCES[0]));
                target.setNoAi(true);
                skeleton.setTarget(target);
            }

            /** Whether it measured all it had to, or cannot go on (its skeleton or its target is gone). */
            boolean done() {
                return stage >= ARROW_DISTANCES.length || !skeleton.isAlive() || !target.isAlive();
            }

            /** Where it rests between arrows, and where it starts when the bow is drawn, so it is at the distance then. */
            void rest() {
                place(x, 1.5D + ARROW_DISTANCES[stage]);
            }

            void place(double px, double pz) {
                Vec3 at = helper.absoluteVec(new Vec3(px, 1.0D, pz));
                target.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
                target.setDeltaMovement(Vec3.ZERO);
            }

            void tick() {
                if (done() || !skeleton.isAlive() || !target.isAlive()) {
                    return;
                }
                boolean now = skeleton.isUsingItem();
                double distance = ARROW_DISTANCES[stage];
                double lead = WALK * 20.0D;
                if (now && !drawing) {
                    // The bow is drawn: it starts where walking brings it to the distance as the arrow is loosed.
                    before = target.getHealth();
                    vx = 0.0D;
                    vz = 0.0D;
                    double startX = x;
                    double startZ = 1.5D + distance;
                    switch (motion) {
                        case "away" -> {
                            // Never behind the skeleton: near, it is loosed a little farther than asked.
                            startZ = Math.max(3.5D, startZ - lead);
                            vz = WALK;
                        }
                        case "toward" -> {
                            startZ += lead;
                            vz = -WALK;
                        }
                        case "side" -> {
                            sideSign = -sideSign;
                            startX -= sideSign * lead;
                            vx = sideSign * WALK;
                        }
                        default -> {
                        }
                    }
                    place(startX, startZ);
                    moving = 20 + ARROW_LANDS;
                }
                if (!now && drawing) {
                    landsIn = ARROW_LANDS;
                    loosedAt = target.distanceTo(skeleton);
                }
                drawing = now;
                if (moving > 0) {
                    moving--;
                    Vec3 here = helper.relativeVec(target.position());
                    place(here.x + vx, here.z + vz);
                }
                if (landsIn > 0 && --landsIn == 0) {
                    boolean struck = target.getHealth() < before;
                    tally[stage][0]++;
                    tally[stage][1] += struck ? 1 : 0;
                    com.google.gson.JsonObject shot = new com.google.gson.JsonObject();
                    shot.addProperty("motion", motion);
                    shot.addProperty("distance", Math.round(loosedAt * 100.0D) / 100.0D);
                    shot.addProperty("struck", struck);
                    shotsTaken.add(shot);
                    target.setHealth(target.getMaxHealth());
                    target.clearFire();
                    if (++shots >= ARROWS_EACH) {
                        shots = 0;
                        stage++;
                    }
                    if (!done()) {
                        rest();
                    }
                }
            }
        }
        List<Lane> all = new java.util.ArrayList<>();
        for (int i = 0; i < lanes; i++) {
            all.add(new Lane(i));
        }
        boolean[] over = new boolean[1];
        int[] ticks = new int[1];
        helper.onEachTick(() -> {
            if (over[0]) {
                return;
            }
            all.forEach(Lane::tick);
            ticks[0]++;
            if (ticks[0] % 500 == 0) {
                for (Lane lane : all) {
                    com.mojang.logging.LogUtils.getLogger().info("ARROWS progress t{} {}: stage {} shot {}, skeleton {}, "
                                    + "target {}, drawing {}, sees {}", ticks[0], lane.motion, lane.stage, lane.shots,
                            lane.skeleton.isAlive() ? "alive" : "dead", lane.target.isAlive() ? "alive" : "dead",
                            lane.skeleton.isUsingItem(), lane.skeleton.hasLineOfSight(lane.target));
                }
            }
            // Near the end of its time it writes what it has, and takes it all away, done or not: skeletons left
            // shooting would spoil the tests after.
            boolean outOfTime = ticks[0] >= 6900;
            if (!outOfTime && !all.stream().allMatch(Lane::done)) {
                return;
            }
            over[0] = true;
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("foe", "skeleton");
            root.addProperty("walk", WALK);
            root.add("distances", new com.google.gson.Gson().toJsonTree(ARROW_DISTANCES));
            com.google.gson.JsonObject motions = new com.google.gson.JsonObject();
            for (Lane lane : all) {
                com.google.gson.JsonArray shares = new com.google.gson.JsonArray();
                StringBuilder line = new StringBuilder();
                for (int stage = 0; stage < ARROW_DISTANCES.length; stage++) {
                    int[] counted = lane.tally[stage];
                    shares.add(counted[0] == 0 ? 0.0D : counted[1] / (double) counted[0]);
                    line.append(String.format(java.util.Locale.ROOT, " %.0f blocks: %d of %d;", ARROW_DISTANCES[stage],
                            counted[1], counted[0]));
                }
                motions.add(lane.motion, shares);
                if (lane.stage < ARROW_DISTANCES.length) {
                    line.append(" cut short: its ").append(lane.skeleton.isAlive() ? "target" : "skeleton")
                            .append(" died");
                }
                com.mojang.logging.LogUtils.getLogger().info("ARROWS {}:{}", lane.motion, line);
            }
            root.add("hit", motions);
            root.add("shots", shotsTaken);
            root.addProperty("complete", !outOfTime);
            tearDown.run();
            try {
                java.nio.file.Path file = java.nio.file.Path.of("arena-calibration", "arrows-skeleton.json");
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                        .toJson(root));
                if (outOfTime) {
                    helper.fail("out of time: what was measured is written, but not all of it");
                } else {
                    helper.succeed();
                }
            } catch (java.io.IOException exception) {
                helper.fail("could not write what was measured: " + exception);
            }
        });
    }

    /** How long one reading is watched: its throw runs two seconds, and then what it set burning burns a while. */
    private static final int DAMAGE_TICKS = 100;
    /** How many times each sentence is read: what is kept is the middle one. */
    private static final int DAMAGE_TIMES = 3;

    /**
     * Reads, one after another, the sentences the arena's elder reads (the best to strike and to draw, near and far,
     * whole, wounded and desperate, full of Vis and short of it) and the plain throws of each energy, each by a fresh
     * sorcerer at a fresh {@code kind} standing still six blocks off (how far made no difference); logs what each did
     * ("DAMAGE ..."), what the arena foresaw (a person's body) and what the mind foresees in the world (this body's
     * Vita), and writes it all to {@code arena-calibration/damage-<foe>.json}, for the arena (src/arena/calibration).
     * Each is read {@link #DAMAGE_TIMES} times: what is kept is the middle one. It takes minutes: it runs only when the
     * environment asks for it ({@code ELDERLEXICON_CALIBRATE}), and passes at once otherwise.
     */
    private static <T extends net.minecraft.world.entity.Mob> void measureDamage(GameTestHelper helper,
            EntityType<T> kind, String foe) {
        if (!calibrating(foe)) {
            helper.succeed();
            return;
        }
        longFloor(helper, 24);
        com.elderlexicon.mod.mind.PhraseCatalog catalog = com.elderlexicon.mod.mind.PhraseCatalog.get();
        Set<String> known = com.elderlexicon.mod.mind.Degree.learnable(com.elderlexicon.mod.magic.lexicon.Lexicons.get());
        List<com.elderlexicon.mod.mind.Option> all = com.elderlexicon.mod.mind.Mind.worthWeighing(
                catalog.readableBy(known).stream().map(com.elderlexicon.mod.mind.Option::of).toList());
        java.util.Map<String, com.elderlexicon.mod.mind.Option> read = new java.util.LinkedHashMap<>();
        for (double distance : new double[]{3.0D, 6.0D, 10.0D, 15.0D}) {
            for (double health : new double[]{1.0D, 0.6D, 0.4D}) {
              for (double vis : new double[]{100.0D, 60.0D, 30.0D}) {
                for (com.elderlexicon.mod.mind.Intent intent : new com.elderlexicon.mod.mind.Intent[]{
                        com.elderlexicon.mod.mind.Intent.STRIKE, com.elderlexicon.mod.mind.Intent.DRAW}) {
                    com.elderlexicon.mod.mind.Mind.Choice best = null;
                    for (com.elderlexicon.mod.mind.Option option : all) {
                        java.util.Optional<com.elderlexicon.mod.mind.Mind.Choice> choice =
                                com.elderlexicon.mod.mind.Mind.evaluate(arenaScene(distance, health, vis),
                                        com.elderlexicon.mod.mind.Personality.AGGRESSIVE, option,
                                        com.elderlexicon.mod.mind.Mind.person(1.0D - health));
                        if (choice.isPresent() && choice.get().intent() == intent
                                && (best == null || choice.get().score() > best.score())) {
                            best = choice.get();
                        }
                    }
                    if (best != null) {
                        read.putIfAbsent(best.option().name(), best.option());
                    }
                }
              }
            }
        }
        for (String plain : List.of("firmo iactare", "igni iactare", "aqua iactare", "aura iactare")) {
            com.elderlexicon.mod.mind.PhraseCatalog.imagine(List.of(plain.split(" ")))
                    .map(com.elderlexicon.mod.mind.Option::of).ifPresent(option -> read.putIfAbsent(plain, option));
        }
        List<Object[]> trials = new java.util.ArrayList<>();
        for (int time = 0; time < DAMAGE_TIMES; time++) {
            for (com.elderlexicon.mod.mind.Option option : read.values()) {
                trials.add(new Object[]{option, 6.0D});
            }
        }
        com.google.gson.JsonArray found = new com.google.gson.JsonArray();
        float[] whole = new float[1];
        for (int i = 0; i < trials.size(); i++) {
            com.elderlexicon.mod.mind.Option option = (com.elderlexicon.mod.mind.Option) trials.get(i)[0];
            double distance = (double) trials.get(i)[1];
            int at = 1 + i * (DAMAGE_TICKS + 10);
            Object[] pair = new Object[2];
            float[] before = new float[1];
            helper.runAfterDelay(at, () -> {
                // A clean lane: what the last reading left (fire, earth) is cleared.
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 24; z++) {
                        for (int y = 1; y < 5; y++) {
                            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                                    Blocks.AIR.defaultBlockState());
                        }
                        helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                                Blocks.STONE.defaultBlockState());
                    }
                }
                Sorcerer caster = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 1.5D));
                caster.setNoAi(true);
                T target = helper.spawn(kind, new Vec3(2.5D, 1.0D, 1.5D + distance));
                target.setNoAi(true);
                if (kind == EntityType.SKELETON) {
                    target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                            new ItemStack(Items.LEATHER_HELMET));
                }
                pair[0] = caster;
                pair[1] = target;
                before[0] = target.getHealth();
                whole[0] = target.getMaxHealth();
                aimFrom(caster, target);
                double live = com.elderlexicon.mod.mind.Mind.evaluate(new com.elderlexicon.mod.mind.Mind.Scene(
                                caster, target, distance, 1.0F, 100.0D), com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                        option).map(com.elderlexicon.mod.mind.Mind.Choice::harm).orElse(0.0D);
                // What the arena foresees of it (desperate, so whatever it costs is weighed).
                java.util.Optional<com.elderlexicon.mod.mind.Mind.Choice> foreseen =
                        com.elderlexicon.mod.mind.Mind.evaluate(arenaScene(distance, 0.3D, 100.0D),
                                com.elderlexicon.mod.mind.Personality.AGGRESSIVE, option,
                                com.elderlexicon.mod.mind.Mind.person(0.0D));
                pair[0] = new Object[]{caster, live, foreseen.map(com.elderlexicon.mod.mind.Mind.Choice::harm)
                        .orElse(0.0D), foreseen.map(choice -> choice.intent().name()).orElse(""),
                        Casters.vis(caster), caster.getHealth()};
                new SpellCastingService().castBlock(caster, option.lines().stream()
                        .map(line -> new SpellCastingService.TimedSpell(line.words(), line.delaySteps())).toList(),
                        later -> {
                        });
            });
            for (int tick = 1; tick < 60; tick++) {
                helper.runAfterDelay(at + tick, () -> {
                    if (pair[0] instanceof Object[] held && held[0] instanceof Sorcerer caster
                            && pair[1] instanceof net.minecraft.world.entity.LivingEntity target && target.isAlive()) {
                        aimFrom(caster, target);
                    }
                });
            }
            helper.runAfterDelay(at + DAMAGE_TICKS, () -> {
                Object[] held = (Object[]) pair[0];
                Sorcerer caster = (Sorcerer) held[0];
                T target = kind.tryCast((net.minecraft.world.entity.Entity) pair[1]);
                boolean killed = !target.isAlive();
                double done = before[0] - (killed ? 0.0F : target.getHealth());
                com.mojang.logging.LogUtils.getLogger().info(
                        "DAMAGE foe={} distance={} measured={} killed={} arena={} world={} health={} visSpent={} "
                                + "lifeLost={} sentence={}", foe, distance, done, killed, held[2], held[1], before[0],
                        (double) held[4] - Casters.vis(caster), (float) held[5] - caster.getHealth(), option.name());
                com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
                entry.addProperty("sentence", option.name());
                entry.addProperty("intent", (String) held[3]);
                entry.addProperty("element", option.acts().isEmpty() ? "" : option.acts().get(0).element().name());
                entry.addProperty("arena", (double) held[2]);
                entry.addProperty("world", (double) held[1]);
                entry.addProperty("measured", done);
                entry.addProperty("killed", killed);
                // What it cost the one who read it: the Vis spent, and the life it lost (3.19).
                entry.addProperty("visSpent", (double) held[4] - Casters.vis(caster));
                entry.addProperty("lifeLost", (float) held[5] - caster.getHealth());
                found.add(entry);
                leave(caster, target);
            });
        }
        helper.runAfterDelay(2 + trials.size() * (DAMAGE_TICKS + 10), () -> {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("foe", foe);
            root.addProperty("health", (double) whole[0]);
            root.addProperty("times", DAMAGE_TIMES);
            // The middle of what each sentence did, and whether it ever killed.
            java.util.Map<String, List<com.google.gson.JsonObject>> bySentence = new java.util.LinkedHashMap<>();
            for (com.google.gson.JsonElement element : found) {
                com.google.gson.JsonObject entry = element.getAsJsonObject();
                bySentence.computeIfAbsent(entry.get("sentence").getAsString(), k -> new java.util.ArrayList<>())
                        .add(entry);
            }
            com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
            bySentence.values().forEach(entries -> {
                List<Double> done = entries.stream().map(entry -> entry.get("measured").getAsDouble()).sorted()
                        .toList();
                com.google.gson.JsonObject middle = entries.get(0).deepCopy();
                middle.addProperty("measured", done.get(done.size() / 2));
                middle.add("each", new com.google.gson.Gson().toJsonTree(done));
                middle.addProperty("killed", entries.stream().anyMatch(entry -> entry.get("killed").getAsBoolean()));
                kept.add(middle);
            });
            root.add("readings", kept);
            try {
                java.nio.file.Path file = java.nio.file.Path.of("arena-calibration", "damage-" + foe + ".json");
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                        .toJson(root));
            } catch (java.io.IOException exception) {
                helper.fail("could not write what was measured: " + exception);
            }
            helper.succeed();
        });
    }

    /** What the arena's elder sees, {@code distance} off, its life at {@code health}, its Vis, on open ground. */
    private static com.elderlexicon.mod.mind.Mind.Scene arenaScene(double distance, double health, double vis) {
        return new com.elderlexicon.mod.mind.Mind.Scene(null, null, distance, (float) health, vis, mark -> 0.0D,
                false, element -> element == com.elderlexicon.mod.vita.VitaElement.AURA
                || element == com.elderlexicon.mod.vita.VitaElement.FIRMO);
    }

    /** {@code caster} looks at the middle of {@code target}, as a mind aims. */
    private static void aimFrom(net.minecraft.world.entity.Mob caster, net.minecraft.world.entity.LivingEntity target) {
        Vec3 middle = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        caster.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
        caster.setYHeadRot(caster.getYRot());
        caster.setYBodyRot(caster.getYRot());
    }

    /**
     * Two casters (docs/feiticeiro-design.md, 3.17): an aggressive elder sorcerer, following its temper's doctrine,
     * against an aggressive master wizard who knows the same runes and follows none. The elder lives, the wizard does
     * not. Not required: no doctrine against casters is installed yet, for the arena's caster is not the world's (it
     * neither backs off nor sets fire where one stands); this says how far from it they are.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1200, batch = "doctrine_duel", required = false)
    public static void anElderBeatsAnotherCaster(GameTestHelper helper) {
        Isle isle = new Isle(helper, 5, 40).floor();
        com.elderlexicon.mod.wizard.Wizard wizard = isle.spawn(ElderLexicon.WIZARD.get(), new Vec3(2.5D, 1.0D, 4.5D));
        wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.MASTER,
                com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                com.elderlexicon.mod.mind.Degree.ELDER.draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(),
                        helper.getLevel().getRandom()));
        anElderFights(isle, wizard, new Vec3(2.5D, 1.0D, 16.5D), 1200, com.elderlexicon.mod.mind.Personality.AGGRESSIVE,
                0.0F);
    }

    /**
     * What the arena is set by, for a spider and a skeleton: how fast each comes for its prey (the skeleton, until it
     * stands within its bow's reach), in blocks a tick, and how long the skeleton draws and waits between two arrows.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "calibration_more")
    public static void theArenaIsSetByTheWorldForSpidersAndSkeletons(GameTestHelper helper) {
        longFloor(helper, 40);
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 38.5D));
        prey.setNoAi(true);
        prey.setInvulnerable(true);
        net.minecraft.world.entity.monster.Spider spider = helper.spawn(EntityType.SPIDER, new Vec3(1.0D, 1.0D, 1.5D));
        spider.targetSelector.removeAllGoals(goal -> true);
        spider.goalSelector.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.LeapAtTargetGoal);
        spider.setTarget(prey);
        net.minecraft.world.entity.monster.Skeleton skeleton = helper.spawn(EntityType.SKELETON,
                new Vec3(3.5D, 1.0D, 1.5D));
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.targetSelector.removeAllGoals(goal -> true);
        skeleton.setTarget(prey);
        double[] from = new double[2];
        int[] shots = new int[1];
        int[] drawn = new int[2];
        boolean[] was = new boolean[1];
        helper.onEachTick(() -> {
            boolean drawing = skeleton.isUsingItem();
            if (drawing) {
                drawn[0]++;
            }
            if (was[0] && !drawing) {
                shots[0]++;
            }
            was[0] = drawing;
        });
        helper.runAfterDelay(20, () -> {
            from[0] = spider.getZ();
            from[1] = skeleton.getZ();
        });
        double[] speeds = new double[2];
        helper.runAfterDelay(60, () -> {
            speeds[0] = (spider.getZ() - from[0]) / 40.0D;
            speeds[1] = (skeleton.getZ() - from[1]) / 40.0D;
        });
        helper.runAfterDelay(380, () -> {
            com.mojang.logging.LogUtils.getLogger().info(
                    "CALIBRATION spider={} skeleton={} (blocks a tick); skeleton shot {} arrows, drew {} ticks, "
                            + "{} from the prey", speeds[0], speeds[1], shots[0], drawn[0], skeleton.distanceTo(prey));
            leave(prey, spider, skeleton);
            helper.succeed();
        });
    }

    /**
     * What the arena (src/arena) is set by: how fast a husk comes for its prey, and how fast a sorcerer and a wizard back
     * away, in blocks a tick, measured here and written to the log (docs/feiticeiro-design.md, 3.10).
     */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "calibration")
    public static void theArenaIsSetByTheWorld(GameTestHelper helper) {
        longFloor(helper, 40);
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(0.5D, 1.0D, 38.5D));
        prey.setNoAi(true);
        net.minecraft.world.entity.monster.Husk husk = helper.spawn(EntityType.HUSK, new Vec3(0.5D, 1.0D, 1.5D));
        husk.targetSelector.removeAllGoals(goal -> true);
        husk.setTarget(prey);
        Sorcerer sorcerer = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(2.5D, 1.0D, 1.5D));
        sorcerer.goalSelector.removeAllGoals(goal -> true);
        sorcerer.targetSelector.removeAllGoals(goal -> true);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(4.5D, 1.0D, 1.5D));
        wizard.goalSelector.removeAllGoals(goal -> true);
        wizard.targetSelector.removeAllGoals(goal -> true);
        double[] from = new double[3];
        helper.runAfterDelay(40, () -> {
            from[0] = husk.getZ();
            from[1] = sorcerer.getZ();
            from[2] = wizard.getZ();
        });
        helper.runAfterDelay(41, () -> {
            // Not too far: a way is found only within its follow range.
            sorcerer.getNavigation().moveTo(2.5D + helper.absolutePos(BlockPos.ZERO).getX(), sorcerer.getY(),
                    helper.absolutePos(new BlockPos(0, 0, 14)).getZ() + 0.5D, 0.8D);
            wizard.getNavigation().moveTo(4.5D + helper.absolutePos(BlockPos.ZERO).getX(), wizard.getY(),
                    helper.absolutePos(new BlockPos(0, 0, 14)).getZ() + 0.5D, 0.7D);
        });
        double[] walked = new double[2];
        helper.runAfterDelay(51, () -> {
            // The first ticks of a walk are slower: measured from the tenth on, for twenty ticks.
            from[1] = sorcerer.getZ();
            from[2] = wizard.getZ();
        });
        helper.runAfterDelay(71, () -> {
            walked[0] = (sorcerer.getZ() - from[1]) / 20.0D;
            walked[1] = (wizard.getZ() - from[2]) / 20.0D;
        });
        helper.runAfterDelay(140, () -> {
            double huskSpeed = (husk.getZ() - from[0]) / 100.0D;
            double sorcererSpeed = walked[0];
            double wizardSpeed = walked[1];
            com.mojang.logging.LogUtils.getLogger().info("CALIBRATION husk={} sorcerer={} wizard={} (blocks a tick)", huskSpeed, sorcererSpeed,
                    wizardSpeed);
            leave(prey, husk, sorcerer, wizard);
            helper.succeed();
        });
    }

    /** One who can write a distance writes it, and throws from where it stands. */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "reach_write")
    public static void aSorcererWhoKnowsHowWritesTheDistance(GameTestHelper helper) {
        longFloor(helper, 30);
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 27.5D));
        prey.setNoAi(true);
        Sorcerer evoker = farFrom(helper, prey, "igni", "iactare", "vocant", "ubis");
        helper.succeedWhen(() -> {
            if (evoker.lastChoice().isEmpty()) {
                helper.fail("it read nothing yet (" + evoker.distanceTo(prey) + " blocks away)");
            }
            List<String> words = evoker.lastChoice().get().option().words();
            if (!words.contains("ubis")) {
                helper.fail("it read " + words + " from " + evoker.distanceTo(prey) + " blocks");
            }
            leave(evoker, prey);
        });
    }

    /**
     * What it throws lands (docs/feiticeiro-design.md, 3.8): it keeps looking at its target while the throw runs its two
     * seconds, so the flow lands on it and not wherever it turned.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "caster_aim")
    public static void aSorcererHurtsWhatItAimsAt(GameTestHelper helper) {
        longFloor(helper, 12);
        Villager prey = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 9.5D));
        prey.setNoAi(true);
        Sorcerer evoker = farFrom(helper, prey, "igni", "iactare", "vocant");
        helper.succeedWhen(() -> {
            if (prey.isAlive() && prey.getHealth() >= prey.getMaxHealth()) {
                helper.fail("the villager is not hurt");
            }
            leave(evoker, prey);
        });
    }

    /** A wizard alone, hurt, draws out of the world what it lacks: it heals (docs/feiticeiro-design.md, 3.8). */
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "caster_heal")
    public static void aWoundedWizardHealsItself(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        wizard.targetSelector.removeAllGoals(goal -> true);
        wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE,
                com.elderlexicon.mod.mind.Personality.CAUTIOUS, Set.of("aura", "vocant", "quantum"));
        float[] least = {Float.MAX_VALUE};
        helper.runAtTickTime(5, () -> com.elderlexicon.mod.vita.VitaSystem.drain(wizard,
                com.elderlexicon.mod.vita.VitaElement.AURA, 15.0D));
        helper.succeedWhen(() -> {
            least[0] = Math.min(least[0], wizard.getHealth());
            if (least[0] >= wizard.getMaxHealth()) {
                helper.fail("the wizard was not hurt yet");
            }
            if (wizard.getHealth() < least[0] + 1.0F) {
                helper.fail("not healed: " + wizard.getHealth() + " (least " + least[0] + "); read "
                        + wizard.casterMind().lastChoice().map(choice -> choice.option().name() + " " + choice.intent())
                        .orElse("nothing"));
            }
        });
    }

    /** A burning wizard brings water to where it stands and puts the fire out. */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "caster_douse")
    public static void aBurningWizardPutsItOut(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        wizard.targetSelector.removeAllGoals(goal -> true);
        wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE,
                com.elderlexicon.mod.mind.Personality.AGGRESSIVE, Set.of("aqua", "vocant"));
        helper.runAtTickTime(5, () -> wizard.setRemainingFireTicks(600));
        helper.succeedWhen(() -> {
            if (helper.getTick() < 10 || wizard.isOnFire()) {
                helper.fail("still burning; read " + wizard.casterMind().lastChoice()
                        .map(choice -> choice.option().name() + " " + choice.intent()).orElse("nothing"));
            }
        });
    }

    /** A foe at hand is kept off: a zone around the caster no creature enters. */
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "caster_ward")
    public static void aCasterWardsAFoeAtHand(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        wizard.targetSelector.removeAllGoals(goal -> true);
        wizard.goalSelector.removeAllGoals(goal -> !(goal.getClass().getEnclosingClass()
                == com.elderlexicon.mod.caster.CasterMind.class));
        wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE,
                com.elderlexicon.mod.mind.Personality.CAUTIOUS, Set.of("vis", "impediunt", "igni", "iactare"));
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(3.0D, 1.0D, 1.5D));
        zombie.setNoAi(true);
        wizard.setTarget(zombie);
        boolean[] warded = {false};
        helper.onEachTick(() -> wizard.casterMind().lastChoice()
                .filter(choice -> choice.intent() == com.elderlexicon.mod.mind.Intent.WARD)
                .ifPresent(choice -> warded[0] = true));
        helper.succeedWhen(() -> {
            if (!warded[0]) {
                helper.fail("it did not ward; read " + wizard.casterMind().lastChoice()
                        .map(choice -> choice.option().name() + " " + choice.intent()).orElse("nothing"));
            }
        });
    }

    /** A wizard is heard: it murmurs now and then, as a villager does. */
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "wizard_voice")
    public static void aWizardIsHeard(GameTestHelper helper) {
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        java.util.List<String> heard = new java.util.ArrayList<>();
        java.util.function.Consumer<net.minecraftforge.event.PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getPosition().distanceTo(wizard.position()) < 1.0D && event.getSound() != null) {
                heard.add(event.getSound().get().getLocation().toString());
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        helper.succeedWhen(() -> {
            if (heard.isEmpty()) {
                helper.fail("not a sound from the wizard");
            }
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
        });
    }

    /** A wizard of few runes, alone in a room whose far wall is {@code wall}: it writes its pages quickly. */
    private static com.elderlexicon.mod.wizard.Wizard studentBefore(GameTestHelper helper,
                                                                     net.minecraft.world.level.block.Block wall) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(x, 1, 4), wall);
        }
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(2.5D, 1.0D, 1.5D));
        wizard.targetSelector.removeAllGoals(goal -> true);
        wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE,
                com.elderlexicon.mod.mind.Personality.CAUTIOUS, Set.of("igni", "iactare", "vocant", "quantum"));
        wizard.casterMind().studySoon();
        return wizard;
    }

    /** At the shelves a caster reads what others wrote: sentences in the runes it knows (docs/mago-design.md, 5c). */
    @GameTest(template = EMPTY, timeoutTicks = 2000, batch = "caster_study")
    public static void aCasterReadsAtTheShelves(GameTestHelper helper) {
        com.elderlexicon.mod.wizard.Wizard wizard = studentBefore(helper, Blocks.BOOKSHELF);
        helper.onEachTick(() -> {
            if (!wizard.casterMind().written().isEmpty() && helper.getTick() % 100 == 0) {
                wizard.casterMind().studySoon();
            }
        });
        helper.succeedWhen(() -> {
            if (wizard.casterMind().studiedSentences().isEmpty()) {
                helper.fail("nothing read at the shelves; " + wizard.describe());
            }
        });
    }

    /** At the galdraria table a wizard engraves a page of its own into a wand, and sells it. */
    @GameTest(template = EMPTY, timeoutTicks = 2000, batch = "caster_engrave")
    public static void aWizardEngravesAWandToSell(GameTestHelper helper) {
        com.elderlexicon.mod.wizard.Wizard wizard = studentBefore(helper,
                com.elderlexicon.mod.galdraria.Galdraria.TABLE.get());
        helper.onEachTick(() -> {
            if (!wizard.casterMind().written().isEmpty() && helper.getTick() % 100 == 0) {
                wizard.casterMind().studySoon();
            }
        });
        helper.succeedWhen(() -> {
            boolean engraved = wizard.getOffers().stream()
                    .anyMatch(offer -> com.elderlexicon.mod.galdraria.Engravings.has(offer.getResult()));
            if (!engraved) {
                helper.fail("no engraved wand offered; " + wizard.describe());
            }
        });
    }

    /** All the text of a written book's pages, as plain text (translatable parts by their keys). */
    private static String bookText(net.minecraft.world.item.ItemStack book) {
        StringBuilder text = new StringBuilder();
        net.minecraft.nbt.ListTag pages = book.getOrCreateTag().getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < pages.size(); i++) {
            text.append(pages.getString(i)).append('\n');
        }
        return text.toString();
    }

    /** A rune guide says what the rune is, sentences that read well, and how it is lived (docs/mago-design.md, 5d). */
    @GameTest(template = EMPTY)
    public static void aRuneGuideTellsTheRune(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        net.minecraft.world.item.ItemStack guide = com.elderlexicon.mod.library.RuneGuides.runeGuide("igni")
                .orElseThrow();
        String text = bookText(guide);
        if (!text.contains("o fogo, a energia como calor")) {
            helper.fail("the guide does not say what igni is: " + text);
        }
        if (!text.contains("igni iactare")) {
            helper.fail("the guide shows no sentence with igni: " + text);
        }
        if (!text.contains("guide.elderlexicon.experience.burning")) {
            helper.fail("the guide does not say how igni is lived: " + text);
        }
        net.minecraft.world.item.ItemStack grammar = com.elderlexicon.mod.library.RuneGuides.familyGuide(
                com.elderlexicon.mod.library.RuneGuides.Family.FILTERS);
        if (!bookText(grammar).contains("ubis")) {
            helper.fail("the guide of the filters does not have ubis");
        }
        helper.succeed();
    }

    /** A librarian has guides among what it may offer, at every level of its trade. */
    @GameTest(template = EMPTY)
    public static void aLibrarianKeepsGuides(GameTestHelper helper) {
        Villager librarian = helper.spawn(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        librarian.setNoAi(true);
        var pool = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(
                net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN);
        for (int level = 1; level <= 5; level++) {
            boolean guide = false;
            for (var listing : pool.get(level)) {
                net.minecraft.world.item.trading.MerchantOffer offer = listing.getOffer(librarian,
                        helper.getLevel().getRandom());
                if (offer != null && com.elderlexicon.mod.library.RuneGuides.subject(offer.getResult()).isPresent()) {
                    guide = true;
                }
            }
            if (!guide) {
                helper.fail("no guide among the librarian's trades of level " + level);
            }
        }
        helper.succeed();
    }

    /** A librarian struck reads the veil: it is lost to the one that hunted it. */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "librarian_veil")
    public static void aLibrarianVanishesFromItsHunter(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        roomWithAWall(helper);
        Villager librarian = helper.spawn(EntityType.VILLAGER, new Vec3(1.5D, 1.0D, 1.5D));
        librarian.setVillagerData(librarian.getVillagerData().setProfession(
                net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
        librarian.setVillagerXp(1); // a librarian with no job site and no trade made is no librarian for long
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        zombie.setNoAi(true);
        zombie.setTarget(librarian);
        helper.runAtTickTime(5, () -> librarian.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F));
        boolean[] tranced = {false};
        helper.onEachTick(() -> tranced[0] |= com.elderlexicon.mod.library.Librarians.inTrance(librarian));
        helper.succeedWhen(() -> {
            if (!tranced[0]) {
                helper.fail("the librarian never read");
            }
            if (zombie.getTarget() == librarian) {
                helper.fail("the zombie still hunts it");
            }
        });
    }

    /** The towers are structures of the world (docs/mago-design.md, 5e). */
    @GameTest(template = EMPTY)
    public static void theTowersAreStructures(GameTestHelper helper) {
        var structures = helper.getLevel().registryAccess().registryOrThrow(
                net.minecraft.core.registries.Registries.STRUCTURE);
        for (String tower : List.of("sorcerer_tower", "wizard_tower")) {
            if (!structures.containsKey(new net.minecraft.resources.ResourceLocation(ElderLexicon.MODID, tower))) {
                helper.fail("no structure " + tower);
            }
        }
        helper.succeed();
    }

    /**
     * A tower drawn whole: its door, its library, its stair, its roof, and its caster at home in it. The tower is larger
     * than a test's room: what it drew is taken away after.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "tower")
    public static void aWizardsTowerIsBuilt(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 0, 2)).above(10);
        com.elderlexicon.mod.tower.TowerPiece tower = new com.elderlexicon.mod.tower.TowerPiece(
                com.elderlexicon.mod.tower.TowerKind.WIZARD, center, 4);
        net.minecraft.world.level.levelgen.structure.BoundingBox box = tower.getBoundingBox();
        // Whatever an earlier run left there is not part of the tower.
        level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, net.minecraft.world.phys.AABB.of(box))
                .forEach(net.minecraft.world.entity.Entity::discard);
        tower.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                box, new net.minecraft.world.level.ChunkPos(center), center);
        List<com.elderlexicon.mod.wizard.Wizard> dwellers = level.getEntitiesOfClass(
                com.elderlexicon.mod.wizard.Wizard.class, net.minecraft.world.phys.AABB.of(box));
        try {
            if (!(level.getBlockState(center.offset(0, 1, 5)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
                helper.fail("no door: " + level.getBlockState(center.offset(0, 1, 5)));
            }
            if (!level.getBlockState(center.offset(0, 6, -3)).is(Blocks.LECTERN)) {
                helper.fail("no lectern in the library: " + level.getBlockState(center.offset(0, 6, -3)));
            }
            if (!level.getBlockState(center.offset(-3, 6, 0)).is(com.elderlexicon.mod.galdraria.Galdraria.TABLE.get())) {
                helper.fail("no galdraria table in the library");
            }
            long shelves = BlockPos.betweenClosedStream(box).filter(pos -> level.getBlockState(pos).is(Blocks.BOOKSHELF))
                    .count();
            if (shelves < 20) {
                helper.fail("a library of " + shelves + " shelves");
            }
            if (!level.getBlockState(center.offset(1, 1, 0)).is(Blocks.SPRUCE_PLANKS)) {
                helper.fail("the stair does not start at the column: " + level.getBlockState(center.offset(1, 1, 0)));
            }
            if (!level.getBlockState(center.offset(0, 4 * 5 + 4 + 5 + 1, 0)).is(Blocks.BLUE_TERRACOTTA)) {
                helper.fail("no roof at the top");
            }
            if (dwellers.size() != 1) {
                helper.fail(dwellers.size() + " wizards in the tower");
            }
            if (dwellers.get(0).casterMind().home().isEmpty()) {
                helper.fail("the wizard has no home");
            }
            helper.succeed();
        } finally {
            dwellers.forEach(net.minecraft.world.entity.Entity::discard);
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, net.minecraft.world.phys.AABB.of(box))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                    .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
        }
    }

    /**
     * The Wizard's houses are saved as the code draws them (docs/mago-design.md, 5f): each is drawn, taken as a
     * structure, and compared with the one the mod carries. One missing or different is saved again into the mod's
     * resources (when run from the project), and the test asks to be run again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "wizard_house_saved")
    public static void theWizardHousesAreSavedAsDrawn(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)).above(20);
        net.minecraft.core.Vec3i size = new net.minecraft.core.Vec3i(com.elderlexicon.mod.village.WizardHouse.WIDTH,
                com.elderlexicon.mod.village.WizardHouse.HEIGHT, com.elderlexicon.mod.village.WizardHouse.DEPTH);
        List<String> saved = new java.util.ArrayList<>();
        for (com.elderlexicon.mod.village.WizardHouse.Style style
                : com.elderlexicon.mod.village.WizardHouse.Style.values()) {
            // Whatever an earlier run left there is not part of the house.
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(origin,
                            origin.offset(size)).inflate(2.0D), entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            com.elderlexicon.mod.village.WizardHouse.build(level, origin, style);
            com.elderlexicon.mod.wizard.Wizard wizard = ElderLexicon.WIZARD.get().create(level);
            BlockPos at = origin.offset(com.elderlexicon.mod.village.WizardHouse.DWELLER);
            wizard.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 180.0F, 0.0F);
            wizard.setPersistenceRequired();
            wizard.arriveWithHouse();
            level.addFreshEntity(wizard);
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate drawn =
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
            drawn.fillFromWorld(level, origin, size, true, Blocks.STRUCTURE_VOID);
            drawn.setAuthor(ElderLexicon.MODID);
            net.minecraft.nbt.CompoundTag fresh = drawn.save(new net.minecraft.nbt.CompoundTag());
            wizard.discard();
            BlockPos.betweenClosed(origin, origin.offset(size).offset(-1, -1, -1))
                    .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
            int taken = fresh.getList("entities", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
            if (taken != 1) {
                helper.fail("the " + style + " house was taken with " + taken + " creatures");
            }
            boolean same = level.getStructureManager().get(style.template())
                    .map(carried -> blocksOf(carried.save(new net.minecraft.nbt.CompoundTag())).equals(blocksOf(fresh)))
                    .orElse(false);
            if (!same) {
                // The project is the first folder up from where the server runs that has the mod's resources.
                java.nio.file.Path project = level.getServer().getServerDirectory().toPath().toAbsolutePath().normalize();
                while (project != null && !java.nio.file.Files.isDirectory(project.resolve("src/main/resources"))) {
                    project = project.getParent();
                }
                if (project == null) {
                    helper.fail("the " + style + " house differs from the saved one, and the project is not here");
                    return;
                }
                java.nio.file.Path file = project.resolve("src/main/resources/data/" + ElderLexicon.MODID
                        + "/structures/" + style.template().getPath() + ".nbt");
                try {
                    java.nio.file.Files.createDirectories(file.getParent());
                    net.minecraft.nbt.NbtIo.writeCompressed(fresh, file.toFile());
                    saved.add(style.village());
                } catch (java.io.IOException exception) {
                    helper.fail("the " + style + " house could not be saved: " + exception);
                }
            }
        }
        if (!saved.isEmpty()) {
            helper.fail("saved again " + saved + ": run the tests again");
        }
        helper.succeed();
    }

    /** What a saved structure is, but for its creatures (they are saved with where and who they were). */
    private static net.minecraft.nbt.CompoundTag blocksOf(net.minecraft.nbt.CompoundTag structure) {
        net.minecraft.nbt.CompoundTag blocks = structure.copy();
        blocks.remove("entities");
        blocks.remove("DataVersion");
        return blocks;
    }

    /** The Wizard's house is among the houses every village may build. */
    @GameTest(template = EMPTY)
    public static void theWizardsHouseIsInTheVillages(GameTestHelper helper) {
        var pools = helper.getLevel().registryAccess().registryOrThrow(
                net.minecraft.core.registries.Registries.TEMPLATE_POOL);
        for (com.elderlexicon.mod.village.WizardHouse.Style style
                : com.elderlexicon.mod.village.WizardHouse.Style.values()) {
            var pool = pools.get(style.housesPool());
            if (pool == null || !com.elderlexicon.mod.village.VillageHouses.holds(pool, style)) {
                helper.fail("the " + style.village() + " villages have no wizard's house");
            }
        }
        helper.succeed();
    }

    /** A house placed from what was saved: its door, its lectern, and its wizard, born anew and at home there. */
    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "wizard_house_placed")
    public static void aWizardMovesIntoItsHouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)).above(20);
        var template = level.getStructureManager().get(
                com.elderlexicon.mod.village.WizardHouse.Style.PLAINS.template());
        if (template.isEmpty()) {
            helper.fail("no plains house saved");
            return;
        }
        template.get().placeInWorld(level, origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                level.getRandom(), 2);
        net.minecraft.world.phys.AABB house = new net.minecraft.world.phys.AABB(origin,
                origin.offset(com.elderlexicon.mod.village.WizardHouse.WIDTH, com.elderlexicon.mod.village.WizardHouse.HEIGHT,
                        com.elderlexicon.mod.village.WizardHouse.DEPTH));
        helper.runAtTickTime(10, () -> {
            List<com.elderlexicon.mod.wizard.Wizard> wizards = level.getEntitiesOfClass(
                    com.elderlexicon.mod.wizard.Wizard.class, house);
            try {
                if (wizards.size() != 1) {
                    helper.fail(wizards.size() + " wizards in the house");
                }
                if (wizards.get(0).casterMind().home().isEmpty()) {
                    helper.fail("the wizard is not at home");
                }
                if (!level.getBlockState(origin.offset(2, 1, 6)).is(Blocks.LECTERN)) {
                    helper.fail("no lectern: " + level.getBlockState(origin.offset(2, 1, 6)));
                }
                helper.succeed();
            } finally {
                wizards.forEach(net.minecraft.world.entity.Entity::discard);
                BlockPos.betweenClosed(origin, origin.offset(com.elderlexicon.mod.village.WizardHouse.WIDTH - 1,
                        com.elderlexicon.mod.village.WizardHouse.HEIGHT - 1,
                        com.elderlexicon.mod.village.WizardHouse.DEPTH - 1))
                        .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
            }
        });
    }

    /**
     * A university drawn whole (docs/mago-design.md, 5g): its houses, and who lives there, each with its rank and chair.
     * The grounds are far larger than a test's room: what they drew is taken away after.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "university")
    public static void aUniversityIsBuilt(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 0, 2)).above(12);
        com.elderlexicon.mod.university.UniversityPiece university =
                new com.elderlexicon.mod.university.UniversityPiece(center);
        com.elderlexicon.mod.university.UniversityRoadPiece road =
                new com.elderlexicon.mod.university.UniversityRoadPiece(center);
        net.minecraft.world.level.levelgen.structure.BoundingBox all = net.minecraft.world.level.levelgen.structure
                .BoundingBox.encapsulatingBoxes(List.of(university.getBoundingBox(), road.getBoundingBox())).orElseThrow();
        net.minecraft.world.phys.AABB area = net.minecraft.world.phys.AABB.of(all);
        level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                .forEach(net.minecraft.world.entity.Entity::discard);
        // Open air where it stands, and stone under it, as the terrain would be shaped to it.
        BlockPos.betweenClosed(all.minX(), all.minY() - 6, all.minZ(), all.maxX(), all.maxY(), all.maxZ())
                .forEach(pos -> level.setBlock(pos, pos.getY() < center.getY() ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 2));
        net.minecraft.world.level.levelgen.structure.BoundingBox drawn = new net.minecraft.world.level.levelgen
                .structure.BoundingBox(all.minX(), all.minY() - 6, all.minZ(), all.maxX(), all.maxY(), all.maxZ());
        university.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                drawn, new net.minecraft.world.level.ChunkPos(center), center);
        road.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                drawn, new net.minecraft.world.level.ChunkPos(center), center);
        helper.runAtTickTime(150, () -> {
            List<com.elderlexicon.mod.wizard.Wizard> wizards = level.getEntitiesOfClass(
                    com.elderlexicon.mod.wizard.Wizard.class, area);
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
            try {
                picture(level, all, "university");
                long rectors = wizards.stream().filter(wizard -> wizard.rank()
                        .filter(com.elderlexicon.mod.university.Rank.RECTOR::equals).isPresent()).count();
                java.util.Set<com.elderlexicon.mod.university.Discipline> chairs = new java.util.HashSet<>();
                wizards.forEach(wizard -> wizard.chair().ifPresent(chairs::add));
                if (rectors != 1) {
                    helper.fail(rectors + " rectors");
                }
                if (chairs.size() != com.elderlexicon.mod.university.Discipline.values().length) {
                    helper.fail("chairs held: " + chairs);
                }
                if (wizards.stream().anyMatch(wizard -> wizard.casterMind().home().isEmpty() || !wizard.hasCustomName())) {
                    helper.fail("a wizard of the university with no home or no title");
                }
                long librarians = villagers.stream().filter(villager -> villager.getVillagerData().getProfession()
                        == net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN).count();
                long clerics = villagers.stream().filter(villager -> villager.getVillagerData().getProfession()
                        == net.minecraft.world.entity.npc.VillagerProfession.CLERIC).count();
                if (librarians < 2 || clerics < 2) {
                    helper.fail(librarians + " librarians and " + clerics + " clerics");
                }
                if (!level.getBlockState(center.offset(-44, 1, -39)).is(Blocks.IRON_DOOR)) {
                    helper.fail("no iron door to the closed room: " + level.getBlockState(center.offset(-44, 1, -39)));
                }
                if (!level.getBlockState(center.offset(37, 1, -23)).is(com.elderlexicon.mod.galdraria.Galdraria.TABLE.get())) {
                    helper.fail("no table for the master of galdraria: " + level.getBlockState(center.offset(37, 1, -23)));
                }
                if (!level.getBlockState(center.offset(0, 1, 62)).isAir()) {
                    helper.fail("the gate is shut: " + level.getBlockState(center.offset(0, 1, 62)));
                }
                net.minecraft.world.level.block.state.BlockState railing = level.getBlockState(center.offset(-52, 9, -30));
                if (!railing.is(Blocks.DARK_OAK_FENCE) || !railing.getValue(net.minecraft.world.level.block.FenceBlock.NORTH)
                        || !railing.getValue(net.minecraft.world.level.block.FenceBlock.SOUTH)) {
                    helper.fail("the railing of the gallery is not joined: " + railing);
                }
                if (!level.getBlockState(center.offset(0, 1, 11)).is(Blocks.STONE_BRICKS)) {
                    helper.fail("the rim of the fountain is not solid: " + level.getBlockState(center.offset(0, 1, 11)));
                }
                // Where a monster could be born of the dark inside the walls.
                List<String> dark = new java.util.ArrayList<>();
                BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
                for (int x = -61; x <= 61; x++) {
                    for (int z = -61; z <= 61; z++) {
                        for (int y = 1; y <= com.elderlexicon.mod.university.UniversityPiece.TOP_FOR_TESTS; y++) {
                            at.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                            if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) == 0
                                    && net.minecraft.world.level.NaturalSpawner.isSpawnPositionOk(
                                    net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND, level, at,
                                    EntityType.ZOMBIE)
                                    && level.noCollision(EntityType.ZOMBIE.getAABB(at.getX() + 0.5D, at.getY(),
                                    at.getZ() + 0.5D))) {
                                dark.add(x + "," + y + "," + z + (dark.size() < 40 ? "=" + net.minecraft.core.registries
                                        .BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.below()).getBlock()).getPath()
                                        : ""));
                            }
                        }
                    }
                }
                System.out.println("[university] dark spots: " + dark.size() + " "
                        + dark.subList(0, Math.min(80, dark.size())));
                if (!dark.isEmpty()) {
                    helper.fail(dark.size() + " dark spots where monsters are born, as "
                            + dark.subList(0, Math.min(8, dark.size())));
                }
                helper.succeed();
            } finally {
                level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                                entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                BlockPos.betweenClosed(all.minX(), all.minY() - 6, all.minZ(), all.maxX(), all.maxY(), all.maxZ())
                        .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
            }
        });
    }

    /**
     * Every master can examine (docs/mago-design.md, 4.1): for each discipline, an examination of six questions is made
     * of the lexicon and of what the house teaches, each with four different answers and one of them right.
     */
    @GameTest(template = EMPTY, batch = "council")
    public static void anExamIsMadeOfTheLexicon(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        for (com.elderlexicon.mod.university.Discipline discipline
                : com.elderlexicon.mod.university.Discipline.values()) {
            com.elderlexicon.mod.university.Exam exam = com.elderlexicon.mod.university.Exam.of(discipline, 6,
                    helper.getLevel().getRandom());
            if (exam.size() != 6) {
                helper.fail(discipline + " asks " + exam.size() + " questions");
            }
            for (com.elderlexicon.mod.university.Exam.Question question : exam.questions()) {
                Set<String> answers = new java.util.HashSet<>();
                question.answers().forEach(answer -> answers.add(answer.getString().toLowerCase(java.util.Locale.ROOT)));
                if (question.answers().size() != 4 || answers.size() != 4 || question.right() < 0
                        || question.right() >= 4) {
                    helper.fail(discipline + ": " + question.prompt().getString() + " " + answers);
                }
            }
        }
        helper.succeed();
    }

    /**
     * The house's book (docs/mago-design.md, 4.1 and 4.2): admitted with the fee the examination set (one who missed
     * nothing is paid), in good standing while the period is paid, examined again when it ends; a master's examination
     * makes a Scriba, three passed with one perfect a Lector.
     */
    @GameTest(template = EMPTY, batch = "council")
    public static void theHouseKeepsItsStudents(GameTestHelper helper) {
        com.elderlexicon.mod.university.Enrollments book = com.elderlexicon.mod.university.Enrollments.of(
                helper.getLevel());
        UUID who = UUID.randomUUID();
        long now = 1000L;
        long period = com.elderlexicon.mod.university.Enrollments.PERIOD;
        book.examined(who, 2, 6, now);
        com.elderlexicon.mod.university.Enrollments.Student student = book.student(who);
        if (student.rank().isPresent() || !student.waiting(now + 10)) {
            helper.fail("two of six admitted, or let try again at once");
        }
        book.examined(who, 4, 6, now + 30000L);
        now += 30000L;
        if (student.rank().orElse(null) != com.elderlexicon.mod.university.Rank.AUDITOR || student.fee() != 12
                || !student.owes(now) || student.inGoodStanding(now)) {
            helper.fail("four of six: " + student.rank() + ", fee " + student.fee() + ", owes " + student.owes(now));
        }
        book.paid(who, now);
        if (!student.inGoodStanding(now + period - 1) || student.owes(now + 1)) {
            helper.fail("paid, and not in good standing");
        }
        if (!student.dueForExamination(now + period)) {
            helper.fail("the period ended, and no new examination is due");
        }
        if (com.elderlexicon.mod.university.Enrollments.feeFor(6, 6) >= 0
                || com.elderlexicon.mod.university.Enrollments.feeFor(5, 6)
                >= com.elderlexicon.mod.university.Enrollments.feeFor(3, 6)) {
            helper.fail("the fee does not fall as the examination goes better");
        }
        book.examinedBy(who, com.elderlexicon.mod.university.Discipline.FONTES, 3, now);
        if (student.rank().orElse(null) != com.elderlexicon.mod.university.Rank.AUDITOR) {
            helper.fail("three of five made a Scriba");
        }
        book.examinedBy(who, com.elderlexicon.mod.university.Discipline.FONTES, 4, now);
        book.examinedBy(who, com.elderlexicon.mod.university.Discipline.FILTROS, 4, now);
        if (student.rank().orElse(null) != com.elderlexicon.mod.university.Rank.SCRIBA || student.mayRead()) {
            helper.fail("after two passed: " + student.rank() + ", may read " + student.mayRead());
        }
        book.examinedBy(who, com.elderlexicon.mod.university.Discipline.NAHVA, 5, now);
        if (!book.makeLector(who) || student.rank().orElse(null) != com.elderlexicon.mod.university.Rank.LECTOR) {
            helper.fail("three passed, one perfect, and not a Lector");
        }
        book.forget(who);
        helper.succeed();
    }

    /**
     * Before the council and a master, on the dialogue screen (docs/mago-design.md, 4.1): the Rector examines a player
     * who asks to be admitted; missing nothing, they are admitted and paid instead of paying. A Magister then examines
     * them in his discipline, and the Auditor is a Scriba. Each master goes back to what it was doing when the talk ends.
     */
    @GameTest(template = EMPTY, batch = "council", timeoutTicks = 60)
    public static void theCouncilAdmitsAPlayer(GameTestHelper helper) {
        com.elderlexicon.mod.mind.PhraseCatalog.get();
        ServerPlayer mage = mage(helper);
        mage.moveTo(helper.absoluteVec(new Vec3(2.5D, 1.0D, 0.5D)));
        com.elderlexicon.mod.wizard.Wizard rector = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 3.5D));
        com.elderlexicon.mod.wizard.Wizard magister = helper.spawn(ElderLexicon.WIZARD.get(),
                new Vec3(3.5D, 1.0D, 3.5D));
        rector.joinUniversity(com.elderlexicon.mod.university.Rank.RECTOR,
                com.elderlexicon.mod.university.Discipline.REITOR, 6);
        magister.joinUniversity(com.elderlexicon.mod.university.Rank.MAGISTER,
                com.elderlexicon.mod.university.Discipline.FONTES, 6);
        com.elderlexicon.mod.university.Enrollments book = com.elderlexicon.mod.university.Enrollments.of(
                helper.getLevel());
        try {
            rector.mobInteract(mage, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (rector.getTradingPlayer() != mage || com.elderlexicon.mod.dialogue.Dialogue.line(mage).isEmpty()) {
                helper.fail("the Rector did not turn to talk");
            }
            com.elderlexicon.mod.dialogue.Dialogue.choose(mage, 0); // I ask to be admitted.
            com.elderlexicon.mod.dialogue.Dialogue.choose(mage, 0); // Let us begin.
            answerRight(helper, mage);
            com.elderlexicon.mod.university.Enrollments.Student student = book.student(mage.getUUID());
            long now = helper.getLevel().getServer().overworld().getGameTime();
            if (student.rank().orElse(null) != com.elderlexicon.mod.university.Rank.AUDITOR
                    || !student.inGoodStanding(now) || mage.getInventory().countItem(Items.EMERALD) != 3) {
                helper.fail("missing nothing: " + student.rank() + ", in good standing " + student.inGoodStanding(now)
                        + ", emeralds " + mage.getInventory().countItem(Items.EMERALD) + "; "
                        + com.elderlexicon.mod.dialogue.Dialogue.line(mage).map(line -> line.text().getString()));
            }
            com.elderlexicon.mod.dialogue.Dialogue.choose(mage, 0); // Goodbye.
            if (rector.getTradingPlayer() != null || com.elderlexicon.mod.dialogue.Dialogue.line(mage).isPresent()) {
                helper.fail("the Rector still holds the talk");
            }
            magister.mobInteract(mage, net.minecraft.world.InteractionHand.MAIN_HAND);
            com.elderlexicon.mod.dialogue.Dialogue.choose(mage, 0); // The examination of the Fonts.
            answerRight(helper, mage);
            if (student.rank().orElse(null) != com.elderlexicon.mod.university.Rank.SCRIBA
                    || student.passed().getOrDefault(com.elderlexicon.mod.university.Discipline.FONTES, 0) != 5) {
                helper.fail("passed the Fonts without a mistake: " + student.rank() + ", " + student.passed());
            }
            com.elderlexicon.mod.dialogue.Dialogue.choose(mage, 0);
            helper.succeed();
        } finally {
            book.forget(mage.getUUID());
        }
    }

    /** Answers right every question of the examination the player sits. */
    private static void answerRight(GameTestHelper helper, ServerPlayer player) {
        for (int i = 0; i < 10; i++) {
            java.util.Optional<com.elderlexicon.mod.university.Exam> exam =
                    com.elderlexicon.mod.university.Council.sitting(player);
            if (exam.isEmpty()) {
                return;
            }
            com.elderlexicon.mod.dialogue.Dialogue.choose(player, exam.get().current().orElseThrow().right());
        }
        helper.fail("the examination never ended");
    }

    /**
     * Among people living in peace, casters do not fight over words (docs/mago-design.md, 5h): two wizards in a
     * village who fall out do not come to blows; the offended one walks off, and neither talks to the other for a
     * while.
     */
    @GameTest(template = EMPTY, batch = "council")
    public static void castersKeepThePeaceInAVillage(GameTestHelper helper) {
        for (int i = 0; i < 3; i++) {
            helper.spawn(EntityType.VILLAGER, new Vec3(0.5D + 2 * i, 1.0D, 4.5D)).setNoAi(true);
        }
        com.elderlexicon.mod.wizard.Wizard one = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        com.elderlexicon.mod.wizard.Wizard other = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(3.5D, 1.0D, 1.5D));
        for (com.elderlexicon.mod.wizard.Wizard wizard : List.of(one, other)) {
            wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE,
                    com.elderlexicon.mod.mind.Personality.AGGRESSIVE, Set.of("igni", "iactare", "vocant"));
        }
        helper.runAtTickTime(5, () -> {
            if (!one.casterMind().civilized()) {
                helper.fail("villagers about, and it does not know it is in a village");
            }
            one.casterMind().fallOut(other.casterMind());
            if (one.getTarget() != null || other.getTarget() != null
                    || one.casterMind().quarrelling(other.getUUID())) {
                helper.fail("they came to blows in a village");
            }
            if (!one.casterMind().offended()) {
                helper.fail("the offended one did not walk off");
            }
            helper.succeed();
        });
    }

    /**
     * Pictures of what stands in {@code box}, for whoever builds it to look at: from above, and from the south, in the
     * colours the game's maps give the blocks (saved by the server, in {@code pictures/}).
     */
    private static void picture(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box,
                                String name) {
        int scale = 4;
        java.awt.image.BufferedImage above = new java.awt.image.BufferedImage(box.getXSpan() * scale,
                box.getZSpan() * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.image.BufferedImage front = new java.awt.image.BufferedImage(box.getXSpan() * scale,
                box.getYSpan() * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int rgb = 0x87CEEB;
                for (int y = box.maxY(); y >= box.minY(); y--) {
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        rgb = shade(state.getMapColor(level, pos).col, (y - box.minY()) / (double) box.getYSpan());
                        break;
                    }
                }
                paint(above, (x - box.minX()) * scale, (z - box.minZ()) * scale, scale, rgb);
            }
            for (int y = box.minY(); y <= box.maxY(); y++) {
                int rgb = 0x87CEEB;
                for (int z = box.maxZ(); z >= box.minZ(); z--) {
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        rgb = shade(state.getMapColor(level, pos).col, 1.0D - (box.maxZ() - z) / (double) box.getZSpan());
                        break;
                    }
                }
                paint(front, (x - box.minX()) * scale, (box.maxY() - y) * scale, scale, rgb);
            }
        }
        try {
            java.nio.file.Path folder = level.getServer().getServerDirectory().toPath().resolve("pictures");
            java.nio.file.Files.createDirectories(folder);
            javax.imageio.ImageIO.write(above, "png", folder.resolve(name + "_above.png").toFile());
            javax.imageio.ImageIO.write(front, "png", folder.resolve(name + "_south.png").toFile());
        } catch (java.io.IOException ignored) {
            // only pictures
        }
    }

    private static int shade(int rgb, double light) {
        double factor = 0.55D + 0.45D * Math.max(0.0D, Math.min(1.0D, light));
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static void paint(java.awt.image.BufferedImage image, int x, int y, int scale, int rgb) {
        for (int i = 0; i < scale; i++) {
            for (int j = 0; j < scale; j++) {
                image.setRGB(x + i, y + j, rgb);
            }
        }
    }

    /** The university is a structure of the world. */
    @GameTest(template = EMPTY)
    public static void theUniversityIsAStructure(GameTestHelper helper) {
        if (!helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .containsKey(new net.minecraft.resources.ResourceLocation(ElderLexicon.MODID, "university"))) {
            helper.fail("no structure university");
        }
        helper.succeed();
    }

    /** A Sorcerer leaves a Wizard be: the two are neither friends nor foes (docs/mago-design.md, 5b). */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "casters_neutral")
    public static void aSorcererLeavesAWizardBe(GameTestHelper helper) {
        roomWithAWall(helper);
        Sorcerer evoker = helper.spawn(ElderLexicon.SORCERER.get(), new Vec3(1.5D, 1.0D, 1.5D));
        com.elderlexicon.mod.wizard.Wizard wizard = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(3.5D, 1.0D, 1.5D));
        helper.runAtTickTime(60, () -> {
            if (evoker.getTarget() == wizard) {
                helper.fail("the sorcerer hunts the wizard");
            }
            if (wizard.getTarget() == evoker) {
                helper.fail("the wizard hunts the sorcerer");
            }
            helper.succeed();
        });
    }

    /** Two casters talk in the Old Tongue (docs/mago-design.md, 5b): what one says, the other hears. */
    @GameTest(template = EMPTY, timeoutTicks = 300, batch = "casters_talk")
    public static void twoCastersTalk(GameTestHelper helper) {
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard one = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        com.elderlexicon.mod.wizard.Wizard other = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(3.5D, 1.0D, 1.5D));
        for (com.elderlexicon.mod.wizard.Wizard wizard : List.of(one, other)) {
            wizard.targetSelector.removeAllGoals(goal -> true);
            wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.MASTER,
                    com.elderlexicon.mod.mind.Personality.CAUTIOUS, Set.of("igni", "aqua", "iactare", "vocant"));
        }
        one.casterMind().talkWith(other.casterMind());
        helper.succeedWhen(() -> {
            if (!one.casterMind().talking()) {
                helper.fail("they are not talking");
            }
            if (other.casterMind().lastHeard().isEmpty()) {
                helper.fail("nothing said yet");
            }
        });
    }

    /** Words come to blows: a duel, until one yields; then both let go. */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "casters_quarrel")
    public static void aQuarrelLastsUntilOneYields(GameTestHelper helper) {
        roomWithAWall(helper);
        com.elderlexicon.mod.wizard.Wizard one = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(1.5D, 1.0D, 1.5D));
        com.elderlexicon.mod.wizard.Wizard other = helper.spawn(ElderLexicon.WIZARD.get(), new Vec3(3.5D, 1.0D, 1.5D));
        for (com.elderlexicon.mod.wizard.Wizard wizard : List.of(one, other)) {
            wizard.targetSelector.removeAllGoals(goal -> true);
            wizard.casterMind().setMind(com.elderlexicon.mod.mind.Degree.APPRENTICE,
                    com.elderlexicon.mod.mind.Personality.AGGRESSIVE, Set.of("igni", "iactare", "vocant"));
        }
        one.casterMind().quarrel(other.casterMind());
        if (one.getTarget() != other || other.getTarget() != one) {
            helper.fail("they did not come to blows");
        }
        helper.runAtTickTime(10, () -> other.setHealth(other.getMaxHealth() * 0.2F));
        helper.runAtTickTime(15, () -> {
            if (one.getTarget() != null || other.getTarget() != null) {
                helper.fail("still fighting after one yielded: " + one.getTarget() + ", " + other.getTarget());
            }
            helper.succeed();
        });
    }

    /**
     * The mind weighs what the scene asks (docs/feiticeiro-design.md, 3.3): drawing the water out of a cow is worth more
     * to a wounded evoker, which heals by it, than to a whole one; and it foresees what the draw takes from the cow.
     * Whole, it may be worth nothing at all: the water would come into a full body and push the rest out (3.19).
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
        if (!(wounded > 0.0D)) {
            helper.fail("drawing out of the cow is worth nothing, wounded (" + share + "): " + wounded);
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
            evoker.hurt(helper.getLevel().damageSources().mobAttack(villager), 1.0F);
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

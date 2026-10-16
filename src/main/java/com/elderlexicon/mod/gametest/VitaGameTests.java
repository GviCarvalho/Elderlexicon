package com.elderlexicon.mod.gametest;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.vita.Fractures;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * What a body out of balance feels (docs/vita-sintomas-design.md): signs of the body, mild until the extreme, never a
 * potion; a broken bone that mends only with the bones full; a lack that kills by its name. Run with
 * {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ElderLexicon.MODID)
@PrefixGameTestTemplate(false)
public final class VitaGameTests {

    private static final String EMPTY = "empty";

    private VitaGameTests() {
    }

    private static void check(GameTestHelper helper, boolean holds, String what) {
        if (!holds) {
            helper.fail(what);
        }
    }

    /** A player logged into the test's level, with a channel that takes what it is sent (see SpellGameTests#mage). */
    private static ServerPlayer body(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-body"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        VitaSystem.resetStatus(player);
        return player;
    }

    private static void leave(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    /** The share of a whole body's step the player has now. */
    private static double step(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed.getValue() / speed.getBaseValue();
    }

    /** Deep cold slows the step and frosts the sight, and leaves no potion behind; warmed again, it is all gone. */
    @GameTest(template = EMPTY)
    public static void aColdBodyShiversAndKeepsNoPotion(GameTestHelper helper) {
        ServerPlayer player = body(helper);
        VitaSystem.setElementEnergy(player, VitaElement.IGNI, VitaSystem.DEFAULT_IGNI * 0.3D);
        VitaSystem.tick(player);
        check(helper, player.getActiveEffects().isEmpty(), "a lack gave a potion: " + player.getActiveEffects());
        check(helper, step(player) < 0.9D, "the cold did not slow the step: " + step(player));
        check(helper, player.getTicksFrozen() > 0, "the deep cold did not frost the sight");

        VitaSystem.setElementEnergy(player, VitaElement.IGNI, VitaSystem.DEFAULT_IGNI);
        VitaSystem.tick(player);
        check(helper, Math.abs(step(player) - 1.0D) < 1.0E-6D, "warm again, the step is still slow: " + step(player));
        leave(player);
        helper.succeed();
    }

    /** A small lack is barely felt: a fifth of the blood gone does not yet touch the step. */
    @GameTest(template = EMPTY)
    public static void aSmallLackIsBarelyFelt(GameTestHelper helper) {
        ServerPlayer player = body(helper);
        VitaSystem.setElementEnergy(player, VitaElement.AQUA, VitaSystem.DEFAULT_AQUA * 0.8D);
        VitaSystem.tick(player);
        check(helper, step(player) > 0.995D, "a fifth of the blood gone already slows: " + step(player));
        leave(player);
        helper.succeed();
    }

    /**
     * A broken leg limps, and does not mend while the bones lack; with earth put in past full, it mends ten times as
     * fast.
     */
    @GameTest(template = EMPTY)
    public static void aBrokenLegMendsOnlyWithTheBonesFull(GameTestHelper helper) {
        ServerPlayer player = body(helper);
        VitaSystem.setElementEnergy(player, VitaElement.FIRMO, VitaSystem.DEFAULT_FIRMO * 0.3D);
        Fractures.breakBone(player, true);
        VitaSystem.tick(player);
        check(helper, Fractures.of(player).leg(), "the leg is not broken");
        check(helper, step(player) < 0.65D, "a broken leg does not limp: " + step(player));

        int left = Fractures.mendingLeft(player, true);
        for (int i = 0; i < 10; i++) {
            VitaSystem.tick(player);
        }
        check(helper, Fractures.mendingLeft(player, true) == left, "the leg mended with the bones lacking");

        VitaSystem.setElementEnergy(player, VitaElement.FIRMO, VitaSystem.DEFAULT_FIRMO * 1.3D);
        VitaSystem.tick(player);
        check(helper, Fractures.mendingLeft(player, true) == left - 10,
                "earth past full did not mend ten times as fast: " + (left - Fractures.mendingLeft(player, true)));
        Fractures.mendAll(player);
        leave(player);
        helper.succeed();
    }

    /** All the heat taken, the body dies of the cold, and the death says so. */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void aLackKillsByItsName(GameTestHelper helper) {
        ServerPlayer player = body(helper);
        // A player just come into the world cannot be hurt for three seconds: the test waits them out.
        helper.runAtTickTime(70, () -> {
            VitaSystem.drain(player, VitaElement.IGNI, VitaSystem.DEFAULT_IGNI);
            VitaSystem.tick(player);
            DamageSource last = player.getLastDamageSource();
            check(helper, player.isDeadOrDying(), "a body with no heat lives on: " + player.getHealth());
            check(helper, last != null && "hypothermia".equals(last.type().msgId()),
                    "the death is not named for the cold: " + (last == null ? "none" : last.type().msgId()));
            leave(player);
            helper.succeed();
        });
    }
}

package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirmoMirrorTest {

    private static final double EPS = 1.0E-9;

    private static FirmoWorld three(FirmoWorld world) {
        world.living("a", 10.0D, "espelho");
        world.living("b", 10.0D, "espelho");
        world.living("c", 10.0D, "espelho");
        world.firmoMirror("espelho");
        return world;
    }

    @Test
    void damageIsRepeatedOnEveryOtherMemberAndNotCancelled() {
        FirmoWorld world = three(new FirmoWorld());

        List<Effect> effects = world.damaged(new MemberId("a"), 3.0D);

        assertTrue(FirmoWorld.of(effects, Effect.CancelDamage.class).isEmpty(), "the one hit still takes the damage");
        List<Effect.DealDamage> dealt = FirmoWorld.of(effects, Effect.DealDamage.class);
        assertEquals(List.of(new MemberId("b"), new MemberId("c")), dealt.stream().map(Effect.DealDamage::target).toList());
        dealt.forEach(d -> assertEquals(3.0D, d.amount(), EPS, "the same final damage, whatever armor they wear"));
        assertEquals(30.0D, world.paid, EPS, "2 reflections x 3 HP x 5 UMU");
    }

    @Test
    void reflectedDamageIsNeverReflectedBack() {
        FirmoWorld world = three(new FirmoWorld());
        world.damaged(new MemberId("a"), 3.0D);

        assertTrue(world.engine.handle(new LinkEvent.Damaged(new MemberId("b"), 3.0D, true)).isEmpty());
        assertTrue(world.engine.handle(new LinkEvent.Damaged(new MemberId("c"), 3.0D, true)).isEmpty());
    }

    @Test
    void anyMemberDyingKillsAllTheOthers() {
        FirmoWorld world = three(new FirmoWorld());

        List<Effect> effects = world.died(new MemberId("a"));

        assertEquals(List.of(new MemberId("b"), new MemberId("c")), FirmoWorld.killed(effects));
        assertEquals(3, FirmoWorld.of(effects, Effect.MemberGone.class).size());
        assertEquals(100.0D, world.paid, EPS, "10 HP + 10 HP left, 5 UMU each");
        assertTrue(world.graph.members("espelho").isEmpty());
    }

    @Test
    void theMoreMembersTheHigherTheCostOfEveryHit() {
        FirmoWorld pair = new FirmoWorld();
        pair.living("a", 10.0D, "espelho");
        pair.living("b", 10.0D, "espelho");
        pair.firmoMirror("espelho");
        pair.damaged(new MemberId("a"), 2.0D);

        FirmoWorld crowd = new FirmoWorld();
        for (String key : List.of("a", "b", "c", "d", "e")) {
            crowd.living(key, 10.0D, "espelho");
        }
        crowd.firmoMirror("espelho");
        crowd.damaged(new MemberId("a"), 2.0D);

        assertEquals(10.0D, pair.paid, EPS);
        assertEquals(40.0D, crowd.paid, EPS, "4 reflections instead of 1");
    }

    @Test
    void anObjectInAMirrorWearsDownAndTakesEveryoneWhenItBreaks() {
        FirmoWorld world = new FirmoWorld();
        world.object("bloco", 4.0D, "espelho");
        world.living("jogador", 20.0D, "espelho");
        world.firmoMirror("espelho");

        List<Effect> effects = world.damaged(new MemberId("jogador"), 5.0D);

        assertTrue(FirmoWorld.killed(effects).contains(new MemberId("bloco")));
        assertTrue(FirmoWorld.killed(effects).contains(new MemberId("jogador")));
    }

    @Test
    void aMirrorOfOneDoesNothing() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10.0D, "espelho");
        world.firmoMirror("espelho");

        assertTrue(world.damaged(new MemberId("a"), 3.0D).isEmpty());
    }

    @Test
    void anUnpayableMirrorBreaksWithoutReflecting() {
        FirmoWorld world = three(new FirmoWorld());
        world.balance = 5.0D;

        List<Effect> effects = world.damaged(new MemberId("a"), 3.0D);

        assertTrue(FirmoWorld.of(effects, Effect.DealDamage.class).isEmpty());
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
        assertTrue(world.graph.links().isEmpty());
    }

    @Test
    void aMemberWhoseMarkWasRemovedNoLongerTakesPart() {
        FirmoWorld world = three(new FirmoWorld());
        world.graph.unmark(new MemberId("c"), "espelho");

        List<Effect.DealDamage> dealt = FirmoWorld.of(world.damaged(new MemberId("a"), 3.0D), Effect.DealDamage.class);

        assertEquals(List.of(new MemberId("b")), dealt.stream().map(Effect.DealDamage::target).toList());
    }

    @Test
    void anOfflineOwnerSuspendsTheMirror() {
        FirmoWorld world = three(new FirmoWorld());
        world.online.remove(FirmoWorld.OWNER);

        assertTrue(world.damaged(new MemberId("a"), 3.0D).isEmpty());
        assertTrue(FirmoWorld.killed(world.died(new MemberId("a"))).isEmpty());
    }
}

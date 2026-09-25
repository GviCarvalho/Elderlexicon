package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirmoHierarchyTest {

    private static final double EPS = 1.0E-9;

    /** A player (child) tied to a hidden pig (parent). */
    private static FirmoWorld pigAndPlayer(FirmoWorld world) {
        world.living("porco", 10.0D, "pai");
        world.living("jogador", 20.0D, "filho");
        world.firmoHierarchy("pai", "filho");
        return world;
    }

    @Test
    void damageTheChildTakesIsCancelledAndMovedToTheParent() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());

        List<Effect> effects = world.damaged(new MemberId("jogador"), 4.0D);

        assertEquals(1, FirmoWorld.of(effects, Effect.CancelDamage.class).size());
        List<Effect.DealDamage> dealt = FirmoWorld.of(effects, Effect.DealDamage.class);
        assertEquals(1, dealt.size());
        assertEquals(new MemberId("porco"), dealt.get(0).target());
        assertEquals(4.0D, dealt.get(0).amount(), EPS);
        assertEquals(20.0D, world.paid, EPS, "4 HP moved cost 20 UMU");
    }

    @Test
    void theParentsOwnDamageDoesNotTouchTheChild() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());

        assertTrue(world.damaged(new MemberId("porco"), 3.0D).isEmpty());
        assertEquals(0.0D, world.paid, EPS);
    }

    @Test
    void whenTheParentDiesTheChildDiesEvenWithoutTakingAHit() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());

        List<Effect> effects = world.died(new MemberId("porco"));

        assertEquals(List.of(new MemberId("jogador")), FirmoWorld.killed(effects));
        assertEquals(100.0D, world.paid, EPS, "a player with 20 HP left costs 100 UMU to take");
        assertFalse(world.graph.hasAnyMark(new MemberId("porco")), "marks disappear with the dead");
        assertFalse(world.graph.hasAnyMark(new MemberId("jogador")));
        assertEquals(2, FirmoWorld.of(effects, Effect.MemberGone.class).size());
    }

    @Test
    void aChildDyingLeavesTheParentAlone() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());

        List<Effect> effects = world.died(new MemberId("jogador"));

        assertTrue(FirmoWorld.killed(effects).isEmpty());
        assertEquals(List.of(new Effect.MemberGone(new MemberId("jogador"))), effects);
        assertTrue(world.graph.hasAnyMark(new MemberId("porco")));
    }

    @Test
    void damageMovedToTheParentIsNeverReflectedAgain() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());
        world.damaged(new MemberId("jogador"), 4.0D);

        // The world reports the transferred damage back, tagged as coming from a link.
        assertTrue(world.engine.handle(new LinkEvent.Damaged(new MemberId("porco"), 4.0D, true)).isEmpty());
    }

    private static FirmoWorld chain(FirmoWorld world) {
        world.living("a", 10.0D, "m1");
        world.living("b", 10.0D, "m2");
        world.living("c", 10.0D, "m3");
        world.firmoHierarchy("m1", "m2");
        world.firmoHierarchy("m2", "m3");
        return world;
    }

    @Test
    void inAChainDamageClimbsToTheTopAndOnlyTheTopTakesIt() {
        FirmoWorld world = chain(new FirmoWorld());

        List<Effect> effects = world.damaged(new MemberId("c"), 4.0D);

        assertEquals(1, FirmoWorld.of(effects, Effect.CancelDamage.class).size());
        List<Effect.DealDamage> dealt = FirmoWorld.of(effects, Effect.DealDamage.class);
        assertEquals(1, dealt.size());
        assertEquals(new MemberId("a"), dealt.get(0).target(), "the middle one passes it on without taking it");
        assertEquals(4.0D, dealt.get(0).amount(), EPS);
        assertEquals(40.0D, world.paid, EPS, "two steps up, 20 UMU each");
    }

    @Test
    void aMiddleMembersOwnDamageAlsoGoesUp() {
        FirmoWorld world = chain(new FirmoWorld());

        List<Effect.DealDamage> dealt = FirmoWorld.of(world.damaged(new MemberId("b"), 3.0D), Effect.DealDamage.class);

        assertEquals(1, dealt.size());
        assertEquals(new MemberId("a"), dealt.get(0).target());
        assertEquals(15.0D, world.paid, EPS);
    }

    @Test
    void theTopOfAChainTakesItsOwnDamage() {
        FirmoWorld world = chain(new FirmoWorld());

        assertTrue(world.damaged(new MemberId("a"), 2.0D).isEmpty());
    }

    @Test
    void climbingSplitsAtEveryLevelAndTheTopTakesTheWholeAmount() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10.0D, "m1");
        world.living("b1", 10.0D, "m2");
        world.living("b2", 10.0D, "m2");
        world.living("c", 10.0D, "m3");
        world.firmoHierarchy("m1", "m2");
        world.firmoHierarchy("m2", "m3");

        List<Effect.DealDamage> dealt = FirmoWorld.of(world.damaged(new MemberId("c"), 6.0D), Effect.DealDamage.class);

        assertEquals(2, dealt.size(), "each of the two parents passes its half up to the top");
        dealt.forEach(d -> {
            assertEquals(new MemberId("a"), d.target());
            assertEquals(3.0D, d.amount(), EPS);
        });
        assertEquals(60.0D, world.paid, EPS, "30 to reach the parents, then 15 + 15 for the second step");
    }

    @Test
    void whenAStepCannotBePaidTheDamageStaysWhereItGot() {
        FirmoWorld world = chain(new FirmoWorld());
        world.balance = 20.0D; // enough for c -> b, not for b -> a

        List<Effect> effects = world.damaged(new MemberId("c"), 4.0D);

        List<Effect.DealDamage> dealt = FirmoWorld.of(effects, Effect.DealDamage.class);
        assertEquals(1, dealt.size());
        assertEquals(new MemberId("b"), dealt.get(0).target(), "b keeps it");
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
        assertEquals(1, world.graph.links().size(), "the first link still stands");
    }

    @Test
    void aBlockAtTheTopOfAChainWearsDownAndTakesTheWholeChainWithIt() {
        FirmoWorld world = new FirmoWorld();
        world.object("obsidiana", 8.0D, "m1");
        world.living("b", 10.0D, "m2");
        world.living("c", 10.0D, "m3");
        world.firmoHierarchy("m1", "m2");
        world.firmoHierarchy("m2", "m3");

        assertTrue(FirmoWorld.killed(world.damaged(new MemberId("c"), 5.0D)).isEmpty());
        List<MemberId> killed = FirmoWorld.killed(world.damaged(new MemberId("c"), 5.0D));

        assertTrue(killed.contains(new MemberId("obsidiana")));
        assertTrue(killed.contains(new MemberId("b")));
        assertTrue(killed.contains(new MemberId("c")));
    }

    @Test
    void damageThatClimbedIsNeverReflectedBackDown() {
        FirmoWorld world = chain(new FirmoWorld());
        world.damaged(new MemberId("c"), 4.0D);

        assertTrue(world.engine.handle(new LinkEvent.Damaged(new MemberId("a"), 4.0D, true)).isEmpty());
    }

    @Test
    void inAChainTheTopDyingTakesEveryoneBelowIt() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10.0D, "m1");
        world.living("b", 10.0D, "m2");
        world.living("c", 10.0D, "m3");
        world.firmoHierarchy("m1", "m2");
        world.firmoHierarchy("m2", "m3");

        List<MemberId> killed = FirmoWorld.killed(world.died(new MemberId("a")));

        assertEquals(List.of(new MemberId("b"), new MemberId("c")), killed);
        assertEquals(100.0D, world.paid, EPS, "10 HP + 10 HP left, 5 UMU each");
    }

    @Test
    void anObsidianParentOutlastsADirtParent() {
        FirmoWorld dirt = new FirmoWorld();
        dirt.object("terra", 1.0D, "pai");
        dirt.living("jogador", 20.0D, "filho");
        dirt.firmoHierarchy("pai", "filho");

        FirmoWorld obsidian = new FirmoWorld();
        obsidian.object("obsidiana", 24.0D, "pai");
        obsidian.living("jogador", 20.0D, "filho");
        obsidian.firmoHierarchy("pai", "filho");

        List<Effect> dirtHit = dirt.damaged(new MemberId("jogador"), 5.0D);
        List<Effect> obsidianHit = obsidian.damaged(new MemberId("jogador"), 5.0D);

        assertTrue(FirmoWorld.killed(dirtHit).contains(new MemberId("jogador")), "dirt breaks and takes the player along");
        assertTrue(FirmoWorld.killed(dirtHit).contains(new MemberId("terra")));
        assertTrue(FirmoWorld.killed(obsidianHit).isEmpty(), "obsidian shrugs off 5 HP");
        assertEquals(5.0D, obsidian.engine.wearOf(new MemberId("obsidiana")), EPS);
    }

    @Test
    void wearBuildsUpAcrossHitsUntilTheBlockBreaks() {
        FirmoWorld world = new FirmoWorld();
        world.object("obsidiana", 24.0D, "pai");
        world.living("jogador", 20.0D, "filho");
        world.firmoHierarchy("pai", "filho");

        for (int hit = 1; hit <= 4; hit++) {
            assertTrue(FirmoWorld.killed(world.damaged(new MemberId("jogador"), 5.0D)).isEmpty(), "hit " + hit);
        }
        List<MemberId> killed = FirmoWorld.killed(world.damaged(new MemberId("jogador"), 5.0D));

        assertTrue(killed.contains(new MemberId("obsidiana")));
        assertTrue(killed.contains(new MemberId("jogador")), "the block gave way, so the player follows");
    }

    @Test
    void damageIsSplitEquallyBetweenParents() {
        FirmoWorld world = new FirmoWorld();
        world.living("porco1", 10.0D, "pai");
        world.living("porco2", 10.0D, "pai");
        world.living("jogador", 20.0D, "filho");
        world.firmoHierarchy("pai", "filho");

        List<Effect.DealDamage> dealt = FirmoWorld.of(world.damaged(new MemberId("jogador"), 6.0D), Effect.DealDamage.class);

        assertEquals(2, dealt.size());
        dealt.forEach(d -> assertEquals(3.0D, d.amount(), EPS));
        assertEquals(30.0D, world.paid, EPS, "the total moved is what is charged");
    }

    @Test
    void anyParentDyingKillsTheChild() {
        FirmoWorld world = new FirmoWorld();
        world.living("porco1", 10.0D, "pai");
        world.living("porco2", 10.0D, "pai");
        world.living("jogador", 20.0D, "filho");
        world.firmoHierarchy("pai", "filho");

        assertEquals(List.of(new MemberId("jogador")), FirmoWorld.killed(world.died(new MemberId("porco1"))));
    }

    @Test
    void aLinkWithoutParentsIsDormantAndTheChildTakesItsOwnDamage() {
        FirmoWorld world = new FirmoWorld();
        world.living("jogador", 20.0D, "filho");
        world.firmoHierarchy("pai", "filho");

        assertTrue(world.damaged(new MemberId("jogador"), 4.0D).isEmpty());
    }

    @Test
    void ifTheOwnerCannotPayTheLinkBreaksAndTheDamageStands() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());
        world.balance = 10.0D;

        List<Effect> effects = world.damaged(new MemberId("jogador"), 4.0D);

        assertTrue(FirmoWorld.of(effects, Effect.CancelDamage.class).isEmpty());
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
        assertTrue(world.graph.links().isEmpty());
    }

    @Test
    void anOfflineOwnerSuspendsTheLink() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());
        world.online.remove(FirmoWorld.OWNER);

        assertTrue(world.damaged(new MemberId("jogador"), 4.0D).isEmpty());
        assertTrue(FirmoWorld.killed(world.died(new MemberId("porco"))).isEmpty());
        assertEquals(1, world.graph.links().size(), "suspended, not broken");
    }

    @Test
    void otherAspectsAreNotHandledByTheFirmoRules() {
        FirmoWorld world = new FirmoWorld();
        world.living("porco", 10.0D, "pai");
        world.living("jogador", 20.0D, "filho");
        world.graph.addLink(new Link(FirmoWorld.OWNER, Aspect.IGNI, "pai", "filho"));

        assertTrue(world.damaged(new MemberId("jogador"), 4.0D).isEmpty());
        assertTrue(world.died(new MemberId("porco")).stream().noneMatch(e -> e instanceof Effect.Kill));
    }

    @Test
    void zeroOrNegativeDamageIsIgnored() {
        FirmoWorld world = pigAndPlayer(new FirmoWorld());

        assertTrue(world.damaged(new MemberId("jogador"), 0.0D).isEmpty());
        assertTrue(world.damaged(new MemberId("jogador"), -3.0D).isEmpty());
    }

    @Test
    void theOrderMembersWereMarkedInDoesNotChangeTheAnswer() {
        FirmoWorld forward = new FirmoWorld();
        forward.living("porco1", 10.0D, "pai");
        forward.living("porco2", 10.0D, "pai");
        forward.living("jogador", 20.0D, "filho");
        forward.firmoHierarchy("pai", "filho");

        FirmoWorld backward = new FirmoWorld();
        backward.living("jogador", 20.0D, "filho");
        backward.living("porco2", 10.0D, "pai");
        backward.living("porco1", 10.0D, "pai");
        backward.firmoHierarchy("pai", "filho");

        assertEquals(forward.damaged(new MemberId("jogador"), 6.0D), backward.damaged(new MemberId("jogador"), 6.0D));
        assertEquals(forward.died(new MemberId("porco1")), backward.died(new MemberId("porco1")));
    }
}

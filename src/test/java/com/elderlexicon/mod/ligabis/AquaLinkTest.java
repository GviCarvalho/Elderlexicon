package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AquaLinkTest {

    private static final double EPS = 1.0E-9;

    private static Effect.SetAir air(String member, int value) {
        return new Effect.SetAir(new MemberId(member), value);
    }

    private static Effect.SetWet wet(String member, boolean value) {
        return new Effect.SetWet(new MemberId(member), value);
    }

    /** Three mirrored members with full lungs, already seen once. */
    private static FirmoWorld lungs() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "ar");
        world.living("b", 10, "ar");
        world.living("c", 10, "ar");
        world.aquaMirror("ar");
        world.observeAir("a=300 b=300 c=300");
        return world;
    }

    private static FirmoWorld parentAndChild() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai", 10, "p");
        world.living("filho", 10, "f");
        world.aquaHierarchy("p", "f");
        world.observeAir("pai=300 filho=300");
        return world;
    }

    // ---------------------------------------------------------------- breath

    @Test
    void inAMirrorWhatOneLosesTheOthersLoseToo() {
        FirmoWorld world = lungs();

        List<Effect.SetAir> aired = FirmoWorld.aired(world.observeAir("a=299 b=300 c=300"));

        assertEquals(List.of(air("b", 299), air("c", 299)), aired);
        assertEquals(0.02D, world.paid, EPS, "1 UMU per 100 ticks of air, 2 members, 1 tick each");
    }

    @Test
    void inAMirrorWhatOneRecoversTheOthersRecoverToo() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "ar");
        world.living("b", 10, "ar");
        world.living("c", 10, "ar");
        world.aquaMirror("ar");
        world.observeAir("a=200 b=200 c=200");

        List<Effect.SetAir> aired = FirmoWorld.aired(world.observeAir("a=204 b=200 c=200"));

        assertEquals(List.of(air("b", 204), air("c", 204)), aired);
    }

    @Test
    void airNeverGoesAboveTheMaximumOrBelowZero() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "ar");
        world.living("b", 10, "ar");
        world.living("c", 10, "ar");
        world.aquaMirror("ar");
        world.observeAir("a=296 b=298 c=300");

        // a recovers 4: b is topped off at 300 and c, already full, is left alone.
        assertEquals(List.of(air("b", 300)), FirmoWorld.aired(world.observeAir("a=300 b=298 c=300")));

        FirmoWorld drowning = new FirmoWorld();
        drowning.living("a", 10, "ar");
        drowning.living("b", 10, "ar");
        drowning.living("c", 10, "ar");
        drowning.aquaMirror("ar");
        drowning.observeAir("a=10 b=3 c=300");

        assertEquals(List.of(air("b", 0), air("c", 295)), FirmoWorld.aired(drowning.observeAir("a=5 b=3 c=300")));
    }

    @Test
    void twoMembersHoldingTheirBreathAtOnceDrainTheSharedLungsFaster() {
        FirmoWorld world = lungs();

        List<Effect.SetAir> aired = FirmoWorld.aired(world.observeAir("a=299 b=299 c=300"));

        // Each already lost 1 on its own and now receives the other's 1; c receives both.
        assertEquals(List.of(air("a", 298), air("b", 298), air("c", 298)), aired);
    }

    @Test
    void theCostFollowsHowMuchAirMoves() {
        FirmoWorld world = lungs();

        world.observeAir("a=200 b=300 c=300");

        assertEquals(2.0D, world.paid, EPS, "100 ticks to each of 2 members, 1 UMU per 100 ticks");
    }

    @Test
    void whatALinkSetIsNeverTakenForANewChange() {
        FirmoWorld world = lungs();
        world.observeAir("a=299 b=300 c=300");

        assertTrue(world.observeAir("a=299 b=299 c=299").isEmpty());
        assertTrue(world.observeAir("a=299 b=299 c=299").isEmpty());
    }

    @Test
    void aParentsChangeReachesTheChildButNotTheOtherWayAround() {
        FirmoWorld world = parentAndChild();

        assertEquals(List.of(air("filho", 298)), FirmoWorld.aired(world.observeAir("pai=298 filho=300")));
        world.observeAir("pai=298 filho=298");
        assertTrue(FirmoWorld.aired(world.observeAir("pai=298 filho=290")).isEmpty(), "a child drowning does not touch the parent");
    }

    @Test
    void aChangePassesDownAChain() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "m1");
        world.living("b", 10, "m2");
        world.living("c", 10, "m3");
        world.aquaHierarchy("m1", "m2");
        world.aquaHierarchy("m2", "m3");
        world.observeAir("a=300 b=300 c=300");

        assertEquals(List.of(air("b", 298), air("c", 298)), FirmoWorld.aired(world.observeAir("a=298 b=300 c=300")));
    }

    @Test
    void aChildOfTwoParentsGetsTheSumOfWhatBothChange() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai1", 10, "p");
        world.living("pai2", 10, "p");
        world.living("filho", 10, "f");
        world.aquaHierarchy("p", "f");
        world.observeAir("pai1=300 pai2=300 filho=300");

        assertEquals(List.of(air("filho", 298)), FirmoWorld.aired(world.observeAir("pai1=299 pai2=299 filho=300")));
    }

    @Test
    void aFirstObservationOnlySetsABaseline() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "ar");
        world.living("b", 10, "ar");
        world.aquaMirror("ar");

        assertTrue(world.observeAir("a=100 b=300").isEmpty());
    }

    @Test
    void aMemberThatHasNeverBeenReportedIsNotPartOfTheChannel() {
        FirmoWorld world = lungs();
        world.living("bloco", 10, "ar"); // marked but never reports air

        List<Effect.SetAir> aired = FirmoWorld.aired(world.observeAir("a=299 b=300 c=300"));

        assertEquals(List.of(air("b", 299), air("c", 299)), aired);
    }

    @Test
    void ifTheOwnerCannotPayTheLinkBreaksAndNoAirMoves() {
        FirmoWorld world = lungs();
        world.balance = 0.0D;

        List<Effect> effects = world.observeAir("a=250 b=300 c=300");

        assertTrue(FirmoWorld.aired(effects).isEmpty());
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
        assertTrue(world.graph.links().isEmpty());
    }

    @Test
    void anOfflineOwnerSuspendsTheLinkAndNothingIsCaughtUpLater() {
        FirmoWorld world = lungs();
        world.online.remove(FirmoWorld.OWNER);

        assertTrue(world.observeAir("a=250 b=300 c=300").isEmpty());

        world.online.add(FirmoWorld.OWNER);
        assertTrue(world.observeAir("a=250 b=300 c=300").isEmpty(), "the loss happened while suspended");
        assertEquals(1, world.graph.links().size());
    }

    @Test
    void whenTheWorldCouldNotApplyAChangeItCanSayWhatTheRealAirIs() {
        FirmoWorld world = lungs();
        world.observeAir("a=299 b=300 c=300");
        world.engine.syncAir(new MemberId("b"), 300);

        assertTrue(world.observeAir("a=299 b=300 c=299").isEmpty());
    }

    @Test
    void theOrderObservationsArriveInDoesNotChangeTheAnswer() {
        FirmoWorld forward = lungs();
        FirmoWorld backward = lungs();

        Map<MemberId, Integer> shuffled = new LinkedHashMap<>();
        shuffled.put(new MemberId("c"), 300);
        shuffled.put(new MemberId("b"), 299);
        shuffled.put(new MemberId("a"), 299);

        assertEquals(forward.observeAir("a=299 b=299 c=300"), backward.engine.observeAir(shuffled));
    }

    @Test
    void aDeadMemberIsForgotten() {
        FirmoWorld world = lungs();
        world.died(new MemberId("a"));

        assertTrue(world.observeAir("a=100 b=300 c=300").isEmpty());
    }

    @Test
    void theSameMirrorCoversBlocksAsWetnessButNeverMixesTheTwoChannels() {
        FirmoWorld world = lungs();
        world.object("bloco1", 5, "ar");
        world.object("bloco2", 5, "ar");
        world.observeWet("bloco1- bloco2-");

        List<Effect.SetWet> wetted = FirmoWorld.wetted(world.observeWet("bloco1+ bloco2-"));

        assertEquals(List.of(wet("bloco2", true)), wetted, "only members reported as blocks are soaked, never a, b or c");
        assertTrue(FirmoWorld.aired(world.observeAir("a=300 b=300 c=300")).isEmpty(), "and no air moved");
    }

    // ------------------------------------------------------------ wet blocks

    private static FirmoWorld wetMirror() {
        FirmoWorld world = new FirmoWorld();
        world.object("x", 5, "agua");
        world.object("y", 5, "agua");
        world.object("z", 5, "agua");
        world.aquaMirror("agua");
        world.observeWet("x- y- z-");
        return world;
    }

    @Test
    void inAMirrorABlockThatBecomesWaterloggedSoaksTheOthers() {
        FirmoWorld world = wetMirror();

        List<Effect.SetWet> wetted = FirmoWorld.wetted(world.observeWet("x+ y- z-"));

        assertEquals(List.of(wet("y", true), wet("z", true)), wetted);
        assertEquals(2.0D, world.paid, EPS, "1 UMU per block changed");
    }

    @Test
    void inAMirrorADryingBlockDriesTheOthers() {
        FirmoWorld world = wetMirror();
        world.observeWet("x+ y- z-");
        world.observeWet("x+ y+ z+");

        assertEquals(List.of(wet("y", false), wet("z", false)), FirmoWorld.wetted(world.observeWet("x- y+ z+")));
    }

    @Test
    void inAHierarchyOnlyTheParentsChangeReachesTheChildBlocks() {
        FirmoWorld world = new FirmoWorld();
        world.object("pai", 5, "p");
        world.object("filho", 5, "f");
        world.aquaHierarchy("p", "f");
        world.observeWet("pai- filho-");

        assertTrue(FirmoWorld.wetted(world.observeWet("pai- filho+")).isEmpty(), "a soaked child leaves the parent dry");

        FirmoWorld other = new FirmoWorld();
        other.object("pai", 5, "p");
        other.object("filho", 5, "f");
        other.aquaHierarchy("p", "f");
        other.observeWet("pai- filho-");

        assertEquals(List.of(wet("filho", true)), FirmoWorld.wetted(other.observeWet("pai+ filho-")));
    }

    @Test
    void whenSomeBlocksSoakAndOthersDryInTheSameTickWetWins() {
        FirmoWorld world = new FirmoWorld();
        world.object("x", 5, "agua");
        world.object("y", 5, "agua");
        world.aquaMirror("agua");
        world.observeWet("x- y+");

        List<Effect.SetWet> wetted = FirmoWorld.wetted(world.observeWet("x+ y-"));

        assertEquals(List.of(wet("y", true)), wetted);
    }

    @Test
    void aLinkThatBreaksLeavesTheBlocksAsTheyWere() {
        FirmoWorld world = wetMirror();
        world.observeWet("x+ y- z-");
        world.observeWet("x+ y+ z+");
        world.graph.links().forEach(world.graph::removeLink);

        assertTrue(world.observeWet("x- y+ z+").isEmpty());
    }

    @Test
    void wetnessLinksIgnoreHeat() {
        FirmoWorld world = wetMirror();
        world.observe("x- y- z-");

        assertTrue(world.observe("x+ y- z-").isEmpty(), "an aqua link does not react to heat");
    }
}

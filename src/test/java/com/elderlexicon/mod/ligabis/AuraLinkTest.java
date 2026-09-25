package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuraLinkTest {

    private static final double EPS = 1.0E-9;

    private static Effect.Displace push(String member, double x, double y, double z) {
        return new Effect.Displace(new MemberId(member), new Vec(x, y, z));
    }

    private static FirmoWorld parentAndChild() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai", 10, "p");
        world.living("filho", 10, "f");
        world.auraHierarchy("p", "f");
        world.observeMotion("pai=0,0,0 filho=0,0,0");
        return world;
    }

    private static FirmoWorld mirror() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "vento");
        world.living("b", 10, "vento");
        world.living("c", 10, "vento");
        world.auraMirror("vento");
        world.observeMotion("a=0,0,0 b=0,0,0 c=0,0,0");
        return world;
    }

    private static FirmoWorld chain() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "m1");
        world.living("b", 10, "m2");
        world.living("c", 10, "m3");
        world.auraHierarchy("m1", "m2");
        world.auraHierarchy("m2", "m3");
        world.observeMotion("a=0,0,0 b=0,0,0 c=0,0,0");
        return world;
    }

    // -------------------------------------------------------------- hierarchy

    @Test
    void whenTheParentMovesTheChildIsMovedTheSame() {
        FirmoWorld world = parentAndChild();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("pai=1,0,0 filho=0,0,0"));

        assertEquals(List.of(push("filho", 1, 0, 0)), moves);
        assertEquals(1.0D, world.paid, EPS, "1 UMU per block of correction");
    }

    @Test
    void whenTheParentStandsStillWhateverTheChildTriesIsCancelled() {
        FirmoWorld world = parentAndChild();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("pai=0,0,0 filho=2,0,0"));

        assertEquals(List.of(push("filho", -2, 0, 0)), moves, "held where it was, like the parent");
        assertEquals(2.0D, world.paid, EPS);
    }

    @Test
    void aChildThatAlreadyMovesLikeItsParentCostsNothing() {
        FirmoWorld world = parentAndChild();

        assertTrue(world.observeMotion("pai=1,0,0 filho=1,0,0").isEmpty());
        assertEquals(0.0D, world.paid, EPS);
    }

    @Test
    void onlyTheMissingPartIsCorrected() {
        FirmoWorld world = parentAndChild();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("pai=1,0,0 filho=0.5,0,0"));

        assertEquals(List.of(push("filho", 0.5, 0, 0)), moves);
        assertEquals(0.5D, world.paid, EPS);
    }

    @Test
    void theParentIsNeverMovedByItsChild() {
        FirmoWorld world = parentAndChild();

        for (String spec : List.of("pai=0,0,0 filho=3,0,0", "pai=0,0,0 filho=3,5,0", "pai=0,0,0 filho=-4,5,1")) {
            assertTrue(FirmoWorld.displaced(world.observeMotion(spec)).stream()
                    .noneMatch(d -> d.target().equals(new MemberId("pai"))));
        }
    }

    @Test
    void movementInAnyDirectionIsFollowed() {
        FirmoWorld world = parentAndChild();

        assertEquals(List.of(push("filho", 1, 2, 3)), FirmoWorld.displaced(world.observeMotion("pai=1,2,3 filho=0,0,0")));
        assertEquals(14.0D, Math.pow(world.paid, 2), EPS, "sqrt(1+4+9) blocks");
    }

    @Test
    void aChainIsSettledFromTheTopDown() {
        FirmoWorld world = chain();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("a=1,0,0 b=0,0,0 c=0,0,0"));

        assertEquals(List.of(push("b", 1, 0, 0), push("c", 1, 0, 0)), moves);
        assertEquals(2.0D, world.paid, EPS, "one block per step down the chain");
    }

    @Test
    void aGrandchildFollowsWhatItsParentWasMadeToDo() {
        FirmoWorld world = chain();

        // The middle one walks off by itself while the top stays put: it is held, and so is the one below.
        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("a=0,0,0 b=2,0,0 c=0,0,0"));

        assertEquals(List.of(push("b", -2, 0, 0)), moves);
        assertEquals(2.0D, world.paid, EPS);
    }

    @Test
    void theOrderLinksWereDeclaredInDoesNotMatterForAChain() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "m1");
        world.living("b", 10, "m2");
        world.living("c", 10, "m3");
        world.auraHierarchy("m2", "m3"); // the lower link first
        world.auraHierarchy("m1", "m2");
        world.observeMotion("a=0,0,0 b=0,0,0 c=0,0,0");

        assertEquals(List.of(push("b", 1, 0, 0), push("c", 1, 0, 0)),
                FirmoWorld.displaced(world.observeMotion("a=1,0,0 b=0,0,0 c=0,0,0")));
    }

    @Test
    void aChildOfTwoParentsFollowsTheSumOfTheirMovement() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai1", 10, "p");
        world.living("pai2", 10, "p");
        world.living("filho", 10, "f");
        world.auraHierarchy("p", "f");
        world.observeMotion("pai1=0,0,0 pai2=0,0,0 filho=0,0,0");

        assertEquals(List.of(push("filho", 3, 0, 0)), FirmoWorld.displaced(world.observeMotion("pai1=1,0,0 pai2=2,0,0 filho=0,0,0")));
    }

    // ----------------------------------------------------------------- mirror

    @Test
    void inAMirrorWhoeverMovesTakesTheOthersAlong() {
        FirmoWorld world = mirror();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("a=1,0,0 b=0,0,0 c=0,0,0"));

        assertEquals(List.of(push("b", 1, 0, 0), push("c", 1, 0, 0)), moves);
        assertEquals(2.0D, world.paid, EPS, "each of the two followers is corrected by a block");
    }

    @Test
    void twoMembersWalkingTheSameWayEachGoTwiceAsFar() {
        FirmoWorld world = mirror();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("a=1,0,0 b=1,0,0 c=0,0,0"));

        assertEquals(List.of(push("a", 1, 0, 0), push("b", 1, 0, 0), push("c", 2, 0, 0)), moves);
        assertEquals(4.0D, world.paid, EPS);
    }

    @Test
    void twoMembersWalkingOppositeWaysLockEachOtherInPlace() {
        FirmoWorld world = mirror();

        List<Effect.Displace> moves = FirmoWorld.displaced(world.observeMotion("a=1,0,0 b=-1,0,0 c=0,0,0"));

        assertEquals(List.of(push("a", -1, 0, 0), push("b", 1, 0, 0)), moves, "each is pushed back to where it started");
        assertEquals(2.0D, world.paid, EPS);
    }

    @Test
    void theCostOfAMirrorFollowsTheDistanceCorrected() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "vento");
        world.living("b", 10, "vento");
        world.auraMirror("vento");
        world.observeMotion("a=0,0,0 b=0,0,0");

        world.observeMotion("a=3,4,0 b=0,0,0");

        assertEquals(5.0D, world.paid, EPS, "b is moved 5 blocks");
    }

    // ------------------------------------------------------------------ common

    @Test
    void whatALinkMovedIsNeverTakenForNewMovement() {
        FirmoWorld world = parentAndChild();
        world.observeMotion("pai=1,0,0 filho=0,0,0"); // the child is pushed to x = 1

        assertTrue(world.observeMotion("pai=1,0,0 filho=1,0,0").isEmpty());
        assertTrue(world.observeMotion("pai=1,0,0 filho=1,0,0").isEmpty());
    }

    @Test
    void aFirstObservationOnlySetsABaseline() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai", 10, "p");
        world.living("filho", 10, "f");
        world.auraHierarchy("p", "f");

        assertTrue(world.observeMotion("pai=5,0,0 filho=-9,0,0").isEmpty());
    }

    @Test
    void movementTooSmallToMatterIsIgnored() {
        FirmoWorld world = parentAndChild();

        assertTrue(world.observeMotion("pai=0.0005,0,0 filho=0,0.0005,0").isEmpty());
        assertEquals(0.0D, world.paid, EPS);
    }

    @Test
    void aMemberThatWasNotReportedThisTickIsLeftAlone() {
        FirmoWorld world = parentAndChild();

        assertTrue(world.observeMotion("pai=1,0,0").isEmpty(), "the child was not reported, so it is not moved");
    }

    @Test
    void ifTheOwnerCannotPayTheLinkBreaksAndNobodyIsMoved() {
        FirmoWorld world = parentAndChild();
        world.balance = 0.5D;

        List<Effect> effects = world.observeMotion("pai=1,0,0 filho=0,0,0");

        assertTrue(FirmoWorld.displaced(effects).isEmpty());
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
        assertTrue(world.graph.links().isEmpty());
    }

    @Test
    void anOfflineOwnerSuspendsTheLinkAndNothingIsCaughtUpLater() {
        FirmoWorld world = parentAndChild();
        world.online.remove(FirmoWorld.OWNER);

        assertTrue(world.observeMotion("pai=1,0,0 filho=0,0,0").isEmpty());

        world.online.add(FirmoWorld.OWNER);
        assertTrue(world.observeMotion("pai=1,0,0 filho=0,0,0").isEmpty(), "the child is not dragged to make up the distance");
        assertEquals(1, world.graph.links().size());
    }

    @Test
    void whenTheWorldCouldNotMoveAMemberItCanSayWhereItReallyIs() {
        FirmoWorld world = parentAndChild();
        world.observeMotion("pai=1,0,0 filho=0,0,0"); // the child was meant to go to x = 1 but did not
        world.engine.syncMotion(new MemberId("filho"), new Vec(0, 0, 0));

        assertTrue(world.observeMotion("pai=1,0,0 filho=0,0,0").isEmpty(), "not read as the child moving back");
    }

    @Test
    void aLinkThatIsRemovedLeavesEveryoneWhereTheyAre() {
        FirmoWorld world = parentAndChild();
        world.graph.links().forEach(world.graph::removeLink);

        assertTrue(world.observeMotion("pai=4,0,0 filho=-4,0,0").isEmpty());
    }

    @Test
    void aDeadMemberIsForgotten() {
        FirmoWorld world = parentAndChild();
        world.died(new MemberId("pai"));

        assertTrue(world.observeMotion("pai=9,0,0 filho=0,0,0").isEmpty());
    }

    @Test
    void theOrderObservationsArriveInDoesNotChangeTheAnswer() {
        FirmoWorld forward = mirror();
        FirmoWorld backward = mirror();

        Map<MemberId, Vec> shuffled = new LinkedHashMap<>();
        shuffled.put(new MemberId("c"), new Vec(0, 0, 0));
        shuffled.put(new MemberId("b"), new Vec(1, 0, 0));
        shuffled.put(new MemberId("a"), new Vec(1, 0, 0));

        assertEquals(forward.observeMotion("a=1,0,0 b=1,0,0 c=0,0,0"), backward.engine.observeMotion(shuffled));
    }

    @Test
    void otherAspectsAreNotHandledByTheAuraRules() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai", 10, "p");
        world.living("filho", 10, "f");
        world.igniHierarchy("p", "f");
        world.observeMotion("pai=0,0,0 filho=0,0,0");

        assertTrue(world.observeMotion("pai=3,0,0 filho=0,0,0").isEmpty());
    }
}

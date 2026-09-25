package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IgniLinkTest {

    private static final double EPS = 1.0E-9;

    private static Effect.SetHeat set(String member, boolean hot) {
        return new Effect.SetHeat(new MemberId(member), hot);
    }

    /** Three members mirrored on one mark, all cold and already seen once. */
    private static FirmoWorld mirror() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "fogo");
        world.living("b", 10, "fogo");
        world.living("c", 10, "fogo");
        world.igniMirror("fogo");
        world.observe("a- b- c-");
        return world;
    }

    /** A parent and a child, both cold and already seen once. */
    private static FirmoWorld parentAndChild() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai", 10, "p");
        world.living("filho", 10, "f");
        world.igniHierarchy("p", "f");
        world.observe("pai- filho-");
        return world;
    }

    @Test
    void inAMirrorWhoeverCatchesFireTakesTheOthersWithIt() {
        FirmoWorld world = mirror();

        List<Effect.SetHeat> heated = FirmoWorld.heated(world.observe("a+ b- c-"));

        assertEquals(List.of(set("b", true), set("c", true)), heated);
        assertEquals(2.0D, world.paid, EPS, "1 UMU per member set on fire");
    }

    @Test
    void inAMirrorGoingOutPutsTheOthersOutToo() {
        FirmoWorld world = mirror();
        world.observe("a+ b- c-");
        world.observe("a+ b+ c+");

        List<Effect.SetHeat> heated = FirmoWorld.heated(world.observe("a- b+ c+"));

        assertEquals(List.of(set("b", false), set("c", false)), heated);
    }

    @Test
    void aMemberThatWasAlreadyBurningAddsNothingToTheCost() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "fogo");
        world.living("b", 10, "fogo");
        world.living("c", 10, "fogo");
        world.igniMirror("fogo");
        world.observe("a- b+ c-"); // first sighting: b already burned, so it is only a baseline

        List<Effect.SetHeat> heated = FirmoWorld.heated(world.observe("a+ b+ c-"));

        assertEquals(List.of(set("c", true)), heated);
        assertEquals(1.0D, world.paid, EPS);
    }

    @Test
    void aParentCatchingFireSetsTheChildOnFireAndGoingOutPutsItOut() {
        FirmoWorld world = parentAndChild();

        assertEquals(List.of(set("filho", true)), FirmoWorld.heated(world.observe("pai+ filho-")));
        world.observe("pai+ filho+");
        assertEquals(List.of(set("filho", false)), FirmoWorld.heated(world.observe("pai- filho+")));
    }

    @Test
    void aChildBurningLeavesTheParentAlone() {
        FirmoWorld world = parentAndChild();

        assertTrue(FirmoWorld.heated(world.observe("pai- filho+")).isEmpty());
        assertTrue(FirmoWorld.heated(world.observe("pai- filho-")).isEmpty(), "and going out changes nothing either");
    }

    @Test
    void aChildPutOutWhileTheParentBurnsIsNotRelit() {
        FirmoWorld world = parentAndChild();
        world.observe("pai+ filho-");
        world.observe("pai+ filho+");

        assertTrue(FirmoWorld.heated(world.observe("pai+ filho-")).isEmpty(), "the link reacts to changes of the parent only");
    }

    @Test
    void whatALinkAppliedIsNotTakenForANewAction() {
        FirmoWorld world = mirror();
        world.observe("a+ b- c-");

        // The world did what was asked: b and c now burn. Seeing that is not a new ignition.
        assertTrue(world.observe("a+ b+ c+").isEmpty());
        assertTrue(world.observe("a+ b+ c+").isEmpty());
    }

    @Test
    void whenSomeIgniteAndOthersGoOutInTheSameTickIgnitingWins() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "fogo");
        world.living("b", 10, "fogo");
        world.living("c", 10, "fogo");
        world.igniMirror("fogo");
        world.observe("a- b+ c-");

        List<Effect.SetHeat> heated = FirmoWorld.heated(world.observe("a+ b- c-"));

        assertTrue(heated.contains(set("b", true)), "b is set back on fire");
        assertTrue(heated.contains(set("c", true)));
        assertTrue(heated.stream().noneMatch(e -> !e.hot()), "nobody is put out");
    }

    @Test
    void twoParentsChangingOppositeWaysLeaveTheChildBurning() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai1", 10, "p");
        world.living("pai2", 10, "p");
        world.living("filho", 10, "f");
        world.igniHierarchy("p", "f");
        world.observe("pai1- pai2+ filho-");

        List<Effect.SetHeat> heated = FirmoWorld.heated(world.observe("pai1+ pai2- filho-"));

        assertEquals(List.of(set("filho", true)), heated);
    }

    @Test
    void aChangePassesDownAChainButNeverUp() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "m1");
        world.living("b", 10, "m2");
        world.living("c", 10, "m3");
        world.igniHierarchy("m1", "m2");
        world.igniHierarchy("m2", "m3");
        world.observe("a- b- c-");

        assertEquals(List.of(set("b", true), set("c", true)), FirmoWorld.heated(world.observe("a+ b- c-")));

        FirmoWorld lower = new FirmoWorld();
        lower.living("a", 10, "m1");
        lower.living("b", 10, "m2");
        lower.living("c", 10, "m3");
        lower.igniHierarchy("m1", "m2");
        lower.igniHierarchy("m2", "m3");
        lower.observe("a- b- c-");

        assertEquals(List.of(set("c", true)), FirmoWorld.heated(lower.observe("a- b+ c-")), "the top is not touched");
    }

    @Test
    void aMirrorInsideAChainSpreadsBothWays() {
        FirmoWorld world = new FirmoWorld();
        world.living("pai", 10, "p");
        world.living("f1", 10, "f");
        world.living("f2", 10, "f");
        world.igniHierarchy("p", "f");
        world.igniMirror("f");
        world.observe("pai- f1- f2-");

        assertEquals(List.of(set("f1", true), set("f2", true)), FirmoWorld.heated(world.observe("pai+ f1- f2-")));

        FirmoWorld other = new FirmoWorld();
        other.living("pai", 10, "p");
        other.living("f1", 10, "f");
        other.living("f2", 10, "f");
        other.igniHierarchy("p", "f");
        other.igniMirror("f");
        other.observe("pai- f1- f2-");

        assertEquals(List.of(set("f2", true)), FirmoWorld.heated(other.observe("pai- f1+ f2-")), "siblings mirror, the parent stays");
    }

    @Test
    void aFirstObservationOnlySetsABaseline() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "fogo");
        world.living("b", 10, "fogo");
        world.igniMirror("fogo");

        assertTrue(world.observe("a+ b-").isEmpty(), "nothing is reflected until somebody changes");
    }

    @Test
    void ifTheOwnerCannotPayTheLinkBreaksAndNothingIsSet() {
        FirmoWorld world = mirror();
        world.balance = 1.0D;

        List<Effect> effects = world.observe("a+ b- c-");

        assertTrue(FirmoWorld.heated(effects).isEmpty());
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
        assertTrue(world.graph.links().isEmpty());
    }

    @Test
    void anOfflineOwnerSuspendsTheLinkAndNothingIsCaughtUpLater() {
        FirmoWorld world = mirror();
        world.online.remove(FirmoWorld.OWNER);

        assertTrue(world.observe("a+ b- c-").isEmpty());

        world.online.add(FirmoWorld.OWNER);
        assertTrue(world.observe("a+ b- c-").isEmpty(), "the change happened while suspended, so it is not replayed");
        assertEquals(1, world.graph.links().size());
    }

    @Test
    void aLinkThatIsRemovedLeavesEveryoneInTheirLastState() {
        FirmoWorld world = mirror();
        world.observe("a+ b- c-");
        world.observe("a+ b+ c+");
        world.graph.links().forEach(world.graph::removeLink);

        // Nothing reverts and nothing is reflected any more: each member simply stays as it was.
        assertTrue(world.observe("a- b+ c+").isEmpty());
        assertTrue(world.observe("a- b+ c+").isEmpty());
    }

    @Test
    void aLinkThatBreaksBecauseOfCostPutsNothingOutEither() {
        FirmoWorld world = mirror();
        world.observe("a+ b- c-");
        world.observe("a+ b+ c+");
        world.balance = 0.0D;

        List<Effect> effects = world.observe("a- b+ c+");

        assertTrue(FirmoWorld.heated(effects).isEmpty(), "b and c keep burning");
        assertEquals(1, FirmoWorld.of(effects, Effect.LinkBroken.class).size());
    }

    @Test
    void whenTheWorldCouldNotApplyAChangeItCanSayWhatTheRealStateIs() {
        FirmoWorld world = mirror();
        world.observe("a+ b- c-");
        // b could not be lit (say, griefing is off): the world says so instead of letting it look like an action.
        world.engine.syncHeat(new MemberId("b"), false);

        assertTrue(world.observe("a+ b- c+").isEmpty());
    }

    @Test
    void aDeadMemberIsForgotten() {
        FirmoWorld world = mirror();
        world.died(new MemberId("a"));

        assertFalse(world.graph.hasAnyMark(new MemberId("a")));
        assertTrue(world.observe("a+ b- c-").isEmpty(), "a is no longer part of anything");
    }

    @Test
    void theOrderObservationsArriveInDoesNotChangeTheAnswer() {
        FirmoWorld forward = mirror();
        FirmoWorld backward = mirror();

        Map<MemberId, Boolean> shuffled = new LinkedHashMap<>();
        shuffled.put(new MemberId("c"), false);
        shuffled.put(new MemberId("b"), false);
        shuffled.put(new MemberId("a"), true);

        assertEquals(forward.observe("a+ b- c-"), backward.engine.observeHeat(shuffled));
    }

    @Test
    void igniLinksDoNotReactToDamageAndFirmoLinksDoNotReactToHeat() {
        FirmoWorld world = new FirmoWorld();
        world.living("a", 10, "x");
        world.living("b", 10, "x");
        world.igniMirror("x");
        world.observe("a- b-");

        assertTrue(world.damaged(new MemberId("a"), 4.0D).isEmpty());
        assertTrue(FirmoWorld.killed(world.died(new MemberId("a"))).isEmpty());

        FirmoWorld firmo = new FirmoWorld();
        firmo.living("a", 10, "x");
        firmo.living("b", 10, "x");
        firmo.firmoMirror("x");
        firmo.observe("a- b-");

        assertTrue(firmo.observe("a+ b-").isEmpty());
    }
}

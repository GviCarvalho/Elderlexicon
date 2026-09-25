package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LinkGraphTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void aSingleMarkIsAMirrorAndSeveralMarksAreAChainOfHierarchies() {
        List<Link> mirror = Link.chain(OWNER, Aspect.FIRMO, List.of("casa"));
        List<Link> chain = Link.chain(OWNER, Aspect.FIRMO, List.of("m1", "m2", "m3"));

        assertEquals(1, mirror.size());
        assertTrue(mirror.get(0).isMirror());
        assertEquals(2, chain.size());
        assertEquals("m1", chain.get(0).first());
        assertEquals("m2", chain.get(0).second());
        assertEquals("m2", chain.get(1).first());
        assertEquals("m3", chain.get(1).second());
    }

    @Test
    void marksAreCaseInsensitive() {
        LinkGraph graph = new LinkGraph();
        MemberId member = new MemberId("x");
        graph.mark(member, "  Casa ");

        assertTrue(graph.hasMark(member, "CASA"));
        assertEquals(List.of(member), graph.members("casa"));
        assertEquals("casa", new Link(OWNER, Aspect.FIRMO, " CASA ", null).first());
    }

    @Test
    void aBlankMarkIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Link(OWNER, Aspect.FIRMO, "   ", null));
    }

    @Test
    void duplicateLinksAreRejected() {
        LinkGraph graph = new LinkGraph();

        assertTrue(graph.addLink(new Link(OWNER, Aspect.FIRMO, "a", "b")));
        assertFalse(graph.addLink(new Link(OWNER, Aspect.FIRMO, "a", "b")));
    }

    @Test
    void hierarchyLoopsAreRejected() {
        LinkGraph graph = new LinkGraph();

        assertFalse(graph.addLink(new Link(OWNER, Aspect.FIRMO, "a", "a")), "a mark cannot be its own parent");
        assertTrue(graph.addLink(new Link(OWNER, Aspect.FIRMO, "a", "b")));
        assertTrue(graph.addLink(new Link(OWNER, Aspect.FIRMO, "b", "c")));
        assertFalse(graph.addLink(new Link(OWNER, Aspect.FIRMO, "b", "a")), "direct loop");
        assertFalse(graph.addLink(new Link(OWNER, Aspect.FIRMO, "c", "a")), "loop through the chain");
    }

    @Test
    void theSameShapeIsFineForAnotherAspectAndAMirrorMayShareAMark() {
        LinkGraph graph = new LinkGraph();
        graph.addLink(new Link(OWNER, Aspect.FIRMO, "a", "b"));

        assertTrue(graph.addLink(new Link(OWNER, Aspect.IGNI, "b", "a")), "loops are per aspect");
        assertTrue(graph.addLink(new Link(OWNER, Aspect.FIRMO, "a", null)), "a mirror on a parent mark is allowed");
    }

    @Test
    void membersComeBackInStableOrderWhateverOrderTheyWereAddedIn() {
        LinkGraph one = new LinkGraph();
        LinkGraph two = new LinkGraph();
        for (String key : List.of("c", "a", "b")) {
            one.mark(new MemberId(key), "m");
        }
        for (String key : List.of("b", "c", "a")) {
            two.mark(new MemberId(key), "m");
        }

        assertEquals(one.members("m"), two.members("m"));
        assertEquals(List.of(new MemberId("a"), new MemberId("b"), new MemberId("c")), one.members("m"));
    }

    @Test
    void forgettingAMemberDropsAllItsMarks() {
        LinkGraph graph = new LinkGraph();
        MemberId member = new MemberId("x");
        graph.mark(member, "a");
        graph.mark(member, "b");

        graph.forget(member);

        assertFalse(graph.hasAnyMark(member));
        assertTrue(graph.members("a").isEmpty());
        assertTrue(graph.members("b").isEmpty());
    }

    @Test
    void unmarkingOnlyRemovesThatMark() {
        LinkGraph graph = new LinkGraph();
        MemberId member = new MemberId("x");
        graph.mark(member, "a");
        graph.mark(member, "b");

        graph.unmark(member, "a");

        assertFalse(graph.hasMark(member, "a"));
        assertTrue(graph.hasMark(member, "b"));
    }
}

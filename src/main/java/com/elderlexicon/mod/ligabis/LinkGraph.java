package com.elderlexicon.mod.ligabis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which links exist and which members carry which marks. Everything is returned in a stable order,
 * so the engine gives the same answer however things were added.
 */
public final class LinkGraph {

    private final Set<Link> links = new TreeSet<>();
    private final Map<String, Set<MemberId>> membersByMark = new HashMap<>();
    private final Map<MemberId, Set<String>> marksByMember = new HashMap<>();

    /**
     * @return false when the link already exists, or would make a hierarchy loop back on itself
     *         (a mark that is its own ancestor)
     */
    public boolean addLink(Link link) {
        if (links.contains(link)) {
            return false;
        }
        if (!link.isMirror()) {
            if (link.first().equals(link.second()) || reaches(link, link.second(), link.first())) {
                return false;
            }
        }
        links.add(link);
        return true;
    }

    public boolean removeLink(Link link) {
        return links.remove(link);
    }

    public List<Link> links() {
        return new ArrayList<>(links);
    }

    public void mark(MemberId member, String mark) {
        String normalized = Link.normalize(mark);
        if (normalized == null) {
            return;
        }
        membersByMark.computeIfAbsent(normalized, key -> new TreeSet<>()).add(member);
        marksByMember.computeIfAbsent(member, key -> new TreeSet<>()).add(normalized);
    }

    public void unmark(MemberId member, String mark) {
        String normalized = Link.normalize(mark);
        if (normalized == null) {
            return;
        }
        Set<MemberId> holders = membersByMark.get(normalized);
        if (holders != null) {
            holders.remove(member);
            if (holders.isEmpty()) {
                membersByMark.remove(normalized);
            }
        }
        Set<String> marks = marksByMember.get(member);
        if (marks != null) {
            marks.remove(normalized);
            if (marks.isEmpty()) {
                marksByMember.remove(member);
            }
        }
    }

    /** Drops every mark of a member: what happens when it dies or is destroyed. */
    public void forget(MemberId member) {
        Set<String> marks = marksByMember.get(member);
        if (marks == null) {
            return;
        }
        for (String mark : new ArrayList<>(marks)) {
            unmark(member, mark);
        }
    }

    public boolean hasMark(MemberId member, String mark) {
        Set<String> marks = marksByMember.get(member);
        return marks != null && marks.contains(Link.normalize(mark));
    }

    public boolean hasAnyMark(MemberId member) {
        return marksByMember.containsKey(member);
    }

    public List<MemberId> members(String mark) {
        Set<MemberId> holders = membersByMark.get(Link.normalize(mark));
        return holders == null ? List.of() : new ArrayList<>(holders);
    }

    /** Every member that carries at least one mark, of any aspect. */
    public Set<MemberId> markedMembers() {
        return new TreeSet<>(marksByMember.keySet());
    }

    /** True when {@code from} reaches {@code target} through hierarchies of the same aspect. */
    private boolean reaches(Link candidate, String from, String target) {
        Set<String> seen = new HashSet<>();
        List<String> stack = new ArrayList<>(List.of(from));
        while (!stack.isEmpty()) {
            String current = stack.remove(stack.size() - 1);
            if (current.equals(target)) {
                return true;
            }
            if (!seen.add(current)) {
                continue;
            }
            for (Link link : links) {
                if (!link.isMirror() && link.aspect() == candidate.aspect() && link.first().equals(current)) {
                    stack.add(link.second());
                }
            }
        }
        return false;
    }
}

package com.elderlexicon.mod.ligabis;

/**
 * How a member takes damage. A {@link Kind#LIVING} member has health of its own, so the world applies
 * damage to it. An {@link Kind#OBJECT} (a block, an item) has none, so the engine wears it down
 * against its {@code capacity} and destroys it when that runs out. That is what makes an obsidian
 * parent last much longer than a dirt one.
 */
public record MemberProfile(Kind kind, double capacity) {

    public enum Kind { LIVING, OBJECT }

    public MemberProfile {
        if (kind == Kind.OBJECT && capacity <= 0.0D) {
            throw new IllegalArgumentException("An object needs a positive capacity");
        }
    }

    public static MemberProfile living() {
        return new MemberProfile(Kind.LIVING, 0.0D);
    }

    public static MemberProfile object(double capacity) {
        return new MemberProfile(Kind.OBJECT, capacity);
    }
}

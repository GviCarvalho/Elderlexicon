package com.elderlexicon.mod.ligabis;

/** Something that happened in the world that a link may care about. */
public sealed interface LinkEvent {

    /**
     * A member took {@code amount} of damage, already reduced by its own armor. {@code fromLink} is true
     * for damage the engine itself asked for; such damage is never reflected again.
     */
    record Damaged(MemberId member, double amount, boolean fromLink) implements LinkEvent { }

    /** A member died or was destroyed, whatever the cause (including damage a link sent). */
    record Died(MemberId member) implements LinkEvent { }
}

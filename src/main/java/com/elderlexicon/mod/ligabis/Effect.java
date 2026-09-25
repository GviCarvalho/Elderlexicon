package com.elderlexicon.mod.ligabis;

import java.util.UUID;

/** What the engine asks the world to do in answer to an event. */
public sealed interface Effect {

    /** The damage that triggered the event must not be applied to the member that took it. */
    record CancelDamage() implements Effect { }

    /**
     * Apply this damage to a living member <b>as final damage, ignoring the target's own armor and
     * enchantments</b>: the amount is what the original member took after its own mitigation, so the
     * target suffers it as if it wore the same armor. The world must mark it as coming from a link, so it
     * is reported back with {@code fromLink = true} and never reflected again.
     */
    record DealDamage(MemberId target, double amount) implements Effect { }

    /** Kill a living member or destroy an object. */
    record Kill(MemberId target) implements Effect { }

    /** Set a member on fire or put it out (a block lit or unlit). The engine already expects this state. */
    record SetHeat(MemberId target, boolean hot) implements Effect { }

    /** Make a block waterlogged or dry. The engine already expects this state. */
    record SetWet(MemberId target, boolean wet) implements Effect { }

    /** Set a living member's air supply, in ticks. The engine already expects this value. */
    record SetAir(MemberId target, int air) implements Effect { }

    /**
     * Move a member by this displacement on top of what it did by itself this tick (a positive push to
     * follow its parent, or the opposite of its own movement to hold it still). The engine already
     * expects the resulting position.
     */
    record Displace(MemberId target, Vec delta) implements Effect { }

    /** An object has absorbed damage and now carries {@code wear} in total: the world should save it. */
    record Worn(MemberId member, double wear) implements Effect { }

    /** The owner has just paid this much UMU. */
    record Charged(UUID owner, double umu) implements Effect { }

    /** The link stopped existing because its owner could not pay. */
    record LinkBroken(Link link) implements Effect { }

    /** The member is gone: the world should drop its marks (they disappear when the member dies). */
    record MemberGone(MemberId member) implements Effect { }
}

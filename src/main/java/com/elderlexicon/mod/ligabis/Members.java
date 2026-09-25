package com.elderlexicon.mod.ligabis;

/** What the engine needs to know about the members, supplied by the world. */
public interface Members {

    MemberProfile profile(MemberId member);

    /** Current health of a living member. Not used for objects. */
    double health(MemberId member);

    /** Most air, in ticks, this member can hold. Vanilla lungs hold 300. */
    default int maxAir(MemberId member) {
        return 300;
    }
}

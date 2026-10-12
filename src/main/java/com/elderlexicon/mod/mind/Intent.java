package com.elderlexicon.mod.mind;

/**
 * What a caster wants, before it chooses how (docs/feiticeiro-design.md, 3.1). It wants what the scene asks: to hurt
 * what is in reach, to draw out of it (and heal by it, wounded), to vanish when hard pressed, to bring its band.
 */
public enum Intent {
    /** Hurt the target: throw at it. */
    STRIKE,
    /** Draw out of the target's body: it is a lack in it, and a fill in the caster when the caster lacks it. */
    DRAW,
    /** Vanish from sight. */
    VEIL,
    /** Bring the band to the target: the allies that carry a mark it gave them. */
    CALL
}

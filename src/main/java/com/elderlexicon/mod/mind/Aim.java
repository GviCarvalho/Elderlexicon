package com.elderlexicon.mod.mind;

/**
 * Where a caster looks when it reads (docs/feiticeiro-design.md, 3.8): the aim is enough, so the same sentence does
 * other things aimed elsewhere. {@code aqua quantum -10 vocant} aimed at a body draws its water; aimed at the sky, at no
 * body, it draws water out of the world around the caster's hand into the caster.
 */
public enum Aim {
    /** At the middle of the target. */
    TARGET,
    /** Up, at no body: what is drawn in comes out of the world. */
    SKY,
    /** Away from the target, a little up: what throws the caster throws it away. */
    AWAY,
    /** At its own feet: what is brought there is brought where it stands. */
    FEET,
    /** Anywhere: what it does is around the caster, wherever it looks (an intent's, never a choice's). */
    ANY
}

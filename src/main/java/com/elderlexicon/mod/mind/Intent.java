package com.elderlexicon.mod.mind;

/**
 * What a caster wants, before it chooses how (docs/feiticeiro-design.md, 3.1 and 3.8). It wants what the scene asks:
 * to hurt what is in reach, to draw out of it (and heal by it, wounded), to vanish when hard pressed, to bring its band;
 * and to keep itself whole: to heal out of the world, to keep a foe off, to get away, to put out the fire on it.
 */
public enum Intent {
    /** Hurt the target: throw at it. */
    STRIKE(Aim.TARGET),
    /** Draw out of the target's body: it is a lack in it, and a fill in the caster when the caster lacks it. */
    DRAW(Aim.TARGET),
    /** Vanish from sight. */
    VEIL(Aim.ANY),
    /** Bring the band to the target: the allies that carry a mark it gave them. */
    CALL(Aim.TARGET),
    /** Heal: draw into its own body, out of the world (not out of a body), what it lacks. */
    HEAL(Aim.SKY),
    /** Keep a foe off: a zone around itself no creature enters ({@code vis impediunt}). */
    WARD(Aim.ANY),
    /** Get away: throw itself away from the foe ({@code ego iactare}). */
    FLEE(Aim.AWAY),
    /** Put out the fire it is burning with: water brought to where it stands ({@code aqua vocant}). */
    DOUSE(Aim.FEET);

    private final Aim aim;

    Intent(Aim aim) {
        this.aim = aim;
    }

    /** Where the caster looks to read for it. */
    public Aim aim() {
        return aim;
    }

    /** Whether it is about the caster itself, not about hurting a foe. */
    public boolean keepsWhole() {
        return this == HEAL || this == WARD || this == FLEE || this == DOUSE || this == VEIL;
    }
}

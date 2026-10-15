package com.elderlexicon.arena;

import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Threat;

/**
 * What the arena is set by: the world's numbers, as the GameTests {@code theArenaIsSetByTheWorld...} measure them
 * (speeds, blocks a tick) and as the game sets them (health, blows, bows). When the world changes, these change with it.
 *
 * @param foe          the kind of foe, as the doctrine is named
 * @param foeSpeed     how fast the foe comes
 * @param guessed      how fast the mind guesses it comes (Threat.PER_SPEED times its speed attribute), as in the world
 * @param foeReach     how far its blow reaches, feet to feet (vanilla: the square root of (2 × its width)² + ours)
 * @param foeDamage    what its blow takes
 * @param foeCooldown  how long between two blows
 * @param foeHealth    its life
 * @param leaps        whether it leaps at the caster from two to four blocks off (a spider)
 * @param bow          how it shoots, if it shoots; null for one that only strikes
 * @param casterSpeed  how fast the caster backs away or walks up
 * @param casterHealth its life
 * @param hit          how often what is thrown lands on one walking (on one standing, what was measured is it)
 * @param damage       what the elder's sentences do to this foe, as measured in the world (3.13)
 * @param follow       how far it sees (its follow range): one unseen it still notices within 7% of that, as the game's
 *                     invisibility goes (SpellVisibility.onNoticed)
 * @param reads        whether it reads the Old Tongue: another caster, with its trance and its rest (3.17)
 * @param mage         how that caster keeps where it stands and chooses, as the world runs it (3.22); null: as an
 *                     aggressive elder who walks up and reads the best it sees (3.17)
 */
public record Calibration(String foe, double foeSpeed, double guessed, double foeReach, double foeDamage,
                          int foeCooldown, double foeHealth, boolean leaps, Bow bow, double casterSpeed,
                          double casterHealth, double hit, Damage damage, double follow, boolean reads, Mage mage) {

    /**
     * Another caster as the world runs it (CasterMind, with no doctrine): it reads whenever it is free and sees its foe,
     * among the best {@code among} with {@code luck} (Mind.choose); it backs away from one nearer than {@code floor}
     * (KeepDistanceGoal), or than {@code Memory.SAFE_DISTANCE} once its trance was broken twice; and walks up only when
     * nothing it reads gets there.
     *
     * @param floor    how near it lets a foe stand (a wizard: five blocks; a sorcerer: none)
     * @param back     how fast it backs away (measured, 3.11)
     * @param approach how fast it walks up
     */
    public record Mage(double floor, double back, double approach, int among, double luck) {

        /** A wizard of the master degree, as the duels in the world set it (3.21). */
        public static Mage wizard() {
            Degree master = Degree.MASTER;
            return new Mage(5.0D, WIZARD, WIZARD * 0.6D / 0.7D, master.among(), master.luck());
        }
    }

    /** A wizard: how fast it backs away (measured 13/10/2026), at 0.7 of its speed; it walks up at 0.6. */
    private static final double WIZARD = 0.264D;

    /**
     * How a foe shoots, as a skeleton's bow goal does: within {@code reach} it waits {@code interval}, draws for
     * {@code draw} and lets fly; it backs off when nearer than three quarters of its reach.
     *
     * @param arrowSpeed how fast the arrow flies
     * @param damage     what an arrow takes
     * @param hitNear    how often an arrow lands on one standing still within five blocks
     * @param hitFar     and at the bow's reach
     * @param moving     how much of that is left against one walking
     * @param measured   how often it strikes, as measured in the world (3.15); null: the guesses above
     */
    public record Bow(double reach, int interval, int draw, double arrowSpeed, double damage, double hitNear,
                      double hitFar, double moving, Arrows measured) {

        double hit(double distance, Arrows.Motion motion) {
            if (measured != null) {
                return measured.hit(distance, motion);
            }
            double share = Math.max(0.0D, Math.min(1.0D, (distance - 5.0D) / (reach - 5.0D)));
            return (hitNear + (hitFar - hitNear) * share) * (motion == Arrows.Motion.STILL ? 1.0D : moving);
        }
    }

    /** A sorcerer: how fast it backs away (measured 13/10/2026), its life, how wide it is. */
    private static final double SORCERER = 0.345D;
    private static final double SORCERER_HEALTH = 24.0D;
    private static final double SORCERER_WIDTH = 0.6D;

    /** The reach of a creature {@code width} wide striking a sorcerer, as vanilla's melee goal works it out. */
    static double reachOf(double width) {
        return Math.sqrt(Math.pow(width * 2.0D, 2.0D) + SORCERER_WIDTH);
    }

    /** A zombie (or a husk) against a sorcerer (measured 13/10/2026). */
    public static Calibration zombie() {
        return new Calibration("zombie", 0.114D, 0.23D * Threat.PER_SPEED, reachOf(0.6D), 3.0D, 20, 20.0D, false,
                null, SORCERER, SORCERER_HEALTH, 0.9D, Damage.FORESEEN, 35.0D, false, null);
    }

    /** A spider: faster, wider (its blow reaches farther), and it leaps (measured 13/10/2026). */
    public static Calibration spider() {
        return new Calibration("spider", 0.18D, 0.3D * Threat.PER_SPEED, reachOf(1.4D), 2.0D, 20, 16.0D, true, null,
                SORCERER, SORCERER_HEALTH, 0.9D, Damage.FORESEEN, 16.0D, false, null);
    }

    /**
     * A skeleton with a bow (normal difficulty): forty ticks between arrows, twenty drawing, fifteen blocks of reach.
     * How often its arrows land is a guess, to be set by playing.
     */
    public static Calibration skeleton() {
        return new Calibration("skeleton", 0.125D, 0.25D * Threat.PER_SPEED, reachOf(0.6D), 2.0D, 20, 20.0D, false,
                new Bow(15.0D, 40, 20, 1.6D, 3.5D, 0.9D, 0.6D, 0.7D, null), SORCERER, SORCERER_HEALTH, 0.9D,
                Damage.FORESEEN, 16.0D, false, null);
    }

    /**
     * Another caster (3.17, 3.22): a master wizard, as the world runs one with no doctrine ({@link Mage#wizard}); it
     * strikes no blow. {@link #elderCaster} is the one the arena first fought.
     */
    public static Calibration caster() {
        Mage wizard = Mage.wizard();
        return new Calibration("caster", wizard.approach(), 0.5D * Threat.PER_SPEED, 0.0D, 0.0D, 20,
                SORCERER_HEALTH, false, null, SORCERER, SORCERER_HEALTH, 0.9D, Damage.FORESEEN, 12.0D, true, wizard);
    }

    /**
     * Another caster as the arena first had it (3.17): an elder of an aggressive temper, reading the best it sees; it
     * walks up when what it reads does not get there (at a sorcerer's pace walking up, 0.7 of its speed).
     */
    public static Calibration elderCaster() {
        return new Calibration("caster", SORCERER * 0.7D / 0.8D, 0.5D * Threat.PER_SPEED, 0.0D, 0.0D, 20,
                SORCERER_HEALTH, false, null, SORCERER, SORCERER_HEALTH, 0.9D, Damage.FORESEEN, 12.0D, true, null);
    }

    public static Calibration of(String foe) {
        return switch (foe) {
            case "caster" -> caster();
            case "elder-caster" -> elderCaster();
            case "zombie" -> zombie();
            case "spider" -> spider();
            case "skeleton" -> skeleton();
            default -> throw new IllegalArgumentException("no arena for " + foe
                    + " (zombie, spider, skeleton, caster)");
        };
    }

    public Calibration withSpeeds(double foe, double caster) {
        return new Calibration(this.foe, foe, guessed, foeReach, foeDamage, foeCooldown, foeHealth, leaps, bow, caster,
                casterHealth, hit, damage, follow, reads, mage);
    }

    /** How near one unseen it still notices. */
    double noticesHidden() {
        return follow * 0.07D;
    }

    /** As this, its bow striking as measured (nothing changes for one with no bow, or when nothing was measured). */
    public Calibration withArrows(Arrows measured) {
        if (bow == null || measured == null) {
            return this;
        }
        Bow aimed = new Bow(bow.reach(), bow.interval(), bow.draw(), bow.arrowSpeed(), bow.damage(), bow.hitNear(),
                bow.hitFar(), bow.moving(), measured);
        return new Calibration(foe, foeSpeed, guessed, foeReach, foeDamage, foeCooldown, foeHealth, leaps, aimed,
                casterSpeed, casterHealth, hit, damage, follow, reads, mage);
    }

    public Calibration withDamage(Damage measured) {
        return new Calibration(foe, foeSpeed, guessed, foeReach, foeDamage, foeCooldown, foeHealth, leaps, bow,
                casterSpeed, casterHealth, hit, measured, follow, reads, mage);
    }
}

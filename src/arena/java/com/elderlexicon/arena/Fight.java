package com.elderlexicon.arena;

import com.elderlexicon.mod.mind.Act;
import com.elderlexicon.mod.mind.Doctrine.Situation;
import com.elderlexicon.mod.mind.Doctrine.Stance;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Threat;
import com.elderlexicon.mod.spell.Barrier;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * One fight in the arena, a caster against a foe that walks up and strikes, tick by tick, as the world runs it: the
 * trance of two seconds a blow breaks, the lines released after it while the caster stands, the rest after that, the
 * Vis spent and drawn back. Flat ground walled on four sides: one who backs away long enough meets a wall.
 * <p>
 * The caster chooses a stance whenever it is free ({@link Policy}); the mind chooses the sentence ({@link Repertoire}).
 */
final class Fight {

    /** What chooses a stance when the caster is free. */
    interface Policy {
        Stance choose(Fight fight, Situation seen, Set<Stance> possible);

        /** What came of the last stance it chose, until now ({@code over}: the fight ended). */
        default void reward(Fight fight, double reward, Situation seen, Set<Stance> possible, boolean over) {
        }
    }

    static final int TRANCE = 40;
    static final int MAX_TICKS = 1200;
    static final int DECIDE_EVERY = 10;
    private static final int BROKEN_REST = 30;
    private static final double VIS = 100.0D;
    private static final double VIS_PER_TICK = 0.5D / 20.0D;
    private static final double UMU_PER_HP = 5.0D;
    /** How far getting away throws the caster (a guess: ego iactare, ten UMU). */
    private static final double THROWN = 6.0D;
    /** How a foe's speed is guessed in the world (Threat.PER_SPEED times a zombie's 0.23). */
    private static final double GUESSED = 0.23D * Threat.PER_SPEED;
    /** After how long backing away without getting farther it is cornered, and for how long. */
    private static final int STUCK = 30;
    private static final int CORNERED = 60;

    final Calibration cal;
    final Repertoire repertoire;
    private final Random random;

    double cx;
    double cz;
    double health;
    double vis;
    double fx;
    double fz;
    double foeHealth;
    int tick;

    private Mind.Choice reading;
    private int trance;
    private int release;
    private int rest;
    private Stance moving = Stance.WAIT;
    private int foeCooldown;
    private int warded;
    private double wardRadius;
    private int veiled;
    private int stuck;
    private double farthest;
    int cornered;

    /** What it chose, read and broke: for the one who looks at the fight. */
    int readings;
    int broken;

    Fight(Calibration cal, Repertoire repertoire, Random random, double distance, double health, double vis,
          double foeHealth) {
        this.cal = cal;
        this.repertoire = repertoire;
        this.random = random;
        double h = cal.half() - 1.0D;
        cx = (random.nextDouble() * 2.0D - 1.0D) * h;
        cz = (random.nextDouble() * 2.0D - 1.0D) * h;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        fx = clamp(cx + Math.cos(angle) * distance);
        fz = clamp(cz + Math.sin(angle) * distance);
        this.health = health * cal.casterHealth();
        this.vis = vis * VIS;
        this.foeHealth = foeHealth * cal.foeHealth();
    }

    double distance() {
        return Math.hypot(fx - cx, fz - cz);
    }

    boolean over() {
        return health <= 0.0D || foeHealth <= 0.0D || tick >= MAX_TICKS;
    }

    boolean won() {
        return foeHealth <= 0.0D && health > 0.0D;
    }

    /** What the caster sees, as the world shows it to the mind (CasterMind.situation). */
    Situation seen() {
        double speed = veiled > 0 ? 0.0D : GUESSED;
        return new Situation(distance(), Threat.ticksToBlow(distance(), Threat.BLOW, speed) / TRANCE,
                health / cal.casterHealth(), vis / VIS, foeHealth / cal.foeHealth(), rest == 0);
    }

    /** What it can do now: read for whatever a sentence serves now and it can pay, after its rest; always move. */
    Set<Stance> possible() {
        Set<Stance> possible = EnumSet.of(Stance.BACK_OFF, Stance.CLOSE_IN, Stance.WAIT);
        if (rest > 0) {
            return possible;
        }
        for (Stance stance : Stance.values()) {
            if (stance.intent() != null && choiceFor(stance.intent()).isPresent()) {
                possible.add(stance);
            }
        }
        return possible;
    }

    Optional<Mind.Choice> choiceFor(Intent intent) {
        return repertoire.best(intent, distance(), health / cal.casterHealth(), vis);
    }

    Optional<Mind.Choice> bestOfAll() {
        return repertoire.bestOfAll(distance(), health / cal.casterHealth(), vis);
    }

    /** Runs the fight to its end. */
    void run(Policy policy) {
        double foeBefore = foeHealth;
        double before = health;
        Situation seen = null;
        Set<Stance> possible = null;
        boolean chosen = false;
        while (!over()) {
            if (free() && (tick % DECIDE_EVERY == 0 || moving == null)) {
                seen = seen();
                possible = possible();
                if (chosen) {
                    policy.reward(this, reward(foeBefore, before), seen, possible, false);
                }
                foeBefore = foeHealth;
                before = health;
                take(policy.choose(this, seen, possible));
                chosen = true;
            }
            step();
        }
        if (chosen) {
            double end = won() ? 1.0D : health <= 0.0D ? -1.0D : 0.0D;
            policy.reward(this, reward(foeBefore, before) + end, seen(), possible(), true);
        }
    }

    private double reward(double foeBefore, double before) {
        return (foeBefore - foeHealth) / cal.foeHealth() - (before - health) / cal.casterHealth();
    }

    private boolean free() {
        return reading == null && release == 0;
    }

    private void take(Stance stance) {
        if (stance.intent() != null) {
            Optional<Mind.Choice> choice = choiceFor(stance.intent());
            if (choice.isPresent() && rest == 0) {
                reading = choice.get();
                trance = 0;
                moving = null;
                readings++;
                return;
            }
            stance = Stance.WAIT;
        }
        if (stance == Stance.BACK_OFF && moving != Stance.BACK_OFF) {
            stuck = 0;
            farthest = distance();
        }
        moving = stance;
    }

    private void step() {
        tick++;
        vis = Math.min(VIS, vis + VIS_PER_TICK);
        if (cornered > 0) {
            cornered--;
        }
        if (reading != null) {
            if (++trance >= TRANCE) {
                cast(reading);
            }
        } else if (release > 0) {
            release--;
        } else {
            if (rest > 0) {
                rest--;
            }
            move();
        }
        foe();
    }

    private void move() {
        if (moving == null || moving == Stance.WAIT) {
            return;
        }
        double dx = cx - fx;
        double dz = cz - fz;
        double length = Math.max(1.0E-6D, Math.hypot(dx, dz));
        double sign = moving == Stance.BACK_OFF ? 1.0D : -1.0D;
        if (moving == Stance.CLOSE_IN && length <= cal.foeReach() + 0.5D) {
            return;
        }
        cx = clamp(cx + sign * dx / length * cal.casterSpeed());
        cz = clamp(cz + sign * dz / length * cal.casterSpeed());
        if (moving == Stance.BACK_OFF) {
            // Along the wall when the way back is shut: it slides, as a walker does.
            double now = distance();
            if (now > farthest + 0.5D) {
                farthest = now;
                stuck = 0;
            } else if (++stuck >= STUCK) {
                cornered = CORNERED;
                stuck = 0;
            }
        }
    }

    private void foe() {
        if (foeCooldown > 0) {
            foeCooldown--;
        }
        if (veiled > 0) {
            veiled--;
            return; // it lost the caster: it stands, looking
        }
        if (warded > 0) {
            warded--;
        }
        double dx = cx - fx;
        double dz = cz - fz;
        double length = Math.hypot(dx, dz);
        double keep = warded > 0 ? Math.max(cal.foeReach(), wardRadius) : cal.foeReach() * 0.8D;
        if (length > keep) {
            double stepLength = Math.min(cal.foeSpeed(), length - keep);
            fx = clamp(fx + dx / length * stepLength);
            fz = clamp(fz + dz / length * stepLength);
        }
        if (distance() <= cal.foeReach() && foeCooldown == 0) {
            foeCooldown = cal.foeCooldown();
            health -= cal.foeDamage();
            if (reading != null) {
                // A blow closes the crack: nothing is read, nothing is spent.
                reading = null;
                trance = 0;
                rest = BROKEN_REST;
                moving = Stance.WAIT;
                broken++;
            }
        }
    }

    private void cast(Mind.Choice choice) {
        Option option = choice.option();
        reading = null;
        trance = 0;
        moving = Stance.WAIT;
        double spent = option.cost();
        switch (choice.intent()) {
            case STRIKE -> {
                if (random.nextDouble() < cal.hit()) {
                    foeHealth -= choice.harm();
                }
            }
            case DRAW -> {
                foeHealth -= choice.harm();
                spent += choice.harm() * UMU_PER_HP;
                health = Math.min(cal.casterHealth(), health + Math.max(0.0D, choice.healed()));
            }
            case HEAL -> health = Math.min(cal.casterHealth(), health + choice.healed());
            case WARD -> {
                for (Act act : option.acts()) {
                    if (act.operation().equals("repel")) {
                        warded = (int) Math.ceil(act.seconds().orElse(2.0D) * 20.0D);
                        wardRadius = Barrier.radius(Math.abs(act.quantity().orElse(Barrier.DEFAULT_UMU))) + 0.5D;
                    }
                }
                double length = Math.max(1.0E-6D, distance());
                if (length < wardRadius) {
                    fx = clamp(cx + (fx - cx) / length * wardRadius);
                    fz = clamp(cz + (fz - cz) / length * wardRadius);
                }
            }
            case FLEE -> {
                double length = Math.max(1.0E-6D, distance());
                cx = clamp(cx + (cx - fx) / length * THROWN);
                cz = clamp(cz + (cz - fz) / length * THROWN);
            }
            case VEIL -> {
                for (Act act : option.acts()) {
                    if (act.operation().equals("perceive")) {
                        veiled = (int) Math.ceil(act.seconds().orElse(6.0D) * 20.0D);
                    }
                }
            }
            default -> {
            }
        }
        // Harm read alongside what it read for (a ward that also throws): it lands too.
        if (choice.intent() != Intent.STRIKE && choice.intent() != Intent.DRAW && choice.harm() > 0.0D) {
            foeHealth -= choice.harm();
        }
        vis = Math.max(0.0D, vis - spent);
        release = option.lastStep() * 5 + Math.max(channel(option), 6);
        rest = 20 + random.nextInt(20);
    }

    /** How long the taps of what it read stay open: as CasterMind.channel. */
    private static int channel(Option option) {
        int longest = 0;
        for (Act act : option.acts()) {
            int ticks;
            if (act.atOnce()) {
                ticks = 0;
            } else if (act.seconds().isPresent()) {
                ticks = (int) Math.ceil(act.seconds().getAsDouble() * 20.0D);
            } else {
                ticks = act.operation().equals("project") || act.operation().equals("repel") ? 40 : 0;
            }
            longest = Math.max(longest, ticks);
        }
        return Math.min(longest, 200);
    }

    private double clamp(double value) {
        return Math.max(-cal.half(), Math.min(cal.half(), value));
    }
}

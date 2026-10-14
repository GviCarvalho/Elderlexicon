package com.elderlexicon.arena;

import com.elderlexicon.mod.mind.Act;
import com.elderlexicon.mod.mind.Doctrine.Situation;
import com.elderlexicon.mod.mind.Doctrine.Stance;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Room;
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

    /** Whether only what is sensible is weighed (Doctrine.sensible), in learning and in following. */
    static boolean sensible;

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
    private static final int FIRST_REST = 20;
    private static final double VIS = 100.0D;
    private static final double VIS_PER_TICK = 0.5D / 20.0D;
    private static final double UMU_PER_HP = 5.0D;
    /** How far getting away throws the caster (a guess: ego iactare, ten UMU). */
    private static final double THROWN = 6.0D;
    /** How long a leap lasts, how fast it goes, and how long until the next one. */
    private static final int LEAP = 8;
    private static final double LEAP_SPEED = 0.35D;
    private static final int LEAP_REST = 20;
    /** How long a foe takes to find again one unseen it still notices (its target goals look twice a second). */
    private static final int NOTICE_TICKS = 10;
    /** After how long backing away without getting farther it is cornered, and for how long. */
    private static final int STUCK = 30;
    private static final int CORNERED = 60;

    final Calibration cal;
    final Hall hall;
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
    private Room.Way backWay;
    private Room.Way circleWay;
    private int side = 1;
    int cornered;
    /** Whether the caster walked this tick, and which way (an arrow strikes one walking as measured, 3.15). */
    private boolean walked;
    private double stepX;
    private double stepZ;
    /** A spider's leap, and its rest after one. */
    private int leap;
    private int leapRest;
    private int noticing;
    /** A bow: how long it still waits, how long it has drawn (-1: it does not), since its last arrow (-1: none seen). */
    private int attackTime;
    private int drawn = -1;
    private int sinceShot = -1;
    /** The arrows in flight: when each lands, and whether it hits. */
    private final java.util.List<int[]> arrows = new java.util.ArrayList<>();

    /**
     * Another caster as the foe (3.17): what it reads, how far into its trance, its lines still released, its rest, its
     * Vis, whether it walked this tick; and the mind that chooses its sentences (aggressive: it pays in flesh).
     */
    static java.util.List<Repertoire> foeRepertoires = java.util.List.of();
    private Repertoire foeRepertoire;
    private Mind.Choice foeReading;
    private int foeTrance;
    private int foeRelease;
    private int foeRest = FIRST_REST;
    private double foeVis = VIS;
    private boolean foeWalked;

    /** What it chose, read and broke: for the one who looks at the fight. */
    int readings;
    int broken;
    int foeBroken;

    Fight(Calibration cal, Hall hall, Repertoire repertoire, Random random, double distance, double health, double vis,
          double foeHealth) {
        this.cal = cal;
        this.hall = hall;
        this.repertoire = repertoire;
        this.random = random;
        cx = hall.clampX((random.nextDouble() * 2.0D - 1.0D) * hall.halfX());
        cz = hall.clampZ((random.nextDouble() * 2.0D - 1.0D) * hall.halfZ());
        double angle = random.nextDouble() * Math.PI * 2.0D;
        fx = hall.clampX(cx + Math.cos(angle) * distance);
        fz = hall.clampZ(cz + Math.sin(angle) * distance);
        this.health = health * cal.casterHealth();
        this.vis = vis * VIS;
        this.foeHealth = foeHealth * cal.foeHealth();
        if (cal.bow() != null) {
            attackTime = random.nextInt(cal.bow().interval() + 1);
        }
        // As in the world (CasterMind.REST_TICKS): a fight finds it resting, not with a reading ready.
        rest = FIRST_REST;
        // Another caster is of one temper or another, drawn for each fight when several are asked.
        if (!foeRepertoires.isEmpty()) {
            foeRepertoire = foeRepertoires.get(random.nextInt(foeRepertoires.size()));
        }
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
        double speed = Math.max(cal.guessed(), cal.foeSpeed());
        double ticks;
        if (veiled > 0) {
            ticks = Double.POSITIVE_INFINITY;
        } else if (cal.reads()) {
            ticks = Threat.ticksToReading(foeReading != null, foeTrance, foeRelease > 0, foeRest, TRANCE);
        } else if (cal.bow() != null) {
            ticks = Threat.ticksToArrow(distance(), speed, drawn, sinceShot, cal.bow().interval());
        } else {
            ticks = Threat.ticksToBlow(distance(), Math.max(Threat.BLOW, cal.foeReach()), speed);
        }
        return new Situation(distance(), ticks / TRANCE,
                health / cal.casterHealth(), vis / VIS, foeHealth / cal.foeHealth(), rest == 0, way().room());
    }

    /** What it can do now: read for whatever a sentence serves now and it can pay, after its rest; always move. */
    Set<Stance> possible() {
        Set<Stance> possible = EnumSet.of(Stance.BACK_OFF, Stance.CLOSE_IN, Stance.WAIT, Stance.CIRCLE);
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
                possible = sensible ? com.elderlexicon.mod.mind.Doctrine.sensible(seen, possible()) : possible();
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
            Situation last = seen();
            policy.reward(this, reward(foeBefore, before) + end, last,
                    sensible ? com.elderlexicon.mod.mind.Doctrine.sensible(last, possible()) : possible(), true);
        }
    }

    /**
     * How much a point of its own life lost weighs against one taken from the foe (each as a share of the whole): more
     * than one, and it does not let itself be beaten to be desperate enough to pay in flesh (3.12).
     */
    static double hurtWeight = 2.0D;

    private double reward(double foeBefore, double before) {
        return (foeBefore - foeHealth) / cal.foeHealth() - hurtWeight * (before - health) / cal.casterHealth();
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
            backWay = null;
        }
        moving = stance;
    }

    private void step() {
        tick++;
        walked = false;
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

    /** The way it would back off now, and how much room there is that way (as in the world: Room). */
    Room.Way way() {
        return Room.away(hall::open, cx, cz, fx, fz);
    }

    private void move() {
        if (moving == null || moving == Stance.WAIT) {
            return;
        }
        if (moving == Stance.CLOSE_IN) {
            double dx = fx - cx;
            double dz = fz - cz;
            double length = Math.max(1.0E-6D, Math.hypot(dx, dz));
            if (length > cal.foeReach() + 0.5D) {
                double wasX = cx;
                double wasZ = cz;
                cx = hall.clampX(cx + dx / length * cal.casterSpeed());
                cz = hall.clampZ(cz + dz / length * cal.casterSpeed());
                walked = true;
                stepX = cx - wasX;
                stepZ = cz - wasZ;
            }
            return;
        }
        if (moving == Stance.CIRCLE) {
            // Around the foe, aside of it, keeping to one side while there is room there (as the world does, 3.16).
            if (tick % 5 == 0 || circleWay == null) {
                circleWay = Room.aside(hall::open, cx, cz, fx, fz, side);
                side = circleWay.dz() * (cx - fx) - circleWay.dx() * (cz - fz) >= 0.0D ? 1 : -1;
            }
            if (circleWay.room() < Room.CORNERED) {
                return;
            }
            double wasX = cx;
            double wasZ = cz;
            cx = hall.clampX(cx + circleWay.dx() * cal.casterSpeed());
            cz = hall.clampZ(cz + circleWay.dz() * cal.casterSpeed());
            walked = true;
            stepX = cx - wasX;
            stepZ = cz - wasZ;
            return;
        }
        // Backing off: the way with the most room, looked for again as often as the world repaths.
        if (backWay == null || tick % DECIDE_EVERY == 0) {
            backWay = way();
        }
        if (backWay.room() < Room.CORNERED) {
            cornered = CORNERED;
            moving = Stance.WAIT;
            return;
        }
        double wasX = cx;
        double wasZ = cz;
        cx = hall.clampX(cx + backWay.dx() * cal.casterSpeed());
        cz = hall.clampZ(cz + backWay.dz() * cal.casterSpeed());
        walked = true;
        stepX = cx - wasX;
        stepZ = cz - wasZ;
        double now = distance();
        if (now > farthest + 0.5D) {
            farthest = now;
            stuck = 0;
        } else if (++stuck >= STUCK) {
            cornered = CORNERED;
            stuck = 0;
        }
    }

    private void foe() {
        if (foeCooldown > 0) {
            foeCooldown--;
        }
        if (sinceShot >= 0) {
            sinceShot++;
        }
        for (java.util.Iterator<int[]> flying = arrows.iterator(); flying.hasNext(); ) {
            int[] arrow = flying.next();
            if (arrow[0] <= tick) {
                flying.remove();
                if (arrow[1] == 1) {
                    struck(cal.bow().damage());
                }
            }
        }
        if (veiled > 0) {
            veiled--;
            drawn = -1;
            // It lost the caster: it stands, looking; but one unseen at hand it still notices, and finds again.
            if (distance() <= cal.noticesHidden() && ++noticing >= NOTICE_TICKS) {
                veiled = 0;
            }
            return;
        }
        noticing = 0;
        if (warded > 0) {
            warded--;
        }
        if (cal.reads()) {
            duelist();
        } else if (cal.bow() != null) {
            archer(cal.bow());
        } else {
            melee();
        }
    }

    /**
     * Another caster: it reads what strikes, draws or heals best now, as an aggressive elder would, with the trance, the
     * lines released and the rest any caster has; it walks up when nothing it reads gets there.
     */
    private void duelist() {
        foeWalked = false;
        foeVis = Math.min(VIS, foeVis + VIS_PER_TICK);
        if (foeReading != null) {
            if (++foeTrance >= TRANCE) {
                foeCast(foeReading);
            }
            return;
        }
        if (foeRelease > 0) {
            foeRelease--;
            return;
        }
        if (foeRest > 0) {
            foeRest--;
            return;
        }
        Mind.Choice best = null;
        double foeShare = foeHealth / cal.foeHealth();
        for (Intent intent : new Intent[]{Intent.STRIKE, Intent.DRAW, Intent.HEAL}) {
            Optional<Mind.Choice> choice = foeRepertoire.best(intent, distance(), foeShare, foeVis);
            if (choice.isPresent() && (best == null || choice.get().score() > best.score())) {
                best = choice.get();
            }
        }
        if (best != null && (best.intent() != Intent.HEAL || foeShare < 0.9D)) {
            foeReading = best;
            foeTrance = 0;
            return;
        }
        double dx = cx - fx;
        double dz = cz - fz;
        double length = Math.max(1.0E-6D, Math.hypot(dx, dz));
        if (length > 4.0D) {
            walk(dx, dz, length, Math.min(cal.foeSpeed(), length - 4.0D));
            foeWalked = true;
        }
    }

    /** What the other caster read comes out: it strikes, or draws out of the caster, or heals itself. */
    private void foeCast(Mind.Choice choice) {
        foeReading = null;
        foeTrance = 0;
        double spent = choice.option().cost();
        switch (choice.intent()) {
            case STRIKE -> {
                if (!walked || random.nextDouble() < cal.hit()) {
                    struck(cal.damage().harm(choice));
                }
            }
            case DRAW -> {
                struck(cal.damage().harm(choice));
                spent += choice.harm() * UMU_PER_HP;
                foeHealth = Math.min(cal.foeHealth(), foeHealth + Math.max(0.0D, choice.healed()));
            }
            case HEAL -> foeHealth = Math.min(cal.foeHealth(), foeHealth + choice.healed());
            default -> {
            }
        }
        foeVis = Math.max(0.0D, foeVis - spent);
        foeRelease = choice.option().lastStep() * 5 + Math.max(channel(choice.option()), 6);
        foeRest = 20 + random.nextInt(20);
    }

    /** It was struck: another caster in its trance has it broken, and nothing is read. */
    private void foeStruck() {
        if (foeReading != null) {
            foeReading = null;
            foeTrance = 0;
            foeRest = BROKEN_REST;
            foeBroken++;
        }
    }

    private void melee() {
        double dx = cx - fx;
        double dz = cz - fz;
        double length = Math.hypot(dx, dz);
        double keep = warded > 0 ? Math.max(cal.foeReach(), wardRadius) : cal.foeReach() * 0.8D;
        if (leapRest > 0) {
            leapRest--;
        }
        if (cal.leaps() && leap == 0 && leapRest == 0 && length >= 2.0D && length <= 4.0D
                && random.nextInt(5) == 0) {
            leap = LEAP;
            leapRest = LEAP + LEAP_REST;
        }
        double speed = cal.foeSpeed();
        if (leap > 0) {
            leap--;
            speed = LEAP_SPEED;
        }
        if (length > keep) {
            walk(dx, dz, length, Math.min(speed, length - keep));
        }
        if (distance() <= cal.foeReach() && foeCooldown == 0) {
            foeCooldown = cal.foeCooldown();
            struck(cal.foeDamage());
        }
    }

    /** A skeleton: it keeps within its bow's reach, backs off when too near, draws, waits, and lets fly. */
    private void archer(Calibration.Bow bow) {
        double dx = cx - fx;
        double dz = cz - fz;
        double length = Math.max(1.0E-6D, Math.hypot(dx, dz));
        if (length > bow.reach()) {
            walk(dx, dz, length, Math.min(cal.foeSpeed(), length - bow.reach()));
        } else if (length < bow.reach() * 0.75D) {
            walk(dx, dz, length, -cal.foeSpeed() * 0.5D);
        }
        if (distance() > bow.reach()) {
            drawn = -1;
            return;
        }
        if (drawn >= 0) {
            if (++drawn >= bow.draw()) {
                drawn = -1;
                attackTime = bow.interval();
                sinceShot = 0;
                double at = distance();
                Arrows.Motion motion = Arrows.Motion.of(walked, stepX, stepZ, (cx - fx) / Math.max(1.0E-6D, at),
                        (cz - fz) / Math.max(1.0E-6D, at));
                boolean hits = random.nextDouble() < bow.hit(at, motion);
                arrows.add(new int[]{tick + (int) Math.ceil(at / bow.arrowSpeed()), hits ? 1 : 0});
            }
        } else if (--attackTime <= 0) {
            drawn = 0;
        }
    }

    /** Whether the foe is on the move now: it comes, or keeps its distance, unless it stands lost or at hand. */
    private boolean foeWalking() {
        return cal.reads() ? foeWalked : veiled == 0 && distance() > cal.foeReach();
    }

    private void walk(double dx, double dz, double length, double stepLength) {
        fx = hall.clampX(fx + dx / length * stepLength);
        fz = hall.clampZ(fz + dz / length * stepLength);
    }

    /** It takes a blow, or an arrow: one in its trance closes the crack, and nothing is read, nothing spent. */
    private void struck(double damage) {
        health -= damage;
        if (reading != null) {
            reading = null;
            trance = 0;
            rest = BROKEN_REST;
            moving = Stance.WAIT;
            broken++;
        }
    }

    private void cast(Mind.Choice choice) {
        Option option = choice.option();
        double foeAtStart = foeHealth;
        reading = null;
        trance = 0;
        moving = Stance.WAIT;
        double spent = option.cost();
        switch (choice.intent()) {
            case STRIKE -> {
                // What was measured, on one standing still; one walking is missed now and then.
                if (!foeWalking() || random.nextDouble() < cal.hit()) {
                    foeHealth -= cal.damage().harm(choice);
                }
            }
            case DRAW -> {
                foeHealth -= cal.damage().harm(choice);
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
                    fx = hall.clampX(cx + (fx - cx) / length * wardRadius);
                    fz = hall.clampZ(cz + (fz - cz) / length * wardRadius);
                }
            }
            case FLEE -> {
                double length = Math.max(1.0E-6D, distance());
                cx = hall.clampX(cx + (cx - fx) / length * THROWN);
                cz = hall.clampZ(cz + (cz - fz) / length * THROWN);
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
            foeHealth -= cal.damage().harm(choice);
        }
        if (foeHealth < foeAtStart) {
            // What strikes the other caster in its trance breaks it, as any blow.
            foeStruck();
            // One hurt turns on who hurt it, seen or not (vanilla's HurtByTargetGoal ignores invisibility): what the
            // caster read to vanish, if it hurt too, did not hide it (3.17).
            veiled = 0;
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
}

package com.elderlexicon.arena;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Doctrine;
import com.elderlexicon.mod.mind.Personality;
import com.google.gson.JsonObject;
import net.minecraft.util.RandomSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The arena (docs/feiticeiro-design.md, 3.10): an elder fights a foe many thousand times, out of the game, learning
 * what each stance is worth in each situation; then it is set against the casters as they were and as they are with no
 * doctrine, and what it learned is written as a doctrine the mod carries.
 * <pre>
 * gradlew trainDoctrines --args="--foe zombie --episodes 1000000 --install"
 * </pre>
 */
public final class Train {

    private static final int TRIALS = 3000;
    /** How often a situation must have been met for what was learned of it to be kept. */
    private static final int KEPT_AFTER = 30;

    private Train() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parse(args);
        String foe = options.getOrDefault("foe", "zombie");
        int episodes = Integer.parseInt(options.getOrDefault("episodes", "200000"));
        long seed = Long.parseLong(options.getOrDefault("seed", "13"));
        Calibration cal = Calibration.of(foe);
        if (options.containsKey("foe-speed") || options.containsKey("caster-speed")) {
            cal = cal.withSpeeds(Double.parseDouble(options.getOrDefault("foe-speed", "" + cal.foeSpeed())),
                    Double.parseDouble(options.getOrDefault("caster-speed", "" + cal.casterSpeed())));
        }
        // --open: only open ground, as the first doctrines were learned (3.10); else halls and corridors too (3.12).
        boolean openOnly = options.containsKey("open");
        Fight.hurtWeight = Double.parseDouble(options.getOrDefault("hurt", "2"));
        Fight.sensible = options.containsKey("sensible");
        // What the elder's sentences do to this foe, as the GameTests measured it (3.13); --foreseen: as the mind guesses.
        if (!options.containsKey("foreseen")) {
            Path measured = Path.of(options.getOrDefault("calibration", "src/arena/calibration"));
            cal = cal.withDamage(Damage.of(measured, cal.foe())).withArrows(Arrows.of(measured));
        }
        Set<String> known = Degree.ELDER.draw(Lexicons.builtIn(), RandomSource.create(seed));
        // The mind that chooses the sentences: cautious by default, for it never pays in flesh (3.13). A doctrine is
        // followed by elders of every temper, and one learned by an aggressive mind counts on paying in flesh when
        // hard pressed: a cautious elder never does, and would wait for it to the end.
        Personality personality = Personality.byId(options.getOrDefault("personality", "cautious"));
        Repertoire repertoire = new Repertoire(known, personality);
        // Another caster as the foe reads as an elder of the temper asked (--foe-personality), or, mixed (the default),
        // of a temper drawn for each fight, as one met in the world (3.17).
        String foeTemper = options.getOrDefault("foe-personality", "mixed");
        java.util.List<Repertoire> foes = new java.util.ArrayList<>();
        if (cal.reads()) {
            for (Personality temper : Personality.values()) {
                if (foeTemper.equals("mixed") || temper.id().equals(foeTemper)) {
                    foes.add(new Repertoire(known, temper));
                }
            }
        }
        Fight.foeRepertoires = foes;
        System.out.printf(Locale.ROOT, "Arena: %s, %d sentences the elder can read, foe %.3f b/t, caster %.3f b/t, %s%n",
                foe, repertoire.size(), cal.foeSpeed(), cal.casterSpeed(),
                openOnly ? "open ground only" : "open ground, halls and corridors");
        System.out.println("Damage: " + cal.damage() + "; the mind choosing sentences: " + personality.id()
                + (cal.bow() == null ? "" : "; arrows: " + (cal.bow().measured() == null ? "guessed"
                : cal.bow().measured().toString())));

        if (options.containsKey("show")) {
            for (double distance : new double[]{2.0D, 5.0D, 9.0D, 15.0D}) {
                System.out.printf(Locale.ROOT, "  at %.0f blocks, whole, full of Vis:%n", distance);
                for (com.elderlexicon.mod.mind.Intent intent : com.elderlexicon.mod.mind.Intent.values()) {
                    repertoire.best(intent, distance, 0.6D, 100.0D).ifPresent(choice -> System.out.printf(
                            Locale.ROOT, "    %-7s %-40s score %5.2f harm %4.1f cost %4.1f%n", intent,
                            choice.option().name(), choice.score(), choice.harm(), choice.option().cost()));
                }
            }
        }

        if (options.containsKey("rank")) {
            double at = Double.parseDouble(options.get("rank"));
            for (double vis : new double[]{90.0D, 60.0D}) {
                System.out.printf(Locale.ROOT, "  the best of each intent at %.0f blocks, whole, %.0f Vis:%n", at, vis);
                for (com.elderlexicon.mod.mind.Intent intent : com.elderlexicon.mod.mind.Intent.values()) {
                    for (com.elderlexicon.mod.mind.Mind.Choice choice : repertoire.ranked(intent, at, 1.0D, vis)) {
                        System.out.printf(Locale.ROOT, "    %-7s %-55s score %5.2f harm %4.1f cost %4.1f%n", intent,
                                choice.option().name(), choice.score(), choice.harm(), choice.option().cost());
                    }
                }
            }
        }

        Random random = new Random(seed);
        Policies.Learning learning = new Policies.Learning(random);
        long started = System.currentTimeMillis();
        for (int episode = 0; episode < episodes; episode++) {
            learning.explore = Math.max(0.02D, 0.3D * (1.0D - episode / (double) episodes));
            fight(cal, openOnly ? Hall.OPEN : Hall.draw(random), repertoire, random).run(learning);
            if ((episode + 1) % Math.max(1, episodes / 10) == 0) {
                System.out.printf(Locale.ROOT, "  %d fights, %d situations, %.0f s%n", episode + 1,
                        learning.worth.size(), (System.currentTimeMillis() - started) / 1000.0D);
            }
        }

        Map<String, double[]> kept = new LinkedHashMap<>();
        learning.worth.forEach((key, values) -> {
            if (learning.met.getOrDefault(key, 0) >= KEPT_AFTER) {
                kept.put(key, values);
            }
        });
        JsonObject trained = new JsonObject();
        trained.addProperty("fights", episodes);
        trained.addProperty("seed", seed);
        trained.addProperty("foeSpeed", cal.foeSpeed());
        trained.addProperty("casterSpeed", cal.casterSpeed());
        trained.addProperty("personality", personality.id());
        if (cal.reads()) {
            trained.addProperty("foePersonality", foeTemper);
        }
        trained.addProperty("hurtWeight", Fight.hurtWeight);
        trained.addProperty("damage", cal.damage().toString());
        trained.addProperty("halls", !openOnly);
        trained.addProperty("sensible", Fight.sensible);
        Doctrine doctrine = new Doctrine(cal.foe(), learning.steps, kept, trained);

        System.out.println("Against " + TRIALS + " fights each, same starts (won / lost / life left / trances broken):");
        System.out.printf(Locale.ROOT, "  %-34s %-28s %-28s %-28s%n", "", "as before 3.9", "elder, no doctrine",
                "elder, doctrine");
        Hall[] halls = {null, Hall.OPEN, Hall.FIELD, Hall.ROOM, Hall.SMALL, Hall.CORRIDOR};
        for (Hall hall : halls) {
            boolean fresh = hall != null;
            Result before = trial(cal, hall, repertoire, Policies.before(), seed, fresh, openOnly);
            Result elder = trial(cal, hall, repertoire, Policies.elder(), seed, fresh, openOnly);
            Result taught = trial(cal, hall, repertoire, Policies.following(doctrine), seed, fresh, openOnly);
            String where = fresh ? "fresh, " + hall.name() : "drawn as in training";
            System.out.printf(Locale.ROOT, "  %-34s %-28s %-28s %-28s%n", where, before, elder, taught);
            trained.addProperty("won " + where + ", before", before.wonShare());
            trained.addProperty("won " + where + ", elder", elder.wonShare());
            trained.addProperty("won " + where + ", doctrine", taught.wonShare());
        }

        // --trace N: N fresh fights in a hall 16 x 16 by the doctrine, each choice it made (to see what it does).
        int traced = Integer.parseInt(options.getOrDefault("trace", "0"));
        for (int i = 0; i < traced; i++) {
            Random trace = new Random(seed * 31L + i);
            Fight fight = new Fight(cal, options.containsKey("trace-corridor") ? Hall.CORRIDOR : Hall.ROOM, repertoire, trace, 6.0D + trace.nextDouble() * 10.0D, 1.0D, 1.0D,
                    1.0D);
            Fight.Policy follow = options.getOrDefault("trace-policy", "doctrine").equals("elder") ? Policies.elder()
                    : Policies.following(doctrine);
            System.out.println("Fight " + (i + 1) + ":");
            fight.run((f, seen, possible) -> {
                com.elderlexicon.mod.mind.Doctrine.Stance chosen = follow.choose(f, seen, possible);
                System.out.printf(Locale.ROOT,
                        "  t%4d  %4.1f blocks  room %2.0f  blow %4.2f  life %4.1f  foe %4.1f  %s%s%s -> %s   [foe %s, last %s]%n", f.tick,
                        seen.distance(), seen.room(), Math.min(9.99D, seen.trancesToBlow()), f.health, f.foeHealth,
                        seen.ready() ? "ready" : "rest ", f.cornered > 0 ? " cornered" : "", f.inFire > 0 ? " in fire" : f.burning > 0 ? " burning" : "", chosen,
                        (f.foeReading != null ? "reading " + f.foeReading.intent() : f.foeInFire > 0 ? "in fire" : "-") + (f.foeBurning > 0 ? ", burning" : ""),
                        f.foeLastRead);
                return chosen;
            });
            System.out.println(fight.won() ? "  won" : fight.health <= 0.0D ? "  lost" : "  time ran out");
        }

        Path out = Path.of(options.getOrDefault("out", "build/doctrines"));
        Files.createDirectories(out);
        // Against another caster, a doctrine is a temper's: what one who pays in flesh can do, one who never does cannot.
        String name = cal.reads() ? cal.foe() + "-" + personality.id() : cal.foe();
        Path file = out.resolve(name + ".json");
        Files.writeString(file, doctrine.toJson(), StandardCharsets.UTF_8);
        System.out.println("Written: " + file.toAbsolutePath() + " (" + kept.size() + " situations)");
        if (options.containsKey("install")) {
            Path installed = Path.of("src/main/resources/data/elderlexicon/doctrines").resolve(name + ".json");
            Files.createDirectories(installed.getParent());
            Files.writeString(installed, doctrine.toJson(), StandardCharsets.UTF_8);
            System.out.println("Installed: " + installed.toAbsolutePath());
        }
    }

    /** A fight from a start drawn at random: how far, how whole each one, how much Vis. */
    private static Fight fight(Calibration cal, Hall hall, Repertoire repertoire, Random random) {
        return new Fight(cal, hall, repertoire, random, 2.0D + random.nextDouble() * 14.0D,
                0.4D + random.nextDouble() * 0.6D, 0.3D + random.nextDouble() * 0.7D,
                0.4D + random.nextDouble() * 0.6D);
    }

    record Result(int fights, int won, int lost, double healthLeft, double ticks, double broken) {
        double wonShare() {
            return won / (double) fights;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%5.1f%% / %5.1f%% / %4.1f / %.2f", 100.0D * won / fights,
                    100.0D * lost / fights, healthLeft, broken);
        }
    }

    /** Fights from the same starts: fresh ones in {@code hall}, or drawn as in training (hall null). */
    private static Result trial(Calibration cal, Hall hall, Repertoire repertoire, Fight.Policy policy, long seed,
                                boolean fresh, boolean openOnly) {
        int won = 0;
        int lost = 0;
        double health = 0.0D;
        double ticks = 0.0D;
        double broken = 0.0D;
        for (int i = 0; i < TRIALS; i++) {
            Random random = new Random(seed * 7919L + i);
            Fight fight = fresh ? new Fight(cal, hall, repertoire, random, 6.0D + random.nextDouble() * 10.0D, 1.0D,
                    1.0D, 1.0D) : fight(cal, openOnly ? Hall.OPEN : Hall.draw(random), repertoire, random);
            fight.run(policy);
            won += fight.won() ? 1 : 0;
            lost += fight.health <= 0.0D ? 1 : 0;
            health += Math.max(0.0D, fight.health);
            ticks += fight.tick;
            broken += fight.broken;
        }
        return new Result(TRIALS, won, lost, health / TRIALS, ticks / TRIALS, broken / TRIALS);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String name = args[i].substring(2);
                boolean flag = i + 1 >= args.length || args[i + 1].startsWith("--");
                options.put(name, flag ? "true" : args[++i]);
            }
        }
        return options;
    }
}

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
 * gradlew trainDoctrines --args="--foe zombie --episodes 200000 --install"
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
        Calibration cal = Calibration.zombie();
        if (options.containsKey("foe-speed") || options.containsKey("caster-speed")) {
            cal = cal.withSpeeds(Double.parseDouble(options.getOrDefault("foe-speed", "" + cal.foeSpeed())),
                    Double.parseDouble(options.getOrDefault("caster-speed", "" + cal.casterSpeed())));
        }
        if (options.containsKey("half")) {
            cal = cal.withHalf(Double.parseDouble(options.get("half")));
        }
        Set<String> known = Degree.ELDER.draw(Lexicons.builtIn(), RandomSource.create(seed));
        Repertoire repertoire = new Repertoire(known, Personality.AGGRESSIVE);
        System.out.printf(Locale.ROOT, "Arena: %s, %d sentences the elder can read, foe %.3f b/t, caster %.3f b/t, walls at %.0f%n",
                foe, repertoire.size(), cal.foeSpeed(), cal.casterSpeed(), cal.half());

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

        Random random = new Random(seed);
        Policies.Learning learning = new Policies.Learning(random);
        long started = System.currentTimeMillis();
        for (int episode = 0; episode < episodes; episode++) {
            learning.explore = Math.max(0.02D, 0.3D * (1.0D - episode / (double) episodes));
            fight(cal, repertoire, random).run(learning);
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
        Doctrine doctrine = new Doctrine(foe, learning.steps, kept, trained);

        for (boolean fresh : new boolean[]{false, true}) {
            System.out.println("Against " + TRIALS + " fights each, same starts, "
                    + (fresh ? "fresh (whole, full of Vis, the foe 6 to 16 blocks off):" : "drawn as in training:"));
            Result before = trial(cal, repertoire, Policies.before(), seed, fresh);
            Result elder = trial(cal, repertoire, Policies.elder(), seed, fresh);
            Result taught = trial(cal, repertoire, Policies.following(doctrine), seed, fresh);
            System.out.println("  as before 3.9      " + before);
            System.out.println("  elder, no doctrine " + elder);
            System.out.println("  elder, doctrine    " + taught);
            String suffix = fresh ? "Fresh" : "Drawn";
            trained.addProperty("wonBefore" + suffix, before.wonShare());
            trained.addProperty("wonElder" + suffix, elder.wonShare());
            trained.addProperty("wonDoctrine" + suffix, taught.wonShare());
        }

        Path out = Path.of(options.getOrDefault("out", "build/doctrines"));
        Files.createDirectories(out);
        Path file = out.resolve(foe + ".json");
        Files.writeString(file, doctrine.toJson(), StandardCharsets.UTF_8);
        System.out.println("Written: " + file.toAbsolutePath() + " (" + kept.size() + " situations)");
        if (options.containsKey("install")) {
            Path installed = Path.of("src/main/resources/data/elderlexicon/doctrines").resolve(foe + ".json");
            Files.createDirectories(installed.getParent());
            Files.writeString(installed, doctrine.toJson(), StandardCharsets.UTF_8);
            System.out.println("Installed: " + installed.toAbsolutePath());
        }
    }

    /** A fight from a start drawn at random: how far, how whole each one, how much Vis. */
    private static Fight fight(Calibration cal, Repertoire repertoire, Random random) {
        return new Fight(cal, repertoire, random, 2.0D + random.nextDouble() * 14.0D,
                0.4D + random.nextDouble() * 0.6D, 0.3D + random.nextDouble() * 0.7D,
                0.4D + random.nextDouble() * 0.6D);
    }

    record Result(int fights, int won, int lost, double healthLeft, double ticks, double broken) {
        double wonShare() {
            return won / (double) fights;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "won %5.1f%%  lost %5.1f%%  life left %4.1f  %5.0f ticks  %.2f trances broken",
                    100.0D * won / fights, 100.0D * lost / fights, healthLeft, ticks, broken);
        }
    }

    private static Result trial(Calibration cal, Repertoire repertoire, Fight.Policy policy, long seed,
                                boolean fresh) {
        int won = 0;
        int lost = 0;
        double health = 0.0D;
        double ticks = 0.0D;
        double broken = 0.0D;
        for (int i = 0; i < TRIALS; i++) {
            Random random = new Random(seed * 7919L + i);
            Fight fight = fresh ? new Fight(cal, repertoire, random, 6.0D + random.nextDouble() * 10.0D, 1.0D, 1.0D,
                    1.0D) : fight(cal, repertoire, random);
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

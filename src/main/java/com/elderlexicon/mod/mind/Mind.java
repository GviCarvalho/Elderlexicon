package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * How a caster chooses what to read (docs/feiticeiro-design.md, 3.1 to 3.4): it weighs what the scene asks of it (its
 * intents), and for every option it can read, what it would do toward them against what it costs and risks:
 * <pre>
 * score = Σ intent weight × what it would do for that intent
 *       − cost weight × (what its verbs spend + the life it would take out of a body, five UMU a point)
 *       − risk (what it would do that serves nothing, or turns on itself)
 *       + a little luck
 * </pre>
 * It reads among the best few, by score, so it is not foreseeable.
 * <p>
 * What a throw does to a body is not foreseen exactly: {@link #potency} is a first guess, to be set by playing. What
 * drawing out of a body takes is foreseen exactly, from the target's Vita.
 */
public final class Mind {

    /** Below this share of its life a caster is wounded: drawing in heals it, and vanishing serves. */
    public static final float WOUNDED = 0.7F;
    /** Below this share, vanishing serves twice as much. */
    private static final float HARD_PRESSED = 0.4F;
    /** How close the target must be for vanishing to serve. */
    private static final double TOO_CLOSE = 8.0D;
    /** How far a throw reaches, and how far a body can be drawn out of. */
    private static final double THROW_REACH = 32.0D;
    private static final double DRAW_REACH = 16.0D;
    /** What a verb spends when no quantity is written (book 4.3.2). */
    private static final double DEFAULT_QUANTITY = 10.0D;
    /** The risk of an act that serves nothing, and of one that turns on the caster itself. */
    private static final double IDLE_RISK = 0.6D;
    private static final double SELF_RISK = 1000.0D;
    /**
     * How much of the Vis it holds a caster spends on one reading, unless it is desperate: a mage does not empty its
     * reserve at once (drawing a person's bones costs the whole Anchor of the Hundred, and kills).
     */
    public static final double SPEND_SHARE = 0.6D;
    /** What an ally brought to the target is worth, in points of health it will take from it (a first guess). */
    private static final double ALLY_WORTH = 3.0D;
    /** The best few it reads among, and how much luck weighs. */
    private static final int AMONG = 3;
    private static final double LUCK = 0.3D;

    private Mind() {
    }

    /** What a caster sees when it chooses: itself, its target, how far, how whole it is, how much Vis it holds. */
    public record Scene(LivingEntity caster, LivingEntity target, double distance, float healthShare, double vis,
                        ToDoubleFunction<String> allies) {

        /** A scene with no band to call. */
        public Scene(LivingEntity caster, LivingEntity target, double distance, float healthShare, double vis) {
            this(caster, target, distance, healthShare, vis, mark -> 0.0D);
        }

        public boolean wounded() {
            return healthShare < WOUNDED;
        }
    }

    /**
     * What it chose: the option, the intent it serves most, its score, and the harm it foresaw it would do to the target
     * (points of health): what it will compare with what came of it (docs/feiticeiro-design.md, 3.5).
     */
    public record Choice(Option option, Intent intent, double score, double harm) {
    }

    /** The options worth weighing: something they do serves an intent, and nothing they do turns on the caster. */
    public static List<Option> worthWeighing(List<Option> options) {
        return options.stream().filter(option -> option.acts().stream().anyMatch(act -> serves(act) != null)
                && option.acts().stream().noneMatch(Mind::turnsOnSelf)).toList();
    }

    /** What it would read now, if anything is worth it. */
    public static Optional<Choice> choose(Scene scene, Personality personality, List<Option> options,
                                          RandomSource random) {
        return choose(scene, personality, options, random, option -> 1.0D);
    }

    /**
     * What it would read now, if anything is worth it, each option's worth multiplied by what the caster learned of it
     * against this target ({@code learned}: 1 when it learned nothing; more when it did better than foreseen).
     */
    public static Optional<Choice> choose(Scene scene, Personality personality, List<Option> options,
                                          RandomSource random, ToDoubleFunction<Option> learned) {
        Foresight foresight = new Live(scene);
        List<Choice> scored = new ArrayList<>();
        for (Option option : options) {
            score(scene, personality, option, foresight, random, learned.applyAsDouble(option)).ifPresent(scored::add);
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingDouble(Choice::score).reversed());
        List<Choice> best = scored.subList(0, Math.min(AMONG, scored.size()));
        double total = best.stream().mapToDouble(Choice::score).sum();
        double roll = random.nextDouble() * total;
        for (Choice choice : best) {
            roll -= choice.score();
            if (roll <= 0.0D) {
                return Optional.of(choice);
            }
        }
        return Optional.of(best.get(0));
    }

    /** The score of one option in the scene, without luck; empty when it serves nothing now, or cannot be paid for. */
    public static Optional<Choice> evaluate(Scene scene, Personality personality, Option option) {
        return score(scene, personality, option, new Live(scene), null, 1.0D);
    }

    /**
     * The score of one option against a body foreseen as {@code foresight} says, without luck: what the mind weighs
     * when it has no scene before it (when it writes, docs/feiticeiro-design.md, 2.1).
     */
    public static Optional<Choice> evaluate(Scene scene, Personality personality, Option option, Foresight foresight) {
        return score(scene, personality, option, foresight, null, 1.0D);
    }

    /** The score of one option in the scene; empty when it serves nothing now, or cannot be paid for. */
    static Optional<Choice> score(Scene scene, Personality personality, Option option, Foresight foresight,
                                  @org.jetbrains.annotations.Nullable RandomSource random, double learned) {
        Map<Intent, Double> done = new EnumMap<>(Intent.class);
        double harm = 0.0D;
        double price = 0.0D;
        double risk = 0.0D;
        boolean struck = false;
        for (Act act : option.acts()) {
            Intent intent = serves(act);
            if (intent == null) {
                risk += turnsOnSelf(act) ? SELF_RISK : IDLE_RISK;
                struck = false;
                continue;
            }
            // Throwing again what was just thrown (igni iactare iactare) pushes the same thing: it is no second strike.
            boolean again = intent == Intent.STRIKE && struck && act.toward() == Act.Toward.RESULT;
            struck = intent == Intent.STRIKE;
            if (again) {
                risk += IDLE_RISK;
                continue;
            }
            switch (intent) {
                case STRIKE -> {
                    if (scene.distance() <= THROW_REACH) {
                        double strike = potency(act);
                        if (act.fromWorld() && act.toward() == Act.Toward.AIM && scene.distance() <= DRAW_REACH) {
                            // Its source is the body aimed at (aqua tenet iactare): that is drawn out of it too.
                            double life = foresight.lifeTaken(act.element(), option.cost());
                            strike += life;
                            price += life * VitaSystem.UMU_PER_HP;
                        }
                        done.merge(Intent.STRIKE, strike, Double::sum);
                        harm += strike;
                    }
                }
                case DRAW -> {
                    if (scene.distance() <= DRAW_REACH) {
                        double amount = act.quantity().orElse(DEFAULT_QUANTITY);
                        double life = foresight.lifeTaken(act.element(), amount);
                        double healed = scene.wounded() ? Math.min(amount, foresight.lack(act.element()))
                                / VitaSystem.UMU_PER_HP : 0.0D;
                        price += life * VitaSystem.UMU_PER_HP;
                        done.merge(Intent.DRAW, life + healed, Double::sum);
                        harm += life;
                    }
                }
                // Unseen twice is unseen once.
                case VEIL -> done.merge(Intent.VEIL, act.seconds().orElse(6.0D) / 6.0D, Math::max);
                // What the band it brings is worth: the allies of that mark worth bringing (far from the target).
                case CALL -> done.merge(Intent.CALL, ALLY_WORTH * scene.allies().applyAsDouble(act.mark()),
                        Double::sum);
            }
        }
        if (done.isEmpty()) {
            return Optional.empty();
        }
        double spent = option.cost() + price;
        if (spent > scene.vis() * SPEND_SHARE && !personality.desperate(scene.healthShare())) {
            return Optional.empty();
        }
        double score = 0.0D;
        Intent most = null;
        double mostDone = 0.0D;
        for (Map.Entry<Intent, Double> entry : done.entrySet()) {
            double weighed = personality.weight(entry.getKey()) * need(scene, entry.getKey()) * entry.getValue()
                    * learned;
            score += weighed;
            if (weighed > mostDone) {
                mostDone = weighed;
                most = entry.getKey();
            }
        }
        score += -personality.costWeight() * spent - risk + (random == null ? 0.0D : random.nextDouble() * LUCK);
        return score > 0.0D && most != null ? Optional.of(new Choice(option, most, score, harm)) : Optional.empty();
    }

    /** How much the scene asks for {@code intent}. */
    static double need(Scene scene, Intent intent) {
        return switch (intent) {
            case STRIKE -> 1.0D;
            case DRAW -> scene.wounded() ? 1.5D : 1.0D;
            case VEIL -> !scene.wounded() || scene.distance() > TOO_CLOSE ? 0.0D
                    : scene.healthShare() < HARD_PRESSED ? 2.0D : 1.0D;
            case CALL -> 1.0D;
        };
    }

    /**
     * What an act serves, by what its verb does (its operation), never by its rune: throwing at the aim hurts (so does
     * fire made where the aim is), drawing in from the aim draws out of a body, the caster unseen vanishes. Null when
     * it serves none of them.
     */
    public static Intent serves(Act act) {
        boolean atTarget = act.toward() == Act.Toward.AIM || act.toward() == Act.Toward.RESULT;
        if (act.operation().equals("project") && !act.reversed() && atTarget) {
            return Intent.STRIKE;
        }
        if (act.operation().equals("manifest") && !act.reversed() && act.toward() == Act.Toward.AIM
                && act.element() == VitaElement.IGNI) {
            return Intent.STRIKE;
        }
        if (act.operation().equals("manifest") && act.reversed() && act.toward() == Act.Toward.AIM) {
            return Intent.DRAW;
        }
        if (act.operation().equals("manifest") && !act.reversed() && act.mark() != null) {
            return Intent.CALL; // what carries a mark, brought where the caster aims
        }
        if (act.operation().equals("perceive") && act.toward() == Act.Toward.SELF
                && act.visibility().isPresent() && act.visibility().getAsDouble() >= 0.0D
                && act.visibility().getAsDouble() < 1.0D) {
            return Intent.VEIL;
        }
        return null;
    }

    /** Whether an act changes the caster itself, other than vanishing: it would convert or move its own body. */
    static boolean turnsOnSelf(Act act) {
        return act.toward() == Act.Toward.SELF && serves(act) == null;
    }

    /**
     * What a throw does to the body it hits, in points of health: a first guess, by the energy thrown (fire and earth
     * hurt most, air least) and how much of it, half as much again when released all at once. To be set by playing.
     */
    static double potency(Act act) {
        double base = switch (act.element()) {
            case IGNI, FIRMO -> 4.0D;
            case BALANCED -> 3.0D;
            case AQUA -> 2.0D;
            case AURA -> 1.0D;
        };
        if (act.operation().equals("manifest")) {
            base *= 0.6D; // fire made where the target stands: it burns, but does not strike
        }
        double scale = Math.abs(act.quantity().orElse(DEFAULT_QUANTITY)) / DEFAULT_QUANTITY;
        return base * scale * (act.atOnce() ? 1.5D : 1.0D);
    }

    /** What the bodies in a scene would give and lack. */
    public interface Foresight {

        /** The life (points of health) drawing {@code amount} UMU of {@code element} out of the target would take. */
        double lifeTaken(VitaElement element, double amount);

        /** What the caster lacks of {@code element}, in UMU. */
        double lack(VitaElement element);
    }

    /** The bodies of a scene as they are, foreseen once for each question. */
    static final class Live implements Foresight {
        private final Scene scene;
        private final Map<String, Double> taken = new HashMap<>();
        private final Map<VitaElement, Double> lacks = new EnumMap<>(VitaElement.class);

        Live(Scene scene) {
            this.scene = scene;
        }

        @Override
        public double lifeTaken(VitaElement element, double amount) {
            return taken.computeIfAbsent(element.name() + ":" + amount,
                    key -> VitaSystem.lifeTakenBy(scene.target(), element, amount));
        }

        @Override
        public double lack(VitaElement element) {
            return lacks.computeIfAbsent(element, key -> VitaSystem.lackOf(scene.caster(), key));
        }
    }

    /**
     * A body foreseen with no one before it: a person's (twenty points of health, a hundred UMU in a person's
     * proportion), whole; the caster lacking {@code lacking} of each element's full share (0 whole).
     */
    public static Foresight person(double lacking) {
        return new Foresight() {
            @Override
            public double lifeTaken(VitaElement element, double amount) {
                double full = VitaSystem.DEFAULT_TOTAL * share(element);
                return full <= 0.0D ? 0.0D : PERSON_HEALTH * Math.min(1.0D, Math.max(0.0D, amount) / full);
            }

            @Override
            public double lack(VitaElement element) {
                return lacking * VitaSystem.DEFAULT_TOTAL * share(element);
            }
        };
    }

    private static final double PERSON_HEALTH = 20.0D;

    private static double share(VitaElement element) {
        return switch (element) {
            case AQUA -> VitaSystem.AQUA_RATIO;
            case AURA -> VitaSystem.AURA_RATIO;
            case IGNI -> VitaSystem.IGNI_RATIO;
            case FIRMO -> VitaSystem.FIRMO_RATIO;
            case BALANCED -> 0.0D;
        };
    }
}

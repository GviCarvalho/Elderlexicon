package com.elderlexicon.mod.mind;

import com.elderlexicon.mod.spell.Barrier;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * How a caster chooses what to read (docs/feiticeiro-design.md, 3.1 to 3.4 and 3.8): it weighs what the scene asks of it
 * (its intents), and for every option it can read and every way it could look while reading it, what it would do toward
 * them against what it costs and risks:
 * <pre>
 * score = Σ intent weight × what it would do for that intent
 *       − cost weight × (what its verbs spend + the life it would take out of a body, five UMU a point)
 *       − risk (what it would do that serves nothing, or turns on itself)
 *       + a little luck
 * </pre>
 * It reads among the best few, by score, so it is not foreseeable.
 * <p>
 * What a throw does to a body is not foreseen exactly: {@link #potency} is a first guess, to be set by playing, and what
 * came of it corrects it ({@link Memory}). What drawing out of a body takes, and what drawing into its own gives back, is
 * foreseen exactly, from the Vita of each.
 */
public final class Mind {

    /** Below this share of its life a caster is wounded: drawing in heals it, and vanishing serves. */
    public static final float WOUNDED = 0.7F;
    /** Below this share, vanishing serves twice as much. */
    private static final float HARD_PRESSED = 0.4F;
    /** How close the target must be for vanishing to serve. */
    private static final double TOO_CLOSE = 8.0D;
    /** How close a foe must be for getting away to serve. */
    private static final double AT_HAND = 4.5D;
    /** How far short of what it reads a target must stand: it moves while the words are read. */
    public static final double REACH_MARGIN = 1.5D;
    /** How far a body can be drawn out of. */
    private static final double DRAW_REACH = Reach.DRAWN;
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
    /** What keeping a foe off for two seconds is worth, and getting away from it, in points of health (first guesses). */
    private static final double WARD_WORTH = 3.0D;
    private static final double FLEE_WORTH = 3.0D;
    /** What putting out the fire on it is worth: what burning on would take. */
    private static final double DOUSE_WORTH = 4.0D;
    /** The best few it reads among, and how much luck weighs. */
    private static final int AMONG = 3;
    private static final double LUCK = 0.3D;
    /** The ways a choice can look. */
    private static final List<Aim> AIMS = List.of(Aim.TARGET, Aim.SKY, Aim.AWAY, Aim.FEET);

    private Mind() {
    }

    /**
     * What a caster sees when it chooses: itself, its target (null when it has none), how far, how whole it is, how much
     * Vis it holds, its allies, whether it burns, and which energies the world around its hand holds.
     */
    public record Scene(LivingEntity caster, LivingEntity target, double distance, float healthShare, double vis,
                        ToDoubleFunction<String> allies, boolean burning, Predicate<VitaElement> inWorld) {

        /** A scene with no band to call, nothing burning, and the air around it. */
        public Scene(LivingEntity caster, LivingEntity target, double distance, float healthShare, double vis) {
            this(caster, target, distance, healthShare, vis, mark -> 0.0D);
        }

        /** A scene with nothing burning, and the air around it. */
        public Scene(LivingEntity caster, LivingEntity target, double distance, float healthShare, double vis,
                     ToDoubleFunction<String> allies) {
            this(caster, target, distance, healthShare, vis, allies, false, element -> element == VitaElement.AURA);
        }

        public boolean wounded() {
            return healthShare < WOUNDED;
        }

        /** Whether a foe stands before it. */
        public boolean foe() {
            return distance < Double.POSITIVE_INFINITY;
        }
    }

    /**
     * What it chose: the option, the intent it serves most, its score, where it looks reading it, the harm it foresaw
     * it would do to the target and the life it foresaw it would get back (points of health): what it will compare with
     * what came of it (docs/feiticeiro-design.md, 3.5).
     */
    public record Choice(Option option, Intent intent, double score, Aim aim, double harm, double healed) {

        public Choice(Option option, Intent intent, double score, double harm) {
            this(option, intent, score, Aim.TARGET, harm, 0.0D);
        }
    }

    /**
     * The options worth weighing: something they do serves an intent, looking some way, and nothing they do turns on the
     * caster.
     */
    public static List<Option> worthWeighing(List<Option> options) {
        return options.stream().filter(option -> option.acts().stream().anyMatch(act -> !aimsOf(act).isEmpty())
                && option.acts().stream().noneMatch(Mind::turnsOnSelf)).toList();
    }

    /** What it would read now, if anything is worth it. */
    public static Optional<Choice> choose(Scene scene, Personality personality, List<Option> options,
                                          RandomSource random) {
        return choose(scene, personality, options, random, option -> 1.0D);
    }

    /**
     * What it would read now, if anything is worth it, each option's worth multiplied by what the caster learned of it
     * against this target ({@code learned}: 1 when it learned nothing; more when it did better than foreseen, less, or
     * nothing at all, when it did worse).
     */
    public static Optional<Choice> choose(Scene scene, Personality personality, List<Option> options,
                                          RandomSource random, ToDoubleFunction<Option> learned) {
        return choose(scene, personality, options, random, learned, AMONG, LUCK);
    }

    /**
     * What it would read now, as {@link #choose(Scene, Personality, List, RandomSource, ToDoubleFunction)}, reading
     * among the best {@code among} with {@code luck} weighing in: how a degree chooses (an elder leaves little to luck).
     */
    public static Optional<Choice> choose(Scene scene, Personality personality, List<Option> options,
                                          RandomSource random, ToDoubleFunction<Option> learned, int among,
                                          double luck) {
        Foresight foresight = new Live(scene);
        List<Choice> scored = new ArrayList<>();
        for (Option option : options) {
            double factor = learned.applyAsDouble(option);
            if (factor <= 0.0D) {
                continue;
            }
            best(scene, personality, option, foresight, random, factor, luck).ifPresent(scored::add);
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingDouble(Choice::score).reversed());
        List<Choice> best = scored.subList(0, Math.min(Math.max(1, among), scored.size()));
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

    /**
     * The best it could read for each intent, without luck, each option's worth multiplied by what it learned of it:
     * what a doctrine chooses among (docs/feiticeiro-design.md, 3.10).
     */
    public static Map<Intent, Choice> bestByIntent(Scene scene, Personality personality, List<Option> options,
                                                   ToDoubleFunction<Option> learned) {
        Foresight foresight = new Live(scene);
        Map<Intent, Choice> best = new EnumMap<>(Intent.class);
        for (Option option : options) {
            double factor = learned.applyAsDouble(option);
            if (factor <= 0.0D) {
                continue;
            }
            best(scene, personality, option, foresight, null, factor, 0.0D).ifPresent(choice -> best.merge(
                    choice.intent(), choice, (old, fresh) -> fresh.score() > old.score() ? fresh : old));
        }
        return best;
    }

    /** The score of one option in the scene, the best way it could look, without luck; empty when it serves nothing. */
    public static Optional<Choice> evaluate(Scene scene, Personality personality, Option option) {
        return best(scene, personality, option, new Live(scene), null, 1.0D, 0.0D);
    }

    /**
     * The score of one option against bodies foreseen as {@code foresight} says, the best way it could look, without
     * luck: what the mind weighs when it has no scene before it (when it writes, docs/feiticeiro-design.md, 2.1).
     */
    public static Optional<Choice> evaluate(Scene scene, Personality personality, Option option, Foresight foresight) {
        return best(scene, personality, option, foresight, null, 1.0D, 0.0D);
    }

    /** The best of the ways it could look reading {@code option}. */
    private static Optional<Choice> best(Scene scene, Personality personality, Option option, Foresight foresight,
                                         RandomSource random, double learned, double luck) {
        Set<Aim> aims = EnumSet.noneOf(Aim.class);
        option.acts().forEach(act -> aims.addAll(aimsOf(act)));
        if (aims.contains(Aim.ANY) || aims.isEmpty()) {
            aims.remove(Aim.ANY);
            aims.add(Aim.TARGET);
        }
        Optional<Choice> best = Optional.empty();
        for (Aim aim : aims) {
            Optional<Choice> choice = score(scene, personality, option, aim, foresight, random, learned, luck);
            if (choice.isPresent() && (best.isEmpty() || choice.get().score() > best.get().score())) {
                best = choice;
            }
        }
        return best;
    }

    /** The score of one option in the scene, looking {@code aim}; empty when it serves nothing now, or cannot be paid. */
    static Optional<Choice> score(Scene scene, Personality personality, Option option, Aim aim, Foresight foresight,
                                  @org.jetbrains.annotations.Nullable RandomSource random, double learned,
                                  double luck) {
        Map<Intent, Double> done = new EnumMap<>(Intent.class);
        Map<VitaElement, Double> given = new EnumMap<>(VitaElement.class);
        double harm = 0.0D;
        double price = 0.0D;
        double risk = 0.0D;
        boolean struck = false;
        for (Act act : option.acts()) {
            Intent intent = serves(act, aim);
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
                    // What does not get to the target does nothing to it (docs/feiticeiro-design.md, 3.7).
                    if (scene.foe() && scene.distance() + REACH_MARGIN <= Reach.of(act)) {
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
                    if (scene.foe() && scene.distance() + REACH_MARGIN <= DRAW_REACH) {
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
                case CALL -> {
                    if (scene.foe() && scene.distance() + REACH_MARGIN <= Reach.of(act)) {
                        done.merge(Intent.CALL, ALLY_WORTH * scene.allies().applyAsDouble(act.mark()),
                                Double::sum);
                    }
                }
                // What the world around its hand holds of the energy, drawn in: what it gets back is worked out below,
                // all the energies together (the life follows the one most lacking).
                case HEAL -> {
                    if (scene.inWorld().test(act.element())) {
                        given.merge(act.element(), Math.abs(act.quantity().orElse(DEFAULT_QUANTITY)), Double::sum);
                    } else {
                        risk += IDLE_RISK;
                    }
                }
                case WARD -> {
                    double radius = Barrier.radius(Math.abs(act.quantity().orElse(Barrier.DEFAULT_UMU)));
                    if (scene.foe() && scene.distance() <= radius + 0.5D && foresight.keepsOff(act.element())) {
                        done.merge(Intent.WARD, WARD_WORTH * act.seconds().orElse(2.0D) / 2.0D, Math::max);
                    }
                }
                case FLEE -> {
                    if (scene.foe() && scene.distance() <= AT_HAND) {
                        done.merge(Intent.FLEE, FLEE_WORTH, Math::max);
                    }
                }
                case DOUSE -> {
                    if (scene.burning()) {
                        done.merge(Intent.DOUSE, DOUSE_WORTH, Math::max);
                    }
                }
            }
        }
        double healed = 0.0D;
        if (!given.isEmpty()) {
            healed = foresight.lifeGiven(given);
            if (healed > 0.0D) {
                done.merge(Intent.HEAL, healed, Double::sum);
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
        score += -personality.costWeight() * spent - risk + (random == null ? 0.0D : random.nextDouble() * luck);
        return score > 0.0D && most != null ? Optional.of(new Choice(option, most, score, aim, harm, healed))
                : Optional.empty();
    }

    /** How much the scene asks for {@code intent}. */
    static double need(Scene scene, Intent intent) {
        return switch (intent) {
            case STRIKE, CALL -> 1.0D;
            case DRAW -> scene.wounded() ? 1.5D : 1.0D;
            case VEIL -> !scene.wounded() || scene.distance() > TOO_CLOSE ? 0.0D
                    : scene.healthShare() < HARD_PRESSED ? 2.0D : 1.0D;
            // Healing matters more the less whole it is, and is worth little to one nearly whole.
            case HEAL -> scene.healthShare() >= 0.9F ? 0.0D : 2.0D * (1.0D - scene.healthShare());
            case WARD -> scene.healthShare() < HARD_PRESSED ? 2.0D : 1.0D;
            case FLEE -> scene.wounded() ? (scene.healthShare() < HARD_PRESSED ? 2.0D : 1.0D) : 0.3D;
            case DOUSE -> 2.0D;
        };
    }

    /**
     * What an act serves looking {@code aim}, by what its verb does (its operation), never by its rune:
     * <ul>
     *   <li>at the target: throwing at the aim hurts (so does fire made where the aim is, and Vis condensed), drawing in
     *       from the aim draws out of a body, what carries a mark brought there calls the band;</li>
     *   <li>at the sky: drawing in, at no body, draws out of the world into the caster, and heals it;</li>
     *   <li>away: the caster thrown ({@code ego iactare}) gets away;</li>
     *   <li>at its feet: water brought there puts out the fire on it;</li>
     *   <li>anywhere: the caster unseen vanishes, a zone around the caster ({@code vis impediunt}) keeps foes off.</li>
     * </ul>
     * Null when it serves none of them looking so.
     */
    public static Intent serves(Act act, Aim aim) {
        Intent intent = intentOf(act, aim);
        return intent != null && (intent.aim() == aim || intent.aim() == Aim.ANY) ? intent : null;
    }

    /** What an act serves looking at the target. */
    public static Intent serves(Act act) {
        return serves(act, Aim.TARGET);
    }

    /** The ways of looking in which {@code act} serves something. */
    public static Set<Aim> aimsOf(Act act) {
        Set<Aim> aims = EnumSet.noneOf(Aim.class);
        for (Aim aim : AIMS) {
            Intent intent = serves(act, aim);
            if (intent != null) {
                aims.add(intent.aim() == Aim.ANY ? Aim.ANY : aim);
            }
        }
        return aims;
    }

    private static Intent intentOf(Act act, Aim aim) {
        boolean atTarget = act.toward() == Act.Toward.AIM || act.toward() == Act.Toward.RESULT;
        String operation = act.operation();
        if (operation.equals("project") && !act.reversed() && act.toward() == Act.Toward.SELF) {
            return Intent.FLEE; // ego iactare: the caster thrown where it looks
        }
        if (operation.equals("project") && !act.reversed() && atTarget && potency(act) > 0.0D) {
            return Intent.STRIKE;
        }
        if (operation.equals("manifest") && !act.reversed() && act.toward() == Act.Toward.AIM && act.mark() == null) {
            if (act.element() == VitaElement.IGNI) {
                return Intent.STRIKE;
            }
            if (act.element() == VitaElement.AQUA) {
                return Intent.DOUSE;
            }
        }
        if (operation.equals("manifest") && act.reversed() && act.toward() == Act.Toward.AIM && act.mark() == null) {
            // Drawn in from where it looks: out of the body aimed at, or, at none, out of the world.
            if (aim == Aim.SKY) {
                return act.element().isBalanced() ? null : Intent.HEAL;
            }
            return Intent.DRAW;
        }
        if (operation.equals("manifest") && !act.reversed() && act.mark() != null) {
            return Intent.CALL; // what carries a mark, brought where the caster aims
        }
        if (operation.equals("repel") && !act.reversed() && act.mark() == null && act.toward() == Act.Toward.AIM
                && act.reach().isEmpty()) {
            return Intent.WARD; // a zone around the caster: what it keeps out is pushed past its edge
        }
        if (operation.equals("perceive") && act.toward() == Act.Toward.SELF
                && act.visibility().isPresent() && act.visibility().getAsDouble() >= 0.0D
                && act.visibility().getAsDouble() < 1.0D) {
            return Intent.VEIL;
        }
        return null;
    }

    /** Whether an act changes the caster itself, other than vanishing or getting away: it would convert its own body. */
    static boolean turnsOnSelf(Act act) {
        return act.toward() == Act.Toward.SELF && aimsOf(act).isEmpty();
    }

    /**
     * What a throw does to the body it hits, in points of health: a first guess, by the energy thrown (fire and earth
     * hurt most, air least) and how much of it, half as much again when released all at once. To be set by playing.
     * <p>
     * A throw with no source ({@code iactare} alone) is Vis with no aspect: a harmless beam of light. Only condensed
     * ({@code chronos 0}) does Vis show its four aspects at once where it lands.
     */
    static double potency(Act act) {
        double base = switch (act.element()) {
            case IGNI, FIRMO -> 4.0D;
            case BALANCED -> act.atOnce() ? 2.0D : 0.0D;
            case AQUA -> 2.0D;
            case AURA -> 1.0D;
        };
        if (base <= 0.0D) {
            return 0.0D;
        }
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

        /** The life (points of health) the caster would get back with {@code given} put into its body. */
        double lifeGiven(Map<VitaElement, Double> given);

        /** Whether a zone keeping {@code element} out keeps the target out: Vis keeps every creature out. */
        default boolean keepsOff(VitaElement element) {
            return element.isBalanced();
        }
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
            if (scene.target() == null) {
                return 0.0D;
            }
            return taken.computeIfAbsent(element.name() + ":" + amount,
                    key -> VitaSystem.lifeTakenBy(scene.target(), element, amount));
        }

        @Override
        public double lack(VitaElement element) {
            return lacks.computeIfAbsent(element, key -> VitaSystem.lackOf(scene.caster(), key));
        }

        @Override
        public double lifeGiven(Map<VitaElement, Double> given) {
            return VitaSystem.lifeGivenBy(scene.caster(), given);
        }

        @Override
        public boolean keepsOff(VitaElement element) {
            return element.isBalanced() || scene.target() != null
                    && com.elderlexicon.mod.vita.ElementAffinityService.matches(element, scene.target());
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

            @Override
            public double lifeGiven(Map<VitaElement, Double> given) {
                double before = 1.0D - lacking;
                double after = Double.MAX_VALUE;
                for (VitaElement element : VitaElement.values()) {
                    double full = VitaSystem.DEFAULT_TOTAL * share(element);
                    if (full <= 0.0D) {
                        continue;
                    }
                    after = Math.min(after, Math.min(1.0D, before + given.getOrDefault(element, 0.0D) / full));
                }
                return after == Double.MAX_VALUE ? 0.0D : Math.max(0.0D, after - before) * PERSON_HEALTH;
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

package com.elderlexicon.mod.caster;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.illager.SorcererGrimoire;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.mind.Aim;
import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Doctrine;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Memory;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.mind.Phrase;
import com.elderlexicon.mod.mind.PhraseCatalog;
import com.elderlexicon.mod.mind.Preparation;
import com.elderlexicon.mod.mind.Reach;
import com.elderlexicon.mod.mind.Room;
import com.elderlexicon.mod.mind.Threat;
import com.elderlexicon.mod.mind.Variations;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.Casters;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.block.Glossary;
import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.spell.block.SpellBlock;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.RecitationPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.damage.VitaDamageHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The mind of a creature that casts the Old Tongue (docs/feiticeiro-design.md, docs/mago-design.md): what the Sorcerer
 * and the Wizard share. It reads the language as a mage does (book, cap. 5): it stops, opens the trance, and the words
 * it reads are seen above its head, rune by rune, for the two seconds the trance lasts; then the spirit reads them.
 * <p>
 * It is born knowing some runes and not others ({@link Degree}), with a way of weighing what it wants
 * ({@link Personality}). It carries a grimoire written by hand ({@link SorcererGrimoire}), and it composes out of every
 * sentence the mind knows ({@link PhraseCatalog}). Out of a fight it writes its own pages ({@link Preparation}); in a
 * fight its mind ({@link Mind}) weighs what the scene asks against what each option costs and risks, remembers what came
 * of each ({@link Memory}) and varies what worked; it writes down what it sees others cast; it learns runes by living
 * and seeing them, and dreaming them. A blow while the trance lasts breaks it ("a fresta se fechará", cap. 6): nothing
 * is cast, nothing is lost.
 * <p>
 * The creature that owns it gives it a body ({@link Body}): how its trance looks, who its allies are, what else it can
 * read.
 */
public final class CasterMind {

    /** The trance: two seconds, as the book says (cap. 5). */
    public static final int TRANCE_TICKS = 40;
    /** How long it waits, at least, once the last line of what it read is out, before it opens another trance. */
    private static final int REST_TICKS = 20;
    /** How long it waits after its trance was broken, or when nothing was worth reading. */
    private static final int BROKEN_REST_TICKS = 30;
    /** At most this many words are seen above its head (what a recitation holds). */
    private static final int SHOWN_WORDS = 16;
    /** How many of the sentences it composed it keeps in its grimoire. */
    private static final int KEPT_COMPOSED = 8;
    /** How long after the last line of a reading it looks at what came of it. */
    private static final int OUTCOME_TICKS = 30;
    /** How many variations it tries of something that worked, and how many sentences it copies, at most. */
    private static final int VARIATIONS = 2;
    private static final int KEPT_COPIED = 4;
    /** How many experiences of a rune it takes to understand it (docs/aprendizado-runas-design.md): then it dreams it. */
    private static final int TO_UNDERSTAND = 3;
    /** How often, in calm, it looks whether it understood something enough to dream it. */
    private static final int DREAM_TICKS = 60;
    /** How many options it rewrites to get farther in one choice, at most (the grammar reads each try). */
    private static final int FARTHER_PER_CHOICE = 12;
    /** How long, at peace, between two visits to a block of the craft; how far it looks for one; how long it walks. */
    private static final int STUDY_REST_TICKS = 600;
    private static final int STATION_REACH = 10;
    private static final int WALK_TICKS = 300;
    /** How many sentences read in books it keeps. */
    private static final int KEPT_STUDIED = 8;
    /** How often, at peace, it looks for another caster to talk with; and how long it keeps to itself after a talk. */
    private static final int TALK_LOOK_TICKS = 100;
    private static final int TALK_REST_TICKS = 1200;
    /** How much it must be annoyed by one caster to come to blows with it. */
    static final double QUARREL = 2.0D;
    /** Below this share of its life, one who came to blows over words yields. */
    static final float YIELD = 0.35F;
    /** How long two who fought keep to themselves before they talk again. */
    private static final int AFTER_QUARREL_TICKS = 6000;
    /** How often it looks whether it stands among people living in peace. */
    private static final int CIVIL_LOOK_TICKS = 100;
    /** How many villagers within how far make a place where people live, claimed beds and bells or not. */
    private static final int CIVIL_PEOPLE = 3;
    private static final double CIVIL_REACH = 32.0D;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SpellCastingService SPIRIT = new SpellCastingService();

    /** What the creature that owns a mind gives it. */
    public interface Body {

        /** Its trance began: how it shows (arms raised, light). */
        default void tranceBegan() {
        }

        /** Its trance goes on: {@code ticksLeft} more. */
        default void tranceHeld(int ticksLeft) {
        }

        /** Its trance ended, read or broken. */
        default void tranceEnded() {
        }

        /**
         * What it read is being released ({@code true}), or is all out ({@code false}): it points its focus where it
         * aims while the lines come out (docs/mago-design.md, 5i).
         */
        default void releasing(boolean releasing) {
        }

        /** How many of its allies carrying {@code mark} are worth bringing to {@code target}. */
        default double allies(String mark, LivingEntity target) {
            return 0.0D;
        }

        /** What else it can read now, beyond its pages and what it learned (calling its band, for one). */
        default List<Option> moreOptions() {
            return List.of();
        }

        /** Whether it may sit down to write now (a raider celebrating does not). */
        default boolean mayWrite() {
            return true;
        }

        /** Whether it may stop to talk now; as it may write, unless it says otherwise. */
        default boolean mayTalk() {
            return mayWrite();
        }

        /** It said something to another caster: how it sounds. */
        default void spoke() {
        }

        /** It heard something and took it well ({@code pleased}) or badly: how it sounds. */
        default void felt(boolean pleased) {
        }

        /** It opened its book to read or write in it, or closed it: how it shows (the book open in its hands). */
        default void reading(boolean reading) {
        }

        /** Whether it would engrave a page of its own into a wand, at a galdraria table. */
        default boolean wantsEngraving() {
            return false;
        }

        /** It engraved {@code wand} at a galdraria table: what it does with it. */
        default void engraved(ItemStack wand) {
        }

        /** Whether it keeps the peace wherever it is (a member of a university does), as in a village. */
        default boolean keepsThePeace() {
            return false;
        }
    }

    private final Mob owner;
    private final Body body;

    @Nullable
    private Degree degree;
    private Personality personality = Personality.AGGRESSIVE;
    private Set<String> known = Set.of();
    /** The sentences it composed and read, newest last, with the intent each served: what its grimoire keeps. */
    private final Map<String, Intent> composed = new LinkedHashMap<>();
    /** The pages it wrote out of a fight (docs/feiticeiro-design.md, 2.1): once written, it fights with these. */
    private List<Option> written = List.of();
    /** Its writing, while it writes. */
    @Nullable
    private Preparation preparation;
    /** What it can read, worth weighing: made when first needed, and again once the catalog is ready. */
    @Nullable
    private List<Option> options;
    private boolean optionsFromCatalog;

    @Nullable
    private Mind.Choice reading;
    private int tranceTicks;
    private int restTicks = REST_TICKS;
    /** What its reading is still being released against, and for how long: it keeps aiming at it until the last line. */
    @Nullable
    private LivingEntity releasingAt;
    /** Where it keeps looking while the lines are released. */
    private Aim releasingAim = Aim.TARGET;
    private int releaseTicks;
    @Nullable
    private Mind.Choice lastChoice;
    /** What it remembers of the ones it fights, for this life (docs/feiticeiro-design.md, 3.5). */
    private final Memory memory = new Memory();
    /** How fast it saw its foe come, and which foe, and where that one stood a tick ago (docs/feiticeiro-design.md, 3.9). */
    private final Threat threat = new Threat();
    @Nullable
    private UUID threatOf;
    @Nullable
    private Vec3 threatWas;
    /** Its foe's bow, as it watches it: how long it has drawn (-1: it does not), since its last arrow (-1: none seen). */
    private int foeDrawn = -1;
    private int foeSinceShot = -1;
    /** What it read last, against whom, and how many times in a row: what an old hand tires of. */
    @Nullable
    private String lastRead;
    @Nullable
    private UUID lastReadAgainst;
    private int inARow;
    /** How long it is still cornered: it could not get away, so it reads where it stands. */
    private int cornered;
    /** What its doctrine said to do, and the sentence for it when it said to read: null where it has none. */
    @Nullable
    private Doctrine.Stance stance;
    @Nullable
    private Mind.Choice stanceChoice;
    /** What it learned to read in this life: variations of what worked, and sentences it saw others cast. */
    private final List<Option> learned = new ArrayList<>();
    /** The options it already varied: one is varied once. */
    private final Set<String> varied = new HashSet<>();
    /**
     * The sentences it read in the books of the shelves (docs/mago-design.md, 5c), newest last: written by others, in
     * runes it knows. Reading teaches no rune: it teaches what can be said with the ones it knows.
     */
    private final Map<String, Intent> studied = new LinkedHashMap<>();
    /** How long until it goes to a block of the craft again. */
    private int studyRest = STUDY_REST_TICKS;
    /** Its home, and how far from it it wanders at peace. */
    @Nullable
    private net.minecraft.core.BlockPos home;
    private int homeRadius;
    /** The station it works at now, if any. */
    @Nullable
    private Station working;
    /** The sentences it copied from others, newest last: what its grimoire keeps as "copiada". */
    private final Map<String, Intent> copied = new LinkedHashMap<>();
    /** How far it understands each rune it does not know yet (docs/aprendizado-runas-design.md). */
    private final Map<String, Integer> understanding = new LinkedHashMap<>();
    /** A reading whose outcome it is waiting to see. */
    @Nullable
    private Outcome awaiting;
    private int tickCount;
    /** What it can read rewritten to get farther, by what and how far: worked out once (docs/feiticeiro-design.md, 3.7). */
    private final Map<String, Optional<Option>> rewritten = new HashMap<>();
    /** How far what it can read gets, and of which options it was worked out. */
    private double reach = -1.0D;
    private List<Option> reachOf;
    private int reachLearned = -1;
    /** The conversation it is in, if any (docs/mago-design.md, 5b). */
    @Nullable
    private Conversation conversation;
    /** How long until it looks for someone to talk with. */
    private int talkRest = TALK_LOOK_TICKS;
    /** How much what each caster said annoyed it, by who said it. */
    private final Map<UUID, Double> annoyance = new HashMap<>();
    /** The caster it came to blows with, while the quarrel lasts. */
    @Nullable
    private UUID quarrelWith;
    /** What another caster said to it last. */
    private List<String> lastHeard = List.of();
    /** Whether it stood among people living in peace when it last looked, and when that was. */
    private boolean civil;
    private int civilLookedAt = -CIVIL_LOOK_TICKS;
    /** How long it stays offended after walking off from a talk gone bad. */
    private int offended;

    /** A reading against a target: the health the target had, and when to look at what came of it. */
    /**
     * A reading awaiting what came of it: against whom (null for what it read for itself), from how far, the health
     * of each when it was read.
     */
    private record Outcome(Mind.Choice choice, @Nullable LivingEntity target, double distance, float health,
                           float ownHealth, long at) {
    }

    public CasterMind(Mob owner, Body body) {
        this.owner = owner;
        this.body = body;
        // A caster never walks a way through fire, and keeps well off it (docs/feiticeiro-design.md, 3.18): the game's
        // walker only shuns it, and goes through when there is no other way.
        owner.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, -1.0F);
        owner.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE, 16.0F);
    }

    // ------------------------------------------------------------------ who it is

    /** Its degree, personality and runes, drawn the first time they are asked for (a creature is born with them). */
    private void ensureMind() {
        if (degree == null) {
            degree = Degree.draw(owner.getRandom());
            personality = Personality.draw(owner.getRandom());
            known = Set.copyOf(degree.draw(Lexicons.get(), owner.getRandom()));
        }
    }

    public Degree degree() {
        ensureMind();
        return degree;
    }

    public Personality personality() {
        ensureMind();
        return personality;
    }

    /** The runes it knows: it reads only what it can read whole. */
    public Set<String> known() {
        ensureMind();
        return known;
    }

    /** What it read last, and why. */
    public Optional<Mind.Choice> lastChoice() {
        return Optional.ofNullable(lastChoice);
    }

    /**
     * Forgets who it was, to be born anew: a creature saved in a structure is placed many times, and each is drawn its
     * own degree, personality and runes, with nothing written yet (docs/mago-design.md, 5f).
     */
    public void reborn() {
        degree = null;
        known = Set.of();
        written = List.of();
        preparation = null;
        options = null;
        composed.clear();
        copied.clear();
        studied.clear();
        learned.clear();
        understanding.clear();
        home = null;
        ensureMind();
    }

    /** Gives it a mind, for a test or a command: its degree, personality and runes. */
    public void setMind(Degree degree, Personality personality, Set<String> known) {
        this.degree = degree;
        this.personality = personality;
        this.known = Set.copyOf(known);
        this.options = null;
        this.written = List.of();
        this.preparation = null;
    }

    /** The pages it wrote out of a fight. */
    public List<Option> written() {
        return written;
    }

    /** Whether it is writing now. */
    public boolean writing() {
        return preparation != null;
    }

    /** Whether it is in trance now, reading. */
    public boolean inTrance() {
        return reading != null;
    }

    /** Whether it is still releasing the lines of what it read. */
    public boolean releasing() {
        return releaseTicks > 0;
    }

    /** Whether it is at peace: nothing to hunt, nothing read, nothing being released. */
    public boolean calm() {
        return owner.getTarget() == null && reading == null && releaseTicks == 0;
    }

    /** What it learned to read in this life (variations and copies). */
    public List<Option> learned() {
        return List.copyOf(learned);
    }

    /** How far it understands each rune it does not know yet. */
    public Map<String, Integer> understanding() {
        return Collections.unmodifiableMap(understanding);
    }

    /** The sentences it composed and read, newest last. */
    public List<String> composedSentences() {
        return Collections.unmodifiableList(new ArrayList<>(composed.keySet()));
    }

    /** What it learned against {@code opponent}: 1 for what it never tried on them. */
    public double learnedOf(UUID opponent, Option option) {
        return memory.factor(opponent, option);
    }

    // ------------------------------------------------------------------ saved with the creature

    public void save(CompoundTag mind) {
        ensureMind();
        mind.putString("Degree", degree.id());
        mind.putString("Personality", personality.id());
        ListTag runes = new ListTag();
        known.forEach(rune -> runes.add(StringTag.valueOf(rune)));
        mind.put("Known", runes);
        mind.put("Composed", intents(composed));
        ListTag pages = new ListTag();
        written.forEach(page -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", page.name());
            entry.putString("Text", page.text());
            pages.add(entry);
        });
        mind.put("Written", pages);
        mind.put("Copied", intents(copied));
        mind.put("Studied", intents(studied));
        if (home != null) {
            mind.putIntArray("Home", new int[]{home.getX(), home.getY(), home.getZ(), homeRadius});
        }
        CompoundTag understood = new CompoundTag();
        understanding.forEach(understood::putInt);
        mind.put("Understanding", understood);
    }

    public void load(CompoundTag mind) {
        degree = Degree.byId(mind.getString("Degree"));
        personality = Personality.byId(mind.getString("Personality"));
        Set<String> runes = new LinkedHashSet<>();
        ListTag stored = mind.getList("Known", Tag.TAG_STRING);
        for (int i = 0; i < stored.size(); i++) {
            runes.add(stored.getString(i));
        }
        known = Set.copyOf(runes);
        composed.clear();
        composed.putAll(intents(mind.getList("Composed", Tag.TAG_COMPOUND)));
        List<Option> pages = new ArrayList<>();
        ListTag writtenTag = mind.getList("Written", Tag.TAG_COMPOUND);
        for (int i = 0; i < writtenTag.size(); i++) {
            CompoundTag entry = writtenTag.getCompound(i);
            page(entry.getString("Name"), entry.getString("Text")).ifPresent(pages::add);
        }
        written = List.copyOf(pages);
        understanding.clear();
        CompoundTag understood = mind.getCompound("Understanding");
        understood.getAllKeys().forEach(rune -> understanding.put(rune, understood.getInt(rune)));
        copied.clear();
        learned.clear();
        copied.putAll(intents(mind.getList("Copied", Tag.TAG_COMPOUND)));
        copied.keySet().forEach(text -> PhraseCatalog.imagine(List.of(text.split(" "))).map(Option::of)
                .ifPresent(learned::add));
        studied.clear();
        studied.putAll(intents(mind.getList("Studied", Tag.TAG_COMPOUND)));
        int[] savedHome = mind.getIntArray("Home");
        if (savedHome.length == 4) {
            home(new net.minecraft.core.BlockPos(savedHome[0], savedHome[1], savedHome[2]), savedHome[3]);
        }
        studied.keySet().forEach(text -> PhraseCatalog.imagine(List.of(text.split(" "))).map(Option::of)
                .ifPresent(learned::add));
        options = null;
    }

    private static ListTag intents(Map<String, Intent> sentences) {
        ListTag list = new ListTag();
        sentences.forEach((text, intent) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Text", text);
            entry.putString("Intent", intent.name());
            list.add(entry);
        });
        return list;
    }

    private static Map<String, Intent> intents(ListTag list) {
        Map<String, Intent> sentences = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                sentences.put(entry.getString("Text"), Intent.valueOf(entry.getString("Intent")));
            } catch (IllegalArgumentException ignored) {
                // an intent the mind no longer has
            }
        }
        return sentences;
    }

    /** A page as a grimoire keeps it (its name and text), read back into an option. */
    private static Optional<Option> page(String name, String text) {
        SpellBlock block = SpellBlock.parse(text, RuneTokens::normalize);
        if (block.isEmpty()) {
            return Optional.empty();
        }
        return Option.page(name, block.lines().stream()
                .map(line -> new Option.Line(line.runeIds(), block.delaySteps(line))).toList());
    }

    // ------------------------------------------------------------------ what it can read

    /**
     * What it can read, worth weighing: the pages written by hand it can read whole, and the pages it wrote itself.
     * Before it has written any (a fight found it first), every sentence of the catalog it can read: it composes as it
     * fights, slower. Until the catalog is made (a few seconds after the server starts) it has only the pages.
     */
    public List<Option> options() {
        ensureMind();
        if (!written.isEmpty()) {
            if (options == null || optionsFromCatalog) {
                List<Option> all = new ArrayList<>(handwritten());
                all.addAll(written);
                options = Mind.worthWeighing(all);
                optionsFromCatalog = false;
            }
            return options;
        }
        Optional<PhraseCatalog> catalog = PhraseCatalog.ready();
        if (options != null && (optionsFromCatalog || catalog.isEmpty())) {
            return options;
        }
        List<Option> all = new ArrayList<>(handwritten());
        catalog.ifPresent(found -> found.readableBy(known).forEach(phrase -> all.add(Option.of(phrase))));
        options = Mind.worthWeighing(all);
        optionsFromCatalog = catalog.isPresent();
        return options;
    }

    /** The pages of the grimoire written by hand it can read whole, as options. */
    private List<Option> handwritten() {
        List<Option> pages = new ArrayList<>();
        for (SorcererGrimoire.Page page : SorcererGrimoire.get().pages()) {
            SpellBlock block = page.block();
            if (!known.containsAll(block.allRuneIds().stream().filter(Lexicons.get()::isRune).toList())) {
                continue;
            }
            List<Option.Line> lines = block.lines().stream()
                    .map(line -> new Option.Line(line.runeIds(), block.delaySteps(line))).toList();
            Option.page(page.name(), lines).ifPresent(pages::add);
        }
        return pages;
    }

    /**
     * What its mind would read now against {@code target} (null when it has none, and reads only for itself), if
     * anything is worth it. What it learned weighs in: what did nothing twice against this one is not read against it
     * again, nor from as far as it fell short (docs/feiticeiro-design.md, 3.8).
     */
    private Optional<Mind.Choice> choose(@Nullable LivingEntity target) {
        Degree degree = degree();
        return Mind.choose(scene(target), personality(), readable(target), owner.getRandom(), learnedAgainst(target),
                degree.among(), degree.luck());
    }

    /** What it sees, choosing against {@code target} (null: for itself). */
    private Mind.Scene scene(@Nullable LivingEntity target) {
        float share = owner.getHealth() / Math.max(1.0F, owner.getMaxHealth());
        double distance = target == null ? Double.POSITIVE_INFINITY : owner.distanceTo(target);
        return new Mind.Scene(owner, target, distance, share, Casters.vis(owner),
                mark -> target == null ? 0.0D : body.allies(mark, target), owner.isOnFire(), inWorld());
    }

    /** Everything it can read now against {@code target}: its pages, what it learned, what else it can, written farther. */
    private List<Option> readable(@Nullable LivingEntity target) {
        List<Option> all = new ArrayList<>(options());
        all.addAll(learned);
        all.addAll(body.moreOptions());
        if (target != null) {
            all.addAll(farther(all, owner.distanceTo(target)));
        }
        return all;
    }

    /** What it learned of each option against {@code target}, and how tired it is of what it read last. */
    private java.util.function.ToDoubleFunction<Option> learnedAgainst(@Nullable LivingEntity target) {
        // With no foe it reads for itself: what it learned of that is kept under its own name.
        UUID opponent = target == null ? owner.getUUID() : target.getUUID();
        double distance = target == null ? Double.POSITIVE_INFINITY : owner.distanceTo(target);
        Degree degree = degree();
        return option -> (target == null ? memory.factor(opponent, option) : memory.factor(opponent, option, distance))
                * (option.name().equals(lastRead) && opponent.equals(lastReadAgainst) ? degree.habit(inARow) : 1.0D);
    }

    // ------------------------------------------------------------------ a doctrine (docs/feiticeiro-design.md, 3.10)

    /** How often, free in a fight, it asks its doctrine what to do. */
    private static final int DOCTRINE_TICKS = 10;

    /** The kind of foe a doctrine is learned against, as the arena names it; null for one it has none for. */
    @Nullable
    static String kindOf(LivingEntity target) {
        if (target instanceof RuneCaster) {
            return "caster";
        }
        if (target instanceof net.minecraft.world.entity.monster.Zombie) {
            return "zombie";
        }
        if (target instanceof net.minecraft.world.entity.monster.Spider) {
            return "spider";
        }
        if (target instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                && target.isHolding(stack -> stack.getItem() instanceof net.minecraft.world.item.BowItem)) {
            return "skeleton";
        }
        return null;
    }

    /** The doctrine it fights {@code target} by: an elder's, against a kind of foe the arena taught. */
    private Optional<Doctrine> doctrineAgainst(LivingEntity target) {
        String kind = kindOf(target);
        if (degree() != Degree.ELDER || kind == null) {
            return Optional.empty();
        }
        // Against another caster, the doctrine of its own temper: one who pays in flesh fights otherwise (3.17).
        Optional<Doctrine> own = Doctrine.of(kind + "-" + personality().id());
        return own.isPresent() ? own : Doctrine.of(kind);
    }

    /** What it sees of a fight with {@code target}, as the arena saw it. */
    private Doctrine.Situation situation(LivingEntity target) {
        double distance = owner.distanceTo(target);
        return new Doctrine.Situation(distance, ticksToHarm(target) / TRANCE_TICKS,
                owner.getHealth() / Math.max(1.0F, owner.getMaxHealth()), Casters.vis(owner) / Casters.ANCHOR_UMU,
                target.getHealth() / Math.max(1.0F, target.getMaxHealth()), restTicks == 0, roomAgainst(target).room());
    }

    /**
     * Free in a fight, an elder asks its doctrine what to do now: read for something (the mind chooses the sentence),
     * back away, walk up or wait. Where the doctrine never was, it fights as it would with none ({@link #stance} null).
     */
    private void followDoctrine() {
        LivingEntity target = owner.getTarget();
        if (reading != null || releaseTicks > 0) {
            return; // busy: what it chose stands until it is free again
        }
        stance = null;
        stanceChoice = null;
        // Burning, it puts the fire out first: no doctrine has a stance for that, and the mind weighs it above all
        // (docs/feiticeiro-design.md, 3.17).
        if (target == null || !target.isAlive() || conversation != null || owner.isOnFire()) {
            return;
        }
        Optional<Doctrine> doctrine = doctrineAgainst(target);
        if (doctrine.isEmpty()) {
            return;
        }
        Set<Doctrine.Stance> possible = java.util.EnumSet.of(Doctrine.Stance.BACK_OFF, Doctrine.Stance.CLOSE_IN,
                Doctrine.Stance.WAIT, Doctrine.Stance.CIRCLE);
        Map<Intent, Mind.Choice> best = restTicks == 0 && owner.hasLineOfSight(target)
                ? Mind.bestByIntent(scene(target), personality(), readable(target), learnedAgainst(target)) : Map.of();
        for (Doctrine.Stance each : Doctrine.Stance.values()) {
            if (each.intent() != null && best.containsKey(each.intent())) {
                possible.add(each);
            }
        }
        doctrine.get().decide(situation(target), possible).ifPresent(chosen -> {
            stance = chosen;
            stanceChoice = chosen.intent() == null ? null : best.get(chosen.intent());
        });
    }

    /** How a fight looks to it now, for the one who watches it (a GameTest, a command). */
    public String describeFight() {
        LivingEntity target = owner.getTarget();
        if (target == null) {
            return "no foe";
        }
        Doctrine.Situation seen = situation(target);
        String key = doctrineAgainst(target).map(doctrine -> doctrine.steps().key(seen)).orElse("-");
        return String.format(Locale.ROOT,
                "%.1f blocks, %.2f trances to harm, room %.0f, %s%s%s, rest %d, Vis %.0f, last \"%s\", key %s",
                seen.distance(), seen.trancesToBlow(), seen.room(), stance == null ? "no stance" : stance.name(),
                reading != null ? ", reading " + reading.intent() : releaseTicks > 0 ? ", releasing" : "",
                cornered > 0 ? ", cornered" : "", restTicks, Casters.vis(owner),
                lastChoice == null ? "-" : lastChoice.option().name(), key);
    }

    /** What its doctrine says to do now, if it follows one and it knows the situation. */
    public Optional<Doctrine.Stance> stance() {
        return Optional.ofNullable(stance);
    }

    // ------------------------------------------------------------------ where it stands (docs/feiticeiro-design.md, 3.9)

    /**
     * Whether {@code target} comes to strike it with a blow: a person may; a creature does when it hunts it and has a
     * blow to strike with. One who shoots, or reads, keeps where it stands; prey runs.
     */
    private boolean closesIn(LivingEntity target) {
        if (target instanceof net.minecraft.world.entity.player.Player) {
            return true;
        }
        return target instanceof Mob mob && mob.getTarget() == owner
                && !(target instanceof net.minecraft.world.entity.monster.RangedAttackMob)
                && !(target instanceof RuneCaster)
                && mob.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    }

    /** How fast {@code target} comes, as far as it can tell: what it saw of it, or what it guesses of one like it. */
    private double speedOf(LivingEntity target) {
        double guessed = target instanceof net.minecraft.world.entity.player.Player ? Threat.PERSON_SPEED
                : target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                * Threat.PER_SPEED;
        return target.getUUID().equals(threatOf) ? threat.speed(guessed) : guessed;
    }

    /** How far {@code target}'s blow reaches: a creature's, as wide as it is (a spider's farther), as vanilla works it out. */
    private double blowOf(LivingEntity target) {
        if (target instanceof net.minecraft.world.entity.player.Player) {
            return Threat.PERSON_BLOW;
        }
        double wide = target.getBbWidth() * 2.0D;
        return Math.max(Threat.BLOW, Math.sqrt(wide * wide + owner.getBbWidth()));
    }

    /** Whether {@code target} shoots it with a bow (a skeleton): its arrows come as its bow is drawn. */
    private boolean shootsAt(LivingEntity target) {
        return target instanceof Mob mob && mob.getTarget() == owner
                && target instanceof net.minecraft.world.entity.monster.RangedAttackMob
                && target.isHolding(stack -> stack.getItem() instanceof net.minecraft.world.item.BowItem);
    }

    /**
     * In how many ticks {@code target} could hurt it: its blow, when it comes to strike; its arrow, when it shoots
     * (docs/feiticeiro-design.md, 3.11); never, else.
     */
    private double ticksToHarm(LivingEntity target) {
        double distance = owner.distanceTo(target);
        if (target instanceof RuneCaster other && other.casterMind().owner.getTarget() == owner) {
            // Another caster: its trance shows above its head; what it reads comes when the trance ends (3.17).
            CasterMind mind = other.casterMind();
            return Threat.ticksToReading(mind.reading != null, mind.tranceTicks, mind.releaseTicks > 0,
                    mind.restTicks, TRANCE_TICKS);
        }
        if (shootsAt(target)) {
            int interval = owner.level().getDifficulty() == net.minecraft.world.Difficulty.HARD
                    ? Threat.BOW_INTERVAL / 2 : Threat.BOW_INTERVAL;
            return Threat.ticksToArrow(distance, speedOf(target), foeDrawn, foeSinceShot, interval);
        }
        return closesIn(target) ? Threat.ticksToBlow(distance, blowOf(target), speedOf(target))
                : Double.POSITIVE_INFINITY;
    }

    /**
     * Whether it may open its trance with {@code target} where it stands: past where that one broke it before, and, when
     * it sees a foe coming (a master, an elder), far enough that the trance ends before the blow.
     */
    private boolean safeToOpen(LivingEntity target) {
        double distance = owner.distanceTo(target);
        if (distance < memory.safeDistance(target.getUUID())) {
            return false;
        }
        // Only a blow is waited out: waiting out arrows, it is shot while it waits (a doctrine knows when to read).
        double foresight = degree().foresight();
        return foresight <= 0.0D || !closesIn(target)
                || Threat.ticksToBlow(distance, blowOf(target), speedOf(target)) >= TRANCE_TICKS * foresight;
    }

    /**
     * How far it wants {@code target} to stand: no nearer than {@code floor}, than where it was struck in its trance
     * before, nor, when it sees a foe coming, than where the blow comes before the trance ends; and never so far that
     * what it reads would no longer get there.
     */
    private double wantedDistance(LivingEntity target, double floor) {
        double wanted = Math.max(floor, memory.safeDistance(target.getUUID()));
        double foresight = degree().foresight();
        if (foresight > 0.0D && closesIn(target)) {
            wanted = Math.max(wanted, Threat.safeDistance(blowOf(target), speedOf(target), TRANCE_TICKS, foresight));
        }
        double reach = reach();
        return reach > 0.0D ? Math.min(wanted, reach - Math.min(6.0D, reach * 0.25D) - 1.0D) : wanted;
    }

    /** The way it would back off from {@code target}, and how much room it has that way (docs/feiticeiro-design.md, 3.12). */
    private Room.Way roomAgainst(LivingEntity target) {
        return Room.away(ground(), owner.getX(), owner.getZ(), target.getX(), target.getZ());
    }

    /**
     * Where it can stand, at the height it stands: nothing in its feet nor its head, no fluid nor fire, and ground under
     * it (or a block down); a block up it can step onto.
     */
    private Room.Ground ground() {
        net.minecraft.world.level.Level level = owner.level();
        int y = owner.getBlockY();
        return (x, z) -> {
            net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(x, y, z);
            if (passable(level, feet) && passable(level, feet.above())) {
                return solid(level, feet.below()) || passable(level, feet.below()) && solid(level, feet.below(2));
            }
            return solid(level, feet) && passable(level, feet.above()) && passable(level, feet.above(2));
        };
    }

    private static boolean passable(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()
                && !state.is(net.minecraft.tags.BlockTags.FIRE);
    }

    private static boolean solid(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty()
                && !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }

    /** It watches its foe come: how far it came toward it this tick. */
    private void watchFoe() {
        LivingEntity target = owner.getTarget();
        if (target == null || !target.isAlive()) {
            if (threatOf != null) {
                threat.forget();
                threatOf = null;
                threatWas = null;
            }
            return;
        }
        Vec3 now = target.position();
        boolean drawing = target.isUsingItem() && shootsAt(target);
        if (!target.getUUID().equals(threatOf) || threatWas == null) {
            threat.forget();
            threatOf = target.getUUID();
            foeSinceShot = -1;
        } else {
            // It stopped drawing: it let fly.
            if (foeDrawn >= 0 && !drawing) {
                foeSinceShot = 0;
            } else if (foeSinceShot >= 0) {
                foeSinceShot++;
            }
            Vec3 toward = owner.position().subtract(now).multiply(1.0D, 0.0D, 1.0D);
            if (toward.lengthSqr() > 1.0E-6D) {
                threat.saw(now.subtract(threatWas).multiply(1.0D, 0.0D, 1.0D).dot(toward.normalize()));
            }
        }
        threatWas = now;
        foeDrawn = drawing ? target.getTicksUsingItem() : -1;
    }

    /** How far around its hand it looks for an energy to draw in. */
    private static final int WORLD_REACH = 6;

    /**
     * Which energies the world around its hand holds, as a caster sees at a glance: air anywhere, earth where there is
     * ground, water and fire where there is some near.
     */
    private java.util.function.Predicate<VitaElement> inWorld() {
        Set<VitaElement> found = java.util.EnumSet.of(VitaElement.AURA);
        net.minecraft.core.BlockPos center = owner.blockPosition();
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                center.offset(-WORLD_REACH, -2, -WORLD_REACH), center.offset(WORLD_REACH, 3, WORLD_REACH))) {
            net.minecraft.world.level.block.state.BlockState state = owner.level().getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                    || state.is(net.minecraft.tags.BlockTags.ICE) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) {
                found.add(VitaElement.AQUA);
            } else if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    || state.is(net.minecraft.tags.BlockTags.FIRE) || state.is(net.minecraft.tags.BlockTags.CAMPFIRES)
                    || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
                found.add(VitaElement.IGNI);
            } else if (state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SAND)) {
                found.add(VitaElement.FIRMO);
            }
            if (found.size() == 4) {
                break;
            }
        }
        return found::contains;
    }

    /**
     * What it can read rewritten to get to a target {@code distance} away, when it knows how to write a distance and
     * what it would throw falls short (docs/feiticeiro-design.md, 3.7): {@code igni iactare} becomes {@code igni 32 ubis
     * iactare}. One who does not know how walks up instead ({@link #approachGoal}).
     */
    private List<Option> farther(List<Option> all, double distance) {
        if (Reach.placeRune(known()).isEmpty()) {
            return List.of();
        }
        if (rewritten.size() > 512) {
            rewritten.clear();
        }
        long far = (long) Reach.written(distance);
        List<Option> found = new ArrayList<>();
        int tried = 0;
        // The plainest first: a sentence alone is the likeliest to read well with a distance in it.
        List<Option> plainestFirst = new ArrayList<>(all);
        plainestFirst.sort(java.util.Comparator.comparingInt((Option option) -> option.lines().size())
                .thenComparingInt(option -> option.words().size()));
        for (Option option : plainestFirst) {
            if (Reach.gets(option, distance) || option.acts().stream().map(Mind::serves)
                    .noneMatch(intent -> intent == Intent.STRIKE || intent == Intent.CALL)) {
                continue;
            }
            String key = option.name() + "@" + far;
            Optional<Option> farther = rewritten.get(key);
            if (farther == null) {
                if (tried++ >= FARTHER_PER_CHOICE) {
                    continue;
                }
                farther = Reach.farther(option, distance, known);
                rewritten.put(key, farther);
            }
            farther.ifPresent(found::add);
        }
        return found;
    }

    /**
     * How far what it can read gets: the farthest of what it can hurt or draw with; as far as it aims when it knows how
     * to write a distance. Zero when it has nothing to fight with (yet).
     */
    public double reach() {
        List<Option> current = options();
        if (reach < 0.0D || current != reachOf || learned.size() != reachLearned) {
            double best = 0.0D;
            List<Option> all = new ArrayList<>(current);
            all.addAll(learned);
            all.addAll(body.moreOptions());
            for (Option option : all) {
                if (option.acts().stream().map(Mind::serves)
                        .anyMatch(intent -> intent == Intent.STRIKE || intent == Intent.DRAW)) {
                    // As far as it gets, or as far as it learned it does not.
                    best = Math.max(best, Math.min(Reach.of(option), memory.reachOf(option) - 1.0D));
                }
            }
            if (best > 0.0D && Reach.placeRune(known()).isPresent()) {
                best = Math.max(best, Reach.SIGHT);
            }
            reach = best;
            reachOf = current;
            reachLearned = learned.size();
        }
        return reach;
    }

    // ------------------------------------------------------------------ living

    /** One tick of the mind, on the server: the lines still released, the rest, a dream in calm, what came of a reading. */
    public void tick() {
        tickCount++;
        watchFoe();
        if (cornered > 0) {
            cornered--;
        }
        if (tickCount % DOCTRINE_TICKS == 0) {
            followDoctrine();
        }
        if (releaseTicks > 0) {
            // The lines come out one step after another, each where it looks then: it keeps the aim.
            releaseTicks--;
            if (releasingAim != Aim.TARGET) {
                look(releasingAim, releasingAt);
            } else if (releasingAt != null && releasingAt.isAlive()) {
                aimAt(releasingAt);
            }
            if (releaseTicks == 0) {
                releasingAt = null;
                body.releasing(false);
            }
        } else if (reading == null && restTicks > 0) {
            restTicks--;
        }
        if (calm() && studyRest > 0) {
            studyRest--;
        }
        if (calm() && tickCount % DREAM_TICKS == 0) {
            dream();
        }
        if (awaiting != null && owner.level().getGameTime() >= awaiting.at()) {
            remember(awaiting);
            awaiting = null;
        }
        if (quarrelWith != null) {
            quarrelling();
        }
        if (offended > 0) {
            offended--;
        }
        if (conversation != null) {
            if (conversation.ledBy(this)) {
                conversation.tick();
            }
        } else if (--talkRest <= 0) {
            talkRest = TALK_LOOK_TICKS;
            lookForTalk();
        }
    }

    /**
     * It was hurt: it lives the energy that struck it (docs/aprendizado-runas-design.md), and a blow during its trance
     * breaks it, and is remembered of who struck. A blow: something struck it. Burning, drowning or a fall do not
     * break it, or one on fire could never read the water that puts it out.
     */
    public void hurt(DamageSource source) {
        lived(source).flatMap(CasterMind::sourceOf).ifPresent(this::experience);
        if (reading != null && (source.getEntity() != null || source.getDirectEntity() != null)) {
            if (source.getEntity() != null) {
                memory.broken(source.getEntity().getUUID());
            }
            endTrance(false);
            restTicks = BROKEN_REST_TICKS;
            owner.playSound(SoundEvents.EVOKER_PREPARE_WOLOLO, 0.6F, 0.6F);
        }
    }

    /**
     * What came of a reading, next to what it foresaw: it remembers it of that target, and varies what did better than
     * foreseen (docs/feiticeiro-design.md, 3.5).
     */
    private void remember(Outcome outcome) {
        Option option = outcome.choice().option();
        if (outcome.choice().healed() > 0.0D) {
            // What it read to heal: did it get its life back?
            double gained = owner.isAlive() ? Math.max(0.0D, owner.getHealth() - outcome.ownHealth()) : 0.0D;
            memory.outcome(owner.getUUID(), option, outcome.choice().healed(), gained);
            if (gained < 0.25D) {
                frustrated();
            }
        }
        LivingEntity target = outcome.target();
        if (target == null || outcome.choice().harm() <= 0.0D) {
            return; // it did not mean to hurt (it vanished, warded, fled): nothing to compare
        }
        float now = target.isAlive() ? target.getHealth() : 0.0F;
        double done = Math.max(0.0D, outcome.health() - now);
        // Only from far enough is falling short a likely cause of doing nothing (near, the body is what withstood it).
        if (outcome.distance() > 0.5D * Reach.of(option)) {
            memory.outcome(target.getUUID(), option, outcome.choice().harm(), done, outcome.distance());
        } else {
            memory.outcome(target.getUUID(), option, outcome.choice().harm(), done);
        }
        if (done < 0.25D) {
            frustrated();
        }
        if (memory.worked(target.getUUID(), option) && varied.add(option.name())) {
            PhraseCatalog.ready().ifPresent(catalog -> {
                List<Option> variations = Variations.of(option, catalog.readableBy(known()), owner.getRandom(),
                        VARIATIONS);
                learned.addAll(Mind.worthWeighing(variations));
            });
        }
    }

    /** What it read did nothing: it shows it, and is a little readier to try something else. */
    private void frustrated() {
        if (owner.level() instanceof ServerLevel server) {
            Vec3 eyes = owner.getEyePosition();
            server.sendParticles(ParticleTypes.SMOKE, eyes.x, eyes.y + 0.4D, eyes.z, 6, 0.2D, 0.1D, 0.2D, 0.01D);
        }
        body.felt(false);
    }

    // ------------------------------------------------------------------ learning (docs/aprendizado-runas-design.md)

    /** It saw these words work: each rune of them it does not know, it understands a little more. */
    public void witness(List<String> words) {
        ensureMind();
        Set<String> learnable = Degree.learnable(Lexicons.get());
        words.stream().distinct().filter(word -> learnable.contains(word) && !known.contains(word))
                .forEach(this::experience);
    }

    /** One more experience of {@code rune}, if it does not know it yet. */
    public void experience(String rune) {
        ensureMind();
        if (!known.contains(rune) && Degree.learnable(Lexicons.get()).contains(rune)) {
            understanding.merge(rune, 1, Integer::sum);
        }
    }

    /**
     * The energy a hurt makes one live: fire burning or cold freezing, the earth of a fall or a crush, the air of
     * drowning, smothering or lightning, the water of the blood a cut spills. Empty for a hurt of no energy (magic).
     */
    private Optional<VitaElement> lived(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING)) {
            return Optional.of(VitaElement.IGNI);
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FALLING_BLOCK)
                || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE)) {
            return Optional.of(VitaElement.FIRMO);
        }
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypeTags.IS_LIGHTNING)) {
            return Optional.of(VitaElement.AURA);
        }
        for (VitaElement element : VitaDamageHandler.natureOf(owner, source)) {
            if (element == VitaElement.AQUA) {
                return Optional.of(VitaElement.AQUA);
            }
        }
        return Optional.empty();
    }

    /** The source rune of an energy (the one the lexicon gives it). */
    private static Optional<String> sourceOf(VitaElement element) {
        return Lexicons.get().runes().stream()
                .filter(rune -> rune.source().map(spec -> spec.element() == element).orElse(false))
                .map(com.elderlexicon.mod.magic.lexicon.Rune::id)
                .findFirst();
    }

    /**
     * In calm, the rune it understands best, if it understands it enough, comes to it in a dream: it learns it. Its
     * degree follows what it knows, and it writes its grimoire again with it.
     */
    public boolean dream() {
        ensureMind();
        Optional<Map.Entry<String, Integer>> best = understanding.entrySet().stream()
                .filter(entry -> entry.getValue() >= TO_UNDERSTAND)
                .max(Map.Entry.comparingByValue());
        if (best.isEmpty()) {
            return false;
        }
        String rune = best.get().getKey();
        understanding.remove(rune);
        Set<String> more = new LinkedHashSet<>(known);
        more.add(rune);
        known = Set.copyOf(more);
        degree = Degree.of(known.size());
        written = List.of();
        preparation = null;
        options = null;
        if (owner.level() instanceof ServerLevel server) {
            Vec3 eyes = owner.getEyePosition();
            server.sendParticles(ParticleTypes.SOUL, eyes.x, eyes.y + 0.3D, eyes.z, 16, 0.4D, 0.4D, 0.4D, 0.02D);
        }
        owner.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F);
        return true;
    }

    /** Writes down what it saw cast, as much of it as it can read; returns whether it kept it. */
    public boolean copy(List<String> words) {
        ensureMind();
        List<String> kept = words.stream()
                .filter(word -> known.contains(word) || isNumber(word))
                .toList();
        if (kept.isEmpty()) {
            return false;
        }
        String text = String.join(" ", kept);
        if (copied.containsKey(text) || learned.stream().anyMatch(option -> option.name().equals(text))) {
            return false;
        }
        Optional<Option> copy = PhraseCatalog.imagine(kept).map(Option::of)
                .filter(option -> !Mind.worthWeighing(List.of(option)).isEmpty());
        if (copy.isEmpty()) {
            return false;
        }
        Intent intent = copy.get().acts().stream().map(Mind::serves).filter(java.util.Objects::nonNull).findFirst()
                .orElse(Intent.STRIKE);
        copied.put(text, intent);
        learned.add(copy.get());
        while (copied.size() > KEPT_COPIED) {
            String oldest = copied.keySet().iterator().next();
            copied.remove(oldest);
            learned.removeIf(option -> option.name().equals(oldest));
        }
        if (owner.level() instanceof ServerLevel server) {
            Vec3 eyes = owner.getEyePosition();
            server.sendParticles(ParticleTypes.ENCHANT, eyes.x, eyes.y + 0.4D, eyes.z, 12, 0.4D, 0.3D, 0.4D, 0.8D);
        }
        return true;
    }

    private static boolean isNumber(String word) {
        return word.matches("-?\\d+");
    }

    // ------------------------------------------------------------------ the trance

    /** The words seen above its head: what it reads, as many as a recitation holds. */
    private static List<String> shown(Option option) {
        List<String> words = option.words();
        return words.size() <= SHOWN_WORDS ? words : words.subList(0, SHOWN_WORDS);
    }

    private void beginTrance(Mind.Choice choice) {
        reading = choice;
        tranceTicks = 0;
        body.tranceBegan();
        owner.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
        recite(List.of(), false);
    }

    /** One tick of the trance: the words come one by one; at the end, the spirit reads them. */
    private void holdTrance(@Nullable LivingEntity target) {
        // Fire does not break a trance; one who sees what comes (a master, an elder) breaks it itself, and gets out
        // (docs/feiticeiro-design.md, 3.18). An apprentice reads on, burning.
        if (standingInFire() && degree().foresight() > 0.0D) {
            endTrance(false);
            restTicks = BROKEN_REST_TICKS / 3;
            return;
        }
        Option option = reading.option();
        tranceTicks++;
        body.tranceHeld(Math.max(1, TRANCE_TICKS - tranceTicks));
        List<String> words = shown(option);
        int spoken = Math.min(words.size(), (int) Math.ceil(words.size() * tranceTicks / (double) (TRANCE_TICKS - 5)));
        if (tranceTicks % 4 == 0 || tranceTicks == 1) {
            recite(words.subList(0, spoken), false);
        }
        if (tranceTicks >= TRANCE_TICKS) {
            cast(reading, target);
        }
    }

    private void cast(Mind.Choice choice, @Nullable LivingEntity target) {
        Option option = choice.option();
        // The aim is enough (docs/feiticeiro-design.md, 7.4): it looks at the middle of what it reads against, or where
        // what it reads for itself needs it to look (3.8).
        look(choice.aim(), target);
        // Read as a player's page is (docs/fluxo-design.md): every line at once, each released at its step. What
        // happens to a creature is not kept, so a line that waits for something to happen is not read.
        List<SpellCastingService.TimedSpell> spells = option.lines().stream()
                .filter(line -> line.words().stream().noneMatch(CasterMind::condition))
                .map(line -> new SpellCastingService.TimedSpell(line.words(), line.delaySteps()))
                .toList();
        try {
            SpellCastingService.Result result = SPIRIT.castBlock(owner, spells, later -> {
            });
            if (result.failed()) {
                LOGGER.debug("{} could not cast '{}': {}", owner.getName().getString(), option.name(),
                        result.message().getString());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("{} failed casting '{}'", owner.getName().getString(), option.name(), exception);
        }
        if (option.composed()) {
            composed.remove(option.name());
            composed.put(option.name(), choice.intent());
            while (composed.size() > KEPT_COMPOSED) {
                composed.remove(composed.keySet().iterator().next());
            }
        }
        lastChoice = choice;
        UUID against = target == null ? owner.getUUID() : target.getUUID();
        inARow = option.name().equals(lastRead) && against.equals(lastReadAgainst) ? inARow + 1 : 1;
        lastRead = option.name();
        lastReadAgainst = against;
        // It keeps looking while the lines are out and the taps it opened run (a throw runs two seconds): the flow
        // lands where it looks at each moment, so looking away is missing.
        // (A line is released at the step after the reading: it keeps looking that long at least.)
        int released = option.lastStep() * SpellCastingService.STEP_TICKS
                + Math.max(channel(option), SpellCastingService.STEP_TICKS + 1);
        awaiting = new Outcome(choice, choice.aim() == Aim.TARGET ? target : null,
                target == null ? 0.0D : owner.distanceTo(target), target == null ? 0.0F : target.getHealth(),
                owner.getHealth(), owner.level().getGameTime() + released + OUTCOME_TICKS);
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.EVOKER_CAST_SPELL,
                owner.getSoundSource(), 1.0F, 1.0F);
        endTrance(true);
        releasingAt = target;
        releasingAim = choice.aim();
        releaseTicks = released;
        body.releasing(released > 0);
        restTicks = REST_TICKS + owner.getRandom().nextInt(20);
    }

    /** How long the taps of what it read stay open after the last line: a throw's two seconds, or its chronos. */
    private static int channel(Option option) {
        int longest = 0;
        for (com.elderlexicon.mod.mind.Act act : option.acts()) {
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

    /** Looks as {@code aim} says: at the target, at the sky, away from the target, at its own feet. */
    private void look(Aim aim, @Nullable LivingEntity target) {
        Vec3 eyes = owner.getEyePosition();
        Vec3 ahead = owner.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
        if (ahead.lengthSqr() < 1.0E-6D) {
            ahead = new Vec3(0.0D, 0.0D, 1.0D);
        }
        ahead = ahead.normalize();
        switch (aim) {
            case TARGET, ANY -> {
                if (target != null) {
                    aimAt(target);
                }
                return;
            }
            // Up, at no body, so what is drawn in comes out of the world around its hand.
            case SKY -> lookAt(eyes.add(ahead.scale(0.5D)).add(0.0D, 8.0D, 0.0D));
            // Away from the foe, a little up: what throws it throws it there.
            case AWAY -> {
                Vec3 from = target == null ? ahead.reverse() : owner.position().subtract(target.position())
                        .multiply(1.0D, 0.0D, 1.0D);
                Vec3 away = from.lengthSqr() < 1.0E-6D ? ahead.reverse() : from.normalize();
                lookAt(eyes.add(away.scale(4.0D)).add(0.0D, 1.5D, 0.0D));
            }
            // Its own feet, a hair ahead: what is brought there is brought where it stands.
            case FEET -> lookAt(owner.position().add(ahead.scale(0.2D)));
        }
    }

    private void lookAt(Vec3 point) {
        owner.lookAt(EntityAnchorArgument.Anchor.EYES, point);
        owner.setYHeadRot(owner.getYRot());
        owner.setYBodyRot(owner.getYRot());
    }

    /** Whether a word is a condition: a line that waits for something to happen. */
    private static boolean condition(String word) {
        return Lexicons.get().rune(word).map(rune -> rune.is(WordClass.CONDITION)).orElse(false);
    }

    private void endTrance(boolean spoken) {
        Mind.Choice choice = reading;
        reading = null;
        tranceTicks = 0;
        body.tranceEnded();
        // Spoken, the words linger and fade; broken, they are gone at once.
        recite(spoken && choice != null ? shown(choice.option()) : List.of(), true);
    }

    private void aimAt(LivingEntity target) {
        Vec3 middle = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        owner.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
        owner.setYHeadRot(owner.getYRot());
        owner.setYBodyRot(owner.getYRot());
    }

    private void recite(List<String> runes, boolean finished) {
        SpellingNetwork.broadcastRecitation(owner, new RecitationPacket(owner.getId(), runes, finished));
    }

    // ------------------------------------------------------------------ talking (docs/mago-design.md, 5b)

    /** Something said: the words, and whether they correct what the other said, or say it back. */
    record Utterance(List<String> words, boolean correcting, boolean agreeing) {
    }

    Mob owner() {
        return owner;
    }

    /** Whether it is at leisure to talk: as its body allows, and in no quarrel. */
    boolean atLeisure() {
        return body.mayTalk() && quarrelWith == null;
    }

    /** Whether it is talking with another caster now. */
    public boolean talking() {
        return conversation != null;
    }

    /** How much what {@code other} said annoyed it. */
    public double annoyanceWith(UUID other) {
        return annoyance.getOrDefault(other, 0.0D);
    }

    /** Whether it came to blows with {@code other} over words, and the quarrel lasts. */
    public boolean quarrelling(UUID other) {
        return other.equals(quarrelWith);
    }

    /**
     * At peace and at leisure, it looks for another caster nearby to talk with, now and then. One bent over its grimoire
     * is not talked to, nor talks.
     */
    private void lookForTalk() {
        if (!calm() || !atLeisure() || writing() || working != null || owner.getRandom().nextInt(3) != 0) {
            return;
        }
        owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(Conversation.REACH),
                        mob -> mob != owner && mob instanceof RuneCaster && mob.isAlive() && owner.hasLineOfSight(mob))
                .stream()
                .map(mob -> ((RuneCaster) mob).casterMind())
                .filter(other -> other.conversation == null && other.calm() && other.atLeisure()
                        && !other.writing() && other.working == null && other.talkRest <= TALK_LOOK_TICKS)
                .min(java.util.Comparator.comparingDouble(other -> other.owner.distanceToSqr(owner)))
                .ifPresent(this::talkWith);
    }

    /** Begins a conversation with {@code other}, now: when it finds someone, or for a test or a command. */
    public void talkWith(CasterMind other) {
        if (conversation == null && other.conversation == null && other != this) {
            Conversation.begin(this, other);
        }
    }

    void joined(Conversation conversation) {
        this.conversation = conversation;
        owner.getNavigation().stop();
    }

    void left(Conversation conversation) {
        if (this.conversation == conversation) {
            this.conversation = null;
            talkRest = TALK_REST_TICKS + owner.getRandom().nextInt(TALK_REST_TICKS);
            annoyance.replaceAll((who, value) -> value * 0.5D);
            annoyance.values().removeIf(value -> value < 0.1D);
        }
    }

    /**
     * What it says, having heard {@code heard} (null when it speaks first): now and then the other's sentence
     * corrected (the same thing, with its own energy or number), now and then said back when it can read it, mostly
     * something of its own: a page it wrote, a sentence it composed or copied, any sentence it can read.
     */
    Utterance utter(@Nullable List<String> heard) {
        ensureMind();
        RandomSource random = owner.getRandom();
        if (heard != null && !heard.isEmpty()) {
            float roll = random.nextFloat();
            Optional<Phrase> phrase = PhraseCatalog.imagine(heard);
            if (roll < 0.45F && phrase.isPresent() && PhraseCatalog.ready().isPresent()) {
                List<Option> corrected = Variations.of(Option.of(phrase.get()),
                        PhraseCatalog.ready().get().readableBy(known), random, 1);
                if (!corrected.isEmpty()) {
                    return new Utterance(corrected.get(0).lines().get(0).words(), true, false);
                }
            }
            if (roll < 0.7F && heard.stream().allMatch(word -> known.contains(word) || isNumber(word)
                    || !Lexicons.get().isRune(word))) {
                return new Utterance(heard, false, true);
            }
        }
        List<List<String>> own = new ArrayList<>();
        written.forEach(page -> own.add(page.lines().get(0).words()));
        composed.keySet().forEach(text -> own.add(List.of(text.split(" "))));
        copied.keySet().forEach(text -> own.add(List.of(text.split(" "))));
        if (own.isEmpty()) {
            PhraseCatalog.ready().map(catalog -> catalog.readableBy(known)).filter(list -> !list.isEmpty())
                    .ifPresent(list -> own.add(list.get(random.nextInt(list.size())).words()));
        }
        if (own.isEmpty()) {
            List<String> runes = new ArrayList<>(known);
            Collections.shuffle(runes, new java.util.Random(random.nextLong()));
            own.add(runes.subList(0, Math.min(runes.size(), 2 + random.nextInt(2))));
        }
        return new Utterance(own.get(random.nextInt(own.size())), false, false);
    }

    /** It says {@code said}: the words are seen above its head, and linger, and fade. */
    void say(Utterance said) {
        recite(said.words().size() <= SHOWN_WORDS ? said.words() : said.words().subList(0, SHOWN_WORDS), true);
        body.spoke();
    }

    /**
     * It hears {@code said} from {@code speaker}: it lives the runes of it it does not know, a little, and takes it well
     * or badly, by its temper and by what was said (being corrected stings, being shown runes it cannot read stings
     * less, its own words said back please it). A Sorcerer and a Wizard take each other's words worse. Returns whether
     * it will not take any more: a quarrel.
     */
    boolean hear(Utterance said, CasterMind speaker) {
        ensureMind();
        lastHeard = said.words();
        witness(said.words());
        double temper = switch (personality) {
            case AGGRESSIVE -> 1.0D;
            case TRICKSTER -> 0.6D;
            case CAUTIOUS -> 0.35D;
        };
        boolean unreadable = said.words().stream().anyMatch(word -> Lexicons.get().isRune(word)
                && !known.contains(word));
        double slight = said.correcting() ? 0.9D : said.agreeing() ? -0.3D : unreadable ? 0.5D : 0.2D;
        if (slight > 0.0D && speaker.owner.getClass() != owner.getClass()) {
            slight *= 1.5D; // another kind of caster: another school
        }
        double taken = temper * slight * (0.5D + owner.getRandom().nextDouble());
        double now = Math.max(0.0D, annoyance.getOrDefault(speaker.owner.getUUID(), 0.0D) + taken);
        annoyance.put(speaker.owner.getUUID(), now);
        boolean pleased = taken <= 0.25D;
        if (owner.level() instanceof ServerLevel server) {
            Vec3 eyes = owner.getEyePosition();
            server.sendParticles(pleased ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER, eyes.x,
                    eyes.y + 0.5D, eyes.z, pleased ? 4 : 2, 0.3D, 0.2D, 0.3D, 0.0D);
        }
        body.felt(pleased);
        return now >= QUARREL;
    }

    /** What another caster said to it last. */
    public List<String> lastHeard() {
        return lastHeard;
    }

    /** It will not take any more from {@code other}: they come to blows, until one yields. */
    public void quarrel(CasterMind other) {
        annoyance.remove(other.owner.getUUID());
        quarrelWith = other.owner.getUUID();
        other.quarrelWith = owner.getUUID();
        owner.setTarget(other.owner);
        other.owner.setTarget(owner);
        owner.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.6F);
    }

    /**
     * It will not take any more from {@code other}. Out in the wild, they come to blows ({@link #quarrel}); among people
     * living in peace (a village, a university), neither does: the one offended turns its back and walks off, and the
     * two keep to themselves for a while (docs/mago-design.md, 5h).
     */
    public void fallOut(CasterMind other) {
        if (civilized() || other.civilized()) {
            walkOff(other);
        } else {
            quarrel(other);
        }
    }

    /**
     * Whether it stands among people living in peace: in a village (its claimed beds and bells, or three villagers
     * near), on a university's grounds, or it keeps the peace anyway.
     */
    public boolean civilized() {
        if (body.keepsThePeace()) {
            return true;
        }
        if (!(owner.level() instanceof ServerLevel server)) {
            return false;
        }
        if (tickCount - civilLookedAt >= CIVIL_LOOK_TICKS) {
            civilLookedAt = tickCount;
            net.minecraft.core.BlockPos at = owner.blockPosition();
            civil = server.isVillage(at) || server.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                    owner.getBoundingBox().inflate(CIVIL_REACH), LivingEntity::isAlive).size() >= CIVIL_PEOPLE
                    || server.structureManager().getStructureWithPieceAt(at,
                    com.elderlexicon.mod.university.UniversityStructure.KEY).isValid();
        }
        return civil;
    }

    /** It turns its back on {@code other}, offended, and walks off; neither talks to the other for a while. */
    private void walkOff(CasterMind other) {
        annoyance.remove(other.owner.getUUID());
        other.annoyance.remove(owner.getUUID());
        talkRest = AFTER_QUARREL_TICKS;
        other.talkRest = AFTER_QUARREL_TICKS;
        offended = AFTER_QUARREL_TICKS;
        if (owner.level() instanceof ServerLevel server) {
            Vec3 eyes = owner.getEyePosition();
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, eyes.x, eyes.y + 0.5D, eyes.z, 4, 0.3D, 0.2D, 0.3D,
                    0.0D);
        }
        body.felt(false);
        if (owner instanceof net.minecraft.world.entity.PathfinderMob walker) {
            Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(walker, 12, 4,
                    other.owner.position());
            if (away != null) {
                owner.getNavigation().moveTo(away.x, away.y, away.z, 0.6D);
            }
        }
    }

    /** How long since it walked off from a talk gone bad: while it lasts, it is still offended (for a test). */
    public boolean offended() {
        return offended > 0;
    }

    /** While the quarrel lasts: it ends when the other is gone or another foe took its place, or when one yields. */
    private void quarrelling() {
        LivingEntity target = owner.getTarget();
        if (target == null || !target.isAlive() || !target.getUUID().equals(quarrelWith)) {
            quarrelWith = null;
            return;
        }
        float share = owner.getHealth() / Math.max(1.0F, owner.getMaxHealth());
        if (share < YIELD) {
            yieldTo(target);
        }
    }

    /** It yields: both let go, and keep to themselves for a while. */
    private void yieldTo(LivingEntity winner) {
        quarrelWith = null;
        owner.setTarget(null);
        owner.setLastHurtByMob(null);
        talkRest = AFTER_QUARREL_TICKS;
        if (winner instanceof Mob mob && winner instanceof RuneCaster caster
                && caster.casterMind().quarrelling(owner.getUUID())) {
            CasterMind other = caster.casterMind();
            other.quarrelWith = null;
            other.talkRest = AFTER_QUARREL_TICKS;
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
        }
        if (owner.level() instanceof ServerLevel server) {
            Vec3 eyes = owner.getEyePosition();
            server.sendParticles(ParticleTypes.POOF, eyes.x, eyes.y, eyes.z, 10, 0.3D, 0.3D, 0.3D, 0.02D);
        }
        body.felt(false);
    }

    // ------------------------------------------------------------------ its grimoire

    /**
     * Its grimoire as an item (docs/feiticeiro-design.md, 5): the pages written by hand it could read, the pages it
     * wrote, the sentences it composed and read in this life (named by what it wanted with them) and the ones it copied,
     * with the creature as their author. Read with surgit, the runes the reader does not know are skipped, as in any
     * reading; the grimoire shows them all the same, and says what each one does.
     */
    public ItemStack grimoire() {
        ensureMind();
        ItemStack book = new ItemStack(ElderLexicon.GRIMOIRE.get());
        CompoundTag tag = book.getOrCreateTag();
        ListTag pages = new ListTag();
        ListTag names = new ListTag();
        List<String> texts = new ArrayList<>();
        for (SorcererGrimoire.Page page : SorcererGrimoire.get().pages()) {
            if (known.containsAll(page.words().stream().filter(Lexicons.get()::isRune).toList())) {
                pages.add(StringTag.valueOf(page.text()));
                names.add(StringTag.valueOf(page.name()));
                texts.add(page.text());
            }
        }
        for (Option page : written) {
            pages.add(StringTag.valueOf(page.text()));
            names.add(StringTag.valueOf(page.name()));
            texts.add(page.text());
        }
        Map<Intent, Integer> counts = new java.util.EnumMap<>(Intent.class);
        composed.forEach((text, intent) -> {
            int count = counts.merge(intent, 1, Integer::sum);
            pages.add(StringTag.valueOf(text));
            names.add(StringTag.valueOf(intentName(intent) + " " + count));
            texts.add(text);
        });
        int copies = 0;
        for (String text : copied.keySet()) {
            copies++;
            pages.add(StringTag.valueOf(text));
            names.add(StringTag.valueOf("copiada " + copies));
            texts.add(text);
        }
        int read = 0;
        for (String text : studied.keySet()) {
            read++;
            pages.add(StringTag.valueOf(text));
            names.add(StringTag.valueOf("lida " + read));
            texts.add(text);
        }
        tag.put("pages", pages);
        tag.put(GrimoireItem.PAGE_NAMES_TAG, names);
        tag.putUUID(GrimoireItem.AUTHOR_ID_TAG, owner.getUUID());
        tag.putString(GrimoireItem.AUTHOR_TAG, owner.getName().getString());
        ListTag glossary = new ListTag();
        ParserDictionary dictionary = ParserDictionary.load();
        Glossary.grow(List.of(), texts, id -> dictionary.lookup(id).isPresent())
                .forEach(id -> glossary.add(StringTag.valueOf(id)));
        tag.put(GrimoireItem.GLOSSARY_TAG, glossary);
        return book;
    }

    /** Whether a grimoire made of it would hold no page at all. */
    public static boolean empty(ItemStack grimoire) {
        return grimoire.getOrCreateTag().getList("pages", Tag.TAG_STRING).isEmpty();
    }

    public static String intentName(Intent intent) {
        return switch (intent) {
            case STRIKE -> "ferir";
            case DRAW -> "tirar";
            case VEIL -> "sumir";
            case CALL -> "chamar";
            case HEAL -> "curar";
            case WARD -> "proteger";
            case FLEE -> "fugir";
            case DOUSE -> "apagar o fogo";
        };
    }

    /** A line about who it is, for the one who asks (/runas mente). */
    public String describe() {
        ensureMind();
        String last = lastChoice == null ? "nada ainda" : "\"" + lastChoice.option().name() + "\" para "
                + intentName(lastChoice.intent()) + String.format(Locale.ROOT, " (nota %.2f)", lastChoice.score());
        String pages = written.isEmpty() ? (preparation != null ? "escrevendo" : "nenhuma página escrita")
                : written.size() + " páginas escritas (" + String.join(", ", written.stream().map(Option::name)
                .toList()) + ")";
        return degree.id() + ", " + personality.id() + ", conhece " + known + "; " + pages + "; " + options().size()
                + " opções, " + learned.size() + " aprendidas (" + copied.size() + " copiadas, " + studied.size()
                + " lidas nas estantes)" + (working != null ? ", trabalhando: " + working.name().toLowerCase(Locale.ROOT)
                : "") + "; entendendo "
                + understanding + "; Vis " + String.format(Locale.ROOT, "%.1f", Casters.vis(owner))
                + "; alcance " + String.format(Locale.ROOT, "%.0f", reach()) + " blocos"
                + (conversation != null ? "; conversando" : "") + (quarrelWith != null ? "; brigando" : "")
                + (offended > 0 ? "; ofendido" : "")
                + (annoyance.isEmpty() ? "" : "; irritação " + annoyance.values().stream()
                .map(value -> String.format(Locale.ROOT, "%.1f", value)).toList())
                + "; leu por último: " + last;
    }

    // ------------------------------------------------------------------ its goals

    /**
     * Out of a fight, with nothing to hunt, it writes (docs/feiticeiro-design.md, 2.1): it stands with its grimoire, the
     * light of the language drawn to it, and works a little every few ticks until its pages are written. A target
     * breaks it off; it goes on where it stopped when it is alone again.
     */
    public Goal writeGoal() {
        return new WriteGoal();
    }

    /**
     * Reads when there is a target in sight, the rest is over and its mind finds something worth reading: it stands
     * still and looks at the target for the two seconds of the trance. Losing the target ends the trance with nothing
     * cast.
     */
    public Goal tranceGoal() {
        return new TranceGoal();
    }

    /**
     * Walks up to its target when what it can read would not get to it, or it cannot see it (docs/feiticeiro-design.md,
     * 3.6): a mage stands where its spells reach, and no nearer than it must.
     */
    public Goal approachGoal(double speed) {
        return new ApproachGoal(speed);
    }

    /**
     * Keeps its foe off (docs/feiticeiro-design.md, 3.9): between readings, it backs away from a foe nearer than it wants
     * it ({@link #wantedDistance}), no nearer than {@code floor} in any case. When it cannot get farther, it is cornered,
     * and reads where it stands.
     */
    public Goal escapeFireGoal(double speed) {
        return new EscapeFireGoal(speed);
    }

    /** Whether it stands in fire: flames where its feet or its head are, or lava. */
    public boolean standingInFire() {
        if (owner.isInLava()) {
            return true;
        }
        net.minecraft.core.BlockPos feet = owner.blockPosition();
        return owner.level().getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE)
                || owner.level().getBlockState(feet.above()).is(net.minecraft.tags.BlockTags.FIRE);
    }

    /**
     * Where the nearest ground out of the fire is, within {@code reach}: room to stand (as {@link #ground()} sees it,
     * which keeps out of flames) and no flame beside it; of those, the one with the fewest flames on the way there (back
     * the way it came, not across a wall of fire), and then the nearest.
     */
    @Nullable
    private Vec3 outOfTheFire(int reach) {
        Room.Ground ground = ground();
        Vec3 best = null;
        int fewest = Integer.MAX_VALUE;
        for (int distance = 1; distance <= reach; distance++) {
            for (int step = 0; step < 16; step++) {
                double angle = Math.PI * 2.0D * step / 16.0D;
                double x = owner.getX() + Math.cos(angle) * distance;
                double z = owner.getZ() + Math.sin(angle) * distance;
                if (!ground.open(x, z) || flamesBeside(x, z)) {
                    continue;
                }
                int flames = flamesOnTheWay(x, z);
                if (flames < fewest) {
                    fewest = flames;
                    best = new Vec3(x, owner.getY(), z);
                }
            }
            if (fewest == 0) {
                break; // none nearer will do better
            }
        }
        return best;
    }

    /** How many blocks of flame stand on the straight way to ({@code x}, {@code z}), past the one it stands in. */
    private int flamesOnTheWay(double x, double z) {
        double dx = x - owner.getX();
        double dz = z - owner.getZ();
        double length = Math.hypot(dx, dz);
        Set<net.minecraft.core.BlockPos> burning = new HashSet<>();
        net.minecraft.core.BlockPos here = owner.blockPosition();
        for (double along = 0.5D; along < length; along += 0.5D) {
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(owner.getX() + dx / length * along,
                    owner.getY(), owner.getZ() + dz / length * along);
            if (!at.equals(here) && (owner.level().getBlockState(at).is(net.minecraft.tags.BlockTags.FIRE)
                    || owner.level().getBlockState(at.above()).is(net.minecraft.tags.BlockTags.FIRE))) {
                burning.add(at.immutable());
            }
        }
        return burning.size();
    }

    private boolean flamesBeside(double x, double z) {
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(x, owner.getY(), z);
        for (net.minecraft.core.BlockPos near : net.minecraft.core.BlockPos.betweenClosed(at.offset(-1, 0, -1),
                at.offset(1, 1, 1))) {
            if (owner.level().getBlockState(near).is(net.minecraft.tags.BlockTags.FIRE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets out of the fire it stands in, before anything else (docs/feiticeiro-design.md, 3.18): to the nearest ground
     * with no flame beside it.
     */
    private final class EscapeFireGoal extends Goal {

        private static final int REACH = 6;
        private final double speed;
        private int repath;
        private int ticks;
        @Nullable
        private Vec3 out;

        EscapeFireGoal(double speed) {
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return reading == null && standingInFire();
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < 60 && (standingInFire() || out != null && owner.distanceToSqr(out) > 0.25D);
        }

        @Override
        public void start() {
            repath = 0;
            ticks = 0;
        }

        @Override
        public void tick() {
            ticks++;
            if (--repath <= 0) {
                repath = 10;
                out = outOfTheFire(REACH);
            }
            // Straight there: a way is never found through fire, and it stands in it.
            if (out != null) {
                owner.getNavigation().stop();
                owner.getMoveControl().setWantedPosition(out.x, out.y, out.z, speed);
            }
        }
    }

    public Goal keepDistanceGoal(double speed, double floor) {
        return new KeepDistanceGoal(speed, floor);
    }

    /** Whether it is cornered now: it could not get away from its foe, and reads where it stands. */
    public boolean cornered() {
        return cornered > 0;
    }

    /** Stands with the one it talks with, looking at it (docs/mago-design.md, 5b). */
    public Goal talkGoal() {
        return new TalkGoal();
    }

    private final class ApproachGoal extends Goal {

        /** How far a leg of the way goes, when the way to the target is too long to find at once. */
        private static final double LEG = 12.0D;

        private final double speed;
        private int repath;

        ApproachGoal(double speed) {
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return wantsCloser(1.0D);
        }

        @Override
        public boolean canContinueToUse() {
            return wantsCloser(Math.min(6.0D, reach() * 0.25D));
        }

        /** Whether its target stands farther than {@code within} short of its reach, or out of its sight. */
        private boolean wantsCloser(double within) {
            LivingEntity target = owner.getTarget();
            if (target == null || !target.isAlive() || reading != null || releaseTicks > 0) {
                return false;
            }
            if (stance != null) {
                return stance == Doctrine.Stance.CLOSE_IN || !owner.hasLineOfSight(target);
            }
            double reach = reach();
            return reach > 0.0D && (owner.distanceTo(target) > reach - within || !owner.hasLineOfSight(target));
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            owner.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = owner.getTarget();
            if (target == null) {
                return;
            }
            owner.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath <= 0) {
                repath = 10;
                // A leg at a time: the way is not found to what stands beyond the distance it can follow.
                Vec3 way = target.position().subtract(owner.position());
                if (way.length() <= LEG) {
                    owner.getNavigation().moveTo(target, speed);
                } else {
                    Vec3 leg = owner.position().add(way.normalize().scale(LEG));
                    owner.getNavigation().moveTo(leg.x, leg.y, leg.z, speed);
                }
            }
        }
    }

    private final class KeepDistanceGoal extends Goal {

        /** How long it backs away without getting farther before it knows it is cornered, and how long it then stays. */
        private static final int STUCK_TICKS = 30;
        private static final int CORNERED_TICKS = 60;
        /** How far past what it wants it goes before it stops: so it does not stop and start at the edge. */
        private static final double PAST = 1.5D;

        private final double speed;
        private final double floor;
        private int repath;
        private int stuck;
        private double farthest;
        /** Which way it walks around the foe, when it does: 1 or -1. */
        private int side = 1;

        KeepDistanceGoal(double speed, double floor) {
            this.speed = speed;
            this.floor = floor;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return tooNear(0.0D);
        }

        @Override
        public boolean canContinueToUse() {
            return tooNear(PAST);
        }

        private boolean tooNear(double past) {
            LivingEntity target = owner.getTarget();
            if (target == null || !target.isAlive() || reading != null || releaseTicks > 0
                    || !(owner instanceof net.minecraft.world.entity.PathfinderMob)) {
                return false;
            }
            if (stance != null) {
                return stance == Doctrine.Stance.BACK_OFF || stance == Doctrine.Stance.CIRCLE;
            }
            return cornered == 0 && owner.distanceTo(target) < wantedDistance(target, floor) + past;
        }

        /** Its doctrine says to walk around the foe: aside of it, a few steps at a time, keeping to one side. */
        private void circle(LivingEntity target) {
            if (--repath > 0) {
                return;
            }
            repath = 5;
            Room.Way way = Room.aside(ground(), owner.getX(), owner.getZ(), target.getX(), target.getZ(), side);
            if (way.room() < Room.CORNERED) {
                return;
            }
            // The side it went is the side it keeps, until there is no room there.
            Vec3 toward = owner.position().subtract(target.position());
            side = way.dz() * toward.x - way.dx() * toward.z >= 0.0D ? 1 : -1;
            double far = Math.min(way.room(), 3.0D);
            owner.getNavigation().moveTo(owner.getX() + way.dx() * far, owner.getY(), owner.getZ() + way.dz() * far,
                    speed);
        }

        @Override
        public void start() {
            repath = 0;
            stuck = 0;
            LivingEntity target = owner.getTarget();
            farthest = target == null ? 0.0D : owner.distanceTo(target);
        }

        @Override
        public void stop() {
            owner.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = owner.getTarget();
            if (target == null) {
                return;
            }
            owner.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (stance == Doctrine.Stance.CIRCLE) {
                circle(target);
                return;
            }
            double distance = owner.distanceTo(target);
            if (distance > farthest + 0.5D) {
                farthest = distance;
                stuck = 0;
            } else if (++stuck >= STUCK_TICKS) {
                cornered = CORNERED_TICKS;
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                // The way with the most room, not the corner straight behind it; none: it is cornered.
                Room.Way way = roomAgainst(target);
                if (way.room() < Room.CORNERED) {
                    cornered = CORNERED_TICKS;
                    return;
                }
                double far = Math.min(way.room(), 8.0D);
                owner.getNavigation().moveTo(owner.getX() + way.dx() * far, owner.getY(), owner.getZ() + way.dz() * far,
                        speed);
            }
        }
    }

    private final class TalkGoal extends Goal {

        TalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return conversation != null && !conversation.over();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            owner.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (conversation == null) {
                return;
            }
            Mob other = conversation.other(CasterMind.this).owner();
            owner.getLookControl().setLookAt(other, 30.0F, 30.0F);
            if (owner.distanceTo(other) > Conversation.CLOSE + 0.5D) {
                if (owner.getNavigation().isDone()) {
                    owner.getNavigation().moveTo(other, 0.5D);
                }
            } else {
                owner.getNavigation().stop();
            }
        }
    }

    /**
     * At peace, with its pages written, it goes now and then to a block of the craft near it and works there a while
     * (docs/mago-design.md, 5c): it reads at the shelves, gathers Vis at the enchanting table, tends the brewing stand
     * while it heals, engraves at the galdraria table.
     */
    public Goal studyGoal() {
        return new StudyGoal();
    }

    /** The block of the craft it would go to now, and where it is: what it needs most, among those near. */
    private Optional<Map.Entry<Station, net.minecraft.core.BlockPos>> pickStation() {
        Map<Station, net.minecraft.core.BlockPos> nearest = new java.util.EnumMap<>(Station.class);
        net.minecraft.core.BlockPos center = owner.blockPosition();
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                center.offset(-STATION_REACH, -3, -STATION_REACH), center.offset(STATION_REACH, 3, STATION_REACH))) {
            net.minecraft.world.level.block.state.BlockState state = owner.level().getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            Station.of(state).ifPresent(station -> nearest.merge(station, pos.immutable(),
                    (old, fresh) -> fresh.distSqr(center) < old.distSqr(center) ? fresh : old));
        }
        if (nearest.isEmpty()) {
            return Optional.empty();
        }
        float share = owner.getHealth() / Math.max(1.0F, owner.getMaxHealth());
        Map<Station, Double> weights = new java.util.EnumMap<>(Station.class);
        for (Station station : nearest.keySet()) {
            double weight = switch (station) {
                case SHELF -> 2.0D;
                case ALTAR -> Casters.vis(owner) < 0.7D * Casters.ANCHOR_UMU ? 3.0D : 0.5D;
                case BREW -> share < 0.9F ? 4.0D : 0.3D;
                case BENCH -> body.wantsEngraving() && engravable().isPresent() ? 2.0D : 0.0D;
            };
            if (weight > 0.0D) {
                weights.put(station, weight);
            }
        }
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double roll = owner.getRandom().nextDouble() * total;
        for (Map.Entry<Station, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll <= 0.0D) {
                return Optional.of(Map.entry(entry.getKey(), nearest.get(entry.getKey())));
            }
        }
        return Optional.empty();
    }

    /**
     * Its home (its tower, docs/mago-design.md, 5e): at peace it does not wander farther than {@code radius} blocks from
     * it. A foe it follows anywhere.
     */
    public void home(net.minecraft.core.BlockPos pos, int radius) {
        home = pos.immutable();
        homeRadius = radius;
        if (owner instanceof net.minecraft.world.entity.PathfinderMob walker) {
            walker.restrictTo(home, radius);
        }
    }

    /** Its home, if it has one. */
    public Optional<net.minecraft.core.BlockPos> home() {
        return Optional.ofNullable(home);
    }

    /** The sentences it read in the books of the shelves, newest last. */
    public List<String> studiedSentences() {
        return List.copyOf(studied.keySet());
    }

    /** It goes to a block of the craft at its next chance (for a test or a command). */
    public void studySoon() {
        studyRest = 0;
    }

    /** It read in the books of the shelves: {@code count} sentences it can read and did not have, if there are any. */
    int study(int count) {
        Optional<PhraseCatalog> catalog = PhraseCatalog.ready();
        if (catalog.isEmpty()) {
            return 0;
        }
        Set<String> had = new HashSet<>(composed.keySet());
        had.addAll(copied.keySet());
        had.addAll(studied.keySet());
        written.forEach(page -> had.add(page.name()));
        learned.forEach(option -> had.add(option.name()));
        List<Option> fresh = new ArrayList<>(Mind.worthWeighing(catalog.get().readableBy(known()).stream()
                .map(Option::of).filter(option -> !had.contains(option.name())).toList()));
        int read = 0;
        while (read < count && !fresh.isEmpty()) {
            Option option = fresh.remove(owner.getRandom().nextInt(fresh.size()));
            studied.put(option.name(), intentOf(option));
            learned.add(option);
            read++;
            while (studied.size() > KEPT_STUDIED) {
                String oldest = studied.keySet().iterator().next();
                studied.remove(oldest);
                learned.removeIf(kept -> kept.name().equals(oldest));
            }
        }
        return read;
    }

    /** What an option serves first, the way it serves it. */
    private static Intent intentOf(Option option) {
        for (com.elderlexicon.mod.mind.Act act : option.acts()) {
            for (Aim aim : List.of(Aim.TARGET, Aim.SKY, Aim.AWAY, Aim.FEET)) {
                Intent intent = Mind.serves(act, aim);
                if (intent != null) {
                    return intent;
                }
            }
        }
        return Intent.STRIKE;
    }

    /** A page of its own that fits a wand (one line, at most ten columns, nothing that waits for a happening). */
    private Optional<Option> engravable() {
        return written.stream()
                .filter(page -> page.lines().size() == 1)
                .filter(page -> page.words().size() <= com.elderlexicon.mod.galdraria.Engravings.MAX_COLUMNS)
                .filter(page -> page.words().stream().noneMatch(CasterMind::condition))
                .findFirst();
    }

    /** It engraves a page of its own into an improvised wand. */
    private void engrave() {
        engravable().ifPresent(page -> {
            ItemStack wand = new ItemStack(ElderLexicon.IMPROVISED_WAND.get());
            com.elderlexicon.mod.galdraria.Engravings.engrave(wand,
                    com.elderlexicon.mod.galdraria.Engravings.written(page.words()));
            wand.setHoverName(net.minecraft.network.chat.Component.literal(page.name()));
            body.engraved(wand);
        });
    }

    private final class StudyGoal extends Goal {

        @Nullable
        private Station station;
        @Nullable
        private net.minecraft.core.BlockPos at;
        private int walked;
        private int worked;
        private boolean there;
        private boolean done;
        private ItemStack held = ItemStack.EMPTY;
        private boolean swapped;

        StudyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (studyRest > 0 || !calm() || conversation != null || written.isEmpty() || preparation != null
                    || !body.mayWrite()) {
                return false;
            }
            Optional<Map.Entry<Station, net.minecraft.core.BlockPos>> picked = pickStation();
            if (picked.isEmpty()) {
                studyRest = STUDY_REST_TICKS / 2;
                return false;
            }
            station = picked.get().getKey();
            at = picked.get().getValue();
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !done && station != null && calm() && conversation == null && walked < WALK_TICKS
                    && Station.of(owner.level().getBlockState(at)).filter(station::equals).isPresent();
        }

        @Override
        public void start() {
            walked = 0;
            worked = 0;
            there = false;
            done = false;
            working = station;
            walk();
        }

        private void walk() {
            owner.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.5D);
        }

        @Override
        public void stop() {
            owner.getNavigation().stop();
            body.reading(false);
            if (swapped) {
                owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held);
            }
            swapped = false;
            held = ItemStack.EMPTY;
            working = null;
            studyRest = STUDY_REST_TICKS + owner.getRandom().nextInt(STUDY_REST_TICKS);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 center = Vec3.atCenterOf(at);
            owner.getLookControl().setLookAt(center.x, center.y, center.z);
            if (!there) {
                walked++;
                if (owner.position().distanceToSqr(center.x, owner.getY(), center.z) <= 2.6D * 2.6D) {
                    there = true;
                    owner.getNavigation().stop();
                    begin();
                } else if (owner.getNavigation().isDone() && walked % 20 == 0) {
                    walk();
                }
                return;
            }
            worked++;
            work();
            if (worked >= station.ticks) {
                finish();
                done = true;
            }
        }

        /** It is there: it shows what it does. */
        private void begin() {
            held = owner.getMainHandItem().copy();
            swapped = station == Station.BREW || station == Station.BENCH;
            switch (station) {
                case SHELF, ALTAR -> body.reading(true);
                case BREW -> owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
                case BENCH -> owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new ItemStack(com.elderlexicon.mod.galdraria.Galdraria.BURIN.get()));
            }
            if (station == Station.ALTAR) {
                owner.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.0F);
            }
        }

        /** A tick of the work: its sounds, now and then; Vis gathered at the table, a little health at the stand. */
        private void work() {
            float pitch = 0.9F + owner.getRandom().nextFloat() * 0.2F;
            switch (station) {
                case SHELF -> {
                    if (worked % 40 == 0) {
                        owner.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, pitch);
                    }
                }
                case ALTAR -> {
                    int shelves = Station.shelvesAround(owner.level(), at);
                    Casters.giveVis(owner, 2.0D * (1.0D + shelves / 5.0D) / 20.0D);
                    if (worked % 60 == 0) {
                        owner.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, pitch);
                    }
                }
                case BREW -> {
                    if (worked % 40 == 0) {
                        owner.playSound(SoundEvents.BREWING_STAND_BREW, 0.6F, pitch);
                        if (owner.getHealth() < owner.getMaxHealth()) {
                            owner.heal(1.0F);
                        }
                        if (owner.level() instanceof ServerLevel server) {
                            server.sendParticles(ParticleTypes.EFFECT, at.getX() + 0.5D, at.getY() + 0.8D,
                                    at.getZ() + 0.5D, 3, 0.15D, 0.1D, 0.15D, 0.0D);
                        }
                    }
                }
                case BENCH -> {
                    if (worked % 20 == 0) {
                        owner.playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.6F, pitch);
                    }
                }
            }
        }

        /** The work is done: what came of it. */
        private void finish() {
            switch (station) {
                case SHELF -> {
                    int shelves = Station.shelvesAround(owner.level(), at);
                    if (study(1 + shelves / 5) > 0) {
                        owner.playSound(SoundEvents.BOOK_PUT, 0.8F, 1.0F);
                    }
                }
                case ALTAR -> owner.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.2F);
                case BREW -> owner.playSound(SoundEvents.BREWING_STAND_BREW, 0.8F, 1.2F);
                case BENCH -> engrave();
            }
        }
    }

    private final class WriteGoal extends Goal {

        /** Ticks between two steps of the work. */
        private static final int STEP_TICKS = 5;
        private int ticks;

        WriteGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return written.isEmpty() && owner.getTarget() == null && reading == null && conversation == null
                    && body.mayWrite() && PhraseCatalog.ready().isPresent();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            owner.getNavigation().stop();
            ensureMind();
            if (preparation == null) {
                preparation = new Preparation(PhraseCatalog.ready().orElseThrow(), known, personality);
            }
            body.reading(true); // its grimoire open in its hands
        }

        @Override
        public void stop() {
            body.reading(false);
        }

        @Override
        public void tick() {
            ticks++;
            if (ticks % 40 == 0) {
                owner.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.9F + owner.getRandom().nextFloat() * 0.2F);
            }
            if (ticks % STEP_TICKS != 0 || preparation == null) {
                return;
            }
            preparation.step();
            if (preparation.done()) {
                written = List.copyOf(preparation.pages());
                preparation = null;
                options = null;
                owner.playSound(SoundEvents.BOOK_PUT, 1.0F, 1.0F);
            }
        }
    }

    private final class TranceGoal extends Goal {

        @Nullable
        private Mind.Choice chosen;

        TranceGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = owner.getTarget();
            if (reading != null || releaseTicks > 0 || restTicks > 0) {
                return false;
            }
            if (standingInFire()) {
                return false; // out of the fire first (3.18)
            }
            if (target == null || !target.isAlive()) {
                // With no foe: hurt, or burning, it reads for itself (docs/feiticeiro-design.md, 3.8).
                float share = owner.getHealth() / Math.max(1.0F, owner.getMaxHealth());
                if (conversation != null || share >= 0.9F && !owner.isOnFire()) {
                    return false;
                }
                chosen = choose(null).orElse(null);
                if (chosen == null) {
                    restTicks = BROKEN_REST_TICKS * 2;
                }
                return chosen != null;
            }
            if (!owner.hasLineOfSight(target)) {
                return false;
            }
            if (stance != null) {
                // Its doctrine says when to read, and what for.
                if (stanceChoice == null) {
                    return false;
                }
                chosen = stanceChoice;
                stanceChoice = null;
                return true;
            }
            // One who broke its trance before is not let near it again, and one who sees a foe coming does not open its
            // trance with it at hand: it opens it only from afar, unless it could not get away, or is desperate.
            float share = owner.getHealth() / Math.max(1.0F, owner.getMaxHealth());
            if (!safeToOpen(target) && cornered == 0 && !personality().desperate(share)) {
                restTicks = 10;
                return false;
            }
            chosen = choose(target).orElse(null);
            if (chosen == null) {
                restTicks = BROKEN_REST_TICKS; // nothing worth it now: look again in a moment
            }
            return chosen != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = owner.getTarget();
            return reading != null && (reading.aim() != Aim.TARGET || target != null && target.isAlive());
        }

        @Override
        public void start() {
            owner.getNavigation().stop();
            if (chosen != null) {
                beginTrance(chosen);
                chosen = null;
            }
        }

        @Override
        public void stop() {
            if (reading != null) {
                endTrance(false);
                restTicks = BROKEN_REST_TICKS;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = owner.getTarget();
            if (reading == null || target == null && reading.aim() == Aim.TARGET) {
                return;
            }
            if (target != null) {
                owner.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            holdTrance(target);
        }
    }
}

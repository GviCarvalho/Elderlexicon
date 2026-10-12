package com.elderlexicon.mod.illager;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Memory;
import com.elderlexicon.mod.mind.Variations;
import com.elderlexicon.mod.spell.SpellWitnessedEvent;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.damage.VitaDamageHandler;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.raid.Raider;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.mind.PhraseCatalog;
import com.elderlexicon.mod.mind.Preparation;
import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.Casters;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.block.Glossary;
import com.elderlexicon.mod.spell.block.SpellBlock;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.RecitationPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The evoker that knows the Old Tongue (docs/feiticeiro-design.md): it reads the language as a mage does (book, cap. 5):
 * it stops, opens the trance, and the words it reads are seen above its head, rune by rune, for the two seconds the
 * trance lasts; then the spirit reads them.
 * <p>
 * It is born knowing some runes and not others ({@link Degree}), with a way of weighing what it wants
 * ({@link Personality}). It carries a grimoire written by hand ({@link SorcererGrimoire}), and it composes: out of every
 * sentence the mind knows ({@link PhraseCatalog}), the ones it can read whole. When it fights, its mind ({@link Mind})
 * weighs what the scene asks (to hurt, to draw out of a body, to vanish) against what each option costs and risks, and
 * it reads among the best.
 * <p>
 * It is an evoker like any other (raids, allies, its look), but the fangs, the vexes and the wololo are gone: all it
 * does is said in the language, and paid as any living being pays (book 3.2): from its Anchor of the Hundred, and, when
 * that is spent and it is desperate enough, from its flesh.
 * <p>
 * The trance is a tell. A blow while it lasts breaks it ("a fresta se fechará", cap. 6): nothing is cast.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public class Sorcerer extends Evoker {

    /** The trance: two seconds, as the book says (cap. 5). */
    public static final int TRANCE_TICKS = 40;
    /** How long it waits, at least, once the last line of what it read is out, before it opens another trance. */
    private static final int REST_TICKS = 20;
    /** How long it waits after its trance was broken, or when nothing was worth reading. */
    private static final int BROKEN_REST_TICKS = 30;
    /** At most this many words are seen above its head (what a recitation holds). */
    private static final int SHOWN_WORDS = 16;
    /** The chance it leaves its grimoire when a player kills it, and how much each level of looting adds. */
    private static final float GRIMOIRE_CHANCE = 0.25F;
    private static final float GRIMOIRE_CHANCE_PER_LOOTING = 0.05F;
    /** How many of the sentences it composed it keeps in its grimoire. */
    private static final int KEPT_COMPOSED = 8;
    private static final String MIND_TAG = "SorcererMind";
    /** How long after the last line of a reading it looks at what came of it. */
    private static final int OUTCOME_TICKS = 30;
    /** How many variations it tries of something that worked, and how many sentences it copies, at most. */
    private static final int VARIATIONS = 2;
    private static final int KEPT_COPIED = 4;
    /** How far it sees a mage cast, to write the sentence down. */
    private static final double WITNESS_REACH = 16.0D;
    /** How many experiences of a rune it takes to understand it (docs/aprendizado-runas-design.md): then it dreams it. */
    private static final int TO_UNDERSTAND = 3;
    /** How often, in calm, it looks whether it understood something enough to dream it. */
    private static final int DREAM_TICKS = 60;
    /** How often it looks at its band, to mark the ones it has not marked yet. */
    private static final int BAND_TICKS = 100;
    /** How far its band reaches. */
    private static final double BAND_REACH = 24.0D;
    /** An ally nearer the target than this is there already: not worth bringing. */
    private static final double ALREADY_THERE = 6.0D;
    /** The syllables of the marks it gives (the sounds of no tongue of this world but its own). */
    private static final String[] SYLLABLES = {"ka", "dor", "vil", "an", "tor", "lig", "nah", "ven", "sar", "ul", "mek",
            "os", "ra", "thi", "gor", "em", "zu", "hal"};
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SpellCastingService SPIRIT = new SpellCastingService();

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
    private int releaseTicks;
    @Nullable
    private Mind.Choice lastChoice;
    /** What it remembers of the ones it fights, for this life (docs/feiticeiro-design.md, 3.5). */
    private final Memory memory = new Memory();
    /** What it learned to read in this life: variations of what worked, and sentences it saw others cast. */
    private final List<Option> learned = new ArrayList<>();
    /** The options it already varied: one is varied once. */
    private final Set<String> varied = new java.util.HashSet<>();
    /** The sentences it copied from others, newest last: what its grimoire keeps as "copiada". */
    private final Map<String, Intent> copied = new LinkedHashMap<>();
    /** How far it understands each rune it does not know yet (docs/aprendizado-runas-design.md). */
    private final Map<String, Integer> understanding = new LinkedHashMap<>();
    /** The marks it gave its band, and to whom (each member's mark is on the member itself). */
    private final Map<UUID, String> band = new LinkedHashMap<>();
    /** A reading whose outcome it is waiting to see. */
    @Nullable
    private Outcome awaiting;

    /** A reading against a target: the health the target had, and when to look at what came of it. */
    private record Outcome(Mind.Choice choice, LivingEntity target, float health, long at) {
    }

    public Sorcerer(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // The fangs, the vexes and the wololo are the vanilla evoker's: this one says what it does.
        goalSelector.removeAllGoals(wrapped -> wrapped.getClass().getEnclosingClass() == Evoker.class);
        goalSelector.addGoal(1, new TranceGoal());
        goalSelector.addGoal(5, new WriteGoal());
    }

    // ------------------------------------------------------------------ who it is

    /** Its degree, personality and runes, drawn the first time they are asked for (a creature is born with them). */
    private void ensureMind() {
        if (degree == null) {
            degree = Degree.draw(getRandom());
            personality = Personality.draw(getRandom());
            known = Set.copyOf(degree.draw(Lexicons.get(), getRandom()));
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ensureMind();
        CompoundTag mind = new CompoundTag();
        mind.putString("Degree", degree.id());
        mind.putString("Personality", personality.id());
        ListTag runes = new ListTag();
        known.forEach(rune -> runes.add(StringTag.valueOf(rune)));
        mind.put("Known", runes);
        ListTag written = new ListTag();
        composed.forEach((text, intent) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Text", text);
            entry.putString("Intent", intent.name());
            written.add(entry);
        });
        mind.put("Composed", written);
        ListTag pages = new ListTag();
        this.written.forEach(page -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", page.name());
            entry.putString("Text", page.text());
            pages.add(entry);
        });
        mind.put("Written", pages);
        ListTag copies = new ListTag();
        copied.forEach((text, intent) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Text", text);
            entry.putString("Intent", intent.name());
            copies.add(entry);
        });
        mind.put("Copied", copies);
        CompoundTag understood = new CompoundTag();
        understanding.forEach(understood::putInt);
        mind.put("Understanding", understood);
        ListTag members = new ListTag();
        band.forEach((member, mark) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Member", member);
            entry.putString("Mark", mark);
            members.add(entry);
        });
        mind.put("Band", members);
        tag.put(MIND_TAG, mind);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains(MIND_TAG)) {
            return;
        }
        CompoundTag mind = tag.getCompound(MIND_TAG);
        degree = Degree.byId(mind.getString("Degree"));
        personality = Personality.byId(mind.getString("Personality"));
        Set<String> runes = new LinkedHashSet<>();
        ListTag stored = mind.getList("Known", Tag.TAG_STRING);
        for (int i = 0; i < stored.size(); i++) {
            runes.add(stored.getString(i));
        }
        known = Set.copyOf(runes);
        composed.clear();
        ListTag written = mind.getList("Composed", Tag.TAG_COMPOUND);
        for (int i = 0; i < written.size(); i++) {
            CompoundTag entry = written.getCompound(i);
            try {
                composed.put(entry.getString("Text"), Intent.valueOf(entry.getString("Intent")));
            } catch (IllegalArgumentException ignored) {
                // an intent the mind no longer has
            }
        }
        List<Option> pages = new ArrayList<>();
        ListTag writtenTag = mind.getList("Written", Tag.TAG_COMPOUND);
        for (int i = 0; i < writtenTag.size(); i++) {
            CompoundTag entry = writtenTag.getCompound(i);
            page(entry.getString("Name"), entry.getString("Text")).ifPresent(pages::add);
        }
        this.written = List.copyOf(pages);
        copied.clear();
        learned.clear();
        understanding.clear();
        CompoundTag understood = mind.getCompound("Understanding");
        understood.getAllKeys().forEach(rune -> understanding.put(rune, understood.getInt(rune)));
        band.clear();
        ListTag members = mind.getList("Band", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag entry = members.getCompound(i);
            if (entry.hasUUID("Member")) {
                band.put(entry.getUUID("Member"), entry.getString("Mark"));
            }
        }
        ListTag copies = mind.getList("Copied", Tag.TAG_COMPOUND);
        for (int i = 0; i < copies.size(); i++) {
            CompoundTag entry = copies.getCompound(i);
            String text = entry.getString("Text");
            try {
                copied.put(text, Intent.valueOf(entry.getString("Intent")));
                PhraseCatalog.imagine(List.of(text.split(" "))).map(Option::of).ifPresent(learned::add);
            } catch (IllegalArgumentException ignored) {
                // an intent the mind no longer has
            }
        }
        options = null;
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

    /**
     * What it can read, worth weighing: the pages written by hand it can read whole, and the pages it wrote itself.
     * Before it has written any (a fight found it first), every sentence of the catalog it can read: it composes as it
     * fights, slower. Until the catalog is made (a few seconds after the server starts) it has only the pages.
     */
    private List<Option> options() {
        ensureMind();
        if (!written.isEmpty()) {
            if (options == null || optionsFromCatalog) {
                List<Option> all = new ArrayList<>(pages());
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
        List<Option> all = new ArrayList<>(pages());
        catalog.ifPresent(found -> found.readableBy(known).forEach(phrase -> all.add(Option.of(phrase))));
        options = Mind.worthWeighing(all);
        optionsFromCatalog = catalog.isPresent();
        return options;
    }

    /** The pages of its grimoire it can read whole, as options. */
    private List<Option> pages() {
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

    // ------------------------------------------------------------------ the trance

    /** Whether it is in trance now, reading. */
    public boolean inTrance() {
        return reading != null;
    }

    /** Whether it is still releasing the lines of what it read. */
    public boolean releasing() {
        return releaseTicks > 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide()) {
            // To be burned, to fall, to drown, to bleed: it lives the energy that struck it (docs/aprendizado-runas-design.md).
            lived(source).flatMap(Sorcerer::sourceOf).ifPresent(this::experience);
        }
        if (hurt && reading != null && !level().isClientSide()) {
            // The fear closes the crack before the order is given (book, cap. 6). It remembers who did it.
            if (source.getEntity() != null) {
                memory.broken(source.getEntity().getUUID());
            }
            endTrance(false);
            restTicks = BROKEN_REST_TICKS;
            playSound(SoundEvents.EVOKER_PREPARE_WOLOLO, 0.6F, 0.6F);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (releaseTicks > 0) {
            // The lines come out one step after another, each where it looks then: it keeps the aim.
            releaseTicks--;
            if (releasingAt != null && releasingAt.isAlive()) {
                aimAt(releasingAt);
            }
            if (releaseTicks == 0) {
                releasingAt = null;
            }
        } else if (reading == null && restTicks > 0) {
            restTicks--;
        }
        boolean calm = getTarget() == null && reading == null && releaseTicks == 0;
        if (calm && tickCount % DREAM_TICKS == 0) {
            dream();
        }
        if (tickCount % BAND_TICKS == 0) {
            markBand();
        }
        if (awaiting != null && level().getGameTime() >= awaiting.at()) {
            remember(awaiting);
            awaiting = null;
        }
    }

    /**
     * What came of a reading, next to what it foresaw: it remembers it of that target, and varies what did better than
     * foreseen (docs/feiticeiro-design.md, 3.5).
     */
    private void remember(Outcome outcome) {
        LivingEntity target = outcome.target();
        float now = target.isAlive() ? target.getHealth() : 0.0F;
        double done = Math.max(0.0D, outcome.health() - now);
        Option option = outcome.choice().option();
        if (outcome.choice().harm() <= 0.0D) {
            return; // it did not mean to hurt (it vanished): nothing to compare
        }
        memory.outcome(target.getUUID(), option, outcome.choice().harm(), done);
        if (memory.worked(target.getUUID(), option) && varied.add(option.name())) {
            PhraseCatalog.ready().ifPresent(catalog -> {
                List<Option> variations = Variations.of(option, catalog.readableBy(known()), getRandom(), VARIATIONS);
                learned.addAll(Mind.worthWeighing(variations));
            });
        }
    }

    /** What it learned against {@code opponent}: 1 for what it never tried on them. */
    public double learnedOf(UUID opponent, Option option) {
        return memory.factor(opponent, option);
    }

    /** What it learned to read in this life (variations and copies). */
    public List<Option> learned() {
        return List.copyOf(learned);
    }

    // ------------------------------------------------------------------ what it sees others cast

    /**
     * A mage cast where a sorcerer could see it: it writes the sentence down (docs/feiticeiro-design.md, 4). Writing
     * is not understanding: the runes it does not know are left out, as its spirit would skip them, and the copy is
     * kept only if what is left still does something worth reading.
     */
    @SubscribeEvent
    public static void onWitnessed(SpellWitnessedEvent event) {
        LivingEntity caster = event.caster();
        if (!(caster instanceof net.minecraft.world.entity.player.Player) || caster.level().isClientSide()) {
            return;
        }
        List<Sorcerer> seeing = caster.level().getEntitiesOfClass(Sorcerer.class,
                caster.getBoundingBox().inflate(WITNESS_REACH), evoker -> evoker.isAlive() && evoker.hasLineOfSight(caster));
        for (Sorcerer evoker : seeing) {
            evoker.witness(event.words());
            evoker.copy(event.words());
        }
    }

    // ------------------------------------------------------------------ learning (docs/aprendizado-runas-design.md)

    /** It saw these words work: each rune of them it does not know, it understands a little more. */
    void witness(List<String> words) {
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
        for (VitaElement element : VitaDamageHandler.natureOf(this, source)) {
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
        if (level() instanceof ServerLevel server) {
            Vec3 eyes = getEyePosition();
            server.sendParticles(ParticleTypes.SOUL, eyes.x, eyes.y + 0.3D, eyes.z, 16, 0.4D, 0.4D, 0.4D, 0.02D);
        }
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F);
        return true;
    }

    /** How far it understands each rune it does not know yet. */
    public Map<String, Integer> understanding() {
        return Collections.unmodifiableMap(understanding);
    }

    // ------------------------------------------------------------------ the band (docs/feiticeiro-design.md, 7.2)

    /**
     * It leads its band: it marks the members it has not marked yet, as it likes (docs/feiticeiro-design.md, 7.2). An
     * aggressive one gives them all one mark, to bring them all at once; a cautious one, a mark for each work (blades,
     * bows, casters); a trickster, a mark for each, to bring them one by one. A member another has marked is not its.
     */
    public void markBand() {
        ensureMind();
        band.keySet().removeIf(member -> !(level() instanceof ServerLevel server)
                || !(server.getEntity(member) instanceof Raider raider) || !raider.isAlive());
        for (Raider member : members()) {
            if (band.containsKey(member.getUUID()) || MarkHelper.markForEntity(member).isPresent()) {
                continue;
            }
            String mark = switch (personality) {
                case AGGRESSIVE -> markFor("all");
                case CAUTIOUS -> markFor(work(member));
                case TRICKSTER -> newMark();
            };
            MarkHelper.applyMark(member, mark);
            band.put(member.getUUID(), mark);
        }
    }

    /** The raiders around it it may lead: in its raid, or with none, near it. Never another sorcerer. */
    private List<Raider> members() {
        return level().getEntitiesOfClass(Raider.class, getBoundingBox().inflate(BAND_REACH), raider -> raider != this
                && raider.isAlive() && !(raider instanceof Sorcerer)
                && (getCurrentRaid() == null || raider.getCurrentRaid() == getCurrentRaid()));
    }

    /** What a member is for: blades, bows or casters. */
    private static String work(Raider member) {
        if (member instanceof SpellcasterIllager) {
            return "casters";
        }
        return member instanceof RangedAttackMob ? "bows" : "blades";
    }

    /** The mark it gives every member of one {@code group}: the same one each time. */
    private String markFor(String group) {
        for (Map.Entry<UUID, String> entry : band.entrySet()) {
            if (level() instanceof ServerLevel server && server.getEntity(entry.getKey()) instanceof Raider raider
                    && (group.equals("all") || work(raider).equals(group))) {
                return entry.getValue();
            }
        }
        return newMark();
    }

    /** A new mark: a word of two or three syllables of its own, that no rune is and no member carries. */
    private String newMark() {
        for (int tries = 0; tries < 20; tries++) {
            StringBuilder word = new StringBuilder();
            int syllables = 2 + getRandom().nextInt(2);
            for (int i = 0; i < syllables; i++) {
                word.append(SYLLABLES[getRandom().nextInt(SYLLABLES.length)]);
            }
            String mark = word.toString();
            if (!Lexicons.get().isRune(mark) && !band.containsValue(mark)) {
                return mark;
            }
        }
        return "bando" + getId();
    }

    /** The marks of its band. */
    public Set<String> bandMarks() {
        return Set.copyOf(band.values());
    }

    /** How many members of its band carrying {@code mark} are worth bringing to {@code target}: the ones far from it. */
    private double allies(String mark, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return 0.0D;
        }
        double worth = 0.0D;
        for (Map.Entry<UUID, String> entry : band.entrySet()) {
            if (entry.getValue().equals(mark) && server.getEntity(entry.getKey()) instanceof Raider raider
                    && raider.isAlive() && raider.distanceTo(target) > ALREADY_THERE) {
                // One that strikes up close is worth more brought there than one that shoots from afar.
                worth += raider instanceof RangedAttackMob || raider instanceof SpellcasterIllager ? 0.4D : 1.0D;
            }
        }
        return worth;
    }

    /** Bringing each mark of its band where it aims ({@code kador vocant}): one option a mark, when it knows the verb. */
    private List<Option> calls() {
        List<Option> calls = new ArrayList<>();
        Optional<String> bring = known.stream()
                .filter(rune -> Lexicons.get().operationOf(rune).filter("manifest"::equals).isPresent()).findFirst();
        if (bring.isEmpty()) {
            return calls;
        }
        for (String mark : bandMarks()) {
            Option.page(mark + " " + bring.get(), List.of(new Option.Line(List.of(mark, bring.get()), 0)))
                    .ifPresent(calls::add);
        }
        return calls;
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
        if (level() instanceof ServerLevel server) {
            Vec3 eyes = getEyePosition();
            server.sendParticles(ParticleTypes.ENCHANT, eyes.x, eyes.y + 0.4D, eyes.z, 12, 0.4D, 0.3D, 0.4D, 0.8D);
        }
        return true;
    }

    private static boolean isNumber(String word) {
        return word.matches("-?\\d+");
    }

    /** What its mind would read now against {@code target}, if anything is worth it. */
    private Optional<Mind.Choice> choose(LivingEntity target) {
        float share = getHealth() / Math.max(1.0F, getMaxHealth());
        Mind.Scene scene = new Mind.Scene(this, target, distanceTo(target), share, Casters.vis(this),
                mark -> allies(mark, target));
        List<Option> all = new ArrayList<>(options());
        all.addAll(learned);
        all.addAll(calls());
        UUID opponent = target.getUUID();
        return Mind.choose(scene, personality(), all, getRandom(), option -> memory.factor(opponent, option));
    }

    /** The words seen above its head: what it reads, as many as a recitation holds. */
    private static List<String> shown(Option option) {
        List<String> words = option.words();
        return words.size() <= SHOWN_WORDS ? words : words.subList(0, SHOWN_WORDS);
    }

    private void beginTrance(Mind.Choice choice) {
        reading = choice;
        tranceTicks = 0;
        spellCastingTickCount = TRANCE_TICKS;
        setIsCastingSpell(IllagerSpell.SUMMON_VEX); // the arms raised, the light of a caster in trance
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
        recite(List.of(), false);
    }

    /** One tick of the trance: the words come one by one; at the end, the spirit reads them. */
    private void holdTrance(LivingEntity target) {
        Option option = reading.option();
        tranceTicks++;
        spellCastingTickCount = Math.max(1, TRANCE_TICKS - tranceTicks);
        List<String> words = shown(option);
        int spoken = Math.min(words.size(), (int) Math.ceil(words.size() * tranceTicks / (double) (TRANCE_TICKS - 5)));
        if (tranceTicks % 4 == 0 || tranceTicks == 1) {
            recite(words.subList(0, spoken), false);
        }
        if (tranceTicks >= TRANCE_TICKS) {
            cast(reading, target);
        }
    }

    private void cast(Mind.Choice choice, LivingEntity target) {
        Option option = choice.option();
        // The aim is enough (docs/feiticeiro-design.md, 7.4): it looks at the middle of what it reads against.
        aimAt(target);
        // Read as a player's page is (docs/fluxo-design.md): every line at once, each released at its step. What
        // happens to a creature is not kept, so a line that waits for something to happen is not read.
        List<SpellCastingService.TimedSpell> spells = option.lines().stream()
                .filter(line -> line.words().stream().noneMatch(Sorcerer::condition))
                .map(line -> new SpellCastingService.TimedSpell(line.words(), line.delaySteps()))
                .toList();
        try {
            SpellCastingService.Result result = SPIRIT.castBlock(this, spells, later -> {
            });
            if (result.failed()) {
                LOGGER.debug("Sorcerer could not cast '{}': {}", option.name(), result.message().getString());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Sorcerer failed casting '{}'", option.name(), exception);
        }
        if (option.composed()) {
            composed.remove(option.name());
            composed.put(option.name(), choice.intent());
            while (composed.size() > KEPT_COMPOSED) {
                composed.remove(composed.keySet().iterator().next());
            }
        }
        lastChoice = choice;
        awaiting = new Outcome(choice, target, target.getHealth(),
                level().getGameTime() + (long) option.lastStep() * SpellCastingService.STEP_TICKS + OUTCOME_TICKS);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.0F);
        endTrance(true);
        releasingAt = target;
        releaseTicks = option.lastStep() * SpellCastingService.STEP_TICKS;
        restTicks = REST_TICKS + getRandom().nextInt(20);
    }

    /** Whether a word is a condition: a line that waits for something to happen. */
    private static boolean condition(String word) {
        return Lexicons.get().rune(word).map(rune -> rune.is(WordClass.CONDITION)).orElse(false);
    }

    private void endTrance(boolean spoken) {
        Mind.Choice choice = reading;
        reading = null;
        tranceTicks = 0;
        spellCastingTickCount = 0;
        setIsCastingSpell(IllagerSpell.NONE);
        // Spoken, the words linger and fade; broken, they are gone at once.
        recite(spoken && choice != null ? shown(choice.option()) : List.of(), true);
    }

    private void aimAt(LivingEntity target) {
        Vec3 middle = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        lookAt(EntityAnchorArgument.Anchor.EYES, middle);
        setYHeadRot(getYRot());
        setYBodyRot(getYRot());
    }

    private void recite(List<String> runes, boolean finished) {
        SpellingNetwork.broadcastRecitation(this, new RecitationPacket(getId(), runes, finished));
    }

    // ------------------------------------------------------------------ what it leaves

    /**
     * Killed by a player, it may leave its grimoire (docs/feiticeiro-design.md, 5): a grimoire like any other, with the
     * pages written by hand it could read, and after them the sentences it composed and read in this life, each named
     * by what it wanted with it. Read with surgit, the runes the reader does not know are skipped, as in any reading;
     * the grimoire shows them all the same, and says what each one does.
     */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit && getRandom().nextFloat() < GRIMOIRE_CHANCE + GRIMOIRE_CHANCE_PER_LOOTING * looting) {
            ItemStack book = grimoire();
            if (!book.getOrCreateTag().getList("pages", Tag.TAG_STRING).isEmpty()) {
                spawnAtLocation(book); // one who could read none of its pages and composed nothing leaves none
            }
        }
    }

    /** Its grimoire as an item: its pages and the sentences it composed, named, with it as their author. */
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
        tag.put("pages", pages);
        tag.put(GrimoireItem.PAGE_NAMES_TAG, names);
        tag.putUUID(GrimoireItem.AUTHOR_ID_TAG, getUUID());
        tag.putString(GrimoireItem.AUTHOR_TAG, getName().getString());
        ListTag glossary = new ListTag();
        ParserDictionary dictionary = ParserDictionary.load();
        Glossary.grow(List.of(), texts, id -> dictionary.lookup(id).isPresent())
                .forEach(id -> glossary.add(StringTag.valueOf(id)));
        tag.put(GrimoireItem.GLOSSARY_TAG, glossary);
        return book;
    }

    /** The sentences it composed and read, newest last. */
    public List<String> composedSentences() {
        return Collections.unmodifiableList(new ArrayList<>(composed.keySet()));
    }

    private static String intentName(Intent intent) {
        return switch (intent) {
            case STRIKE -> "ferir";
            case DRAW -> "tirar";
            case VEIL -> "sumir";
            case CALL -> "chamar";
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
                + " opções, " + learned.size() + " aprendidas (" + copied.size() + " copiadas); entendendo "
                + understanding + "; bando " + band.size() + " com as marcas " + bandMarks() + "; Vis " + String.format(Locale.ROOT, "%.1f", Casters.vis(this)) + "; leu por último: " + last;
    }

    /**
     * Out of a fight, with nothing to hunt, it writes (docs/feiticeiro-design.md, 2.1): it stands with its grimoire, the
     * light of the language drawn to it, and works a little every few ticks until its pages are written. A target
     * breaks it off; it goes on where it stopped when it is alone again.
     */
    private final class WriteGoal extends Goal {

        /** Ticks between two steps of the work. */
        private static final int STEP_TICKS = 5;
        private int ticks;

        WriteGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return written.isEmpty() && getTarget() == null && reading == null && !isCelebrating()
                    && PhraseCatalog.ready().isPresent();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            getNavigation().stop();
            ensureMind();
            if (preparation == null) {
                preparation = new Preparation(PhraseCatalog.ready().orElseThrow(), known, personality);
            }
        }

        @Override
        public void tick() {
            ticks++;
            if (level() instanceof ServerLevel server && ticks % 4 == 0) {
                Vec3 eyes = getEyePosition();
                server.sendParticles(ParticleTypes.ENCHANT, eyes.x, eyes.y + 0.6D, eyes.z, 3, 0.6D, 0.4D, 0.6D, 0.4D);
            }
            if (ticks % 40 == 0) {
                playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.9F + getRandom().nextFloat() * 0.2F);
            }
            if (ticks % STEP_TICKS != 0 || preparation == null) {
                return;
            }
            preparation.step();
            if (preparation.done()) {
                written = List.copyOf(preparation.pages());
                preparation = null;
                options = null;
                playSound(SoundEvents.BOOK_PUT, 1.0F, 1.0F);
            }
        }
    }

    /**
     * Reads when there is a target in sight, the rest is over and its mind finds something worth reading: it stands
     * still and looks at the target for the two seconds of the trance. Losing the target ends the trance with nothing
     * cast.
     */
    private final class TranceGoal extends Goal {

        @Nullable
        private Mind.Choice chosen;

        TranceGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || reading != null || releaseTicks > 0 || restTicks > 0
                    || !hasLineOfSight(target)) {
                return false;
            }
            // One who broke its trance before is not let near it again: it opens its trance only from afar.
            float share = getHealth() / Math.max(1.0F, getMaxHealth());
            if (distanceTo(target) < memory.safeDistance(target.getUUID()) && !personality().desperate(share)) {
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
            LivingEntity target = getTarget();
            return reading != null && target != null && target.isAlive();
        }

        @Override
        public void start() {
            getNavigation().stop();
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
            LivingEntity target = getTarget();
            if (target == null || reading == null) {
                return;
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            holdTrance(target);
        }
    }
}

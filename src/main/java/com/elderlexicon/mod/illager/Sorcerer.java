package com.elderlexicon.mod.illager;

import com.elderlexicon.mod.caster.CasterMind;
import com.elderlexicon.mod.caster.RuneCaster;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Sorcerer (Feiticeiro): the evoker that knows the Old Tongue (docs/feiticeiro-design.md). Its mind is a caster's
 * ({@link CasterMind}): it reads the language with a trance others see, writes its pages out of a fight, learns, and
 * remembers. It is an evoker like any other (raids, allies, its look), but the fangs, the vexes and the wololo are gone:
 * all it does is said in the language, and paid as any living being pays (book 3.2).
 * <p>
 * What is its own: it leads its band (it marks the Illagers around it as it likes, and brings them where it aims,
 * {@code kador vocant}), it shows its trance with the arms of an evoker, and, killed by a player, it may leave its
 * grimoire.
 */
public class Sorcerer extends Evoker implements RuneCaster, CasterMind.Body {

    /** The trance: two seconds, as the book says (cap. 5). */
    public static final int TRANCE_TICKS = CasterMind.TRANCE_TICKS;
    /** The chance it leaves its grimoire when a player kills it, and how much each level of looting adds. */
    private static final float GRIMOIRE_CHANCE = 0.25F;
    private static final float GRIMOIRE_CHANCE_PER_LOOTING = 0.05F;
    private static final String MIND_TAG = "SorcererMind";
    /** Whether it has its book open in its hands: the clients draw it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> READING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Sorcerer.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Whether it points its focus where it aims, while what it read is released: the clients draw it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> POINTING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Sorcerer.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Its degree, for the clients to draw its beard by. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DEGREE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Sorcerer.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** How often it looks at its band, to mark the ones it has not marked yet. */
    private static final int BAND_TICKS = 100;
    /** The wand it engraved with a page of its own at a galdraria table, if any. */
    private ItemStack engravedWand = ItemStack.EMPTY;
    /** How far its band reaches. */
    private static final double BAND_REACH = 24.0D;
    /** An ally nearer the target than this is there already: not worth bringing. */
    private static final double ALREADY_THERE = 6.0D;
    /** The syllables of the marks it gives (the sounds of no tongue of this world but its own). */
    private static final String[] SYLLABLES = {"ka", "dor", "vil", "an", "tor", "lig", "nah", "ven", "sar", "ul", "mek",
            "os", "ra", "thi", "gor", "em", "zu", "hal"};

    /**
     * Its mind: made when first asked for, since the goals that use it are made while the evoker is still being built
     * (no field of this class is set yet then).
     */
    private CasterMind mind;
    /** The marks it gave its band, and to whom (each member's mark is on the member itself). */
    private final Map<UUID, String> band = new LinkedHashMap<>();

    public Sorcerer(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
    }

    @Override
    public CasterMind casterMind() {
        if (mind == null) {
            mind = new CasterMind(this, this);
        }
        return mind;
    }

    /**
     * An evoker's body, but it sees farther: it fights from where its spells get (docs/feiticeiro-design.md, 3.7), and
     * an evoker loses sight of what is twelve blocks away.
     */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Evoker.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // The fangs, the vexes and the wololo are the vanilla evoker's: this one says what it does.
        goalSelector.removeAllGoals(wrapped -> wrapped.getClass().getEnclosingClass() == Evoker.class);
        goalSelector.addGoal(1, casterMind().tranceGoal());
        goalSelector.addGoal(2, casterMind().keepDistanceGoal(0.8D, 0.0D));
        goalSelector.addGoal(3, casterMind().approachGoal(0.7D));
        goalSelector.addGoal(4, casterMind().talkGoal());
        goalSelector.addGoal(5, casterMind().writeGoal());
        goalSelector.addGoal(6, casterMind().studyGoal());
        // Villagers are its prey, but not a Wizard: the two are neither friends nor foes (docs/mago-design.md, 5b).
        targetSelector.removeAllGoals(wrapped -> wrapped instanceof NearestAttackableTargetGoal<?>);
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, false, false,
                villager -> !(villager instanceof Wizard)).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, false));
    }

    // ------------------------------------------------------------------ its body, for its mind

    @Override
    public void tranceBegan() {
        spellCastingTickCount = TRANCE_TICKS;
        setIsCastingSpell(IllagerSpell.SUMMON_VEX); // the arms raised, the light of a caster in trance
    }

    @Override
    public void tranceHeld(int ticksLeft) {
        spellCastingTickCount = ticksLeft;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(READING, false);
        entityData.define(DEGREE, (byte) 0);
        entityData.define(POINTING, false);
    }

    @Override
    public com.elderlexicon.mod.mind.Degree shownDegree() {
        return com.elderlexicon.mod.mind.Degree.values()[entityData.get(DEGREE)];
    }

    @Override
    public void reading(boolean reading) {
        entityData.set(READING, reading);
    }

    @Override
    public boolean readingBook() {
        return entityData.get(READING);
    }

    /** It keeps one wand engraved with a page of its own, until it engraves another: it falls when it dies. */
    @Override
    public boolean wantsEngraving() {
        return engravedWand.isEmpty() || getRandom().nextInt(4) == 0;
    }

    @Override
    public void engraved(ItemStack wand) {
        engravedWand = wand;
    }

    @Override
    public void spoke() {
        playSound(SoundEvents.EVOKER_AMBIENT, 1.0F, 0.9F + getRandom().nextFloat() * 0.2F);
    }

    @Override
    public void felt(boolean pleased) {
        playSound(pleased ? SoundEvents.EVOKER_CELEBRATE : SoundEvents.VINDICATOR_AMBIENT, 1.0F, pleased ? 1.1F : 0.8F);
    }

    @Override
    public void tranceEnded() {
        spellCastingTickCount = 0;
        setIsCastingSpell(IllagerSpell.NONE);
    }

    @Override
    public void releasing(boolean releasing) {
        entityData.set(POINTING, releasing);
    }

    @Override
    public boolean entranced() {
        return isCastingSpell();
    }

    @Override
    public boolean pointing() {
        return entityData.get(POINTING);
    }

    @Override
    public boolean mayWrite() {
        return !isCelebrating();
    }

    @Override
    public List<Option> moreOptions() {
        return calls();
    }

    // ------------------------------------------------------------------ living

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide()) {
            casterMind().hurt(source);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        casterMind().tick();
        // Its degree grows as it learns: its beard with it.
        entityData.set(DEGREE, (byte) casterMind().degree().ordinal());
        if (tickCount % BAND_TICKS == 0) {
            markBand();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag saved = new CompoundTag();
        casterMind().save(saved);
        ListTag members = new ListTag();
        band.forEach((member, mark) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Member", member);
            entry.putString("Mark", mark);
            members.add(entry);
        });
        saved.put("Band", members);
        if (!engravedWand.isEmpty()) {
            saved.put("EngravedWand", engravedWand.save(new CompoundTag()));
        }
        tag.put(MIND_TAG, saved);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains(MIND_TAG)) {
            return;
        }
        CompoundTag saved = tag.getCompound(MIND_TAG);
        casterMind().load(saved);
        engravedWand = saved.contains("EngravedWand") ? ItemStack.of(saved.getCompound("EngravedWand"))
                : ItemStack.EMPTY;
        band.clear();
        ListTag members = saved.getList("Band", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag entry = members.getCompound(i);
            if (entry.hasUUID("Member")) {
                band.put(entry.getUUID("Member"), entry.getString("Mark"));
            }
        }
    }

    // ------------------------------------------------------------------ the band (docs/feiticeiro-design.md, 7.2)

    /**
     * It leads its band: it marks the members it has not marked yet, as it likes (docs/feiticeiro-design.md, 7.2). An
     * aggressive one gives them all one mark, to bring them all at once; a cautious one, a mark for each work (blades,
     * bows, casters); a trickster, a mark for each, to bring them one by one. A member another has marked is not its.
     */
    public void markBand() {
        Personality personality = casterMind().personality();
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
    @Override
    public double allies(String mark, LivingEntity target) {
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
        Optional<String> bring = casterMind().known().stream()
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

    // ------------------------------------------------------------------ what it leaves

    /**
     * Killed by a player, it may leave its grimoire (docs/feiticeiro-design.md, 5); one that could read none of its
     * pages and composed nothing leaves none.
     */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!engravedWand.isEmpty()) {
            spawnAtLocation(engravedWand.copy());
        }
        if (recentlyHit && getRandom().nextFloat() < GRIMOIRE_CHANCE + GRIMOIRE_CHANCE_PER_LOOTING * looting) {
            ItemStack book = grimoire();
            if (!CasterMind.empty(book)) {
                spawnAtLocation(book);
            }
        }
    }

    // ------------------------------------------------------------------ its mind, as tests and commands see it

    public ItemStack grimoire() {
        return casterMind().grimoire();
    }

    /** A line about who it is, for the one who asks (/runas mente). */
    public String describe() {
        return casterMind().describe() + "; bando " + band.size() + " com as marcas " + bandMarks();
    }

    public void setMind(Degree degree, Personality personality, Set<String> known) {
        casterMind().setMind(degree, personality, known);
    }

    public Degree degree() {
        return casterMind().degree();
    }

    public Personality personality() {
        return casterMind().personality();
    }

    public Set<String> known() {
        return casterMind().known();
    }

    public List<Option> written() {
        return casterMind().written();
    }

    public boolean writing() {
        return casterMind().writing();
    }

    public boolean inTrance() {
        return casterMind().inTrance();
    }

    public boolean releasing() {
        return casterMind().releasing();
    }

    public List<Option> learned() {
        return casterMind().learned();
    }

    public Map<String, Integer> understanding() {
        return casterMind().understanding();
    }

    public void experience(String rune) {
        casterMind().experience(rune);
    }

    public boolean dream() {
        return casterMind().dream();
    }

    public boolean copy(List<String> words) {
        return casterMind().copy(words);
    }

    public Optional<Mind.Choice> lastChoice() {
        return casterMind().lastChoice();
    }
}

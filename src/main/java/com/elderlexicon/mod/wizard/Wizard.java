package com.elderlexicon.mod.wizard;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.caster.CasterMind;
import com.elderlexicon.mod.caster.RuneCaster;
import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.spell.block.WrittenTexts;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.spelling.render.SpellMapHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Wizard (Mago): the neutral one of the casters (docs/mago-design.md, 1). Its mind is a caster's
 * ({@link CasterMind}), the Sorcerer's own: it reads the language with a trance others see, writes its pages in peace,
 * learns runes by living and seeing them, and remembers who it fought. It hunts no one: it fights whoever hurts it, and
 * whoever hurts a villager near it, as an iron golem would. In a fight it keeps its distance.
 * <p>
 * It trades (docs/mago-design.md, 2): wands, a page of its grimoire torn out, its grimoire whole once it has written it.
 */
public class Wizard extends AbstractVillager implements RuneCaster, CasterMind.Body,
        com.elderlexicon.mod.dialogue.Dialogue.Listener {

    private static final String MIND_TAG = "WizardMind";
    /** What the clients draw of it: its book open in its hands (1), its arms raised in a trance (2). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> POSE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Wizard.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /**
     * What the clients draw it by (docs/mago-design.md, 5i): its degree (its beard), where it lives (its robe), and at
     * a university its rank and chair (its gown, its hat, its stole); -1 for none.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DEGREE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Wizard.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DWELLING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Wizard.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> RANK =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Wizard.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> CHAIR =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Wizard.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final byte READING = 1;
    private static final byte CASTING = 2;
    private static final byte POINTING = 4;
    /** How many wands engraved with its own pages it keeps to sell. */
    private static final int ENGRAVED_WARES = 3;
    /** How near a hurt villager must be for it to take up the villager's cause. */
    private static final double DEFENDS_WITHIN = 16.0D;
    /** How recent the hurt must be. */
    private static final int HURT_RECENTLY_TICKS = 100;
    /** How close it lets the one it fights come before it steps away. */
    private static final double KEEPS_AWAY = 5.0D;

    /** Its mind: made when first asked for (the goals that use it are made before any field of this class is set). */
    private CasterMind mind;
    /** How many pages it had written when its offers were made: when it writes more, they are made again. */
    private int offeredFor = -1;
    /** Wands it engraved with its own pages at a galdraria table, to sell (docs/mago-design.md, 5c). */
    private final List<ItemStack> engravedWares = new java.util.ArrayList<>();
    /** Whether what it has to sell changed since its offers were made. */
    private boolean waresChanged;
    /**
     * Whether it was just placed with a house it was saved in (docs/mago-design.md, 5f): it is born anew there, and it
     * is its home.
     */
    private boolean arriving;
    /** How far from its house it wanders at peace. */
    private static final int HOUSE_RADIUS = 24;

    public Wizard(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public CasterMind casterMind() {
        if (mind == null) {
            mind = new CasterMind(this, this);
        }
        return mind;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(0, casterMind().escapeFireGoal(0.9D));
        goalSelector.addGoal(2, casterMind().tranceGoal());
        goalSelector.addGoal(3, casterMind().keepDistanceGoal(0.7D, KEEPS_AWAY));
        goalSelector.addGoal(4, casterMind().approachGoal(0.6D));
        goalSelector.addGoal(5, casterMind().talkGoal());
        goalSelector.addGoal(6, casterMind().writeGoal());
        goalSelector.addGoal(7, casterMind().studyGoal());
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35D));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        // Whoever hurts it is its foe, and the wizards near it are told (never against another wizard).
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Wizard.class).setAlertOthers());
        targetSelector.addGoal(2, new DefendVillagersGoal());
    }

    // ------------------------------------------------------------------ its body, for its mind

    @Override
    public boolean mayWrite() {
        return !isTrading();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSE, (byte) 0);
        entityData.define(DEGREE, (byte) 0);
        entityData.define(DWELLING, (byte) Dwelling.WILDS.ordinal());
        entityData.define(RANK, (byte) -1);
        entityData.define(CHAIR, (byte) -1);
    }

    private void pose(byte flag, boolean on) {
        byte now = entityData.get(POSE);
        entityData.set(POSE, (byte) (on ? now | flag : now & ~flag));
    }

    @Override
    public void reading(boolean reading) {
        pose(READING, reading);
    }

    @Override
    public boolean readingBook() {
        return (entityData.get(POSE) & READING) != 0;
    }

    /** Whether it is in a trance, its arms raised as an evoker's: on the client too. */
    public boolean castingSpell() {
        return (entityData.get(POSE) & CASTING) != 0;
    }

    @Override
    public void tranceBegan() {
        pose(READING, false);
        pose(CASTING, true);
    }

    @Override
    public void tranceEnded() {
        pose(CASTING, false);
    }

    @Override
    public void releasing(boolean releasing) {
        pose(POINTING, releasing);
    }

    @Override
    public boolean entranced() {
        return castingSpell();
    }

    @Override
    public boolean pointing() {
        return (entityData.get(POSE) & POINTING) != 0;
    }

    @Override
    public boolean wantsEngraving() {
        return engravedWares.size() < ENGRAVED_WARES;
    }

    @Override
    public void engraved(ItemStack wand) {
        if (engravedWares.size() < ENGRAVED_WARES) {
            engravedWares.add(wand);
            waresChanged = true;
        }
    }

    /** A voice a little deeper than a villager's. */
    @Override
    public float getVoicePitch() {
        return 0.85F + (getRandom().nextFloat() - getRandom().nextFloat()) * 0.1F;
    }

    @Override
    public void spoke() {
        playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, 0.85F + getRandom().nextFloat() * 0.2F);
    }

    @Override
    public void felt(boolean pleased) {
        playSound(pleased ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, 1.0F, 0.9F);
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
            if (castingSpell()) {
                spellLight();
            }
            return;
        }
        if (arriving) {
            arriving = false;
            casterMind().reborn();
            casterMind().home(blockPosition(), HOUSE_RADIUS);
            dwellIn(Dwelling.VILLAGE);
            offers = null;
        }
        casterMind().tick();
        // Its degree grows as it learns: its beard with it.
        entityData.set(DEGREE, (byte) casterMind().degree().ordinal());
        // One it talked or traded with walked away without a word: it goes back to what it was doing.
        if (getTradingPlayer() instanceof net.minecraft.server.level.ServerPlayer player
                && !(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)
                && !com.elderlexicon.mod.dialogue.Dialogue.speaking(player, this)) {
            setTradingPlayer(null);
        }
        // Once it wrote its pages, what it has to offer changes: its grimoire, a page of it.
        if (getTradingPlayer() == null && (offeredFor != casterMind().written().size() || waresChanged)) {
            offers = null;
            waresChanged = false;
        }
        // At peace again: the one it fought is gone or far.
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || distanceTo(target) > 2.0D * DEFENDS_WITHIN)) {
            setTarget(null);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag saved = new CompoundTag();
        casterMind().save(saved);
        net.minecraft.nbt.ListTag wares = new net.minecraft.nbt.ListTag();
        engravedWares.forEach(wand -> wares.add(wand.save(new CompoundTag())));
        saved.put("EngravedWares", wares);
        saved.putBoolean("Arriving", arriving);
        saved.putString("Dwelling", dwelling().id());
        rank().ifPresent(rank -> saved.putString("Rank", rank.id()));
        chair().ifPresent(chair -> saved.putString("Chair", chair.id()));
        tag.put(MIND_TAG, saved);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(MIND_TAG)) {
            CompoundTag saved = tag.getCompound(MIND_TAG);
            casterMind().load(saved);
            arriving = saved.getBoolean("Arriving");
            var rank = com.elderlexicon.mod.university.Rank.byId(saved.getString("Rank"));
            var chair = com.elderlexicon.mod.university.Discipline.byId(saved.getString("Chair"));
            entityData.set(RANK, (byte) rank.map(Enum::ordinal).orElse(-1).intValue());
            entityData.set(CHAIR, (byte) chair.map(Enum::ordinal).orElse(-1).intValue());
            // One saved before it knew where it lives: of a university if it has a rank, else of the wilds.
            dwellIn(saved.contains("Dwelling") ? Dwelling.byId(saved.getString("Dwelling"))
                    : rank.isPresent() ? Dwelling.UNIVERSITY : Dwelling.WILDS);
            engravedWares.clear();
            net.minecraft.nbt.ListTag wares = saved.getList("EngravedWares", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < wares.size(); i++) {
                engravedWares.add(ItemStack.of(wares.getCompound(i)));
            }
        }
    }

    // ------------------------------------------------------------------ trading (docs/mago-design.md, 2)

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading() || isBaby() || getTarget() != null || casterMind().inTrance()) {
            return super.mobInteract(player, hand);
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(net.minecraft.stats.Stats.TALKED_TO_VILLAGER);
        }
        // A master of a university talks first: the examinations, the fee, the ranks (docs/mago-design.md, 4.1).
        if (chair().isPresent() && player instanceof net.minecraft.server.level.ServerPlayer server) {
            setTradingPlayer(player);
            com.elderlexicon.mod.university.Council.talk(server, this);
            return InteractionResult.SUCCESS;
        }
        if (getOffers().isEmpty()) {
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!level().isClientSide()) {
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName(), 1);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /**
     * What it offers: an improvised wand and a made one; a page of its grimoire torn out (one it wrote, or one written
     * by hand it can read); its grimoire whole, once it has written its pages.
     */
    @Override
    protected void updateTrades() {
        MerchantOffers offered = getOffers();
        offered.add(new MerchantOffer(new ItemStack(Items.EMERALD, 4), new ItemStack(ElderLexicon.IMPROVISED_WAND.get()),
                8, 2, 0.05F));
        offered.add(new MerchantOffer(new ItemStack(Items.EMERALD, 14), new ItemStack(ElderLexicon.WAND.get()), 3, 10,
                0.05F));
        List<Option> pages = casterMind().written();
        if (!pages.isEmpty() && level() instanceof ServerLevel server) {
            Option page = pages.get(getRandom().nextInt(pages.size()));
            offered.add(new MerchantOffer(new ItemStack(Items.EMERALD, 6), new ItemStack(Items.PAPER),
                    detachedPage(server, page), 4, 5, 0.05F));
        }
        // The wands it engraved with its own pages: one of each.
        engravedWares.forEach(wand -> offered.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8), wand.copy(), 1,
                8, 0.05F)));
        ItemStack grimoire = casterMind().grimoire();
        if (!CasterMind.empty(grimoire)) {
            offered.add(new MerchantOffer(new ItemStack(Items.EMERALD, 24), new ItemStack(Items.BOOK), grimoire, 1, 20,
                    0.05F));
        }
        offeredFor = casterMind().written().size();
    }

    /** A page of its grimoire, torn out: a detached page with the page's text, as a player tears one out. */
    private ItemStack detachedPage(ServerLevel server, Option page) {
        ItemStack sheet = SpellScrollItem.create(server, getBlockX(), getBlockZ(), (byte) 3);
        String text = page.text();
        SpellMapHelper.paintSpell(server, sheet, text);
        sheet.setHoverName(Component.literal(page.name()));
        CompoundTag custom = sheet.getOrCreateTag();
        custom.putString("DetachedPageText", text);
        WrittenTexts.mark(custom);
        return sheet;
    }

    /** An engraved wand sold is gone: it engraves another, in time. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        if (engravedWares.removeIf(wand -> ItemStack.isSameItemSameTags(wand, offer.getResult()))) {
            waresChanged = true;
        }
    }

    /** The light of the language at its hands while the trance lasts, as at an evoker's. */
    private void spellLight() {
        float angle = yBodyRot * ((float) Math.PI / 180.0F) + net.minecraft.util.Mth.cos(tickCount * 0.6662F) * 0.25F;
        float x = net.minecraft.util.Mth.cos(angle);
        float z = net.minecraft.util.Mth.sin(angle);
        level().addParticle(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, getX() + x * 0.6D, getY() + 1.8D,
                getZ() + z * 0.6D, 0.4D, 0.3D, 0.9D);
        level().addParticle(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, getX() - x * 0.6D, getY() + 1.8D,
                getZ() - z * 0.6D, 0.4D, 0.3D, 0.9D);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            int xp = 3 + getRandom().nextInt(4);
            level().addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level(), getX(), getY() + 0.5D, getZ(),
                    xp));
        }
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    // ------------------------------------------------------------------ its mind, as tests and commands see it

    public String describe() {
        return casterMind().describe();
    }

    /**
     * It is of a university (docs/mago-design.md, 4 and 5g): born of the degree its rank asks for, called by its title,
     * at home where it is now, within {@code radius} blocks.
     */
    public void joinUniversity(com.elderlexicon.mod.university.Rank rank,
                               @Nullable com.elderlexicon.mod.university.Discipline chair, int radius) {
        entityData.set(RANK, (byte) rank.ordinal());
        entityData.set(CHAIR, (byte) (chair == null ? -1 : chair.ordinal()));
        dwellIn(Dwelling.UNIVERSITY);
        casterMind().setMind(rank.degree(), Personality.draw(getRandom()),
                rank.degree().draw(com.elderlexicon.mod.magic.lexicon.Lexicons.get(), getRandom()));
        casterMind().home(blockPosition(), radius);
        setCustomName(Component.translatable(chair == null ? "university.elderlexicon.rank." + rank.id()
                : "university.elderlexicon.chair." + chair.id()));
        setPersistenceRequired();
    }

    /** A member of a university keeps the peace wherever it is (docs/mago-design.md, 5h). */
    @Override
    public boolean keepsThePeace() {
        return rank().isPresent();
    }

    /** A talk with {@code player} ended: unless it turned to trading, it goes back to what it was doing. */
    @Override
    public void dialogueEnded(net.minecraft.server.level.ServerPlayer player) {
        if (getTradingPlayer() == player && !(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)) {
            setTradingPlayer(null);
        }
    }

    /** Its rank at a university, if it has one. */
    public java.util.Optional<com.elderlexicon.mod.university.Rank> rank() {
        byte rank = entityData.get(RANK);
        return rank < 0 ? java.util.Optional.empty()
                : java.util.Optional.of(com.elderlexicon.mod.university.Rank.values()[rank]);
    }

    /** Its chair at a university, if it holds one. */
    public java.util.Optional<com.elderlexicon.mod.university.Discipline> chair() {
        byte chair = entityData.get(CHAIR);
        return chair < 0 ? java.util.Optional.empty()
                : java.util.Optional.of(com.elderlexicon.mod.university.Discipline.values()[chair]);
    }

    /** Where it lives (docs/mago-design.md, 5i): on the client too, for its robe. */
    public Dwelling dwelling() {
        return Dwelling.values()[entityData.get(DWELLING)];
    }

    /** It lives at {@code dwelling} now, and dresses for it. */
    public void dwellIn(Dwelling dwelling) {
        entityData.set(DWELLING, (byte) dwelling.ordinal());
    }

    @Override
    public Degree shownDegree() {
        return Degree.values()[entityData.get(DEGREE)];
    }

    /** It will be born anew where it is placed next, and make that its home (a house saved with it). */
    public void arriveWithHouse() {
        arriving = true;
    }

    public void setMind(Degree degree, Personality personality, Set<String> known) {
        casterMind().setMind(degree, personality, known);
    }

    // ------------------------------------------------------------------ its goals

    /**
     * Whoever hurt a villager near it is its foe (docs/mago-design.md, 1), as an iron golem's: the villager's last
     * attacker, if the hurt was recent. Never another wizard, never a villager.
     */
    private final class DefendVillagersGoal extends TargetGoal {

        @Nullable
        private LivingEntity foe;

        DefendVillagersGoal() {
            super(Wizard.this, false, true);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (getRandom().nextInt(10) != 0) {
                return false;
            }
            List<AbstractVillager> hurt = level().getEntitiesOfClass(AbstractVillager.class,
                    getBoundingBox().inflate(DEFENDS_WITHIN), villager -> villager != Wizard.this
                            && villager.getLastHurtByMob() != null
                            && tickCount - villager.getLastHurtByMobTimestamp() < HURT_RECENTLY_TICKS
                            && villager.tickCount - villager.getLastHurtByMobTimestamp() < HURT_RECENTLY_TICKS);
            for (AbstractVillager villager : hurt) {
                LivingEntity attacker = villager.getLastHurtByMob();
                if (attacker != null && !(attacker instanceof AbstractVillager)
                        && canAttack(attacker, TargetingConditions.DEFAULT)) {
                    foe = attacker;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            setTarget(foe);
            targetMob = foe;
            super.start();
        }
    }
}

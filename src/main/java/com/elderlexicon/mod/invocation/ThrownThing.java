package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.ImpactLaw;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.Qualities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A thing thrown as a blow (docs/invocacao-design.md, section 5.1): a sword, an axe, a spear, a cube of earth,
 * {@code iactare}d at someone. It flies point first and straight (after a creature it was thrown at, turning to follow
 * it, as a blade sent by magic does), strikes as hard as the thing strikes in the hand (a
 * thing that is no weapon, as hard as its mass and speed make it, by the law of impact), and what it is made of acts
 * where it strikes: the hot sets burning, the wet puts out, the light throws back. Then it falls, and lies as the item
 * it is; volatile matter comes apart in its time, flying or not.
 */
public final class ThrownThing extends AbstractArrow {

    private static final EntityDataAccessor<ItemStack> THING =
            SynchedEntityData.defineId(ThrownThing.class, EntityDataSerializers.ITEM_STACK);
    /** A thrown thing flies straight this long before it starts to fall. */
    private static final int STRAIGHT_TICKS = 60;
    /** Fallen to the ground, it lies as an item after this long. */
    private static final int LIES_AFTER_TICKS = 10;
    private static final int BURNING_SECONDS = 4;
    /** How much of its course a blade sent after a creature turns toward it each tick. */
    private static final double HOMING = 0.35D;

    /** The creature it was thrown at, followed until it strikes (none: it flies where it was aimed). */
    @Nullable
    private Entity target;

    private boolean struck;
    private int flying;

    public ThrownThing(EntityType<? extends ThrownThing> type, Level level) {
        super(type, level);
    }

    public ThrownThing(Level level, ItemStack thing, @Nullable Entity owner, Vec3 from) {
        super(Invocations.THROWN.get(), from.x, from.y, from.z, level);
        setOwner(owner);
        setThing(thing);
        setNoGravity(true);
        pickup = Pickup.ALLOWED;
        if (Conjured.qualities(Conjured.matter(thing)).hot()) {
            setSecondsOnFire(100);
        }
    }

    /** Sends it after {@code creature}, to follow it until it strikes. */
    public void homeOn(@Nullable Entity creature) {
        this.target = creature;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(THING, ItemStack.EMPTY);
    }

    public ItemStack thing() {
        return entityData.get(THING);
    }

    private void setThing(ItemStack thing) {
        entityData.set(THING, thing.copy());
    }

    /** Whether a thing thrown strikes as a blow: a weapon or tool, or anything the bench made. */
    public static boolean strikes(ItemStack stack) {
        return !stack.isEmpty() && (Conjured.conjured(stack) || handDamage(stack) > 0.0D);
    }

    /** What a thing adds to the hand's blow, as the game gives it. */
    private static double handDamage(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION)
                .mapToDouble(AttributeModifier::getAmount).sum();
    }

    /**
     * How hard the thing strikes at {@code speed}: a weapon as hard as in the hand (the fist's 1 and what it adds); any
     * other thing by the law of impact, its matter's mass at that speed.
     */
    public static double blow(ItemStack stack, double speed) {
        double hand = handDamage(stack);
        if (hand > 0.0D) {
            return 1.0D + hand;
        }
        Particles matter = Conjured.matter(stack).present();
        double umu = matter.total() > 0L ? matter.umu() * stack.getCount() : 0.5D;
        return ImpactLaw.damage(ImpactLaw.energy(Conjured.qualities(matter), umu, speed));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ItemStack thing = thing();
        if (Conjured.isVolatile(thing)) {
            Conjured.tick(thing, level(), this);
            if (thing.isEmpty()) {
                discard();
                return;
            }
        }
        if (!inGround && !struck) {
            trail(thing);
        }
        if (isNoGravity() && ++flying > STRAIGHT_TICKS) {
            setNoGravity(false);
            target = null;
        }
        follow();
        if (inGround && inGroundTime > LIES_AFTER_TICKS) {
            lie();
        }
    }

    /**
     * What it leaves behind it as it flies: each aspect it holds, as strong as it is pressed into its room (one UMU in a
     * square is the common element), so a blade of thirty UMU of fire is seen and heard for what it is. On the first
     * tick, the rush of it leaving.
     */
    private void trail(ItemStack thing) {
        if (!Conjured.conjured(thing) || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        Particles matter = Conjured.matter(thing).present();
        double room = Conjured.room(thing);
        double strongest = 0.0D;
        for (com.elderlexicon.mod.vita.VitaElement aspect : Particles.ASPECTS) {
            double intensity = matter.count(aspect) / (double) Particles.PER_UMU / room;
            if (aspect != com.elderlexicon.mod.vita.VitaElement.FIRMO || !Conjured.isVolatile(thing)) {
                com.elderlexicon.mod.spell.function.Releases.trail(server, position(), aspect,
                        aspect == com.elderlexicon.mod.vita.VitaElement.FIRMO ? 0.0D : intensity);
            }
            strongest = Math.max(strongest, intensity);
        }
        if (flying == 0 && strongest > 1.0D) {
            float loud = (float) Math.min(2.5D, 0.6D + 0.35D * Math.log(1.0D + strongest));
            float pitch = (float) Math.max(0.5D, 1.2D - 0.12D * Math.log(1.0D + strongest));
            playSound(SoundEvents.BLAZE_SHOOT, loud * 0.6F, pitch);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, loud, pitch * 0.7F);
        }
    }

    /**
     * Struck, volatile matter lets go: what it held in a form it does not keep goes into the world where it struck, as
     * strong as it was pressed (docs/invocacao-design.md, "O golpe mostra a sua força"), and the solid parts fall.
     * Returns whether it did.
     */
    private boolean letGo() {
        ItemStack thing = thing();
        if (!Conjured.isVolatile(thing) || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return false;
        }
        Particles matter = Conjured.matter(thing).present();
        Particles all = Particles.NONE;
        for (int i = 0; i < thing.getCount(); i++) {
            all = all.plus(matter);
        }
        Entity owner = getOwner();
        com.elderlexicon.mod.spell.function.Releases.matter(server, owner instanceof LivingEntity caster ? caster : null,
                position(), all, Conjured.room(thing) * thing.getCount());
        // Spent: only what was solid is left, and it falls where it struck.
        for (ItemStack part : Conjured.parts(thing)) {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(),
                    part.copyWithCount(part.getCount() * thing.getCount())));
        }
        discard();
        return true;
    }

    /** Turns toward the creature it was thrown at, keeping its speed, while it flies and the creature lives. */
    private void follow() {
        if (target == null || struck || inGround) {
            return;
        }
        if (!target.isAlive() || target.level() != level()) {
            target = null;
            return;
        }
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        Vec3 toward = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(position());
        if (speed < 1.0E-3D || toward.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 turned = velocity.normalize().scale(1.0D - HOMING).add(toward.normalize().scale(HOMING));
        setDeltaMovement(turned.normalize().scale(speed));
    }

    /** It lies where it fell, as the item it is. */
    private void lie() {
        ItemStack thing = thing();
        if (!thing.isEmpty()) {
            ItemEntity lying = new ItemEntity(level(), getX(), getY(), getZ(), thing.copy());
            lying.setDefaultPickUpDelay();
            level().addFreshEntity(lying);
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        if (struck || target == getOwner()) {
            return;
        }
        struck = true;
        ItemStack thing = thing();
        double speed = getDeltaMovement().length();
        Entity owner = getOwner();
        float damage = (float) blow(thing, speed);
        // Each blade of a volley is a blow of its own: the moment of grace after a wound does not turn the next aside.
        target.invulnerableTime = 0;
        if (target.hurt(damageSources().thrown(this, owner == null ? this : owner), damage)
                && target instanceof LivingEntity living) {
            Qualities qualities = Conjured.qualities(Conjured.matter(thing));
            if (qualities.hot()) {
                living.setSecondsOnFire(BURNING_SECONDS);
            } else if (qualities.wet()) {
                living.clearFire();
            }
            if (qualities.light()) {
                Vec3 way = getDeltaMovement().normalize();
                living.knockback(qualities.lightness() + 0.4D, -way.x, -way.z);
            }
            if (owner instanceof LivingEntity attacker) {
                attacker.setLastHurtMob(living);
            }
        }
        if (letGo()) {
            return;
        }
        // Struck, it falls where it struck.
        setNoGravity(false);
        setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
        setNoGravity(false);
        if (!struck && letGo()) {
            return;
        }
        super.onHitBlock(hit);
    }

    @Override
    protected ItemStack getPickupItem() {
        return thing().copy();
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Thing", thing().save(new CompoundTag()));
        tag.putBoolean("Struck", struck);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setThing(ItemStack.of(tag.getCompound("Thing")));
        struck = tag.getBoolean("Struck");
    }

    /** Thrown, a thing is no arrow: nothing is lost when it lies on the ground. */
    @Override
    protected void tickDespawn() {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}

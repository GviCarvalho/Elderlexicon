package com.elderlexicon.mod.nahva;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The reflection ({@link TowerRoom#MIRROR}): the climber's double on the other side of the mirror, with their face and
 * what they wear and hold, doing what they did a moment before. It has no will of its own and cannot be hurt: a blow
 * on it falls on whoever struck. Its room moves it, every tick.
 */
public class Reflection extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Reflection.class,
            EntityDataSerializers.OPTIONAL_UUID);

    public Reflection(EntityType<? extends Reflection> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
    }

    /** Whose reflection it is: their face is its face. */
    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    /** A blow on the reflection falls on the one who struck it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player striker) {
            striker.hurt(damageSources().magic(), amount);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

package com.elderlexicon.mod.ligabis.world.golem;

import com.mojang.logging.LogUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * A living statue born from the golem ritual (see {@link GolemStructure}): borrows the iron golem's model,
 * animations and hitbox, but has no goals of its own — an aura hierarchy link puppets it entirely, and its
 * texture is generated at render time from the block the ritual was built with (see {@code GolemRenderer}).
 * <p>
 * Movement does not go through vanilla's AI-oriented input system ({@code xxa}/{@code zza}, speed
 * attribute, friction) at all — that system exists to serve goals and move controls, both of which this
 * golem has none of, and coercing it into acting on externally-set fields turned out to be unreliable
 * (see the design doc's "ritual de golem" section for the trail of hidden vanilla behavior that caused).
 * Instead {@link #travel(Vec3)} is replaced outright: {@code LigabisManager} works out the exact world-space
 * velocity the golem should have this tick and hands it over with {@link #setMoveIntent}; this class just
 * applies gravity and lets {@link net.minecraft.world.entity.Entity#move} — the same low-level primitive
 * vanilla itself uses — resolve collision and step-up.
 */
public final class GolemEntity extends IronGolem {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<String> BLOCK_ID =
            SynchedEntityData.defineId(GolemEntity.class, EntityDataSerializers.STRING);
    private static final double GRAVITY_PER_TICK = 0.08D;
    private static final double TERMINAL_FALL_SPEED = -3.92D;
    private static final double GROUNDED_Y_MOTION = -0.0784000015258789D;

    private double moveX;
    private double moveZ;

    public GolemEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // Movement comes entirely from the aura link that created this golem; it never acts on its own.
    }

    /** The world-space horizontal velocity (blocks/tick) this golem should attempt this tick. */
    public void setMoveIntent(double worldX, double worldZ) {
        this.moveX = worldX;
        this.moveZ = worldZ;
    }

    @Override
    public void travel(Vec3 travelVector) {
        double yMotion = this.onGround() ? GROUNDED_Y_MOTION : Math.max(this.getDeltaMovement().y - GRAVITY_PER_TICK, TERMINAL_FALL_SPEED);
        Vec3 desired = new Vec3(this.moveX, yMotion, this.moveZ);
        Vec3 before = this.position();
        this.setDeltaMovement(desired);
        this.move(MoverType.SELF, desired);
        if (!this.level().isClientSide) {
            LOGGER.info("Ligabis debug: golem travel moveX={} moveZ={} onGround={} before={} after={} horizontalCollision={}",
                    this.moveX, this.moveZ, this.onGround(), before, this.position(), this.horizontalCollision);
        }
        this.calculateEntityAnimation(desired.horizontalDistanceSqr() > 1.0E-7D);
    }

    /**
     * {@code Mob#isEffectiveAi()} (and so {@code isControlledByLocalInstance()}) is false whenever
     * {@code isNoAi()} is true. {@link #travel(Vec3)} no longer reads that flag, but other vanilla code
     * (fluid pushing, other entities' collision response) still does, and treating this golem as "remote"
     * there would be equally wrong for the same reason it was for movement.
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BLOCK_ID, "minecraft:dirt");
    }

    public void setBlockId(String registryName) {
        this.entityData.set(BLOCK_ID, registryName);
    }

    public String getBlockId() {
        return this.entityData.get(BLOCK_ID);
    }
}

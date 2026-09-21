package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A lightning bolt that can point anywhere: it starts at the entity's position and runs to a
 * target given as an offset. Unlike the vanilla bolt, which only falls from the sky, this one is
 * drawn along whatever line joins the two points. It only exists to be seen; damage and sound are
 * handled by whoever spawns it.
 */
public class ArcBoltEntity extends Entity {

    /** How long the bolt stays visible, in ticks. */
    public static final int LIFETIME_TICKS = 8;

    private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(ArcBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(ArcBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(ArcBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(ArcBoltEntity.class, EntityDataSerializers.INT);

    public ArcBoltEntity(EntityType<? extends ArcBoltEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Creates a bolt from {@code start} to {@code end}; the caller still has to add it to the level. */
    public static ArcBoltEntity create(ServerLevel level, Vec3 start, Vec3 end) {
        ArcBoltEntity bolt = new ArcBoltEntity(ExampleMod.ARC_BOLT.get(), level);
        bolt.setPos(start);
        Vec3 offset = end.subtract(start);
        bolt.entityData.set(END_X, (float) offset.x);
        bolt.entityData.set(END_Y, (float) offset.y);
        bolt.entityData.set(END_Z, (float) offset.z);
        bolt.entityData.set(SEED, level.random.nextInt());
        return bolt;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(END_X, 0.0F);
        entityData.define(END_Y, 0.0F);
        entityData.define(END_Z, 0.0F);
        entityData.define(SEED, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFETIME_TICKS) {
            discard();
        }
    }

    /** Vector from the start (the entity's position) to the far end of the bolt. */
    public Vec3 offsetToEnd() {
        return new Vec3(entityData.get(END_X), entityData.get(END_Y), entityData.get(END_Z));
    }

    /** Seed that fixes the zigzag, so every client draws the same bolt. */
    public int seed() {
        return entityData.get(SEED);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

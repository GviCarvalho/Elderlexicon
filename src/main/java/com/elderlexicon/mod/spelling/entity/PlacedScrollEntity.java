package com.elderlexicon.mod.spelling.entity;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class PlacedScrollEntity extends Entity {

    private static final EntityDataAccessor<ItemStack> SCROLL = SynchedEntityData.defineId(PlacedScrollEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(PlacedScrollEntity.class, EntityDataSerializers.INT);
    private BlockPos supportPos = BlockPos.ZERO;

    public PlacedScrollEntity(EntityType<? extends PlacedScrollEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public PlacedScrollEntity(Level level, BlockPos supportPos, Direction face, ItemStack scroll) {
        this(ExampleMod.PLACED_SCROLL.get(), level);
        this.supportPos = supportPos == null ? BlockPos.ZERO : supportPos.immutable();
        setFace(face);
        setScroll(scroll);
        setPos(positionFor(this.supportPos, getFace()));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SCROLL, ItemStack.EMPTY);
        entityData.define(FACE, Direction.NORTH.get3DDataValue());
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide && !isSupported()) {
            dropScroll();
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player) {
            return hurt(damageSources().playerAttack(player), 1.0F);
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return true;
        }
        dropScroll();
        discard();
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Scroll", getScroll().save(new CompoundTag()));
        tag.putString("Face", getFace().getName());
        tag.putLong("SupportPos", supportPos.asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setScroll(ItemStack.of(tag.getCompound("Scroll")));
        setFace(Direction.byName(tag.getString("Face")));
        if (tag.contains("SupportPos")) {
            supportPos = BlockPos.of(tag.getLong("SupportPos"));
        }
        setPos(positionFor(supportPos, getFace()));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public ItemStack getScroll() {
        return entityData.get(SCROLL);
    }

    public Direction getFace() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    public BlockPos supportPos() {
        return supportPos;
    }

    public void setScroll(ItemStack stack) {
        ItemStack stored = stack == null ? ItemStack.EMPTY : stack.copy();
        stored.setCount(Math.min(1, stored.getCount()));
        entityData.set(SCROLL, stored);
    }

    private void setFace(Direction face) {
        Direction safeFace = face == null ? Direction.NORTH : face;
        entityData.set(FACE, safeFace.get3DDataValue());
    }

    private boolean isSupported() {
        return supportPos != null && level().isLoaded(supportPos) && !level().getBlockState(supportPos).isAir();
    }

    private void dropScroll() {
        if (!level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            return;
        }
        ItemStack stack = getScroll();
        if (!stack.isEmpty()) {
            spawnAtLocation(stack.copy());
        }
    }

    private static Vec3 positionFor(BlockPos supportPos, Direction face) {
        Direction safeFace = face == null ? Direction.NORTH : face;
        Vec3 center = Vec3.atCenterOf(supportPos == null ? BlockPos.ZERO : supportPos);
        return center.add(Vec3.atLowerCornerOf(safeFace.getNormal()).scale(0.505D));
    }
}

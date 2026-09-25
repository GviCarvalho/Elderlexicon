package com.elderlexicon.mod.spell.function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Moves marked things: teleports and throws entities, and carries whole blocks (a chest keeps what it
 * holds, and every block keeps its mark).
 */
final class MarkMotion {

    /** How far around the wanted spot to look for room, in blocks. */
    private static final int SEARCH_RADIUS = 2;
    private static final int SEARCH_HEIGHT = 3;

    private MarkMotion() {
    }

    /** A block taken out of the world: its state, what its block entity held, and its mark. */
    record LiftedBlock(BlockState state, @Nullable CompoundTag data, String mark) { }

    /** Where {@code entity} fits at or near {@code wanted}: the exact spot if free, else the closest free block spot. */
    static Optional<Vec3> freeSpotFor(Entity entity, ServerLevel level, Vec3 wanted) {
        if (fits(entity, level, wanted)) {
            return Optional.of(wanted);
        }
        BlockPos base = BlockPos.containing(wanted);
        for (int dy = 0; dy <= SEARCH_HEIGHT; dy++) {
            for (int ring = 0; ring <= SEARCH_RADIUS; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        Vec3 candidate = Vec3.atBottomCenterOf(base.offset(dx, dy, dz));
                        if (fits(entity, level, candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean fits(Entity entity, ServerLevel level, Vec3 feet) {
        if (!level.isInWorldBounds(BlockPos.containing(feet))) {
            return false;
        }
        AABB moved = entity.getBoundingBox().move(feet.subtract(entity.position()));
        return level.noCollision(entity, moved);
    }

    /** A spot at or near {@code wanted} where a block can go (air, water, grass...). */
    static Optional<BlockPos> freeBlockSpot(ServerLevel level, BlockPos wanted) {
        for (int dy = 0; dy <= SEARCH_HEIGHT; dy++) {
            for (int ring = 0; ring <= SEARCH_RADIUS; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        BlockPos candidate = wanted.offset(dx, dy, dz);
                        if (level.isInWorldBounds(candidate) && level.getBlockState(candidate).canBeReplaced()) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static void teleport(Entity entity, Vec3 feet) {
        if (entity.isPassenger()) {
            entity.stopRiding();
        }
        Vec3 from = entity.position();
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(player.serverLevel(), feet.x, feet.y, feet.z, player.getYRot(), player.getXRot());
        } else {
            entity.teleportTo(feet.x, feet.y, feet.z);
        }
        entity.resetFallDistance();
        ServerLevel level = (ServerLevel) entity.level();
        flash(level, from);
        flash(level, feet);
    }

    static void launch(Entity entity, Vec3 velocity) {
        if (entity.isPassenger()) {
            entity.stopRiding();
        }
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true; // makes the server send the new motion, players included
    }

    /**
     * Takes a block out of the world without spilling what it holds. It is unmarked first, so Ligabis
     * does not take the move for the block being destroyed.
     */
    static LiftedBlock lift(ServerLevel level, BlockPos pos, String mark) {
        BlockState state = level.getBlockState(pos);
        MarkHelper.applyMark(level, pos, null);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag data = null;
        if (blockEntity != null) {
            data = blockEntity.saveWithoutMetadata();
            Clearable.tryClear(blockEntity);
        }
        level.removeBlock(pos, false);
        flash(level, Vec3.atCenterOf(pos));
        return new LiftedBlock(state, data, mark);
    }

    static void place(ServerLevel level, BlockPos pos, LiftedBlock block) {
        level.setBlock(pos, block.state(), 3);
        if (block.data() != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.load(block.data());
                blockEntity.setChanged();
            }
        }
        if (block.mark() != null && !block.mark().isEmpty()) {
            MarkHelper.applyMark(level, pos, block.mark());
        }
        flash(level, Vec3.atCenterOf(pos));
    }

    /**
     * Throws a block: it flies as a falling block holding its contents and, where it lands, becomes a block
     * again and gets its mark back ({@code LigabisManager.entityLeft}).
     */
    static FallingBlockEntity launchBlock(ServerLevel level, BlockPos pos, String mark, Vec3 velocity) {
        BlockState state = level.getBlockState(pos);
        MarkHelper.applyMark(level, pos, null);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag data = null;
        if (blockEntity != null) {
            data = blockEntity.saveWithoutMetadata();
            Clearable.tryClear(blockEntity);
        }
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        falling.blockData = data;
        MarkHelper.applyMark(falling, mark);
        launch(falling, velocity);
        return falling;
    }

    static void flash(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.5D, at.z, 24, 0.3D, 0.5D, 0.3D, 0.2D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.2F);
    }
}

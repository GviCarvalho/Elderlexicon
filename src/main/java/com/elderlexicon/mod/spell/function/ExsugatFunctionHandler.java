package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ExsugatFunctionHandler implements SpellFunctionHandler {

    private static final int RANGE = 5;
    private static final double EPSILON = 1.0E-4D;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        double needed = context.payableCost();
        if (needed <= EPSILON) {
            needed = defaultDrainAmount(element);
        }
        double gained = switch (element) {
            case IGNI -> drainIgnis(context, needed);
            case AQUA -> drainAqua(context, needed);
            case FIRMO -> drainFirmo(context, needed);
            default -> 0.0D;
        };
        if (gained > EPSILON) {
            context.addAmbientEnergy(element, gained);
        }
    }

    private double drainIgnis(SpellContext context, double limit) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        return drainSphere(level, player, player.position(), limit, VitaElement.IGNI,
                (pos, remaining) -> drainIgnisAt(level, pos, remaining));
    }

    private double drainAqua(SpellContext context, double limit) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        return drainSphere(level, player, player.position(), limit, VitaElement.AQUA,
                (pos, remaining) -> drainAquaAt(level, pos, remaining));
    }

    private double drainFirmo(SpellContext context, double limit) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        return drainSphere(level, player, player.position(), limit, VitaElement.FIRMO,
                (pos, remaining) -> drainFirmoAt(level, pos, remaining));
    }

    private double drainSphere(ServerLevel level, ServerPlayer player, Vec3 origin,
                               double limit, VitaElement element, BlockDrain consumer) {
        if (limit <= EPSILON) {
            return 0.0D;
        }
        BlockPos center = BlockPos.containing(origin);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double rangeSq = RANGE * RANGE + 1.0D;
        double total = 0.0D;
        outer:
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dy = -RANGE; dy <= RANGE; dy++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    if (limit - total <= EPSILON) {
                        break outer;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Vec3 candidate = Vec3.atCenterOf(cursor);
                    if (origin.distanceToSqr(candidate) > rangeSq) {
                        continue;
                    }
                    double value = consumer.drain(cursor.immutable(), limit - total);
                    if (value <= EPSILON) {
                        continue;
                    }
                    emitDrainParticles(player, element, cursor.immutable(), value);
                    total += value;
                }
            }
        }
        return Math.min(total, limit);
    }

    private double drainIgnisAt(ServerLevel level, BlockPos pos, double remaining) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        double value = 1.0D;
        if (remaining + EPSILON < value) {
            return 0.0D;
        }
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            level.removeBlock(pos, false);
            return value;
        }
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            level.destroyBlock(pos, false);
            return value;
        }
        if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
            level.destroyBlock(pos, false);
            return value;
        }
        if (block instanceof CampfireBlock) {
            if (state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
                return value;
            }
            return 0.0D;
        }
        if (block instanceof AbstractFurnaceBlock) {
            if (state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT)) {
                BlockState unlit = state.setValue(AbstractFurnaceBlock.LIT, false);
                level.setBlock(pos, unlit, Block.UPDATE_ALL);
                return value;
            }
            return 0.0D;
        }
        return 0.0D;
    }

    private double drainAquaAt(ServerLevel level, BlockPos pos, double remaining) {
        BlockState state = level.getBlockState(pos);
        double value;
        if (state.getFluidState().isSource() && state.getFluidState().is(FluidTags.WATER)) {
            value = 3.0D;
            if (remaining + EPSILON < value) {
                return 0.0D;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return value;
        }
        if (state.is(Blocks.WATER_CAULDRON)) {
            value = 3.0D;
            if (remaining + EPSILON < value) {
                return 0.0D;
            }
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
            return value;
        }
        if (state.is(Blocks.FARMLAND) && state.hasProperty(FarmBlock.MOISTURE)) {
            int moisture = state.getValue(FarmBlock.MOISTURE);
            if (moisture > 0) {
                value = 1.0D;
                if (remaining + EPSILON < value) {
                    return 0.0D;
                }
                level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 0), Block.UPDATE_ALL);
                return value;
            }
        }
        return 0.0D;
    }

    private double drainFirmoAt(ServerLevel level, BlockPos pos, double remaining) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 0.0D;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return 0.0D;
        }
        if (state.is(Blocks.BEDROCK)) {
            return 0.0D;
        }
        float resistance = state.getBlock().getExplosionResistance();
        if (Float.isInfinite(resistance)) {
            return 0.0D;
        }
        double value = resistance / 10.0D;
        if (value <= EPSILON) {
            return 0.0D;
        }
        if (remaining + EPSILON < value) {
            return 0.0D;
        }
        level.destroyBlock(pos, false);
        return value;
    }

    @FunctionalInterface
    private interface BlockDrain {
        double drain(BlockPos pos, double remaining);
    }

    private double defaultDrainAmount(VitaElement element) {
        if (element == null || element.isBalanced()) {
            return 1.0D;
        }
        return 1.0D;
    }

    private void emitDrainParticles(ServerPlayer player, VitaElement element, BlockPos source, double amount) {
        if (player == null || amount <= EPSILON) {
            return;
        }
        SpellEffects.spawnDrainParticles(player, element, source, amount);
    }
}

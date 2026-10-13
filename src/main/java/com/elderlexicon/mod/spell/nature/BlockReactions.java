package com.elderlexicon.mod.spell.nature;

import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Optional;

/**
 * What the drives do to the blocks they cannot hold (docs/particulas-design.md): a campfire, a furnace, a cauldron are
 * walls to the field, but the world is not. Heat enough to make a flame kindles what can be kindled beside it: a
 * campfire, candles, a furnace (which then burns a while), TNT. Water, lava or snow that comes to a cauldron, or is poured
 * into it, fills it and is spent; water poured on a burning campfire puts it out.
 */
public final class BlockReactions {

    /** How long heat that kindles a furnace keeps it burning, in ticks: as a stick of wood would. */
    private static final int FURNACE_TICKS = 100;

    private BlockReactions() {
    }

    // ------------------------------------------------------------------ heat

    /** The air at {@code hot} is a flame (or hotter): what beside it can catch, catches. */
    static void flameAt(ServerLevel level, BlockPos hot) {
        for (Direction side : Direction.values()) {
            kindle(level, hot.relative(side));
        }
    }

    /** Kindles the block at {@code pos}, if it is something heat kindles; true when it did. */
    public static boolean kindle(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CampfireBlock && !state.getValue(CampfireBlock.LIT)
                && !state.getValue(CampfireBlock.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
            return true;
        }
        if (block instanceof AbstractCandleBlock && state.hasProperty(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.LIT)
                && !(state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED))) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
            return true;
        }
        if (block instanceof AbstractFurnaceBlock && !state.getValue(AbstractFurnaceBlock.LIT)) {
            return kindleFurnace(level, pos, state);
        }
        if (block instanceof TntBlock) {
            TntBlock.explode(level, pos);
            level.removeBlock(pos, false);
            return true;
        }
        return false;
    }

    /** A furnace kindled: it burns a while, as on a stick of wood, whatever is in it. */
    private static boolean kindleFurnace(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return false;
        }
        CompoundTag tag = furnace.saveWithoutMetadata();
        tag.putShort("BurnTime", (short) Math.max(FURNACE_TICKS, tag.getShort("BurnTime")));
        furnace.load(tag);
        furnace.setChanged();
        level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_ALL);
        return true;
    }

    // ------------------------------------------------------------------ what falls into a cauldron

    /**
     * {@code matter} is let into the world at {@code at}: when it is water, lava or snow and a cauldron is there or just
     * under, it goes into the cauldron instead (true; the matter is spent). Water on a burning campfire puts it out first,
     * and goes on its way.
     */
    public static boolean pour(ServerLevel level, BlockPos at, Matter matter) {
        Optional<BlockState> shown = WorldMatter.blockOf(matter);
        if (shown.isEmpty()) {
            return false;
        }
        BlockState as = shown.get();
        if (as.is(Blocks.WATER)) {
            douse(level, at);
            douse(level, at.below());
        }
        return fill(level, at, as) || fill(level, at.below(), as);
    }

    /** A block the drives show at {@code pos} came to rest on a cauldron: it falls in. True when it did. */
    static boolean settled(ServerLevel level, BlockPos pos, BlockState block) {
        if (!fill(level, pos.below(), block)) {
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private static void douse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            CampfireBlock.dowse(null, level, pos, state);
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
        }
    }

    /** Fills the cauldron at {@code pos} with what {@code as} is (water, lava, snow); true when it took it. */
    private static boolean fill(ServerLevel level, BlockPos pos, BlockState as) {
        BlockState cauldron = level.getBlockState(pos);
        boolean water = as.is(Blocks.WATER);
        boolean lava = as.is(Blocks.LAVA);
        boolean snow = as.is(Blocks.SNOW_BLOCK) || as.is(Blocks.POWDER_SNOW) || as.is(Blocks.SNOW);
        if (!water && !lava && !snow) {
            return false;
        }
        BlockState filled;
        if (cauldron.is(Blocks.CAULDRON)) {
            filled = lava ? Blocks.LAVA_CAULDRON.defaultBlockState()
                    : (water ? Blocks.WATER_CAULDRON : Blocks.POWDER_SNOW_CAULDRON).defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, 1);
        } else if (water && cauldron.is(Blocks.WATER_CAULDRON) || snow && cauldron.is(Blocks.POWDER_SNOW_CAULDRON)) {
            int now = cauldron.getValue(LayeredCauldronBlock.LEVEL);
            if (now >= LayeredCauldronBlock.MAX_FILL_LEVEL) {
                return false;
            }
            filled = cauldron.setValue(LayeredCauldronBlock.LEVEL, now + 1);
        } else {
            return false;
        }
        level.setBlock(pos, filled, Block.UPDATE_ALL);
        level.playSound(null, pos, lava ? SoundEvents.BUCKET_EMPTY_LAVA
                : snow ? SoundEvents.BUCKET_EMPTY_POWDER_SNOW : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}

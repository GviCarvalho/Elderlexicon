package com.elderlexicon.mod.ligabis.world.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * The golem-ritual shape: a two-block stem, a three-block arm row on top of it, and a head block on top
 * of the row's center — the same silhouette as building an iron golem, but with any single block used
 * throughout, and checked only at the moment a mage casts an aura link at it (no passive world scanning).
 */
public final class GolemStructure {

    private GolemStructure() {
    }

    public record Found(Block block, Direction.Axis axis) {
    }

    /** Every position of the shape, relative to the head, for a given arm orientation. */
    public static List<BlockPos> offsets(Direction.Axis axis) {
        Direction side = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos head = BlockPos.ZERO;
        BlockPos armRow = head.below();
        return List.of(head, armRow, armRow.relative(side), armRow.relative(side.getOpposite()),
                armRow.below(), armRow.below(2));
    }

    /** Where the golem's feet land, relative to the head, once the structure is consumed. */
    public static BlockPos feetOffset() {
        return new BlockPos(0, -3, 0);
    }

    /**
     * Checks whether {@code head} is the top block of a valid structure, in either horizontal orientation.
     */
    public static Optional<Found> find(BlockGetter level, BlockPos head) {
        BlockState headState = level.getBlockState(head);
        if (headState.isAir()) {
            return Optional.empty();
        }
        Block block = headState.getBlock();
        for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
            boolean matches = true;
            for (BlockPos offset : offsets(axis)) {
                if (!level.getBlockState(head.offset(offset)).is(block)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return Optional.of(new Found(block, axis));
            }
        }
        return Optional.empty();
    }
}

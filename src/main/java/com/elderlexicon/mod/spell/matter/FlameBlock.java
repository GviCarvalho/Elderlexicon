package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A flame: air agitated until it glows (docs/particulas-design.md, section 2). It is not a thing that spreads by
 * itself, as the game's fire does: it burns what stands in it and gives light while the drives keep the air there that
 * hot, and goes out when they no longer do. What it lights, it lights through them.
 */
public final class FlameBlock extends BaseFireBlock {

    /** How often a flame looks whether the air it is still glows. */
    private static final int LOOK_TICKS = 10;

    public FlameBlock(Properties properties) {
        super(properties, 1.0F);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide() && level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, LOOK_TICKS);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!NatureWorld.flameAt(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, LOOK_TICKS);
    }
}

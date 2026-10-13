package com.elderlexicon.mod.caster;

import com.elderlexicon.mod.galdraria.Galdraria;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * A block of the craft a caster goes to at peace (docs/mago-design.md, 5c): the shelves to read in, the enchanting
 * table to gather Vis at, the brewing stand to tend while it heals, the galdraria table to engrave at.
 */
enum Station {
    /** A bookshelf or a lectern: it reads what others wrote, sentences it can read; the more shelves, the more. */
    SHELF(200),
    /** The enchanting table: it gathers Vis there, faster with the shelves around it. */
    ALTAR(200),
    /** The brewing stand: it tends it, and heals while it does. */
    BREW(160),
    /** The galdraria table: it engraves a page of its own into a wand. */
    BENCH(160);

    /** How long the work takes, once it is there. */
    final int ticks;

    Station(int ticks) {
        this.ticks = ticks;
    }

    /** The station a block is, if it is one. */
    static Optional<Station> of(BlockState state) {
        if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF) || state.is(Blocks.LECTERN)) {
            return Optional.of(SHELF);
        }
        if (state.is(Blocks.ENCHANTING_TABLE)) {
            return Optional.of(ALTAR);
        }
        if (state.is(Blocks.BREWING_STAND)) {
            return Optional.of(BREW);
        }
        if (state.is(Galdraria.TABLE.get())) {
            return Optional.of(BENCH);
        }
        return Optional.empty();
    }

    /** How many books stand around {@code pos} (as an enchanting table counts them), at most fifteen. */
    static int shelvesAround(Level level, BlockPos pos) {
        float power = 0.0F;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 2, 3))) {
            BlockState state = level.getBlockState(near);
            if (!state.isAir()) {
                power += state.getEnchantPowerBonus(level, near);
            }
            if (power >= 15.0F) {
                return 15;
            }
        }
        return (int) power;
    }
}

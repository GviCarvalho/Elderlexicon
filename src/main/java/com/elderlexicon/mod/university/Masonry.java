package com.elderlexicon.mod.university;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Supplier;

/**
 * The mason of a university (docs/mago-design.md, 5g): places blocks by their offset from the middle of the grounds,
 * only inside the chunk being made, and knows the university's stone. What it draws by chance is drawn by the
 * position, so the chunks agree.
 */
final class Masonry {

    final WorldGenLevel level;
    final BoundingBox chunk;
    private final int cx;
    private final int y0;
    private final int cz;

    Masonry(WorldGenLevel level, BoundingBox chunk, int cx, int y0, int cz) {
        this.level = level;
        this.chunk = chunk;
        this.cx = cx;
        this.y0 = y0;
        this.cz = cz;
    }

    BlockPos at(int x, int y, int z) {
        return new BlockPos(cx + x, y0 + y, cz + z);
    }

    /** Whether the chunk being made reaches into the box between the two offsets. */
    boolean touches(int x0, int y0, int z0, int x1, int y1, int z1) {
        return chunk.intersects(cx + Math.min(x0, x1), cz + Math.min(z0, z1), cx + Math.max(x0, x1),
                cz + Math.max(z0, z1)) && this.y0 + Math.max(y0, y1) >= chunk.minY()
                && this.y0 + Math.min(y0, y1) <= chunk.maxY();
    }

    boolean set(int x, int y, int z, BlockState state) {
        BlockPos pos = at(x, y, z);
        if (!chunk.isInside(pos)) {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        if (joins(state)) {
            joining.add(pos.immutable());
        }
        return true;
    }

    /** The blocks that join their neighbours (fences, panes, bars, walls), placed in this chunk. */
    private final java.util.List<BlockPos> joining = new java.util.ArrayList<>();

    private static boolean joins(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallBlock;
    }

    /**
     * Joins every fence, pane, bar and wall placed to what stands beside it, now that everything is placed: placed one
     * by one, each was joined to nothing.
     */
    void join() {
        for (BlockPos pos : joining) {
            BlockState state = level.getBlockState(pos);
            if (joins(state)) {
                level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_CLIENTS);
            }
        }
        joining.clear();
    }

    /** Fills a box, only where it is inside the chunk. */
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int ax = Math.max(cx + Math.min(x0, x1), chunk.minX());
        int bx = Math.min(cx + Math.max(x0, x1), chunk.maxX());
        int az = Math.max(cz + Math.min(z0, z1), chunk.minZ());
        int bz = Math.min(cz + Math.max(z0, z1), chunk.maxZ());
        int ay = Math.max(this.y0 + Math.min(y0, y1), chunk.minY());
        int by = Math.min(this.y0 + Math.max(y0, y1), chunk.maxY());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                for (int y = ay; y <= by; y++) {
                    level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    BlockState get(int x, int y, int z) {
        BlockPos pos = at(x, y, z);
        return chunk.isInside(pos) ? level.getBlockState(pos) : Blocks.STONE.defaultBlockState();
    }

    boolean solid(int x, int y, int z) {
        BlockState state = get(x, y, z);
        return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** A number from 0 to 99, the same for a place in every chunk. */
    int chance(int x, int y, int z) {
        return Math.floorMod(Mth.getSeed(cx + x, y0 + y, cz + z), 100);
    }

    /** The walls' stone: now and then mossy or cracked. */
    BlockState stone(int x, int y, int z) {
        int roll = chance(x, y, z);
        return (roll < 8 ? Blocks.MOSSY_STONE_BRICKS : roll < 13 ? Blocks.CRACKED_STONE_BRICKS
                : Blocks.STONE_BRICKS).defaultBlockState();
    }

    static BlockState stairs(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    static BlockState stairsUpsideDown(Block block, Direction facing) {
        return stairs(block, facing).setValue(StairBlock.HALF, Half.TOP);
    }

    /** A door opening to {@code outside}, two blocks tall; {@code right} for the right leaf of a pair. */
    void door(int x, int z, Direction outside, Block door, boolean right) {
        BlockState state = door.defaultBlockState().setValue(DoorBlock.FACING, outside)
                .setValue(DoorBlock.HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        set(x, 1, z, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, 2, z, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A lantern hung on a chain {@code length} long from the ceiling at {@code ceiling}. */
    void chandelier(int x, int ceiling, int z, int length) {
        for (int y = ceiling - 1; y > ceiling - length; y--) {
            set(x, y, z, Blocks.CHAIN.defaultBlockState());
        }
        set(x, ceiling - length, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    void chest(int x, int y, int z, Direction facing, String loot) {
        if (!set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing))) {
            return;
        }
        if (level.getBlockEntity(at(x, y, z)) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(new ResourceLocation(ElderLexicon.MODID, loot), Mth.getSeed(at(x, y, z)));
        }
    }

    void lectern(int x, int y, int z, Direction facing, Supplier<ItemStack> book) {
        BlockState lectern = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing);
        BlockPos pos = at(x, y, z);
        if (!chunk.isInside(pos)) {
            return;
        }
        ItemStack stack = book.get();
        level.setBlock(pos, lectern.setValue(LecternBlock.HAS_BOOK, !stack.isEmpty()), Block.UPDATE_CLIENTS);
        if (!stack.isEmpty() && level.getBlockEntity(pos) instanceof LecternBlockEntity entity) {
            entity.setBook(stack);
        }
    }

    /**
     * A cone of {@code block}, round, from {@code radius} at {@code y} to a point, a layer every {@code step}; where a
     * layer's edge is left open to the sky, it is of {@code edge} (a slab: nothing is born on a slab).
     */
    void cone(int x, int y, int z, int radius, int step, BlockState block, BlockState edge) {
        int layer = 0;
        for (int r = radius; r >= 0; r--) {
            for (int s = 0; s < step; s++) {
                ring(x, y + layer, z, r, block, true);
                if (s == step - 1) {
                    if (r == 0) {
                        set(x, y + layer, z, edge);
                    } else {
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                double d = Math.sqrt(dx * dx + dz * dz);
                                if (d <= r + 0.5D && d > r - 0.5D) {
                                    set(x + dx, y + layer, z + dz, edge);
                                }
                            }
                        }
                    }
                }
                layer++;
            }
        }
    }

    /** A disc ({@code filled}) or a ring of {@code radius} at {@code y}. */
    void ring(int x, int y, int z, int radius, BlockState block, boolean filled) {
        if (!touches(x - radius, y, z - radius, x + radius, y, z + radius)) {
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= radius + 0.5D && (filled || d > radius - 0.5D)) {
                    set(x + dx, y, z + dz, block);
                }
            }
        }
    }
}

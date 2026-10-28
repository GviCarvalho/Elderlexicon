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
 * The mason of a university (docs/mago-design.md, 5g; docs/universidade-arquitetura-design.md): places blocks by their
 * offset from the middle of the grounds, only inside the chunk being made, and knows the university's stone. What it
 * draws by chance is drawn by the position, so the chunks agree. The campus is turned as a whole ({@code rotation},
 * every block's facing with it), and a building may be drawn shifted from where its drawing puts it.
 */
final class Masonry {

    final WorldGenLevel level;
    final BoundingBox chunk;
    private final int cx;
    private final int y0;
    private final int cz;
    private final net.minecraft.world.level.block.Rotation rotation;
    private final int ox;
    private final int oz;

    Masonry(WorldGenLevel level, BoundingBox chunk, int cx, int y0, int cz) {
        this(level, chunk, cx, y0, cz, net.minecraft.world.level.block.Rotation.NONE, 0, 0,
                new java.util.ArrayList<>());
    }

    Masonry(WorldGenLevel level, BoundingBox chunk, int cx, int y0, int cz,
            net.minecraft.world.level.block.Rotation rotation) {
        this(level, chunk, cx, y0, cz, rotation, 0, 0, new java.util.ArrayList<>());
    }

    private Masonry(WorldGenLevel level, BoundingBox chunk, int cx, int y0, int cz,
                    net.minecraft.world.level.block.Rotation rotation, int ox, int oz,
                    java.util.List<BlockPos> joining) {
        this.level = level;
        this.chunk = chunk;
        this.cx = cx;
        this.y0 = y0;
        this.cz = cz;
        this.rotation = rotation;
        this.ox = ox;
        this.oz = oz;
        this.joining = joining;
    }

    /** The same mason, drawing what is drawn at {@code (x, z)} at {@code (x + dx, z + dz)}. */
    Masonry shifted(int dx, int dz) {
        return new Masonry(level, chunk, cx, y0, cz, rotation, ox + dx, oz + dz, joining);
    }

    /** The offset {@code (x, z)} of the campus, turned as the campus is. */
    static int[] turn(net.minecraft.world.level.block.Rotation rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> new int[]{x, z};
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
        };
    }

    /** {@code facing} as it lies in the world, the campus turned. */
    Direction turn(Direction facing) {
        return rotation.rotate(facing);
    }

    /**
     * Three letters and figures that are this house's alone, from where its middle is: written after the marks it
     * lays (a1_k3f), so two universities in one world never answer to one mark. The same in every chunk.
     */
    String houseCode() {
        int code = Math.floorMod(cx * 73856093 ^ cz * 19349663, 36 * 36 * 36);
        StringBuilder text = new StringBuilder(Integer.toString(code, 36));
        while (text.length() < 3) {
            text.insert(0, '0');
        }
        // Never a code of figures alone, which a spell would read as a number.
        if (Character.isDigit(text.charAt(0))) {
            text.setCharAt(0, (char) ('a' + (text.charAt(0) - '0')));
        }
        return text.toString();
    }

    BlockPos at(int x, int y, int z) {
        int[] turned = turn(rotation, x + ox, z + oz);
        return new BlockPos(cx + turned[0], y0 + y, cz + turned[1]);
    }

    /** Whether the chunk being made reaches into the box between the two offsets. */
    boolean touches(int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockPos a = at(x0, 0, z0);
        BlockPos b = at(x1, 0, z1);
        return chunk.intersects(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(),
                b.getX()), Math.max(a.getZ(), b.getZ())) && this.y0 + Math.max(y0, y1) >= chunk.minY()
                && this.y0 + Math.min(y0, y1) <= chunk.maxY();
    }

    boolean set(int x, int y, int z, BlockState state) {
        BlockPos pos = at(x, y, z);
        if (!chunk.isInside(pos)) {
            return false;
        }
        state = state.rotate(rotation);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        if (joins(state)) {
            joining.add(pos.immutable());
        }
        return true;
    }

    /** The blocks that join their neighbours (fences, panes, bars, walls), placed in this chunk. */
    private final java.util.List<BlockPos> joining;

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
        BlockPos a = at(x0, 0, z0);
        BlockPos b = at(x1, 0, z1);
        state = state.rotate(rotation);
        int ax = Math.max(Math.min(a.getX(), b.getX()), chunk.minX());
        int bx = Math.min(Math.max(a.getX(), b.getX()), chunk.maxX());
        int az = Math.max(Math.min(a.getZ(), b.getZ()), chunk.minZ());
        int bz = Math.min(Math.max(a.getZ(), b.getZ()), chunk.maxZ());
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
        return Math.floorMod(Mth.getSeed(at(x, y, z)), 100);
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

    /** {@code state}, a container, at {@code (x, y, z)}, filled from the loot table {@code loot} of the mod. */
    void container(int x, int y, int z, BlockState state, String loot) {
        if (!set(x, y, z, state)) {
            return;
        }
        if (level.getBlockEntity(at(x, y, z)) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(new ResourceLocation(ElderLexicon.MODID, loot), Mth.getSeed(at(x, y, z)));
        }
    }

    /** Suspicious sand at {@code (x, y, z)}, holding what the archaeology table {@code loot} of the game gives. */
    void suspicious(int x, int y, int z, String loot) {
        if (!set(x, y, z, Blocks.SUSPICIOUS_SAND.defaultBlockState())) {
            return;
        }
        if (level.getBlockEntity(at(x, y, z))
                instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity brushable) {
            brushable.setLootTable(new ResourceLocation(loot), Mth.getSeed(at(x, y, z)));
        }
    }

    /** A spring of water at {@code (x, y, z)} that runs once the world is made: its flow is set going. */
    void spring(int x, int y, int z) {
        BlockPos pos = at(x, y, z);
        if (set(x, y, z, Blocks.WATER.defaultBlockState())) {
            level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, 1);
        }
    }

    /** The offset of the world's height {@code worldY} from the ground this is drawn at. */
    int toWorld(int worldY) {
        return worldY - y0;
    }

        void lectern(int x, int y, int z, Direction facing, Supplier<ItemStack> book) {
        BlockState lectern = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing);
        BlockPos pos = at(x, y, z);
        if (!chunk.isInside(pos)) {
            return;
        }
        ItemStack stack = book.get();
        level.setBlock(pos, lectern.setValue(LecternBlock.HAS_BOOK, !stack.isEmpty()).rotate(rotation),
                Block.UPDATE_CLIENTS);
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

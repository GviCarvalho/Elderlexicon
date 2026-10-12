package com.elderlexicon.mod.nahva;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The realm of {@code igni} (docs/aprendizado-runas-design.md): the idea of heat. A sea of lava under a chain of burning
 * islands, and air that sets the dreamer alight. Cold is the way through: it waits in a chest beside where the dreamer
 * appears, and holding it puts the fire out. At the far end of the chain is the rift, and going through it is waking up
 * having learned the rune.
 */
final class IgniRealm {

    /** Where the islands float, above the sea. */
    private static final int ISLAND_Y = 80;
    private static final int SEA_Y = 59;
    /** How far the rift is from where the dreamer appears, along x. */
    static final int LENGTH = 270;
    private static final int SEA_HALF_WIDTH = 16;
    private static final int[] ISLANDS_X = {0, 40, 80, 120, 160, 200, 240, LENGTH};

    private IgniRealm() {
    }

    /** Lays the realm out at {@code origin}, anew over what was there, and says where the dreamer and the rift are. */
    static Realms.Built build(ServerLevel level, BlockPos origin) {
        RandomSource random = RandomSource.create(origin.asLong());
        sea(level, origin);
        BlockPos chest = null;
        for (int i = 0; i < ISLANDS_X.length; i++) {
            boolean first = i == 0;
            boolean last = i == ISLANDS_X.length - 1;
            int z = first || last ? 0 : (i % 2 == 0 ? 7 : -7);
            int y = ISLAND_Y + (first || last ? 0 : random.nextInt(7) - 3);
            BlockPos center = origin.offset(ISLANDS_X[i], y, z);
            island(level, center, first || last ? 5 : 3, !first && !last, random);
            if (first) {
                chest = center.offset(2, 1, 2);
            }
        }
        if (chest != null) {
            fillChest(level, chest);
        }
        Vec3 rift = rift(level, origin.offset(LENGTH, ISLAND_Y + 4, 0));
        return new Realms.Built(Vec3.atBottomCenterOf(origin.offset(0, ISLAND_Y + 1, 0)), rift);
    }

    /** A sea of lava under the whole chain, on a floor of basalt, held in by a rim of blackstone. */
    private static void sea(ServerLevel level, BlockPos origin) {
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState floor = Blocks.BASALT.defaultBlockState();
        BlockState rim = Blocks.BLACKSTONE.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -SEA_HALF_WIDTH; x <= LENGTH + SEA_HALF_WIDTH; x++) {
            for (int z = -SEA_HALF_WIDTH; z <= SEA_HALF_WIDTH; z++) {
                boolean edge = x == -SEA_HALF_WIDTH || x == LENGTH + SEA_HALF_WIDTH
                        || Math.abs(z) == SEA_HALF_WIDTH;
                level.setBlock(pos.set(origin.getX() + x, SEA_Y - 1, origin.getZ() + z), floor, 2);
                level.setBlock(pos.set(origin.getX() + x, SEA_Y, origin.getZ() + z), edge ? rim : lava, 2);
            }
        }
    }

    /** A round island of netherrack over magma; a burning one has fire here and there on it. */
    private static void island(ServerLevel level, BlockPos center, int radius, boolean burning, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > radius + 0.3D) {
                    continue;
                }
                level.setBlock(pos.set(center.getX() + x, center.getY(), center.getZ() + z),
                        Blocks.NETHERRACK.defaultBlockState(), 2);
                level.setBlock(pos.set(center.getX() + x, center.getY() + 1, center.getZ() + z),
                        burning && distance > 1.0D && random.nextFloat() < 0.35F
                                ? Blocks.FIRE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                if (distance <= radius - 1) {
                    level.setBlock(pos.set(center.getX() + x, center.getY() - 1, center.getZ() + z),
                            Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                }
            }
        }
    }

    /** The cold, waiting beside the dreamer: ice to hold, water and snow. */
    private static void fillChest(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            chest.setItem(0, new ItemStack(Items.BLUE_ICE, 4));
            chest.setItem(1, new ItemStack(Items.WATER_BUCKET));
            chest.setItem(2, new ItemStack(Items.WATER_BUCKET));
            chest.setItem(3, new ItemStack(Items.POWDER_SNOW_BUCKET));
            chest.setItem(4, new ItemStack(Items.SNOWBALL, 16));
            chest.setChanged();
        }
    }

    /** A ring of crying obsidian standing across the way, facing the dreamer; its middle is the rift. */
    private static Vec3 rift(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = -3; y <= 3; y++) {
            for (int z = -3; z <= 3; z++) {
                double distance = Math.sqrt(y * y + z * z);
                level.setBlock(pos.set(center.getX(), center.getY() + y, center.getZ() + z),
                        distance >= 2.0D && distance < 3.2D ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        return Vec3.atCenterOf(center);
    }
}

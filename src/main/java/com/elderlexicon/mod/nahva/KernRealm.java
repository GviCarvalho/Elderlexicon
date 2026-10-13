package com.elderlexicon.mod.nahva;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The mage's own island in Nahvá, their kern (book 2.1.1): where a dead spirit wakes, to rest there forever. A green
 * island adrift in the dark, a tree in bloom, a pond, flowers, and quiet. It is theirs: it is laid out once, and what
 * they make of it stays. At its edge stands a gate onto the road, the long way to the tower of Lig'dar; for now the road
 * is short, and the gate leads straight to the rim of the volcano.
 */
final class KernRealm {

    private static final int Y = 100;
    private static final int RADIUS = 22;
    /** The gate onto the road, at the island's edge: its middle, along z. */
    private static final int GATE_Z = RADIUS - 2;

    private KernRealm() {
    }

    /** Lays the island out the first time; after that, it is left as the mage left it. */
    static Realms.Built build(ServerLevel level, BlockPos origin) {
        if (!level.getBlockState(origin.offset(0, Y - 30, 0)).is(Blocks.BEDROCK)) {
            RandomSource random = RandomSource.create(origin.asLong());
            island(level, origin, random);
            pond(level, origin.offset(7, Y, -6));
            tree(level, origin.offset(-6, Y + 1, 5), TreeFeatures.CHERRY, random);
            tree(level, origin.offset(9, Y + 1, 9), TreeFeatures.BIRCH, random);
            tree(level, origin.offset(-11, Y + 1, -8), TreeFeatures.OAK, random);
            flowers(level, origin, random);
            road(level, origin);
            level.setBlock(origin.offset(0, Y - 30, 0), Blocks.BEDROCK.defaultBlockState(), 2);
        }
        // There is no rift: no one wakes from here. It is set where it cannot be reached.
        return new Realms.Built(Vec3.atBottomCenterOf(origin.offset(0, Y + 1, 0)),
                new Vec3(origin.getX(), -4096.0D, origin.getZ()));
    }

    private static int depth(double r, RandomSource random) {
        return (int) Math.round((RADIUS - r) * 0.9D) + 2 + random.nextInt(3);
    }

    private static void island(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double r = Math.sqrt(x * x + z * z) + random.nextDouble() * 1.5D;
                if (r > RADIUS) {
                    continue;
                }
                int depth = depth(r, random);
                for (int d = 0; d <= depth; d++) {
                    BlockState state = d == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : d < 4 ? Blocks.DIRT.defaultBlockState()
                            : random.nextFloat() < 0.15F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : Blocks.STONE.defaultBlockState();
                    level.setBlock(origin.offset(x, Y - d, z), state, 2);
                }
                if (r > RADIUS - 3 && random.nextFloat() < 0.25F) {
                    level.setBlock(origin.offset(x, Y - depth - 1, z), Blocks.HANGING_ROOTS.defaultBlockState(), 2);
                }
                // Lights no one sees: the island is never quite in the dark.
                if (x % 6 == 0 && z % 6 == 0) {
                    level.setBlock(origin.offset(x, Y + 6, z), Blocks.LIGHT.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void pond(ServerLevel level, BlockPos center) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 3.2D) {
                    continue;
                }
                level.setBlock(center.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2);
                if (r < 2.0D) {
                    level.setBlock(center.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 2);
                    level.setBlock(center.offset(x, -2, z), Blocks.CLAY.defaultBlockState(), 2);
                } else {
                    level.setBlock(center.offset(x, -1, z), Blocks.CLAY.defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(center.offset(1, 1, -1), Blocks.LILY_PAD.defaultBlockState(), 2);
        level.setBlock(center.offset(-1, 1, 1), Blocks.LILY_PAD.defaultBlockState(), 2);
    }

    private static void tree(ServerLevel level, BlockPos at,
                             net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>> feature,
                             RandomSource random) {
        Optional<Holder.Reference<ConfiguredFeature<?, ?>>> holder = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(feature);
        holder.ifPresent(tree -> tree.value().place(level, level.getChunkSource().getGenerator(), random, at));
    }

    private static void flowers(ServerLevel level, BlockPos origin, RandomSource random) {
        BlockState[] kinds = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(),
                Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
                Blocks.GRASS.defaultBlockState(), Blocks.GRASS.defaultBlockState(), Blocks.GRASS.defaultBlockState()};
        for (int n = 0; n < 160; n++) {
            int x = random.nextInt(2 * RADIUS) - RADIUS;
            int z = random.nextInt(2 * RADIUS) - RADIUS;
            if (Math.abs(x) <= 1 && z > 0) {
                continue; // the road
            }
            BlockPos at = origin.offset(x, Y + 1, z);
            if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && level.getBlockState(at).isAir()) {
                level.setBlock(at, kinds[random.nextInt(kinds.length)], 2);
            }
        }
    }

    /** A path of old stones to the island's edge, and the gate onto the road. */
    private static void road(ServerLevel level, BlockPos origin) {
        for (int z = 2; z <= GATE_Z; z++) {
            for (int x = -1; x <= 1; x++) {
                level.setBlock(origin.offset(x, Y, z), (x + z) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : Blocks.DIRT_PATH.defaultBlockState(), 2);
            }
        }
        BlockState frame = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        for (int x = -2; x <= 2; x++) {
            for (int dy = 1; dy <= 6; dy++) {
                boolean edge = Math.abs(x) == 2 || dy == 6;
                level.setBlock(origin.offset(x, Y + dy, GATE_Z), edge ? frame : Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** Each tick on the island: through the gate, the road to the tower. */
    static void everyTick(ServerPlayer player, CompoundTag dream) {
        BlockPos origin = new BlockPos(dream.getInt("ox"), 0, dream.getInt("oz"));
        double x = player.getX() - origin.getX() - 0.5D;
        double z = player.getZ() - origin.getZ() - 0.5D;
        double dy = player.getY() - Y;
        if (Math.abs(x) < 1.6D && dy > 0.5D && dy < 6.0D && z > GATE_Z - 0.3D && z < GATE_Z + 0.7D) {
            Dreams.takeTheRoad(player);
            return;
        }
        if (player.tickCount % 10 == 0) {
            player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, false, origin.getX() + 0.5D,
                    Y + 3.5D, origin.getZ() + GATE_Z + 0.5D, 2, 0.8D, 1.4D, 0.1D, 0.01D);
        }
    }
}

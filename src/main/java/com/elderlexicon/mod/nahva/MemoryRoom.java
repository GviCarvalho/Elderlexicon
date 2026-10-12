package com.elderlexicon.mod.nahva;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The memory ({@link TowerRoom#MEMORY}): the tower copies the place the mage sleeps (where they would be born again) or,
 * with no bed, the place they died, into the room: empty, by the light it had of its own, its chests empty, nothing in
 * it that burns, floods or opens elsewhere. One thing in it is wrong: a block turned round, or the same block of another
 * wood or colour. Striking the wrong thing sets it right and breaks the seal; striking anything else hurts, and the
 * wrong thing is somewhere else.
 */
final class MemoryRoom {

    private static final int HALF = 15;
    private static final int HEIGHT = 15;
    /** Below this share of open air, the place is buried (a death in a cave): the tower remembers the world's spawn. */
    private static final double OPEN = 0.3D;
    private static final float WRONG_GUESS = 3.0F;
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
            "cherry", "bamboo", "crimson", "warped"};
    private static final String[] COLOURS = {"white", "light_gray", "gray", "black", "brown", "red", "orange",
            "yellow", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"};

    private MemoryRoom() {
    }

    /** Where the mage would be born again, or else where they died: what the tower remembers of them. */
    static CompoundTag source(ServerPlayer player, CompoundTag dream) {
        CompoundTag source = new CompoundTag();
        BlockPos bed = player.getRespawnPosition();
        ServerLevel bedLevel = player.server.getLevel(player.getRespawnDimension());
        if (bed != null && bedLevel != null && !Nahva.isNahva(bedLevel)) {
            source.putString("dim", player.getRespawnDimension().location().toString());
            source.putLong("pos", bed.asLong());
        } else {
            source.putString("dim", dream.getString("dimension"));
            source.putLong("pos", BlockPos.containing(dream.getDouble("x"), dream.getDouble("y"), dream.getDouble("z"))
                    .asLong());
        }
        return source;
    }

    static void build(TowerRoom.Room room) {
        CompoundTag source = room.state().getCompound("source");
        ResourceLocation dimension = ResourceLocation.tryParse(source.getString("dim"));
        ServerLevel from = dimension == null ? null
                : room.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (from == null || Nahva.isNahva(from)) {
            from = room.level().getServer().overworld();
        }
        BlockPos center = source.contains("pos") ? BlockPos.of(source.getLong("pos")) : spawn(from);
        if (openness(from, center) < OPEN) {
            from = room.level().getServer().overworld();
            center = spawn(from);
        }
        Rotation turn = room.s() > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        BlockState stone = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                for (int dy = 0; dy <= HEIGHT; dy++) {
                    if (departure(x, z)) {
                        if (dy == 0) {
                            room.set(x, 0, z, stone);
                        }
                        continue;
                    }
                    if (rest(x, z)) {
                        continue;
                    }
                    BlockState state = tame(from.getBlockState(center.offset(x, dy - 1, z))).rotate(turn);
                    if (dy == 0 && state.isAir()) {
                        state = stone;
                    }
                    room.set(x, dy, z, state);
                }
            }
        }
        // The ways in and out stay clear: the corner the stairs come up in, the way to the stairs out, the door of rest.
        for (int x = -15; x <= -7; x++) {
            for (int z = -15; z <= -7; z++) {
                room.set(x, 0, z, stone);
                for (int dy = 1; dy <= 5; dy++) {
                    room.set(x, dy, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = 3; x <= 6; x++) {
            for (int z = 9; z <= 13; z++) {
                for (int dy = 1; dy <= 3; dy++) {
                    room.set(x, dy, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = -13; x <= -12; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int dy = 1; dy <= 3; dy++) {
                    room.set(x, dy, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        misplace(room, null);
    }

    private static boolean departure(int x, int z) {
        return Math.abs(x - 11) <= 4 && Math.abs(z - 11) <= 4;
    }

    private static boolean rest(int x, int z) {
        return x <= -14 && Math.abs(z) <= 2;
    }

    private static boolean kept(int x, int z) {
        return departure(x, z) || rest(x, z) || x <= -7 && z <= -7 || x >= 3 && x <= 6 && z >= 9 && z <= 13
                || x >= -13 && x <= -12 && Math.abs(z) <= 2;
    }

    private static BlockPos spawn(ServerLevel level) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
    }

    /** How much of the place is open air, where a body could be. */
    private static double openness(ServerLevel level, BlockPos center) {
        int air = 0;
        int all = 0;
        for (int x = -HALF; x <= HALF; x += 3) {
            for (int z = -HALF; z <= HALF; z += 3) {
                for (int dy = 1; dy <= HEIGHT; dy += 2) {
                    all++;
                    if (!level.getBlockState(center.offset(x, dy - 1, z)).blocksMotion()) {
                        air++;
                    }
                }
            }
        }
        return (double) air / all;
    }

    /** What of a remembered block comes into the tower: nothing that flows, burns, opens elsewhere or calls things up. */
    private static BlockState tame(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LiquidBlock || block == Blocks.BUBBLE_COLUMN || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE || block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL
                || block == Blocks.END_GATEWAY || block == Blocks.SPAWNER || block == Blocks.TNT
                || block == Blocks.LIGHT || block == Blocks.BARRIER) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return state;
    }

    /** The same thing, not quite: another wood or colour of it, or it turned round. Empty when it cannot be. */
    private static Optional<BlockState> altered(BlockState state, Random random) {
        if (state.isAir() || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART) || state.getBlock() instanceof LiquidBlock) {
            return Optional.empty();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String[] family : new String[][]{WOODS, COLOURS}) {
            for (String kind : family) {
                if (!id.getPath().startsWith(kind + "_")) {
                    continue;
                }
                String rest = id.getPath().substring(kind.length() + 1);
                List<String> others = new ArrayList<>(List.of(family));
                others.remove(kind);
                Collections.shuffle(others, random);
                for (String other : others) {
                    Optional<Block> sibling = BuiltInRegistries.BLOCK.getOptional(
                            new ResourceLocation(id.getNamespace(), other + "_" + rest));
                    if (sibling.isPresent() && sibling.get() != Blocks.AIR) {
                        return Optional.of(sibling.get().withPropertiesOf(state));
                    }
                }
            }
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return Optional.of(state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getOpposite()));
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return Optional.of(state.setValue(BlockStateProperties.FACING, facing.getOpposite()));
        }
        Block twin = state.is(Blocks.COBBLESTONE) ? Blocks.MOSSY_COBBLESTONE
                : state.is(Blocks.STONE_BRICKS) ? Blocks.CRACKED_STONE_BRICKS
                : state.is(Blocks.TORCH) ? Blocks.SOUL_TORCH
                : state.is(Blocks.LANTERN) ? Blocks.SOUL_LANTERN
                : state.is(Blocks.GLASS) ? Blocks.TINTED_GLASS
                : state.is(Blocks.BOOKSHELF) ? Blocks.CHISELED_BOOKSHELF
                : state.is(Blocks.CRAFTING_TABLE) ? Blocks.FLETCHING_TABLE
                : state.is(Blocks.GRASS_BLOCK) ? Blocks.PODZOL
                : state.is(Blocks.DIRT) ? Blocks.COARSE_DIRT
                : state.is(Blocks.STONE) ? Blocks.ANDESITE
                : state.is(Blocks.SAND) ? Blocks.RED_SAND
                : state.is(Blocks.GRAVEL) ? Blocks.CLAY
                : state.is(Blocks.DEEPSLATE) ? Blocks.TUFF
                : state.is(Blocks.SNOW_BLOCK) ? Blocks.POWDER_SNOW
                : state.is(Blocks.NETHERRACK) ? Blocks.CRIMSON_NYLIUM
                : state.is(Blocks.GRASS) ? Blocks.FERN
                : state.is(Blocks.POPPY) ? Blocks.CORNFLOWER
                : state.is(Blocks.DANDELION) ? Blocks.POPPY : null;
        return twin == null ? Optional.empty() : Optional.of(twin.withPropertiesOf(state));
    }

    /** Whether a thing is of the place itself (ground, stone, leaves, grass) rather than of the life lived in it. */
    private static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.LEAVES) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND) || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SNOW) || state.is(Blocks.DEEPSLATE_BRICKS);
    }

    /**
     * Makes one thing in the room wrong (not the one at {@code not}): one that can be seen, and better one of the life
     * lived there than of the ground.
     */
    private static void misplace(TowerRoom.Room room, BlockPos not) {
        Random random = new Random(room.level().getRandom().nextLong());
        List<BlockPos> lived = new ArrayList<>();
        List<BlockPos> ground = new ArrayList<>();
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                if (kept(x, z)) {
                    continue;
                }
                for (int dy = 1; dy <= HEIGHT; dy++) {
                    BlockPos pos = room.pos(x, dy, z);
                    BlockState state = room.level().getBlockState(pos);
                    if (state.isAir() || pos.equals(not) || !seen(room, pos) || altered(state, random).isEmpty()) {
                        continue;
                    }
                    (natural(state) ? ground : lived).add(pos);
                }
            }
        }
        List<BlockPos> choice = lived.isEmpty() ? ground : lived;
        if (choice.isEmpty()) {
            added(room, not, random);
            return;
        }
        BlockPos wrong = choice.get(random.nextInt(choice.size()));
        BlockState right = room.level().getBlockState(wrong);
        room.state().putLong("wrong", wrong.asLong());
        room.state().put("right", NbtUtils.writeBlockState(right));
        room.level().setBlock(wrong, altered(right, random).orElse(right), 2);
    }

    /** Nothing there can be altered: something is there that was not, standing on the ground. */
    private static void added(TowerRoom.Room room, BlockPos not, Random random) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = -HALF + 1; x < HALF; x++) {
            for (int z = -HALF + 1; z < HALF; z++) {
                if (kept(x, z)) {
                    continue;
                }
                for (int dy = 1; dy < HEIGHT; dy++) {
                    BlockPos pos = room.pos(x, dy, z);
                    if (room.level().getBlockState(pos).isAir() && !pos.equals(not)
                            && room.level().getBlockState(pos.below()).isFaceSturdy(room.level(), pos.below(),
                            Direction.UP)) {
                        spots.add(pos);
                        break;
                    }
                }
            }
        }
        if (spots.isEmpty()) {
            return;
        }
        BlockPos spot = spots.get(random.nextInt(spots.size()));
        BlockState[] things = {Blocks.LANTERN.defaultBlockState(), Blocks.FLOWER_POT.defaultBlockState(),
                Blocks.CANDLE.defaultBlockState(), Blocks.SKELETON_SKULL.defaultBlockState(),
                Blocks.CAULDRON.defaultBlockState()};
        room.state().putLong("wrong", spot.asLong());
        room.state().put("right", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        room.level().setBlock(spot, things[random.nextInt(things.length)], 2);
    }

    private static boolean seen(TowerRoom.Room room, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (!room.level().getBlockState(pos.relative(side)).canOcclude()) {
                return true;
            }
        }
        return false;
    }

    /** The mage strikes {@code pos}: whether it is the wrong thing (and it is set right). */
    static boolean struck(ServerPlayer player, TowerRoom.Room room, BlockPos pos) {
        CompoundTag state = room.state();
        if (!state.contains("wrong")) {
            return false;
        }
        BlockPos wrong = BlockPos.of(state.getLong("wrong"));
        BlockState right = NbtUtils.readBlockState(room.level().holderLookup(Registries.BLOCK),
                state.getCompound("right"));
        if (pos.equals(wrong)) {
            room.level().setBlock(wrong, right, 2);
            room.level().sendParticles(ParticleTypes.REVERSE_PORTAL, wrong.getX() + 0.5D, wrong.getY() + 0.5D,
                    wrong.getZ() + 0.5D, 20, 0.3D, 0.3D, 0.3D, 0.02D);
            state.remove("wrong");
            return true;
        }
        int dx = pos.getX() - room.origin().getX();
        int dz = pos.getZ() - room.origin().getZ();
        int dy = pos.getY() - room.y();
        long now = room.level().getGameTime();
        if (Math.abs(dx) > HALF || Math.abs(dz) > HALF || dy < 0 || dy > HEIGHT || now - state.getLong("struck") < 10) {
            return false;
        }
        // Wrong: it hurts, and the wrong thing is somewhere else.
        state.putLong("struck", now);
        player.hurt(player.damageSources().magic(), WRONG_GUESS);
        room.level().playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 0.5F, 0.6F);
        room.level().setBlock(wrong, right, 2);
        misplace(room, wrong);
        Phantur.maybe(player, "memory_wrong", 0.5F);
        return false;
    }
}

package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The memories of the body (book 1.3; docs/aprendizado-runas-design.md, 4b): Harlvilt hands the kern what the body
 * felt, touched, suffered. When a mage first lives an experience of a rune (catches fire, freezes), the place around
 * them at that moment is kept, as it was: a piece of the world, a few blocks each way. Their fever dream of the rune
 * brings these pieces back, turned and wrong. Kept with the world, by mage and by experience.
 */
public final class BodyMemories extends SavedData {

    private static final String NAME = ElderLexicon.MODID + "_body_memories";
    /** How far the piece reaches round the mage, across and down; how high above their feet. */
    static final int HALF = 5;
    static final int BELOW = 2;
    static final int ABOVE = 5;

    /** A piece of the world: its blocks, in order x, then y, then z, as indices into its palette. */
    public record Piece(List<BlockState> palette, int[] blocks) {

        public static final int WIDTH = 2 * HALF + 1;
        public static final int HEIGHT = BELOW + ABOVE + 1;

        /** The block at {@code (x, y, z)} of the piece, each from 0. */
        public BlockState at(int x, int y, int z) {
            return palette.get(blocks[(x * HEIGHT + y) * WIDTH + z]);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            ListTag states = new ListTag();
            palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
            tag.put("Palette", states);
            tag.putIntArray("Blocks", blocks);
            return tag;
        }

        static Piece load(CompoundTag tag, net.minecraft.core.HolderGetter<Block> blocks) {
            List<BlockState> palette = new ArrayList<>();
            ListTag states = tag.getList("Palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < states.size(); i++) {
                palette.add(NbtUtils.readBlockState(blocks, states.getCompound(i)));
            }
            return new Piece(palette, tag.getIntArray("Blocks"));
        }

        /** The place round {@code center} as it is now, the feet at its floor's height plus {@link #BELOW}. */
        static Piece of(ServerLevel level, BlockPos center) {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> indices = new HashMap<>();
            int[] blocks = new int[WIDTH * HEIGHT * WIDTH];
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    for (int z = 0; z < WIDTH; z++) {
                        BlockState state = level.getBlockState(center.offset(x - HALF, y - BELOW, z - HALF));
                        // What holds things, or opens elsewhere, is remembered as what it looks like, not what it is.
                        if (state.hasBlockEntity() || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) {
                            state = Blocks.AIR.defaultBlockState();
                        }
                        BlockState kept = state;
                        blocks[(x * HEIGHT + y) * WIDTH + z] = indices.computeIfAbsent(kept, known -> {
                            palette.add(known);
                            return palette.size() - 1;
                        });
                    }
                }
            }
            return new Piece(palette, blocks);
        }
    }

    /** By mage, by experience. */
    private final Map<UUID, Map<String, Piece>> memories = new HashMap<>();

    static BodyMemories get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                tag -> load(tag, overworld.holderLookup(Registries.BLOCK)), BodyMemories::new, NAME);
    }

    /** {@code player} lived {@code experience} for the first time: where they are now is kept. */
    public static void remember(ServerPlayer player, String experience) {
        BodyMemories kept = get(player.server);
        kept.memories.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>())
                .put(experience, Piece.of(player.serverLevel(), player.blockPosition()));
        kept.setDirty();
    }

    /** Whether {@code player}'s body keeps the place of {@code experience}. */
    public static boolean has(ServerPlayer player, String experience) {
        return get(player.server).memories.getOrDefault(player.getUUID(), Map.of()).containsKey(experience);
    }

    /** What {@code mage}'s body remembers of {@code experiences}: one piece each it was there for. */
    static List<Piece> of(MinecraftServer server, UUID mage, List<String> experiences) {
        Map<String, Piece> theirs = get(server).memories.getOrDefault(mage, Map.of());
        List<Piece> pieces = new ArrayList<>();
        for (String experience : experiences) {
            Piece piece = theirs.get(experience);
            if (piece != null) {
                pieces.add(piece);
            }
        }
        return pieces;
    }

    private static BodyMemories load(CompoundTag tag, net.minecraft.core.HolderGetter<Block> blocks) {
        BodyMemories kept = new BodyMemories();
        for (String mage : tag.getAllKeys()) {
            CompoundTag theirs = tag.getCompound(mage);
            Map<String, Piece> pieces = new HashMap<>();
            for (String experience : theirs.getAllKeys()) {
                pieces.put(experience, Piece.load(theirs.getCompound(experience), blocks));
            }
            kept.memories.put(UUID.fromString(mage), pieces);
        }
        return kept;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        memories.forEach((mage, pieces) -> {
            CompoundTag theirs = new CompoundTag();
            pieces.forEach((experience, piece) -> theirs.put(experience, piece.save()));
            tag.put(mage.toString(), theirs);
        });
        return tag;
    }
}

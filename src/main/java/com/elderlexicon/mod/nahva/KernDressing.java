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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a fever dream has put on a mage's kern (docs/aprendizado-runas-design.md, 4b): every block it changed, with the
 * block that was there. Taken off at waking, and the kern is as the mage left it. Kept with the world, so a dream cut
 * short by the server going down is still taken off when the mage comes back.
 */
final class KernDressing extends SavedData {

    private static final String NAME = ElderLexicon.MODID + "_kern_dressing";
    /**
     * How the dream sets its blocks: seen by the clients, but nothing round them told, and nothing dropped. Told, the
     * game breaks what has lost what holds it for the moment (half a bed, a bush on stone, a lantern while its ceiling
     * is changed), and it drops on the floor of the dream.
     */
    private static final int QUIET = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
            | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;

    /** By mage: each changed position, and what was there before the dream. */
    private final Map<UUID, Map<Long, BlockState>> worn = new HashMap<>();

    static KernDressing get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                tag -> load(tag, overworld.holderLookup(Registries.BLOCK)), KernDressing::new, NAME);
    }

    /** The dream of {@code mage} puts {@code state} at {@code pos}; what was there is kept the first time. */
    static void put(ServerLevel level, UUID mage, BlockPos pos, BlockState state) {
        KernDressing dressing = get(level.getServer());
        Map<Long, BlockState> theirs = dressing.worn.computeIfAbsent(mage, uuid -> new LinkedHashMap<>());
        BlockState was = level.getBlockState(pos);
        if (was == state) {
            return;
        }
        theirs.putIfAbsent(pos.asLong(), was);
        level.setBlock(pos, state, QUIET);
        dressing.setDirty();
    }

    /** The dreamer changed {@code pos} themselves (set something down): {@code was} comes back at waking. */
    static void note(ServerLevel level, UUID mage, BlockPos pos, BlockState was) {
        KernDressing dressing = get(level.getServer());
        dressing.worn.computeIfAbsent(mage, uuid -> new LinkedHashMap<>()).putIfAbsent(pos.asLong(), was);
        dressing.setDirty();
    }

    /** Whether the dream of {@code mage} has anything on their kern. */
    static boolean wearing(MinecraftServer server, UUID mage) {
        return !get(server).worn.getOrDefault(mage, Map.of()).isEmpty();
    }

    /** Everything the dream of {@code mage} put on, taken off: the kern as it was. */
    static void takeOff(ServerLevel level, UUID mage) {
        KernDressing dressing = get(level.getServer());
        Map<Long, BlockState> theirs = dressing.worn.remove(mage);
        if (theirs == null) {
            return;
        }
        // In the order put on, backwards: what stood on another goes first.
        Long[] positions = theirs.keySet().toArray(new Long[0]);
        for (int i = positions.length - 1; i >= 0; i--) {
            level.setBlock(BlockPos.of(positions[i]), theirs.get(positions[i]), QUIET);
        }
        dressing.setDirty();
    }

    private static KernDressing load(CompoundTag tag, net.minecraft.core.HolderGetter<Block> blocks) {
        KernDressing dressing = new KernDressing();
        for (String mage : tag.getAllKeys()) {
            ListTag list = tag.getList(mage, Tag.TAG_COMPOUND);
            Map<Long, BlockState> theirs = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                theirs.put(entry.getLong("Pos"), NbtUtils.readBlockState(blocks, entry.getCompound("State")));
            }
            dressing.worn.put(UUID.fromString(mage), theirs);
        }
        return dressing;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        worn.forEach((mage, theirs) -> {
            ListTag list = new ListTag();
            theirs.forEach((pos, state) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Pos", pos);
                entry.put("State", NbtUtils.writeBlockState(state));
                list.add(entry);
            });
            tag.put(mage.toString(), list);
        });
        return tag;
    }
}

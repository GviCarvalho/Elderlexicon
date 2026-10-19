package com.elderlexicon.mod.invocation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where every marked square of every invocation bench is (docs/invocacao-design.md, section 1), so that a spell finds
 * {@code g2} wherever the bench stands, loaded or not. A mark names one square; marking another with it takes it from
 * the first.
 */
public final class BenchSlots extends SavedData {

    private static final String NAME = "elderlexicon_invocation_benches";

    /** A marked square: the bench and which of its nine. */
    public record Slot(ResourceKey<Level> dimension, BlockPos pos, int index) {
    }

    private final Map<String, Slot> slots = new LinkedHashMap<>();

    public static BenchSlots get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BenchSlots::load, BenchSlots::new, NAME);
    }

    /** The square a mark names, if a bench has it. */
    public static Optional<Slot> find(MinecraftServer server, String mark) {
        if (server == null || mark == null || mark.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(get(server).slots.get(mark));
    }

    /** The bench a square is in, loaded; empty when it is gone or its world is not there. */
    public static Optional<InvocationBenchBlockEntity> bench(MinecraftServer server, Slot slot) {
        ServerLevel level = server.getLevel(slot.dimension());
        if (level == null) {
            return Optional.empty();
        }
        return level.getBlockEntity(slot.pos()) instanceof InvocationBenchBlockEntity bench
                ? Optional.of(bench) : Optional.empty();
    }

    /** The marks of a bench changed: what it had is forgotten, and what it has now is kept. */
    public void set(ResourceKey<Level> dimension, BlockPos pos, String[] marks) {
        forget(dimension, pos);
        for (int i = 0; i < marks.length; i++) {
            if (marks[i] != null && !marks[i].isBlank()) {
                slots.put(marks[i], new Slot(dimension, pos.immutable(), i));
            }
        }
        setDirty();
    }

    /** A bench is gone, or no longer a bench. */
    public void forget(ResourceKey<Level> dimension, BlockPos pos) {
        if (slots.values().removeIf(slot -> slot.dimension().equals(dimension) && slot.pos().equals(pos))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        slots.forEach((mark, slot) -> {
            CompoundTag one = new CompoundTag();
            one.putString("dimension", slot.dimension().location().toString());
            one.putLong("pos", slot.pos().asLong());
            one.putInt("index", slot.index());
            all.put(mark, one);
        });
        tag.put("slots", all);
        return tag;
    }

    private static BenchSlots load(CompoundTag tag) {
        BenchSlots loaded = new BenchSlots();
        CompoundTag all = tag.getCompound("slots");
        for (String mark : all.getAllKeys()) {
            CompoundTag one = all.getCompound(mark);
            ResourceLocation dimension = ResourceLocation.tryParse(one.getString("dimension"));
            if (dimension == null || !one.contains("pos", Tag.TAG_LONG)) {
                continue;
            }
            loaded.slots.put(mark, new Slot(ResourceKey.create(Registries.DIMENSION, dimension),
                    BlockPos.of(one.getLong("pos")), one.getInt("index")));
        }
        return loaded;
    }
}

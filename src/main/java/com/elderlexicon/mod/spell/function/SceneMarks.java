package com.elderlexicon.mod.spell.function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Marks that no one wrote: what a referent rune ({@code ego}, {@code ille}) points at, bound when its line is cast
 * (docs/fluxo-design.md). Each binding is a mark of its own ({@code ille_12}) that {@link MarkTargets#find} answers
 * before any mark of the world, so every verb, filter and place reads it as it reads a mark. Bindings are forgotten a
 * while after they are made; a spell that lasts longer than that binds again at each window.
 */
public final class SceneMarks {

    /** How long a binding is kept: long enough for any spell read from it. */
    private static final long KEEP_MS = 10L * 60L * 1000L;

    private static final Map<String, Bound> BOUND = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT = new AtomicLong();

    private SceneMarks() {
    }

    /** What a binding points at: a living thing, a block that is there, or only a point (a dead one, a broken block). */
    private record Bound(ResourceKey<Level> dimension, @Nullable UUID entity, @Nullable BlockPos block, Vec3 at,
                         long madeMs) {
    }

    /** Binds {@code word} to an entity; returns the mark to write in its place. */
    public static String entity(String word, Entity entity) {
        return bind(word, new Bound(entity.level().dimension(), entity.getUUID(), null, entity.position(), now()));
    }

    /** Binds {@code word} to the block at {@code pos}. */
    public static String block(String word, ServerLevel level, BlockPos pos) {
        return bind(word, new Bound(level.dimension(), null, pos.immutable(), Vec3.atCenterOf(pos), now()));
    }

    /** Binds {@code word} to a point where something was. */
    public static String point(String word, ServerLevel level, Vec3 at) {
        return bind(word, new Bound(level.dimension(), null, null, at, now()));
    }

    private static String bind(String word, Bound bound) {
        long now = bound.madeMs();
        BOUND.values().removeIf(old -> now - old.madeMs() > KEEP_MS);
        String mark = word + "_" + NEXT.incrementAndGet();
        BOUND.put(mark, bound);
        return mark;
    }

    /** What a bound mark points at, or empty when {@code mark} is no binding. */
    static Optional<List<MarkTargets.Marked>> find(MinecraftServer server, String mark) {
        Bound bound = BOUND.get(mark);
        if (bound == null || server == null) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(bound.dimension());
        if (level == null) {
            return Optional.of(List.of());
        }
        if (bound.entity() != null) {
            Entity entity = level.getEntity(bound.entity());
            if (entity != null && entity.isAlive()) {
                return Optional.of(List.of(MarkTargets.Marked.of(mark, entity)));
            }
        }
        if (bound.block() != null && !level.getBlockState(bound.block()).isAir()) {
            return Optional.of(List.of(MarkTargets.Marked.block(mark, level, bound.block(),
                    level.getBlockState(bound.block()))));
        }
        return Optional.of(List.of(MarkTargets.Marked.point(mark, level, bound.at())));
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}

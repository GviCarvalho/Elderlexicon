package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ligabis.BlockIntegrity;
import com.elderlexicon.mod.ligabis.MemberId;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.ligabis.world.MemberKeys;
import com.elderlexicon.mod.spell.mark.MarkCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Finds the things that carry a mark anywhere in the world: loaded entities, marked blocks and entities
 * whose chunk was unloaded (remembered by Ligabis where they were last seen). Before acting,
 * {@link #load} brings the far ones back with a temporary chunk ticket.
 */
final class MarkTargets {

    /** Keeps a chunk loaded while a spell acts on what is in it; expires on its own. */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("elderlexicon_mark", Comparator.comparingLong(ChunkPos::toLong), 100);
    /** How long to wait for the entities of a freshly loaded chunk to appear. */
    private static final int MAX_WAIT_TICKS = 40;

    private MarkTargets() {
    }

    /** One thing that carries a mark. Blocks have a position; entities have an id and, once loaded, the entity. */
    static final class Marked {
        final String mark;
        final ServerLevel level;
        @Nullable final UUID entityId;
        @Nullable final BlockPos blockPos;
        final double mass;
        @Nullable Entity entity;
        Vec3 position;

        private Marked(String mark, ServerLevel level, @Nullable UUID entityId, @Nullable BlockPos blockPos,
                       double mass, @Nullable Entity entity, Vec3 position) {
            this.mark = mark;
            this.level = level;
            this.entityId = entityId;
            this.blockPos = blockPos;
            this.mass = mass;
            this.entity = entity;
            this.position = position;
        }

        static Marked of(String mark, Entity entity) {
            return new Marked(mark, (ServerLevel) entity.level(), entity.getUUID(), null, massOf(entity), entity, entity.position());
        }

        static Marked block(String mark, ServerLevel level, BlockPos pos, BlockState state) {
            return new Marked(mark, level, null, pos.immutable(), massOf(state), null, Vec3.atCenterOf(pos));
        }

        boolean isBlock() {
            return blockPos != null;
        }

        /** True when there is something to act on: a block that is still there, or a live entity. */
        boolean present() {
            if (isBlock()) {
                return !level.getBlockState(blockPos).isAir();
            }
            return entity != null && entity.isAlive() && !entity.isRemoved();
        }

        boolean sameAs(Marked other) {
            if (isBlock()) {
                return other.isBlock() && level == other.level && blockPos.equals(other.blockPos);
            }
            return entityId != null && entityId.equals(other.entityId);
        }
    }

    /** Everything that carries {@code mark}; empty when nothing does (or Ligabis is not running). */
    static List<Marked> find(MinecraftServer server, String mark) {
        LigabisManager manager = LigabisManager.get();
        List<Marked> found = new ArrayList<>();
        if (manager == null || server == null) {
            return found;
        }
        for (MemberId id : manager.membersOf(mark)) {
            if (MemberKeys.isEntity(id)) {
                Entity entity = findEntity(server, MemberKeys.entityId(id));
                if (entity != null && entity.isAlive()) {
                    found.add(Marked.of(mark, entity));
                }
            } else if (MemberKeys.isBlock(id)) {
                ServerLevel level = server.getLevel(MemberKeys.blockDimension(id));
                BlockPos pos = MemberKeys.blockPos(id);
                if (level != null) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        found.add(Marked.block(mark, level, pos, state));
                    }
                }
            }
        }
        manager.rememberedEntities(mark).forEach((id, stored) -> {
            ResourceLocation dimension = ResourceLocation.tryParse(stored.dimension());
            ServerLevel level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level != null) {
                double mass = stored.mass() > 0.0D ? stored.mass() : MarkCost.ITEM_MASS;
                found.add(new Marked(mark, level, id, null, mass, null, new Vec3(stored.x(), stored.y(), stored.z())));
            }
        });
        return found;
    }

    /** The caster as a target, for spells whose subject or target is left out. */
    static Marked caster(Entity caster) {
        return Marked.of("", caster);
    }

    /**
     * Makes sure everything in {@code things} is loaded, then runs {@code then}. Entities of unloaded
     * chunks show up a few ticks after their chunk loads; those that never do are left absent.
     */
    static void load(List<Marked> things, Runnable then) {
        List<Marked> waiting = new ArrayList<>();
        for (Marked thing : things) {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(thing.position));
            thing.level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
            if (!thing.isBlock() && thing.entity == null) {
                waiting.add(thing);
            }
        }
        if (waiting.isEmpty()) {
            then.run();
            return;
        }
        poll(waiting, then, 0);
    }

    private static void poll(List<Marked> waiting, Runnable then, int waited) {
        waiting.removeIf(thing -> {
            Entity entity = thing.level.getEntity(thing.entityId);
            if (entity == null) {
                return false;
            }
            thing.entity = entity;
            thing.position = entity.position();
            return true;
        });
        if (waiting.isEmpty() || waited >= MAX_WAIT_TICKS) {
            then.run();
            return;
        }
        SpellEffects.schedule(waiting.get(0).level, 1, () -> poll(waiting, then, waited + 1));
    }

    @Nullable
    static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    static double massOf(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getMaxHealth();
        }
        if (entity instanceof ItemEntity) {
            return MarkCost.ITEM_MASS;
        }
        return 1.0D;
    }

    static double massOf(BlockState state) {
        return BlockIntegrity.capacity(state.getBlock().getExplosionResistance());
    }

    static boolean sameDimension(Marked thing, Level level) {
        return thing.level == level;
    }
}

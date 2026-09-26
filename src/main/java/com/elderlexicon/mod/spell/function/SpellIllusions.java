package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.spelling.network.IllusionPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The illusions standing in the world, on the server ({@code docs/surgit-visao-design.md}, section 4): blocks that are
 * only seen (an image of water, of a wall, of fire) and images of marked things, each until its time is up. They are
 * sent to whoever watches their chunk; nothing of them can be touched, since only the clients know they are there.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class SpellIllusions {

    private record Place(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record BlockImage(BlockState state, long untilTick) {
    }

    /**
     * An image of a marked thing: what it is an image of, from where it travels to where (at how many blocks a tick,
     * from which tick), and until when it stands there.
     */
    private record ThingImage(ResourceKey<Level> dimension, int sourceId, Vec3 from, Vec3 to, double speed,
                              long startTick, long untilTick) {

        IllusionPacket packet(int id, long now) {
            return IllusionPacket.thing(id, sourceId, from, to, speed, (int) Math.max(0L, now - startTick));
        }
    }

    private static final Map<Place, BlockImage> BLOCKS = new ConcurrentHashMap<>();
    private static final Map<Integer, ThingImage> THINGS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_IMAGE = new AtomicInteger();

    private SpellIllusions() {
    }

    /** Shows the image of {@code state} at {@code pos} for {@code ticks}, unless something solid is already there. */
    public static void showBlock(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        Place place = new Place(level.dimension(), pos.immutable());
        BLOCKS.put(place, new BlockImage(state, level.getServer().getTickCount() + ticks));
        SpellingNetwork.sendIllusion(level.getChunkAt(pos), IllusionPacket.block(pos, state));
    }

    /** Shows an image of {@code source} standing at {@code at} for {@code ticks}. */
    public static void showThing(ServerLevel level, Entity source, Vec3 at, int ticks) {
        showThing(level, source, at, at, 0.0D, ticks);
    }

    /**
     * Shows an image of {@code source} travelling from {@code from} to {@code to} at {@code speed} blocks a tick (thrown,
     * pulled or pushed), then standing there until {@code ticks} are over.
     */
    public static void showThing(ServerLevel level, Entity source, Vec3 from, Vec3 to, double speed, int ticks) {
        int id = NEXT_IMAGE.incrementAndGet();
        long now = level.getServer().getTickCount();
        ThingImage image = new ThingImage(level.dimension(), source.getId(), from, to, speed, now, now + ticks);
        THINGS.put(id, image);
        // Sent to whoever watches where it sets off and where it lands.
        SpellingNetwork.sendIllusion(level.getChunkAt(BlockPos.containing(from)), image.packet(id, now));
        if (!new ChunkPos(BlockPos.containing(from)).equals(new ChunkPos(BlockPos.containing(to)))) {
            SpellingNetwork.sendIllusion(level.getChunkAt(BlockPos.containing(to)), image.packet(id, now));
        }
    }

    /** Whether an image of a block stands at {@code pos}. */
    public static boolean imageAt(ServerLevel level, BlockPos pos) {
        return BLOCKS.containsKey(new Place(level.dimension(), pos));
    }

    /**
     * Dissipates the images of {@code kind} within {@code radius} of {@code center} (an image of fire, when fire's image
     * is drawn off or pushed away); returns how many.
     */
    public static int dissipate(ServerLevel level, Vec3 center, double radius, Revelation.Kind kind) {
        int gone = 0;
        for (Map.Entry<Place, BlockImage> entry : new ArrayList<>(BLOCKS.entrySet())) {
            Place place = entry.getKey();
            if (place.dimension().equals(level.dimension())
                    && Vec3.atCenterOf(place.pos()).distanceToSqr(center) <= radius * radius
                    && RevelationSight.carries(kind, entry.getValue().state())) {
                removeBlock(level, place);
                gone++;
            }
        }
        return gone;
    }

    private static void removeBlock(ServerLevel level, Place place) {
        if (BLOCKS.remove(place) != null) {
            SpellingNetwork.sendIllusion(level.getChunkAt(place.pos()), IllusionPacket.blockGone(place.pos()));
        }
    }

    private static void removeThing(MinecraftServer server, int id) {
        ThingImage image = THINGS.remove(id);
        ServerLevel level = image == null ? null : server.getLevel(image.dimension());
        if (level != null) {
            SpellingNetwork.sendIllusion(level.getChunkAt(BlockPos.containing(image.from())), IllusionPacket.thingGone(id));
            SpellingNetwork.sendIllusion(level.getChunkAt(BlockPos.containing(image.to())), IllusionPacket.thingGone(id));
        }
    }

    @SubscribeEvent
    public static void expire(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BLOCKS.isEmpty() && THINGS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (Map.Entry<Place, BlockImage> entry : new ArrayList<>(BLOCKS.entrySet())) {
            if (now >= entry.getValue().untilTick()) {
                ServerLevel level = server.getLevel(entry.getKey().dimension());
                if (level != null) {
                    removeBlock(level, entry.getKey());
                } else {
                    BLOCKS.remove(entry.getKey());
                }
            }
        }
        List<Integer> ended = new ArrayList<>();
        THINGS.forEach((id, image) -> {
            if (now >= image.untilTick()) {
                ended.add(id);
            }
        });
        ended.forEach(id -> removeThing(server, id));
    }

    /** Someone starting to watch a chunk is shown the images standing in it. */
    @SubscribeEvent
    public static void watchChunk(ChunkWatchEvent.Watch event) {
        ChunkPos chunk = event.getPos();
        ResourceKey<Level> dimension = event.getLevel().dimension();
        BLOCKS.forEach((place, image) -> {
            if (place.dimension().equals(dimension) && new ChunkPos(place.pos()).equals(chunk)) {
                SpellingNetwork.sendIllusionTo(event.getPlayer(), IllusionPacket.block(place.pos(), image.state()));
            }
        });
        long now = event.getLevel().getServer().getTickCount();
        THINGS.forEach((id, image) -> {
            if (image.dimension().equals(dimension) && (new ChunkPos(BlockPos.containing(image.from())).equals(chunk)
                    || new ChunkPos(BlockPos.containing(image.to())).equals(chunk))) {
                SpellingNetwork.sendIllusionTo(event.getPlayer(), image.packet(id, now));
            }
        });
    }
}

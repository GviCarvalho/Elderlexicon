package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.Barrier;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.vita.ElementAffinityService;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The zones impediunt keeps an element out of ({@code igni eu ubis quantum 40 chronos 30 impediunt}): what of the
 * element stands inside is moved just past the edge (fire relit there, water poured there, earth piled into a wall),
 * creatures of the element are pushed out, and while the zone lasts the element cannot come back in. The zone moves
 * with what it is centred on. See {@link Barrier} for its shape and docs/impediunt-design.md.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class ImpediuntZones {

    /** How often a lasting zone is swept again, in ticks. */
    private static final int SWEEP_TICKS = 5;
    private static final double PUSH_SPEED = 0.5D;
    private static final double PUSH_LIFT = 0.2D;

    private static final List<Zone> ZONES = new ArrayList<>();

    private ImpediuntZones() {
    }

    private static final class Zone {
        final ServerLevel level;
        final ServerPlayer caster;
        final VitaElement element;
        final Supplier<Optional<Vec3>> center;
        final double radius;
        final long untilTick;
        Vec3 lastCenter;

        Zone(ServerLevel level, ServerPlayer caster, VitaElement element, Supplier<Optional<Vec3>> center, double radius,
             long untilTick, Vec3 lastCenter) {
            this.level = level;
            this.caster = caster;
            this.element = element;
            this.center = center;
            this.radius = radius;
            this.untilTick = untilTick;
            this.lastCenter = lastCenter;
        }
    }

    /**
     * Opens a zone of {@code radius} around {@code center} for {@code ticks}, clearing it at once. Earth is moved only
     * then, into a wall at the edge; fire, water and creatures are kept out for as long as it lasts.
     */
    static void open(ServerPlayer caster, VitaElement element, Supplier<Optional<Vec3>> center, double radius, int ticks) {
        Optional<Vec3> at = center.get();
        if (at.isEmpty() || radius <= 0.0D) {
            return;
        }
        ServerLevel level = caster.serverLevel();
        Zone zone = new Zone(level, caster, element, center, radius, level.getGameTime() + Math.max(1, ticks), at.get());
        sweep(zone, at.get(), true);
        showEdge(zone, at.get(), 48);
        ZONES.add(zone);
    }

    /** What an active zone of {@code element} looks like to a spell acting near it: its centre and radius. */
    record Edge(Vec3 center, double radius) {

        /** Whether {@code at} is past the edge, in the band {@code width} wide just outside it. */
        boolean inBand(Vec3 at, double width) {
            double dx = at.x - center.x;
            double dz = at.z - center.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            return distance >= radius && distance <= radius + width;
        }
    }

    /**
     * The zone of {@code element} that {@code pos} lies in, if any. Invoked matter counts from the ground under the
     * centre's feet up, since a vocant lays earth under the feet of what it is called on.
     */
    static Optional<Edge> zoneAt(ServerLevel level, BlockPos pos, VitaElement element) {
        Zone zone = find(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, element, true);
        return zone == null ? Optional.empty() : Optional.of(new Edge(zone.lastCenter, zone.radius));
    }

    /** The zone of {@code element} that the point {@code at} lies in, if any. */
    static Optional<Edge> zoneAt(ServerLevel level, Vec3 at, VitaElement element) {
        Zone zone = find(level, at.x, Math.floor(at.y), at.z, element, true);
        return zone == null ? Optional.empty() : Optional.of(new Edge(zone.lastCenter, zone.radius));
    }

    /**
     * Where something of {@code element} that would appear at {@code pos}, inside a zone, is pushed to. Pushed out
     * evenly from the centre, it spreads all along the edge at once: fire becomes a whole ring in an instant, earth a
     * whole layer of the wall (each more that comes in raises it a layer). Empty when the edge has no room left.
     */
    static List<BlockPos> pushedOut(ServerLevel level, BlockPos pos, VitaElement element, BlockState matter) {
        Zone zone = find(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, element, true);
        return zone == null ? List.of() : ring(zone, matter);
    }

    /** One free spot in every column just past the edge of {@code zone}, the lowest where {@code matter} can stand. */
    private static List<BlockPos> ring(Zone zone, BlockState matter) {
        ServerLevel level = zone.level;
        Vec3 center = zone.lastCenter;
        int floor = (int) Math.floor(center.y);
        double out = zone.radius + 1.0D;
        int columns = Math.max(8, (int) Math.ceil(2.0D * Math.PI * out));
        boolean fire = matter.is(BlockTags.FIRE);
        List<BlockPos> spots = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int index = 0; index < columns; index++) {
            double angle = 2.0D * Math.PI * index / columns;
            BlockPos column = BlockPos.containing(center.x + Math.cos(angle) * out, floor, center.z + Math.sin(angle) * out);
            if (!seen.add(BlockPos.asLong(column.getX(), 0, column.getZ()))) {
                continue;
            }
            for (int y = floor - 1; y < floor + Barrier.HEIGHT; y++) {
                BlockPos spot = new BlockPos(column.getX(), y, column.getZ());
                BlockState there = level.isLoaded(spot) ? level.getBlockState(spot) : null;
                if (there == null || !there.canBeReplaced() || there.is(matter.getBlock())) {
                    continue;
                }
                if (fire ? BaseFireBlock.getState(level, spot).canSurvive(level, spot)
                        : !level.getBlockState(spot.below()).canBeReplaced()) {
                    spots.add(spot);
                    break;
                }
            }
        }
        return spots;
    }

    /** Whether {@code pos} lies in a zone of {@code element}, where nothing of it may appear. */
    static boolean forbids(ServerLevel level, BlockPos pos, VitaElement element) {
        return find(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, element, true) != null;
    }

    private static Zone find(ServerLevel level, double x, double y, double z, VitaElement element, boolean withGround) {
        for (Zone zone : ZONES) {
            if (zone.level != level || zone.element != element) {
                continue;
            }
            Vec3 center = zone.lastCenter;
            int floor = (int) Math.floor(center.y);
            int bottom = withGround ? floor - 1 : floor;
            if (y < bottom || y >= floor + Barrier.HEIGHT) {
                continue;
            }
            double dx = x - center.x;
            double dz = z - center.z;
            if (dx * dx + dz * dz <= zone.radius * zone.radius) {
                return zone;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ZONES.isEmpty()) {
            return;
        }
        Iterator<Zone> iterator = ZONES.iterator();
        while (iterator.hasNext()) {
            Zone zone = iterator.next();
            long now = zone.level.getGameTime();
            if (now >= zone.untilTick) {
                iterator.remove();
                continue;
            }
            if (now % SWEEP_TICKS != 0) {
                continue;
            }
            Optional<Vec3> at = zone.center.get();
            if (at.isEmpty()) {
                iterator.remove(); // what it was centred on is gone
                continue;
            }
            zone.lastCenter = at.get();
            sweep(zone, at.get(), false);
            showEdge(zone, at.get(), 16);
        }
    }

    // ------------------------------------------------------------------ the sweep

    private static void sweep(Zone zone, Vec3 center, boolean first) {
        pushCreatures(zone, center);
        Optional<Revelation.Kind> kind = switch (zone.element) {
            case IGNI -> Optional.of(Revelation.Kind.IGNI);
            case AQUA -> Optional.of(Revelation.Kind.AQUA);
            case FIRMO -> Optional.of(Revelation.Kind.FIRMO);
            default -> Optional.empty();
        };
        if (kind.isEmpty() || kind.get() == Revelation.Kind.FIRMO && !first) {
            return;
        }
        ServerLevel level = zone.level;
        boolean fireInside = false;
        int reach = (int) Math.ceil(zone.radius);
        int floor = (int) Math.floor(center.y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = floor; y < floor + Barrier.HEIGHT; y++) {
            for (int x = (int) Math.floor(center.x) - reach; x <= (int) Math.floor(center.x) + reach; x++) {
                for (int z = (int) Math.floor(center.z) - reach; z <= (int) Math.floor(center.z) + reach; z++) {
                    if (!Barrier.inside(center.x, center.y, center.z, x, y, z, zone.radius)) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    switch (kind.get()) {
                        case IGNI -> fireInside |= keepFireOut(level, cursor.immutable(), state, center, zone.radius);
                        case AQUA -> keepWaterOut(level, cursor.immutable(), state, center, zone.radius);
                        case FIRMO -> pushEarth(level, cursor.immutable(), state, center, zone.radius);
                        default -> {
                        }
                    }
                }
            }
        }
        if (fireInside) {
            // Pushed out evenly, the fire spreads around the whole edge at once.
            for (BlockPos spot : ring(zone, Blocks.FIRE.defaultBlockState())) {
                level.setBlock(spot, BaseFireBlock.getState(level, spot), Block.UPDATE_ALL);
            }
        }
    }

    /** Creatures and things of the element are pushed out past the edge; with no element (vis), any creature. */
    private static void pushCreatures(Zone zone, Vec3 center) {
        AABB box = new AABB(center.x - zone.radius, Math.floor(center.y), center.z - zone.radius,
                center.x + zone.radius, Math.floor(center.y) + Barrier.HEIGHT, center.z + zone.radius);
        boolean any = zone.element == null || zone.element.isBalanced();
        List<Entity> inside = new ArrayList<>();
        inside.addAll(zone.level.getEntitiesOfClass(LivingEntity.class, box, living -> living != zone.caster
                && living.isAlive() && !living.isSpectator() && ElementAffinityService.matches(zone.element, living)));
        inside.addAll(zone.level.getEntitiesOfClass(ItemEntity.class, box,
                item -> any || ElementAffinityService.matchesItem(zone.element, item.getItem())));
        for (Entity entity : inside) {
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            if (dx * dx + dz * dz > zone.radius * zone.radius) {
                continue;
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            Vec3 out = length < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(dx / length, 0.0D, dz / length);
            entity.setDeltaMovement(out.x * PUSH_SPEED, PUSH_LIFT, out.z * PUSH_SPEED);
            entity.hurtMarked = true;
        }
    }

    /**
     * Fire inside goes out; the sweep then spreads it around the edge (true when there was fire). A lava source is
     * poured out straight past the edge.
     */
    private static boolean keepFireOut(ServerLevel level, BlockPos pos, BlockState state, Vec3 center, double radius) {
        boolean fire = state.is(BlockTags.FIRE);
        boolean lava = state.getFluidState().is(FluidTags.LAVA);
        if (!fire && !lava) {
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (fire || !state.getFluidState().isSource()) {
            return fire; // what flows in is only turned back; the source it comes from is moved on its own
        }
        BlockPos edge = edgeOf(pos, center, radius);
        for (BlockPos spot : new BlockPos[]{edge, edge.below(), edge.above()}) {
            if (level.isLoaded(spot) && level.getBlockState(spot).canBeReplaced()) {
                level.setBlock(spot, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                return false;
            }
        }
        return false;
    }

    /** Water inside is poured out past the edge; what flows back in is turned back every sweep. */
    private static void keepWaterOut(ServerLevel level, BlockPos pos, BlockState state, Vec3 center, double radius) {
        if (!state.is(Blocks.WATER)) {
            return;
        }
        boolean source = state.getFluidState().isSource();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!source) {
            return;
        }
        BlockPos edge = edgeOf(pos, center, radius);
        for (BlockPos spot : new BlockPos[]{edge, edge.above()}) {
            if (level.isLoaded(spot) && level.getBlockState(spot).canBeReplaced()
                    && !level.getBlockState(spot).is(Blocks.WATER)) {
                level.setBlock(spot, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }
    }

    /**
     * Earth standing inside is pushed out and piles up just past the edge into a wall; where the wall is already full it
     * is left there as a block to pick up.
     */
    private static void pushEarth(ServerLevel level, BlockPos pos, BlockState state, Vec3 center, double radius) {
        if (!RevelationSight.carries(Revelation.Kind.FIRMO, state) || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0F) {
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos edge = edgeOf(pos, center, radius);
        for (int up = 0; up < Barrier.HEIGHT + 1; up++) {
            BlockPos spot = edge.above(up);
            if (level.isLoaded(spot) && level.getBlockState(spot).canBeReplaced()) {
                level.setBlock(spot, state, Block.UPDATE_ALL);
                return;
            }
        }
        Block.popResource(level, edge.above(Barrier.HEIGHT), new ItemStack(state.getBlock()));
    }

    private static BlockPos edgeOf(BlockPos pos, Vec3 center, double radius) {
        double[] edge = Barrier.edge(center.x, center.z, pos.getX() + 0.5D, pos.getZ() + 0.5D, radius);
        return BlockPos.containing(edge[0], pos.getY(), edge[1]);
    }

    /** A ring of the element's particles along the edge, so the zone can be seen while it lasts. */
    private static void showEdge(Zone zone, Vec3 center, int points) {
        ParticleOptions particle = switch (zone.element) {
            case IGNI -> ParticleTypes.FLAME;
            case AQUA -> ParticleTypes.SPLASH;
            case FIRMO -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
            default -> ParticleTypes.END_ROD;
        };
        int count = Math.max(points, (int) (points * zone.radius / Barrier.DEFAULT_RADIUS));
        for (int i = 0; i < count; i++) {
            double angle = 2.0D * Math.PI * i / count;
            zone.level.sendParticles(particle, center.x + Math.cos(angle) * zone.radius, Math.floor(center.y) + 0.2D,
                    center.z + Math.sin(angle) * zone.radius, 1, 0.02D, 0.05D, 0.02D, 0.0D);
        }
    }
}

package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.magic.lexicon.Traits;
import com.elderlexicon.mod.spell.ElementPersistence;
import com.elderlexicon.mod.spell.SpellFlow;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.WindLift;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What Vocant makes appear where it lands, by the nature of the element ({@link ElementPersistence}).
 * <ul>
 *   <li><b>Permanent</b> (water, earth, mud, magma): the matter is laid and stays. With chronos it stays for that
 *       window, stands back up if broken meanwhile, and is gone when the window ends.</li>
 *   <li><b>Ephemeral</b> (fire, air, Vis, lightning, steam, mist, dust): it acts and goes. With chronos it keeps
 *       acting on that spot for the whole window, the same energy spread over it (book 4.3.2), then goes.</li>
 * </ul>
 */
final class Invocation {

    private static final int PULSE_TICKS = 5;
    private static final int REGENERATION_TICKS = 20;
    private static final int LIGHTNING_TICKS = 20;
    private static final double FIELD_BASE_RADIUS = 1.5D;

    private Invocation() {
    }

    /**
     * Where an invocation acts, asked again on every pulse so it can follow marked things while chronos lasts.
     * {@code atFeet} says it rises from under what it is on (a place written with a mark): solid matter is laid
     * under the feet rather than on them, and air blows up.
     */
    interface Where {
        Optional<SpellEffects.SpellImpact> now();

        boolean atFeet();

        static Where fixed(SpellEffects.SpellImpact impact) {
            return new Where() {
                @Override
                public Optional<SpellEffects.SpellImpact> now() {
                    return Optional.of(impact);
                }

                @Override
                public boolean atFeet() {
                    return false;
                }
            };
        }
    }

    /** @param windowTicks the chronos window; zero or less is the element's own timing */
    static void invoke(ServerPlayer player, VitaElement element, String elementRuneId, Where where,
                       double power, int windowTicks) {
        String rune = elementRuneId == null ? element.runeId() : elementRuneId.toLowerCase(Locale.ROOT);
        Optional<SpellEffects.SpellImpact> first = where.now();
        if (first.isEmpty()) {
            return;
        }
        if (ElementPersistence.of(rune) == ElementPersistence.PERMANENT) {
            permanent(player, element, rune, where, first.get(), power, windowTicks);
        } else {
            ephemeral(player, element, rune, where, first.get(), power, windowTicks);
        }
    }

    // ------------------------------------------------------------------ image

    /**
     * The image of what vocant would make ({@code igni surgit vocant}): the same matter on the same spots, only seen and
     * never touched, for {@code ticks}. What has no matter to show (air, Vis, lightning) shows its light alone: its
     * particles for the whole time, and a lightning that strikes nothing (docs/surgit-visao-design.md, section 4).
     */
    static void imagine(ServerPlayer player, VitaElement element, String elementRuneId, SpellEffects.SpellImpact impact,
                        double power, int ticks) {
        ServerLevel level = player.serverLevel();
        String rune = elementRuneId == null ? element.runeId() : elementRuneId.toLowerCase(Locale.ROOT);
        BlockState image = imageMatterOf(rune);
        if (image != null) {
            BlockPos center = image.is(Blocks.FIRE)
                    ? (impact.entity() != null ? impact.entity().blockPosition() : SpellEffects.firePlacementPos(impact))
                    : groundOf(impact, image, false);
            if (center == null) {
                return;
            }
            int count = SpellEffects.blocksFor(power);
            for (BlockPos pos : count <= 1 ? List.of(center) : SpellEffects.groundSpots(level, center, count)) {
                if (level.isLoaded(pos) && level.isEmptyBlock(pos) && !SpellIllusions.imageAt(level, pos)) {
                    SpellIllusions.showBlock(level, pos, image, ticks);
                }
            }
            return;
        }
        Vec3 center = impact.location();
        double radius = FIELD_BASE_RADIUS + 0.5D * Math.sqrt(SpellEffects.blocksFor(power));
        Vec3 direction = windDirection(player, impact);
        boolean strikes = SourceLooks.traits(rune).strikes();
        for (int elapsed = 0; elapsed <= ticks; elapsed += PULSE_TICKS) {
            final int tick = elapsed;
            SpellEffects.schedule(level, elapsed, () -> {
                if (isWind(rune)) {
                    showWind(level, center, radius, direction, 4);
                } else {
                    show(level, rune, element, center, power);
                }
                if (strikes && tick % LIGHTNING_TICKS == 0) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(center);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                }
            });
        }
    }

    /**
     * The matter a source's image shows as a block, or null when it has none to show (air, Vis, lightning): the block
     * the lexicon gives its image, or its matter when it is permanent.
     */
    static BlockState imageMatterOf(String rune) {
        if (rune == null) {
            return null;
        }
        return SourceLooks.block(SourceLooks.traits(rune).imageBlock()).orElse(null);
    }

    // ------------------------------------------------------------------ permanent

    private static void permanent(ServerPlayer player, VitaElement element, String rune, Where where,
                                  SpellEffects.SpellImpact impact, double power, int windowTicks) {
        ServerLevel level = player.serverLevel();
        // Thrown at a creature, matter strikes it; called at its feet, it only appears under it.
        if (impact.entity() != null && !where.atFeet()) {
            SpellEffects.applyToEntity(player, element, rune, impact.entity(), power, false);
        }
        if (SourceLooks.traits(rune).quenches() && impact.entity() == null && impact.blockPos() != null
                && SpellEffects.applyAquaBlockEffect(level, impact.blockPos())) {
            return; // it filled a cauldron or put out a fire: the water went there
        }
        BlockState matter = matterOf(rune);
        List<BlockPos> laid = new ArrayList<>(lay(level, element, groundOf(impact, matter, where.atFeet()), matter,
                SpellEffects.blocksFor(power)));
        if (windowTicks <= 0) {
            return;
        }
        // While the window lasts it follows what it was called on (laying more under its feet as it moves) and stands
        // back up where it was broken; when the window ends, all of it is gone.
        for (int elapsed = PULSE_TICKS; elapsed < windowTicks; elapsed += PULSE_TICKS) {
            final boolean regenerate = elapsed % REGENERATION_TICKS == 0;
            SpellEffects.schedule(level, elapsed, () -> {
                if (where.atFeet()) {
                    where.now().ifPresent(now -> {
                        for (BlockPos pos : lay(level, element, groundOf(now, matter, true), matter, 1)) {
                            if (!laid.contains(pos)) {
                                laid.add(pos);
                            }
                        }
                    });
                }
                if (regenerate) {
                    for (BlockPos pos : laid) {
                        if (level.isLoaded(pos) && level.isEmptyBlock(pos)) {
                            level.setBlock(pos, matter, 3);
                        }
                    }
                }
            });
        }
        SpellEffects.schedule(level, windowTicks, () -> {
            for (BlockPos pos : laid) {
                if (level.isLoaded(pos) && level.getBlockState(pos).is(matter.getBlock())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        });
    }

    /** The block a permanent source lays, as the lexicon says of it (loose soil when it names none). */
    private static BlockState matterOf(String rune) {
        return SourceLooks.block(SourceLooks.traits(rune).matterBlock()).orElse(Blocks.DIRT.defaultBlockState());
    }

    /**
     * Where matter lands: on the face of the block it hit, or where it stopped in the air. On a creature it hit, at
     * its feet; called at a marked creature's feet, solid matter goes under them (water still at the feet).
     */
    private static BlockPos groundOf(SpellEffects.SpellImpact impact, BlockState matter, boolean atFeet) {
        if (impact.entity() != null || atFeet) {
            BlockPos feet = impact.entity() != null ? impact.entity().blockPosition() : BlockPos.containing(impact.location());
            return atFeet && !matter.is(Blocks.WATER) ? feet.below() : feet;
        }
        if (impact.blockPos() != null) {
            BlockPos above = SpellEffects.firePlacementPos(impact);
            return above == null ? impact.blockPos() : above;
        }
        return BlockPos.containing(impact.location());
    }

    private static List<BlockPos> lay(ServerLevel level, VitaElement element, BlockPos center, BlockState matter,
                                      int count) {
        List<BlockPos> spots = count <= 1 ? List.of(center) : SpellEffects.groundSpots(level, center, count);
        List<BlockPos> laid = new ArrayList<>();
        for (BlockPos written : spots) {
            // Inside an impediunt of this element the matter is pushed out to the edge (docs/impediunt-design.md).
            List<BlockPos> targets = ImpediuntZones.forbids(level, written, element)
                    ? ImpediuntZones.pushedOut(level, written, element, matter) : List.of(written);
            for (BlockPos pos : targets) {
                if (level.isLoaded(pos) && level.isEmptyBlock(pos)) {
                    level.setBlock(pos, matter, 3);
                    laid.add(pos.immutable());
                }
            }
        }
        return laid;
    }

    // ------------------------------------------------------------------ ephemeral

    private static void ephemeral(ServerPlayer player, VitaElement element, String rune, Where where,
                                  SpellEffects.SpellImpact impact, double power, int windowTicks) {
        ServerLevel level = player.serverLevel();
        Traits traits = SourceLooks.traits(rune);
        boolean laysFire = traits.kindles();
        boolean wind = isWind(rune);
        double energy = power * EmissionRecorder.DEFAULT_QUANTITY_UMU;
        double radius = FIELD_BASE_RADIUS + 0.5D * Math.sqrt(SpellEffects.blocksFor(power));
        if (windowTicks <= 0) {
            // Its own short life: fire burns out, air blows once, Vis glows a moment, lightning strikes once.
            Vec3 center = impact.location();
            if (wind) {
                blow(level, center, radius, energy, 1, windDirection(player, impact));
                if (traits.windStrikes() && impact.entity() != null && !where.atFeet()) {
                    SpellEffects.applyToEntity(player, element, rune, impact.entity(), power, false);
                }
            } else if (laysFire) {
                SpellEffects.applyElementEffect(player, element, rune, impact, power);
            } else if (impact.entity() != null && traits.touches()) {
                SpellEffects.applyToEntity(player, element, rune, impact.entity(), power, false);
            }
            show(level, rune, element, center, power);
            if (traits.strikes()) {
                strike(level, player, center);
            }
            return;
        }

        List<BlockPos> fires = new ArrayList<>();
        if (laysFire) {
            lightFires(level, impact, SpellEffects.blocksFor(power), fires);
        }
        // Held open, it flows as strongly as in the default two seconds for the whole window (SpellFlow).
        double flowing = SpellFlow.total(power, windowTicks);
        double flowingEnergy = flowing * EmissionRecorder.DEFAULT_QUANTITY_UMU;
        int pulses = windowTicks / PULSE_TICKS + 1;
        double share = flowing / pulses;
        for (int elapsed = 0; elapsed <= windowTicks; elapsed += PULSE_TICKS) {
            final int tick = elapsed;
            SpellEffects.schedule(level, elapsed, () -> where.now().ifPresent(now -> {
                Vec3 center = now.location();
                show(level, rune, element, center, Math.max(0.5D, power / Math.sqrt(pulses)));
                if (!wind && traits.touches()) {
                    // What burns or scalds spares its caster; the wind does not (see blow). Inside an impediunt of its
                    // element it is pushed out: it burns the band just past the edge and spares the middle.
                    Optional<ImpediuntZones.Edge> edge = ImpediuntZones.zoneAt(level, center, element);
                    Vec3 around = edge.map(ImpediuntZones.Edge::center).orElse(center);
                    double reach = edge.map(zone -> zone.radius() + radius).orElse(radius);
                    for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(around, around).inflate(reach),
                            candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator()
                                    && edge.map(zone -> zone.inBand(candidate.position(), radius)).orElse(true))) {
                        SpellEffects.applyToEntity(player, element, rune, living, share, true);
                    }
                }
                if (laysFire && where.atFeet()) {
                    lightFires(level, now, 1, fires); // it keeps burning at the feet of what it follows
                }
                for (BlockPos pos : fires) {
                    if (level.isLoaded(pos) && level.isEmptyBlock(pos) && !level.isEmptyBlock(pos.below())
                            && !ImpediuntZones.forbids(level, pos, VitaElement.IGNI)) { // pushed out, it burns on the edge
                        level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3); // it keeps burning while the window lasts
                    }
                }
                if (traits.strikes() && tick % LIGHTNING_TICKS == 0) {
                    strike(level, player, center);
                }
            }));
        }
        if (wind) {
            // Wind acts every tick, or gravity would win between gusts and an upward wind could never lift anything.
            for (int elapsed = 0; elapsed <= windowTicks; elapsed++) {
                SpellEffects.schedule(level, elapsed, () -> where.now().ifPresent(now ->
                        blow(level, now.location(), radius, flowingEnergy, windowTicks, windDirection(player, now))));
            }
        }
        SpellEffects.schedule(level, windowTicks + 1, () -> {
            for (BlockPos pos : fires) {
                if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.FIRE)) {
                    level.removeBlock(pos, false); // and then it goes
                }
            }
        });
    }

    /** Lights up to {@code count} fires on the ground around where it landed, remembering them to put out later. */
    private static void lightFires(ServerLevel level, SpellEffects.SpellImpact impact, int count, List<BlockPos> fires) {
        BlockPos firePos = impact.entity() != null ? impact.entity().blockPosition() : SpellEffects.firePlacementPos(impact);
        if (firePos == null) {
            return;
        }
        List<BlockPos> spots = new ArrayList<>(SpellEffects.groundSpots(level, firePos, count));
        if (spots.isEmpty() && ImpediuntZones.forbids(level, firePos, VitaElement.IGNI)) {
            spots.add(firePos); // at the feet of what it follows: still fed into the zone, to be pushed out
        }
        for (BlockPos written : spots) {
            // An impediunt pushes the fire out: it spreads around the whole edge and burns there as a ring.
            List<BlockPos> targets = ImpediuntZones.forbids(level, written, VitaElement.IGNI)
                    ? ImpediuntZones.pushedOut(level, written, VitaElement.IGNI, Blocks.FIRE.defaultBlockState())
                    : List.of(written);
            for (BlockPos pos : targets) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
                if (!fires.contains(pos)) {
                    fires.add(pos.immutable());
                }
            }
        }
    }

    /** Whether a source blows, as the lexicon says (air, mist, dust): it moves what it reaches. */
    private static boolean isWind(String rune) {
        return SourceLooks.traits(rune).wind();
    }

    /**
     * Air blows out of the surface it was called on: up from the ground, out of a wall, up from under the feet of a
     * marked creature. Called on a creature the mage aimed at, or in the open air, it blows the way the mage looks.
     */
    private static Vec3 windDirection(ServerPlayer player, SpellEffects.SpellImpact impact) {
        if (impact.face() != null) {
            return Vec3.atLowerCornerOf(impact.face().getNormal());
        }
        return player.getLookAngle().normalize();
    }

    /**
     * Air blowing along {@code direction} over everything loose in reach (creatures, the caster too, and items on
     * the ground). Without chronos it is one gust: each thing moves along it at least at the speed a throw of that
     * energy would give it ({@link MarkCost#throwWith}). With chronos it is a force (see {@link WindLift}), heavier
     * things moved less.
     */
    private static void blow(ServerLevel level, Vec3 center, double radius, double energy, int durationTicks, Vec3 direction) {
        // Inside an impediunt of air the wind is pushed out: it blows in the band just past the edge, the middle calm.
        Optional<ImpediuntZones.Edge> edge = ImpediuntZones.zoneAt(level, center, VitaElement.AURA);
        Vec3 around = edge.map(ImpediuntZones.Edge::center).orElse(center);
        double reach = edge.map(zone -> zone.radius() + radius).orElse(radius);
        if (edge.isPresent()) {
            double ring = edge.get().radius() + radius / 2.0D;
            int gusts = Math.max(8, (int) (ring * 2.0D));
            for (int gust = 0; gust < gusts; gust++) {
                double angle = 2.0D * Math.PI * gust / gusts;
                showWind(level, around.add(Math.cos(angle) * ring, 0.0D, Math.sin(angle) * ring), radius / 2.0D,
                        direction, durationTicks <= 1 ? 6 : 1);
            }
        } else {
            showWind(level, center, radius, direction, durationTicks <= 1 ? 24 : 4);
        }
        boolean updraft = direction.y > 0.7D;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(around, around).inflate(reach),
                candidate -> (candidate instanceof LivingEntity || candidate instanceof ItemEntity)
                        && candidate.isAlive() && !candidate.isSpectator()
                        && edge.map(zone -> zone.inBand(candidate.position(), radius)).orElse(true))) {
            Vec3 motion = entity.getDeltaMovement();
            if (updraft && entity instanceof ServerPlayer rider && !rider.onGround()) {
                motion = walkInTheAir(rider, motion);
                entity.setDeltaMovement(motion);
                entity.hurtMarked = true;
            }
            if (durationTicks > 1) {
                // A lasting wind is a force that fades away from what it blows out of (WindLift): an updraft holds
                // things at the height where it matches their weight instead of carrying them up forever.
                double distance = updraft ? heightAboveGround(level, entity)
                        : Math.max(0.0D, entity.position().subtract(center).dot(direction));
                double push = WindLift.acceleration(energy / durationTicks, MarkTargets.massOf(entity), distance);
                if (updraft) {
                    // Minecraft's air barely slows a fall, so without this a held thing bounces around its height
                    // (and every bounce down counts as a fall); braking the vertical speed settles it there.
                    motion = new Vec3(motion.x, motion.y * (1.0D - WindLift.DAMPING), motion.z);
                    if (push >= WindLift.GRAVITY * 0.5D) {
                        entity.resetFallDistance(); // held up by the wind: no fall is building up
                    }
                }
                entity.setDeltaMovement(motion.add(direction.scale(push)));
                entity.hasImpulse = true;
                entity.hurtMarked = true;
                continue;
            }
            double speed = MarkCost.throwWith(energy, MarkTargets.massOf(entity), MarkCost.MAX_ENTITY_SPEED,
                    durationTicks).speed();
            double along = motion.dot(direction);
            if (along >= speed) {
                continue;
            }
            // A sideways gust lifts a little, so it carries things instead of scraping them along the ground.
            double lift = durationTicks <= 1 && Math.abs(direction.y) < 0.5D ? Math.min(0.5D, 0.3D * speed) : 0.0D;
            entity.setDeltaMovement(motion.add(direction.scale(speed - along)).add(0.0D, lift, 0.0D));
            entity.hasImpulse = true;
            entity.hurtMarked = true;
        }
    }

    /** How far the thing floats above the first solid ground under it (up to 32 blocks; farther counts as 32). */
    private static double heightAboveGround(ServerLevel level, Entity entity) {
        BlockPos.MutableBlockPos cursor = entity.blockPosition().mutable();
        for (int step = 0; step <= 32; step++) {
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double top = cursor.getY() + shape.max(Direction.Axis.Y);
                if (top <= entity.getY() + 1.0E-3D) {
                    return entity.getY() - top;
                }
            }
            cursor.move(Direction.DOWN);
        }
        return 32.0D;
    }

    /** Ground walking speed, in blocks per tick; a held-up player walks on the wind at it (or sprints at 1.3x). */
    private static final double WIND_WALK_SPEED = 0.22D;

    /**
     * A player held up by an updraft steers like on the ground instead of the feeble control Minecraft gives in
     * the air: the walking keys the client reports (see {@code PlayerMotionInputPacket}) set the sideways speed.
     */
    private static Vec3 walkInTheAir(ServerPlayer rider, Vec3 motion) {
        LigabisManager manager = LigabisManager.get();
        float[] input = manager == null ? null : manager.playerInput(rider.getUUID());
        if (input == null || (Math.abs(input[0]) < 1.0E-3F && Math.abs(input[1]) < 1.0E-3F)) {
            return motion;
        }
        double yaw = Math.toRadians(rider.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = input[0] * cos - input[1] * sin;
        double z = input[1] * cos + input[0] * sin;
        double length = Math.sqrt(x * x + z * z);
        double speed = WIND_WALK_SPEED * (rider.isSprinting() ? 1.3D : 1.0D);
        return new Vec3(x / Math.max(1.0D, length) * speed, motion.y, z / Math.max(1.0D, length) * speed);
    }

    /** Air one can see: puffs rising out of the surface, the way the wind blows. */
    private static void showWind(ServerLevel level, Vec3 center, double radius, Vec3 direction, int puffs) {
        for (int puff = 0; puff < puffs; puff++) {
            double x = center.x + (level.random.nextDouble() - 0.5D) * radius * 1.5D;
            double y = center.y + 0.1D + level.random.nextDouble() * 0.3D;
            double z = center.z + (level.random.nextDouble() - 0.5D) * radius * 1.5D;
            // count 0: the offsets are the particle's own velocity, so each puff flies along the wind.
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, direction.x, direction.y, direction.z, 0.25D);
        }
    }

    private static void strike(ServerLevel level, ServerPlayer caster, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at);
            bolt.setCause(caster);
            level.addFreshEntity(bolt);
        }
    }

    /**
     * What can be seen of it: the glow the lexicon gives it where it is invoked (Vis is a faint glow one can follow, book
     * 9.4), or else the particles it flows with.
     */
    private static void show(ServerLevel level, String rune, VitaElement element, Vec3 at, double power) {
        int count = (int) Math.max(4, Math.min(60, Math.round(12.0D * power)));
        String glow = SourceLooks.traits(rune).glow();
        Optional<ParticleOptions> particle = glow != null ? SourceLooks.particle(glow)
                : SpellEffects.resolveParticle(element, rune);
        particle.ifPresent(options -> level.sendParticles(options, at.x, at.y + 0.5D, at.z, count, 0.5D, 0.5D, 0.5D, 0.02D));
    }
}

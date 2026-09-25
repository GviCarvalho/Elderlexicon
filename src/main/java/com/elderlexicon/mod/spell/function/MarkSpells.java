package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.MarkTargets.Marked;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * What the functions do when a mark is their subject ({@code docs/marcas-como-runas-design.md}):
 * summon (vocant), throw, pull and push (iactare, exsugat, impediunt), swap (transvocatio) and convert
 * (vertere). Costs are charged when the spell is cast, from where things are at that moment.
 */
final class MarkSpells {

    /** Vocant reaches where the mage aims up to this far; ubis goes beyond. */
    static final double SUMMON_RANGE = 12.0D;
    private static final double THROW_AIM_RANGE = 64.0D;
    /** Upward nudge of every throw, so things leave the ground in an arc. */
    private static final double THROW_LIFT = 0.2D;
    /** Transvocatio happens at once unless chronos sets when. */
    static final int SWAP_DELAY_TICKS = 0;
    private static final int RELEASE_LINGER_TICKS = 20;
    private static final ResourceKey<DamageType> VERTERE_VITA =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "vertere_vita"));

    private MarkSpells() {
    }

    /** Where a function acts: a point, and what is there (for the element effects). */
    record Destination(ServerLevel level, Vec3 point, @Nullable Entity entity, @Nullable BlockPos blockPos,
                       @Nullable Direction face) {

        SpellEffects.SpellImpact impact() {
            return new SpellEffects.SpellImpact(point, entity, blockPos, face);
        }
    }

    enum Push {
        TOWARD_AIM,
        TOWARD_CASTER,
        AWAY_FROM_CASTER
    }

    /** The place written with ubis or, without one, where the mage aims within {@code range}. */
    static Optional<Destination> destination(SpellContext context, Optional<SpellPlace> place, double range) {
        return destination(context.player(), place, range, false);
    }

    /**
     * Where a place written with marks is now, for spells that follow it while they last (chronos): it moves with
     * the marked things. Quiet, since it is asked again on every pulse.
     */
    static Supplier<Optional<Destination>> follower(SpellContext context, SpellPlace place, double range) {
        ServerPlayer player = context.player();
        return () -> SpellEffects.isPlayerValid(player)
                ? destination(player, Optional.of(place), range, true)
                : Optional.empty();
    }

    private static Optional<Destination> destination(ServerPlayer player, Optional<SpellPlace> place, double range,
                                                     boolean quiet) {
        ServerLevel level = player.serverLevel();
        if (place.isEmpty()) {
            SpellEffects.SpellImpact impact = SpellEffects.findImpact(player, range);
            Vec3 point = impact.location();
            if (impact.entity() != null) {
                point = impact.entity().position();
            } else if (impact.blockPos() != null && impact.face() != null) {
                point = Vec3.atBottomCenterOf(impact.blockPos().relative(impact.face()));
            }
            return Optional.of(new Destination(level, point, impact.entity(), impact.blockPos(), impact.face()));
        }
        SpellPlace written = place.get();
        return switch (written.kind()) {
            case DISTANCE -> {
                Vec3 point = player.getEyePosition().add(player.getLookAngle().normalize().scale(written.distance()));
                yield Optional.of(ground(level, point));
            }
            case DISTANCE_HEIGHT -> {
                // Along the ground the way the mage faces, and that high above the mage's feet.
                Vec3 look = player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-6D ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
                Vec3 point = player.position().add(flat.scale(written.distance())).add(0.0D, written.height(), 0.0D);
                yield Optional.of(ground(level, point));
            }
            case COORDINATES -> {
                double[] xyz = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    SpellPlace.Axis given = written.axes().get(axis);
                    if (!given.isMark()) {
                        // Whole block coordinates point at the middle of the block, as /tp does.
                        xyz[axis] = axis == 1 ? given.value() : given.value() + 0.5D;
                        continue;
                    }
                    Optional<Average> where = averageOf(player, given.mark(), quiet);
                    if (where.isEmpty()) {
                        yield Optional.empty();
                    }
                    xyz[axis] = axis == 0 ? where.get().point().x : axis == 1 ? where.get().point().y : where.get().point().z;
                }
                yield Optional.of(ground(level, new Vec3(xyz[0], xyz[1], xyz[2])));
            }
            case MARK -> {
                Optional<Average> where = averageOf(player, written.mark(), quiet);
                if (where.isEmpty()) {
                    yield Optional.empty();
                }
                Average there = where.get();
                // At the feet of what carries the mark: what is invoked there rises from under it.
                yield Optional.of(new Destination(there.level(), there.point(), there.only(),
                        BlockPos.containing(there.point()).below(), Direction.UP));
            }
        };
    }

    /** Where the carriers of a mark are, on average, and the one creature when there is only one. */
    private record Average(ServerLevel level, Vec3 point, @Nullable Entity only) { }

    /**
     * The average of where the carriers of {@code mark} stand (a creature by its feet, a block by its top), among
     * those in the mage's dimension, or else in the dimension of the closest one.
     */
    private static Optional<Average> averageOf(ServerPlayer player, String mark, boolean quiet) {
        List<Marked> found = MarkTargets.find(player.server, mark);
        if (found.isEmpty()) {
            if (!quiet) {
                tell(player, "A marca '" + mark + "' nao responde.");
            }
            return Optional.empty();
        }
        found.sort(Comparator.comparingDouble(thing -> distanceFor(player, thing)));
        ServerLevel level = found.get(0).level;
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        Entity only = null;
        for (Marked thing : found) {
            if (thing.level != level) {
                continue;
            }
            Vec3 at = thing.isBlock() ? Vec3.atBottomCenterOf(thing.blockPos.above())
                    : thing.entity != null ? thing.entity.position() : thing.position;
            sum = sum.add(at);
            count++;
            only = count == 1 ? thing.entity : null;
        }
        return Optional.of(new Average(level, sum.scale(1.0D / count), only));
    }

    private static Destination ground(ServerLevel level, Vec3 point) {
        return new Destination(level, point, null, BlockPos.containing(point).below(), Direction.UP);
    }

    // ------------------------------------------------------------------ vocant

    /**
     * {@code m1 vocant}: whatever carries the mark appears at the destination and, like any permanent matter, stays.
     * With a chronos window ({@code stayTicks} above zero) it goes back where it was when the window ends.
     */
    static void summon(SpellContext context, String mark, Optional<SpellPlace> place, int delayTicks, int stayTicks) {
        ServerPlayer player = context.player();
        Optional<Destination> found = destination(context, place, SUMMON_RANGE);
        if (found.isEmpty()) {
            return;
        }
        Destination destination = found.get();
        List<Marked> things = sameDimension(player, MarkTargets.find(player.server, mark), destination.level(), mark);
        if (things.isEmpty()) {
            return;
        }
        double cost = 0.0D;
        for (Marked thing : things) {
            cost += MarkCost.movement(thing.mass, thing.position.distanceTo(destination.point()));
        }
        context.addTotalCost(cost);

        SpellEffects.schedule(destination.level(), delayTicks, () -> MarkTargets.load(things, () -> {
            for (Marked thing : things) {
                if (!thing.present()) {
                    continue;
                }
                Vec3 origin = thing.isBlock() ? Vec3.atCenterOf(thing.blockPos) : thing.entity.position();
                Optional<BlockPos> arrived = arrive(thing, destination);
                if (arrived.isEmpty()) {
                    tell(player, "Sem espaco para trazer '" + mark + "' ate la.");
                } else if (stayTicks > 0) {
                    SpellEffects.schedule(destination.level(), stayTicks, () -> goBack(thing, arrived.get(), origin));
                }
            }
        }));
    }

    /** Brings the thing to the destination; returns where it now is (a block's new position), empty if no room. */
    private static Optional<BlockPos> arrive(Marked thing, Destination destination) {
        ServerLevel level = destination.level();
        if (thing.isBlock()) {
            Optional<BlockPos> spot = MarkMotion.freeBlockSpot(level, BlockPos.containing(destination.point()));
            spot.ifPresent(pos -> MarkMotion.place(level, pos, MarkMotion.lift(thing.level, thing.blockPos, thing.mark)));
            return spot;
        }
        Optional<Vec3> spot = MarkMotion.freeSpotFor(thing.entity, level, destination.point());
        spot.ifPresent(feet -> MarkMotion.teleport(thing.entity, feet));
        return spot.map(BlockPos::containing);
    }

    /** The chronos window of a summoned thing is over: it returns where it was (or as close as there is room). */
    private static void goBack(Marked thing, BlockPos now, Vec3 origin) {
        ServerLevel level = thing.level;
        if (thing.isBlock()) {
            if (level.getBlockState(now).isAir()) {
                return; // destroyed while summoned: nothing to send back
            }
            Optional<BlockPos> home = MarkMotion.freeBlockSpot(level, thing.blockPos);
            home.ifPresent(pos -> MarkMotion.place(level, pos, MarkMotion.lift(level, now, thing.mark)));
            return;
        }
        Entity entity = thing.entity;
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.level() != level) {
            return;
        }
        MarkMotion.freeSpotFor(entity, level, origin).ifPresent(feet -> MarkMotion.teleport(entity, feet));
    }

    // ------------------------------------------------------------------ iactare, exsugat, impediunt

    /**
     * Throws, pulls or pushes whatever carries the mark, from where it is, with {@code energy} UMU (quantum,
     * or {@link MarkCost#DEFAULT_THROW_ENERGY}). More energy than the top speed takes keeps pushing for longer.
     * A chronos window ({@code durationTicks} above zero) spreads the push over that long at a steady speed.
     */
    static void push(SpellContext context, String mark, Optional<SpellPlace> place, Push mode, double energy,
                     int durationTicks) {
        ServerPlayer player = context.player();
        List<Marked> things = sameDimension(player, MarkTargets.find(player.server, mark), player.serverLevel(), mark);
        if (things.isEmpty()) {
            return;
        }
        Vec3 aim;
        Optional<Vec3> fixedAim = Optional.empty();
        if (mode == Push.TOWARD_AIM && place.isEmpty()) {
            aim = SpellEffects.findImpact(player, THROW_AIM_RANGE).location();
        } else if (place.isPresent()) {
            Optional<Destination> destination = destination(context, place, THROW_AIM_RANGE);
            if (destination.isEmpty()) {
                return;
            }
            aim = destination.get().point();
            fixedAim = Optional.of(aim);
        } else {
            aim = player.getEyePosition();
        }

        Optional<Vec3> homing = fixedAim;
        List<Vec3> velocities = new ArrayList<>();
        List<Integer> pushes = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();
        double cost = 0.0D;
        for (Marked thing : things) {
            Vec3 direction = switch (mode) {
                case TOWARD_AIM, TOWARD_CASTER -> aim.subtract(thing.position);
                case AWAY_FROM_CASTER -> thing.position.subtract(aim);
            };
            if (direction.lengthSqr() < 1.0E-6D) {
                velocities.add(null);
                pushes.add(0);
                speeds.add(0.0D);
                continue;
            }
            MarkCost.Throw thrown = MarkCost.throwWith(energy, thing.mass,
                    thing.isBlock() ? MarkCost.MAX_BLOCK_SPEED : MarkCost.MAX_ENTITY_SPEED, durationTicks);
            velocities.add(direction.normalize().scale(thrown.speed()));
            pushes.add(thrown.pushTicks());
            speeds.add(thrown.speed());
            cost += thrown.cost();
        }
        context.addTotalCost(cost);

        MarkTargets.load(things, () -> {
            for (int index = 0; index < things.size(); index++) {
                Marked thing = things.get(index);
                Vec3 velocity = velocities.get(index);
                if (velocity == null || !thing.present()) {
                    continue;
                }
                Vec3 first = pushes.get(index) > 1 ? velocity : velocity.add(0.0D, THROW_LIFT, 0.0D);
                Entity flying = thing.isBlock()
                        ? MarkMotion.launchBlock(thing.level, thing.blockPos, thing.mark, first)
                        : thing.entity;
                if (!thing.isBlock()) {
                    MarkMotion.launch(flying, first);
                }
                Steering steering = new Steering(player, mode, homing, velocity.normalize());
                thrust(flying, speeds.get(index), steering, pushes.get(index) - 1);
            }
        });
    }

    /**
     * Where a thrust points on each tick: it follows the mage while the spell lasts. Thrown toward the aim, it
     * turns where the mage looks now (the mage's own look, when the mage is the one flying); toward a place written
     * with ubis, it homes in on it; pulled or pushed, it follows where the mage stands now.
     */
    private record Steering(ServerPlayer player, Push mode, Optional<Vec3> fixedAim, Vec3 initial) {

        Vec3 heading(Entity flying, Vec3 previous) {
            if (!SpellEffects.isPlayerValid(player)) {
                return previous;
            }
            Vec3 toward = switch (mode) {
                case TOWARD_AIM -> fixedAim.isPresent()
                        ? fixedAim.get().subtract(flying.position())
                        : flying == player
                                ? player.getLookAngle()
                                : player.pick(THROW_AIM_RANGE, 0.0F, false).getLocation().subtract(flying.position());
                case TOWARD_CASTER -> player.getEyePosition().subtract(flying.position());
                case AWAY_FROM_CASTER -> flying.position().subtract(fixedAim.orElse(player.position()));
            };
            return toward.lengthSqr() < 1.0E-6D ? previous : toward.normalize();
        }
    }

    /** Keeps a thrown thing at its speed for {@code ticks} more ticks, like a rocket, steering as it goes. */
    private static void thrust(Entity flying, double speed, Steering steering, int ticks) {
        thrust(flying, speed, steering, steering.initial(), ticks);
    }

    private static void thrust(Entity flying, double speed, Steering steering, Vec3 heading, int ticks) {
        if (ticks <= 0 || !flying.isAlive() || flying.isRemoved()) {
            return;
        }
        SpellEffects.schedule((ServerLevel) flying.level(), 1, () -> {
            if (flying.isAlive() && !flying.isRemoved()) {
                Vec3 now = steering.heading(flying, heading);
                MarkMotion.launch(flying, now.scale(speed));
                thrust(flying, speed, steering, now, ticks - 1);
            }
        });
    }

    // ------------------------------------------------------------------ transvocatio

    /**
     * {@code m1 transvocatio m2}: the two swap places. Groups pair up from the closest to the farthest (from
     * the caster); whoever is left without a pair stays. A missing side is the caster.
     */
    static void swap(SpellContext context, Optional<String> subjectMark, Optional<String> targetMark, int delayTicks) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        List<Marked> left = side(player, subjectMark);
        List<Marked> right = side(player, targetMark);
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        left.sort(Comparator.comparingDouble(thing -> distanceFor(player, thing)));
        right.sort(Comparator.comparingDouble(thing -> distanceFor(player, thing)));

        List<Marked[]> pairs = new ArrayList<>();
        double cost = 0.0D;
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            Marked a = left.get(index);
            Marked b = right.get(index);
            if (a.sameAs(b)) {
                continue;
            }
            if (a.level != b.level) {
                tell(player, "Nao da para trocar coisas de dimensoes diferentes.");
                continue;
            }
            double distance = a.position.distanceTo(b.position);
            cost += MarkCost.movement(a.mass, distance) + MarkCost.movement(b.mass, distance);
            pairs.add(new Marked[] {a, b});
        }
        if (pairs.isEmpty()) {
            return;
        }
        context.addTotalCost(cost);

        List<Marked> everything = new ArrayList<>();
        pairs.forEach(pair -> {
            everything.add(pair[0]);
            everything.add(pair[1]);
        });
        SpellEffects.schedule(level, delayTicks, () -> MarkTargets.load(everything, () -> {
            for (Marked[] pair : pairs) {
                if (pair[0].present() && pair[1].present() && !swapPair(pair[0], pair[1])) {
                    tell(player, "Sem espaco para a troca.");
                }
            }
        }));
    }

    private static List<Marked> side(ServerPlayer player, Optional<String> mark) {
        if (mark.isEmpty()) {
            List<Marked> caster = new ArrayList<>();
            caster.add(MarkTargets.caster(player));
            return caster;
        }
        List<Marked> found = MarkTargets.find(player.server, mark.get());
        if (found.isEmpty()) {
            tell(player, "A marca '" + mark.get() + "' nao responde.");
        }
        return found;
    }

    private static boolean swapPair(Marked a, Marked b) {
        ServerLevel level = a.level;
        if (!a.isBlock() && !b.isBlock()) {
            Vec3 aFeet = a.entity.position();
            Vec3 bFeet = b.entity.position();
            MarkMotion.teleport(a.entity, bFeet);
            MarkMotion.teleport(b.entity, aFeet);
            return true;
        }
        if (a.isBlock() && b.isBlock()) {
            MarkMotion.LiftedBlock aBlock = MarkMotion.lift(level, a.blockPos, a.mark);
            MarkMotion.LiftedBlock bBlock = MarkMotion.lift(level, b.blockPos, b.mark);
            MarkMotion.place(level, b.blockPos, aBlock);
            MarkMotion.place(level, a.blockPos, bBlock);
            return true;
        }
        Marked block = a.isBlock() ? a : b;
        Marked entity = a.isBlock() ? b : a;
        Optional<BlockPos> spot = MarkMotion.freeBlockSpot(level, BlockPos.containing(entity.entity.position()));
        if (spot.isEmpty()) {
            return false;
        }
        MarkMotion.LiftedBlock lifted = MarkMotion.lift(level, block.blockPos, block.mark);
        MarkMotion.teleport(entity.entity, Vec3.atBottomCenterOf(block.blockPos));
        MarkMotion.place(level, spot.get(), lifted);
        return true;
    }

    // ------------------------------------------------------------------ vertere

    /** {@code m1 vertere m2}: everything that carried m1 now carries m2. */
    static void rename(SpellContext context, String mark, String newMark, int delayTicks) {
        ServerPlayer player = context.player();
        List<Marked> things = MarkTargets.find(player.server, mark);
        if (things.isEmpty()) {
            tell(player, "A marca '" + mark + "' nao responde.");
            return;
        }
        SpellEffects.schedule(player.serverLevel(), delayTicks, () -> MarkTargets.load(things, () -> {
            int renamed = 0;
            for (Marked thing : things) {
                if (!thing.present()) {
                    continue;
                }
                boolean done = thing.isBlock()
                        ? MarkHelper.applyMark(thing.level, thing.blockPos, newMark)
                        : MarkHelper.applyMark(thing.entity, newMark);
                if (done) {
                    renamed++;
                }
            }
            tell(player, renamed + " com a marca '" + mark + "' agora levam '" + newMark + "'.");
        }));
    }

    /**
     * {@code m1 vertere aqua}: the matter of what carries m1 becomes the element, keeping its UMU. A living
     * thing's Vita (its health, 5 UMU per point) is converted: all of it, or only {@code limit} UMU when a
     * quantum says so ({@code m1 quantum 20 vertere aqua} takes 4 health). Whole, it dies unless a totem saves
     * it. A block or an item on the ground is converted whole, so a quantum smaller than it leaves it alone;
     * a container spills what it held.
     */
    static void convert(SpellContext context, String mark, VitaElement element, String elementRuneId, OptionalDouble limit,
                        int delayTicks) {
        ServerPlayer player = context.player();
        List<Marked> things = MarkTargets.find(player.server, mark);
        if (things.isEmpty()) {
            tell(player, "A marca '" + mark + "' nao responde.");
            return;
        }
        double estimate = 0.0D;
        for (Marked thing : things) {
            double vita = MarkCost.vitaOf(thing.entity instanceof LivingEntity living
                    ? living.getHealth() + living.getAbsorptionAmount()
                    : thing.mass);
            if (limit.isPresent()) {
                boolean living = thing.entity instanceof LivingEntity || (!thing.isBlock() && thing.entity == null);
                vita = living ? Math.min(vita, limit.getAsDouble()) : (limit.getAsDouble() >= vita ? vita : 0.0D);
            }
            estimate += vita;
        }
        context.addTotalCost(estimate);

        SpellEffects.schedule(player.serverLevel(), delayTicks, () -> MarkTargets.load(things, () -> {
            for (Marked thing : things) {
                if (!thing.present()) {
                    continue;
                }
                Vec3 at = thing.isBlock() ? Vec3.atCenterOf(thing.blockPos) : thing.entity.position();
                double released = convertOne(player, thing, limit);
                if (released > 0.0D) {
                    EmissionRecorder.pointAt(context, element, at, released, RELEASE_LINGER_TICKS);
                    BlockPos ground = BlockPos.containing(at).below();
                    SpellEffects.applyElementEffect(player, element, elementRuneId,
                            new SpellEffects.SpellImpact(at, null, ground, Direction.UP),
                            released / EmissionRecorder.DEFAULT_QUANTITY_UMU);
                }
            }
        }));
    }

    private static double convertOne(ServerPlayer player, Marked thing, OptionalDouble limit) {
        if (!(thing.entity instanceof LivingEntity)) {
            double whole = MarkCost.vitaOf(thing.mass);
            if (limit.isPresent() && limit.getAsDouble() < whole) {
                tell(player, "Quantum pequeno demais para converter um bloco ou item inteiro (" + Math.round(whole) + " UMU).");
                return 0.0D;
            }
        }
        if (thing.isBlock()) {
            thing.level.destroyBlock(thing.blockPos, false, player);
            return MarkCost.vitaOf(thing.mass);
        }
        Entity entity = thing.entity;
        if (entity instanceof LivingEntity living) {
            float before = living.getHealth() + living.getAbsorptionAmount();
            float amount = limit.isPresent()
                    ? (float) Math.min(before, limit.getAsDouble() / MarkCost.UMU_PER_HEALTH)
                    : before;
            if (amount <= 0.0F || !living.hurt(vertereSource(living, player), amount)) {
                return 0.0D;
            }
            // A totem leaves it alive with a little health; only what was really taken becomes the element.
            float after = living.isAlive() ? living.getHealth() + living.getAbsorptionAmount() : 0.0F;
            return MarkCost.vitaOf(Math.max(0.0F, before - after));
        }
        if (entity instanceof ItemEntity) {
            entity.discard();
            return MarkCost.vitaOf(thing.mass);
        }
        return 0.0D;
    }

    private static DamageSource vertereSource(Entity target, ServerPlayer caster) {
        Holder<DamageType> holder = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(VERTERE_VITA);
        return new DamageSource(holder, caster, caster);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Marked> sameDimension(ServerPlayer player, List<Marked> things, ServerLevel level, String mark) {
        if (things.isEmpty()) {
            tell(player, "A marca '" + mark + "' nao responde.");
            return things;
        }
        List<Marked> here = new ArrayList<>();
        for (Marked thing : things) {
            if (thing.level == level) {
                here.add(thing);
            }
        }
        if (here.size() < things.size()) {
            tell(player, "Parte de '" + mark + "' esta em outra dimensao e nao responde.");
        }
        return here;
    }

    private static double distanceFor(ServerPlayer player, Marked thing) {
        return thing.level == player.serverLevel() ? thing.position.distanceToSqr(player.position()) : Double.MAX_VALUE;
    }

    static void tell(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}

package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.sight.Illusion;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Functions that work with the image of their source, surgit written right before them: they take the light of the
 * source and leave its matter alone ({@code docs/surgit-visao-design.md}, section 4). Everything lasts two seconds, or
 * what chronos asks, and costs a tenth of the matter it shows for every two seconds it lasts.
 * <ul>
 *   <li>{@code igni surgit vocant}: an image of fire where vocant would lay it; {@code m1 surgit vocant} an image of m1.</li>
 *   <li>{@code igni surgit iactare}: a stream one sees, that neither burns nor pushes.</li>
 *   <li>{@code igni quantum -10 surgit vocant}: draws the light off the fire nearby; it burns on, unseen. Images of fire are gone.</li>
 *   <li>{@code igni surgit impediunt}: pushes the fire's image away; the same, the light flying off instead.</li>
 *   <li>{@code igni surgit vertere aqua}: the fire nearby looks like water; it is still fire.</li>
 * </ul>
 */
public final class ImageSpells {

    private static final int STREAM_STEP_TICKS = 5;
    private static final double STREAM_LENGTH = 8.0D;

    private ImageSpells() {
    }

    private static Double chronos(SpellAction action) {
        return action.seconds().isPresent() ? action.seconds().getAsDouble() : null;
    }

    private static double energy(SpellAction action) {
        return action.quantity().orElse(EmissionRecorder.DEFAULT_QUANTITY_UMU);
    }

    /** The image of what vocant would bring: an element's matter or light, or what bears a mark. */
    public static void vocant(SpellContext context, VitaElement element, SpellAction action) {
        ServerPlayer player = context.player();
        int ticks = Illusion.ticks(chronos(action));
        Optional<MarkSpells.Destination> destination = MarkSpells.destination(context, action.place(), MarkSpells.SUMMON_RANGE);
        if (destination.isEmpty()) {
            return;
        }
        ServerLevel level = destination.get().level();
        SpellEffects.SpellImpact impact = destination.get().impact();
        Optional<String> mark = action.subjectMark();
        if (mark.isPresent()) {
            int shown = 0;
            for (MarkTargets.Marked thing : MarkTargets.find(player.server, mark.get())) {
                if (thing.entity != null) {
                    SpellIllusions.showThing(level, thing.entity, destination.get().point(), ticks);
                    shown++;
                } else if (thing.blockPos != null) {
                    BlockPos at = BlockPos.containing(destination.get().point());
                    if (level.isEmptyBlock(at)) {
                        SpellIllusions.showBlock(level, at, thing.level.getBlockState(thing.blockPos), ticks);
                        shown++;
                    }
                }
            }
            if (shown == 0) {
                MarkSpells.tell(player, "A marca '" + mark.get() + "' nao responde.");
            }
            context.addTotalCost(Illusion.cost(EmissionRecorder.DEFAULT_QUANTITY_UMU, Illusion.seconds(chronos(action))) * shown);
            return;
        }
        double energy = energy(action);
        context.addTotalCost(Illusion.cost(energy, Illusion.seconds(chronos(action))));
        Invocation.imagine(player, element, context.elementRuneId(), impact, energy / EmissionRecorder.DEFAULT_QUANTITY_UMU,
                ticks);
    }

    /** A stream one sees following the aim, that neither burns nor pushes; with a mark, an image of it thrown. */
    public static void iactare(SpellContext context, VitaElement element, SpellAction action) {
        ServerPlayer player = context.player();
        if (action.subjectMark().isPresent()) {
            Optional<MarkSpells.Destination> aim = MarkSpells.destination(context, action.place(), MarkSpells.THROW_AIM_RANGE);
            aim.ifPresent(destination -> sendImages(context, action, thing -> destination.point()));
            return;
        }
        ServerLevel level = player.serverLevel();
        int ticks = Illusion.ticks(chronos(action));
        double energy = energy(action);
        context.addTotalCost(Illusion.cost(energy, Illusion.seconds(chronos(action))));
        Optional<ParticleOptions> particle = SpellEffects.resolveParticle(element, context.elementRuneId());
        if (particle.isEmpty()) {
            return;
        }
        int count = (int) Math.max(2, Math.min(24, Math.round(2.0D * energy / EmissionRecorder.DEFAULT_QUANTITY_UMU)));
        for (int elapsed = 0; elapsed <= ticks; elapsed += STREAM_STEP_TICKS) {
            SpellEffects.schedule(level, elapsed, () -> {
                if (!SpellEffects.isPlayerValid(player)) {
                    return;
                }
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                for (double along = 1.0D; along <= STREAM_LENGTH; along += 0.5D) {
                    Vec3 at = eye.add(look.scale(along));
                    level.sendParticles(particle.get(), at.x, at.y, at.z, count / 4 + 1, 0.08D, 0.08D, 0.08D, 0.01D);
                }
            });
        }
    }

    /** Draws the light off the source nearby: it stays, unseen, and its images are gone; with a mark, its image comes. */
    public static void absorb(SpellContext context, VitaElement element, SpellAction action) {
        if (action.subjectMark().isPresent()) {
            ServerPlayer player = context.player();
            sendImages(context, action, thing -> player.position());
            return;
        }
        takeLight(context, element, action, true);
    }

    /** Pushes the source's image away: it stays, unseen, and its images are gone; with a mark, its image is pushed off. */
    public static void repel(SpellContext context, VitaElement element, SpellAction action) {
        if (action.subjectMark().isPresent()) {
            ServerPlayer player = context.player();
            sendImages(context, action, thing -> {
                Vec3 away = thing.position.subtract(player.position());
                Vec3 unit = away.lengthSqr() < 1.0E-6D ? player.getLookAngle() : away.normalize();
                return thing.position.add(unit.scale(MarkSpells.SUMMON_RANGE));
            });
            return;
        }
        takeLight(context, element, action, false);
    }

    /**
     * Sends an image of every loaded thing bearing the mark from where it is to where {@code landing} says, at the
     * speed a throw of the spell's energy would give it (the thing itself stays put), to stand there until the time is
     * up.
     */
    private static void sendImages(SpellContext context, SpellAction action,
                                   java.util.function.Function<MarkTargets.Marked, Vec3> landing) {
        ServerPlayer player = context.player();
        String mark = action.subjectMark().orElseThrow();
        double seconds = Illusion.seconds(chronos(action));
        int ticks = Illusion.ticks(chronos(action));
        double energy = action.quantity().orElse(com.elderlexicon.mod.spell.mark.MarkCost.DEFAULT_THROW_ENERGY);
        int sent = 0;
        for (MarkTargets.Marked thing : MarkTargets.find(player.server, mark)) {
            if (thing.entity == null || thing.level != player.serverLevel()) {
                continue;
            }
            double speed = com.elderlexicon.mod.spell.mark.MarkCost.throwWith(energy, thing.mass,
                    com.elderlexicon.mod.spell.mark.MarkCost.MAX_ENTITY_SPEED).speed();
            SpellIllusions.showThing(player.serverLevel(), thing.entity, thing.position, landing.apply(thing),
                    Math.max(0.2D, speed), ticks);
            sent++;
        }
        if (sent == 0) {
            MarkSpells.tell(player, "A marca '" + mark + "' nao responde.");
        }
        context.addTotalCost(Illusion.cost(EmissionRecorder.DEFAULT_QUANTITY_UMU, seconds) * sent);
    }

    private static void takeLight(SpellContext context, VitaElement element, SpellAction action, boolean towardMage) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Revelation.Kind kind = Revelation.Kind.ofSource(context.elementRuneId());
        double seconds = Illusion.seconds(chronos(action));
        Vec3 center = player.position();
        SpellIllusions.dissipate(level, center, Illusion.REACH, kind);
        Optional<ParticleOptions> particle = SpellEffects.resolveParticle(element, context.elementRuneId());
        double owed = 0.0D;
        for (BlockPos pos : sourcesNear(level, player, kind)) {
            owed += SpellVisibility.hideBlock(player, level, pos, seconds);
            particle.ifPresent(options -> {
                Vec3 from = Vec3.atCenterOf(pos);
                Vec3 way = towardMage ? player.getEyePosition().subtract(from) : from.subtract(player.getEyePosition());
                Vec3 unit = way.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : way.normalize();
                // count 0: the offsets are the particle's own velocity, so the light flies toward or away from the mage.
                level.sendParticles(options, from.x, from.y, from.z, 0, unit.x, unit.y, unit.z, 0.3D);
            });
        }
        context.addTotalCost(owed);
    }

    /** The source nearby looks like the element written after vertere; it is still what it was. */
    public static void disguise(SpellContext context, SpellAction action) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Revelation.Kind kind = Revelation.Kind.ofSource(context.elementRuneId());
        double seconds = Illusion.seconds(chronos(action));
        BlockState look = Invocation.imageMatterOf(action.targetRuneId());
        double owed = 0.0D;
        for (BlockPos pos : sourcesNear(level, player, kind)) {
            owed += SpellVisibility.hideBlock(player, level, pos, seconds);
            if (look != null) {
                SpellIllusions.showBlock(level, pos, look, Illusion.ticks(seconds));
                owed += Illusion.cost(EmissionRecorder.DEFAULT_QUANTITY_UMU, seconds) / 10.0D;
            }
        }
        context.addTotalCost(owed);
    }

    /** Blocks within reach of the mage that carry the element sought. */
    private static List<BlockPos> sourcesNear(ServerLevel level, ServerPlayer player, Revelation.Kind kind) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos center = player.blockPosition();
        int reach = (int) Math.ceil(Illusion.REACH);
        double reachSq = Illusion.REACH * Illusion.REACH;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (pos.distSqr(center) <= reachSq && level.isLoaded(pos)
                    && RevelationSight.carries(kind, level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }
}

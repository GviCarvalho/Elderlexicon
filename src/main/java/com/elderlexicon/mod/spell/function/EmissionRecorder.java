package com.elderlexicon.mod.spell.function;

import net.minecraft.world.entity.LivingEntity;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.scene.ElementProperties;
import com.elderlexicon.mod.spell.scene.Emission;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Registers what a function handler puts into the world on the cast's shared scene.
 */
final class EmissionRecorder {

    /** Placeholder until the quantum filter sets the amount per spell (book default: 10 UMU). */
    static final double DEFAULT_QUANTITY_UMU = 10.0D;
    private static final double BEAM_RADIUS = 0.5D;
    private static final double POINT_RADIUS = 1.0D;
    /** How far the aim is sought, for the beam to leave the hand toward it (a throw's reach). */
    private static final double AIM_REACH = 20.0D;

    private EmissionRecorder() {
    }

    /** Records a beam that leaves the caster's hand toward where it aims now. */
    static void beamFromCaster(SpellContext context, VitaElement element, double length, double energy, int lifetimeTicks) {
        LivingEntity player = context.player();
        if (player == null) {
            return;
        }
        Vec3 hand = WorldSources.handOf(player);
        Vec3 look = WorldSources.aimFrom(player, hand, AIM_REACH);
        context.scene().add(Emission.beam(
                context.sceneSpellId(),
                player.getUUID(),
                context.elementRuneId(),
                ElementProperties.forElement(element),
                energy,
                new Emission.Point(hand.x, hand.y, hand.z),
                look.x, look.y, look.z,
                length, BEAM_RADIUS, nowTick(player), lifetimeTicks));
    }

    /** Records matter that appears at a fixed spot. */
    static void pointAt(SpellContext context, VitaElement element, Vec3 center, double energy, int lifetimeTicks) {
        LivingEntity player = context.player();
        if (player == null || center == null) {
            return;
        }
        context.scene().add(Emission.point(
                context.sceneSpellId(),
                player.getUUID(),
                context.elementRuneId(),
                ElementProperties.forElement(element),
                energy,
                new Emission.Point(center.x, center.y, center.z),
                POINT_RADIUS, nowTick(player), lifetimeTicks));
    }

    private static long nowTick(LivingEntity player) {
        return player.getServer().getTickCount();
    }
}

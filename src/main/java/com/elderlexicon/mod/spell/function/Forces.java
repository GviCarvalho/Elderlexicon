package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * A force on what the verb before produced (R2 and R3 of docs/plano-materia-e-forca.md): {@code igni vocant iactare}
 * makes fire and pushes it. The product flies from where it was made, along the force, and does what it does where it
 * lands. The force verbs differ only in where they push:
 * <ul>
 *   <li>{@code iactare}: toward the point, the ubis written before it or where the mage aims;</li>
 *   <li>{@code impediunt}: away from the centre, the ubis written before it or the mage;</li>
 *   <li>turned around (a negative quantity), the other way.</li>
 * </ul>
 * The quantity is the energy of the push: more of it, faster the product flies.
 */
final class Forces {

    private static final double RANGE = 20.0D;
    /** How fast the product flies with the default energy, in blocks a tick. */
    private static final double BASE_SPEED = 1.5D;

    private Forces() {
    }

    /**
     * Pushes what the verb before handed on, if it handed anything on and this verb acts on it; false when there is
     * nothing to push, and the verb acts by itself.
     */
    static boolean onProduct(SpellContext context, SpellAction action, MarkSpells.Push push) {
        if (!action.chained()) {
            return false;
        }
        Optional<Product> handed = context.takeHandedOn();
        if (handed.isEmpty()) {
            return false;
        }
        ServerPlayer player = context.player();
        double energy = action.quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY);
        context.addTotalCost(energy - MarkCost.DEFAULT_THROW_ENERGY);
        // A place written for the force is fixed when the spell is cast; without one, the aim is read when it pushes.
        Optional<SpellPlace> place = action.place();
        Optional<MarkSpells.Destination> written = place.isPresent()
                ? MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE)
                : Optional.empty();
        if (place.isPresent() && written.isEmpty()) {
            handed.get().take(product -> product.arrive(impactOf(BlockHitResult.miss(product.at(),
                    net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.containing(product.at())))));
            return true;
        }
        double speed = Math.min(MarkCost.MAX_ENTITY_SPEED, BASE_SPEED * Math.sqrt(Math.max(0.1D, energy)
                / MarkCost.DEFAULT_THROW_ENERGY));
        handed.get().take(product -> {
            if (!SpellEffects.isPlayerValid(player) || product.level() != player.serverLevel()) {
                return;
            }
            ServerLevel level = product.level();
            Vec3 from = product.at();
            Vec3 direction = direction(player, push, from, written.map(MarkSpells.Destination::point));
            ElementOrb orb = ElementOrb.gathering(level, player, List.of(product.element()), product.umu(), 0,
                    () -> from, 1);
            level.addFreshEntity(orb);
            orb.strike(direction.scale(speed), hit -> product.arrive(impactOf(hit)));
        });
        return true;
    }

    /** Which way the force pushes what is at {@code from}, as a unit vector. */
    private static Vec3 direction(ServerPlayer player, MarkSpells.Push push, Vec3 from, Optional<Vec3> written) {
        Vec3 toward = switch (push) {
            case TOWARD_AIM -> written.orElseGet(() -> SpellEffects.findImpact(player, RANGE).location()).subtract(from);
            case TOWARD_CASTER -> written.orElseGet(player::getEyePosition).subtract(from);
            case AWAY_FROM_CASTER -> from.subtract(written.orElseGet(player::position));
        };
        // Right on the point it would be pushed to (or from), it goes the way the mage looks.
        return toward.lengthSqr() < 0.25D ? player.getLookAngle().normalize() : toward.normalize();
    }

    /** Where a product lands, read from what it struck. */
    static SpellEffects.SpellImpact impactOf(HitResult hit) {
        if (hit instanceof EntityHitResult entity) {
            return new SpellEffects.SpellImpact(hit.getLocation(), entity.getEntity(), null, null);
        }
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return new SpellEffects.SpellImpact(hit.getLocation(), null, block.getBlockPos(), block.getDirection());
        }
        return new SpellEffects.SpellImpact(hit.getLocation(), null, null, null);
    }
}

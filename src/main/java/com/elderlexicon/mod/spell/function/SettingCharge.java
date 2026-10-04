package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spelling.item.WandItem;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A source invoked into a marked wand goes into the gem set in it (docs/varinhas-design.md, 3.1): {@code vis quantum
 * 20 v1 ubis vocant} fills the wand marked v1 with 20 of the mage's vis, and {@code aqua tenet quantum 20 v1 ubis vocant}
 * fills a gem of aqua with water taken from the world. It is the same as invoking a source into any marked thing; the
 * wand is only one more. A gem takes only what it holds and only until it is full; what it does not take is invoked
 * where the wand is, as if there were no gem.
 */
final class SettingCharge {

    private static final double EPSILON = 1.0E-4D;

    private SettingCharge() {
    }

    /** A marked wand and who or what has it: a player carrying it, or the item lying on the ground. */
    record Bearer(String mark, ItemStack stack, WandItem wand, Entity holder) {

        /** Where what the gem does not take is invoked: where the wand is. */
        MarkSpells.Destination destination() {
            return new MarkSpells.Destination((ServerLevel) holder.level(), holder.position(), holder, null, null);
        }
    }

    /** The wand the place names, when the place is a mark borne by a wand with a gem set in it. */
    static Optional<Bearer> find(MinecraftServer server, Optional<SpellPlace> place) {
        if (server == null || place.isEmpty() || place.get().kind() != SpellPlace.Kind.MARK) {
            return Optional.empty();
        }
        String mark = place.get().mark();
        if (mark == null || mark.isBlank()) {
            return Optional.empty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (List<ItemStack> compartment : List.of(player.getInventory().items, player.getInventory().offhand,
                    player.getInventory().armor)) {
                for (ItemStack stack : compartment) {
                    Optional<Bearer> bearer = bearer(mark, stack, player);
                    if (bearer.isPresent()) {
                        return bearer;
                    }
                }
            }
        }
        for (MarkTargets.Marked thing : MarkTargets.find(server, mark)) {
            if (thing.entity instanceof ItemEntity lying) {
                Optional<Bearer> bearer = bearer(mark, lying.getItem(), lying);
                if (bearer.isPresent()) {
                    return bearer;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Bearer> bearer(String mark, ItemStack stack, Entity holder) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WandItem wand) || !wand.hasSetting(stack)) {
            return Optional.empty();
        }
        return MarkHelper.markForItem(stack).filter(mark::equals).map(found -> new Bearer(mark, stack, wand, holder));
    }

    /** The kind of reserve a source fills: vis for vis, the source's own for the others. */
    static String kindOf(VitaElement element) {
        return element == null || element.isBalanced() ? WandItem.VIS : element.runeId();
    }

    /** Puts up to {@code amount} of {@code element} into the gem; returns what it did not take. */
    static double charge(ServerPlayer caster, Bearer bearer, VitaElement element, double amount) {
        String kind = kindOf(element);
        double stored = bearer.wand().store(bearer.stack(), kind, amount);
        if (bearer.holder() instanceof ItemEntity lying) {
            lying.setItem(bearer.stack());
        }
        if (stored > EPSILON) {
            MarkSpells.tell(caster, "O engaste de " + bearer.mark() + " guarda " + format(stored) + " UMU de " + kind
                    + " (" + format(bearer.wand().reserve(bearer.stack(), kind)) + " / "
                    + format(bearer.wand().reserveCapacity(bearer.stack(), kind)) + ").");
        } else if (bearer.wand().reserveCapacity(bearer.stack(), kind) <= EPSILON) {
            MarkSpells.tell(caster, "O engaste de " + bearer.mark() + " nao guarda " + kind + ".");
        } else {
            MarkSpells.tell(caster, "O engaste de " + bearer.mark() + " esta cheio de " + kind + ".");
        }
        return Math.max(0.0D, amount - stored);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, Math.abs(value - Math.rint(value)) < 0.05D ? "%.0f" : "%.1f", value);
    }
}

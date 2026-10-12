package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Casters;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.module.SpellCostModule;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * A living body as a source (docs/feiticeiro-design.md, 7.4): what a mage draws out of it is a lack in it, and the lack
 * hurts it. The spirit charges for the life it takes, not for the energy it moves: five UMU for each point of health,
 * as a bond of firmo charges for the life it moves (docs/ligabis-design.md). Paid as a bond pays, experience first, then
 * food, then the mage's own life; the spirit is literal, and a draw the mage cannot afford is taken from their flesh.
 */
final class Bodies {

    private static final double EPSILON = 1.0E-4D;
    private static final SpellCostModule PRICE = new SpellCostModule();

    private Bodies() {
    }

    /** Draws up to {@code wanted} UMU of {@code element} out of {@code body}, charging the caster the life it takes. */
    static double draw(SpellContext context, LivingEntity body, VitaElement element, double wanted) {
        LivingEntity mage = context.player();
        VitaSystem.Drawn drawn = VitaSystem.drain(body, element, wanted, mage);
        if (drawn.umu() <= EPSILON) {
            return 0.0D;
        }
        double price = drawn.life() * VitaSystem.UMU_PER_HP;
        if (price > EPSILON) {
            PRICE.payOutsideCast(mage, price,
                    context.focusActive() || SpellConduitItem.holdsReadyConduit(context.mage().orElse(null)));
            Casters.status(mage, Component.literal(String.format(Locale.ROOT,
                    "Tiraste %.1f de vida; o espírito cobrou %s UMU.", drawn.life(),
                    SpellCostCalculator.formatCost(price))));
        }
        return drawn.umu();
    }
}

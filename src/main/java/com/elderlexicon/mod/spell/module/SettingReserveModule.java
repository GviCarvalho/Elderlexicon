package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import com.elderlexicon.mod.spelling.item.WandItem;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Pays what it can of a spell out of the reserve of the gem set in a held wand (docs/varinhas-design.md), before the
 * mage pays the rest. A gem that holds the spell's source pays first; vis, which stands for the mage's own mana, pays
 * any spell. The spell still runs through the wand as it always did: the reserve only says where the energy comes from.
 */
public final class SettingReserveModule implements SpellModule {

    private static final double EPSILON = 1.0E-4D;

    @Override
    public void apply(SpellContext context) {
        ServerPlayer player = context.player();
        if (player == null || context.payableCost() <= EPSILON) {
            return;
        }
        List<String> kinds = kindsFor(context.primaryElement());
        drawFrom(context, player.getMainHandItem(), kinds);
        drawFrom(context, player.getOffhandItem(), kinds);
    }

    /** The reserves that may pay a spell of {@code element}, in the order they pay. */
    static List<String> kindsFor(VitaElement element) {
        if (element == null || element.isBalanced()) {
            return List.of(WandItem.VIS);
        }
        return List.of(element.runeId(), WandItem.VIS);
    }

    private static void drawFrom(SpellContext context, ItemStack stack, List<String> kinds) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WandItem wand)) {
            return;
        }
        for (String kind : kinds) {
            double owed = context.payableCost();
            if (owed <= EPSILON) {
                return;
            }
            context.addReserveContribution(wand.draw(stack, kind, owed));
        }
    }
}

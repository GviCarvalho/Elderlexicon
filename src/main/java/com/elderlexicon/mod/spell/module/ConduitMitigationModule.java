package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Routes payable UMU through held conduits before the cost is applied to the player.
 */
public final class ConduitMitigationModule implements SpellModule {

    private static final double EPSILON = 1.0E-4D;

    @Override
    public void apply(SpellContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            context.setConduitOverflow(context.payableCost());
            return;
        }
        double payable = context.payableCost();
        if (payable <= EPSILON) {
            context.setConduitOverflow(0.0D);
            return;
        }

        List<String> runes = context.lexemes();
        double remaining = payable;
        remaining = conductThrough(player.getMainHandItem(), remaining, player, runes);
        remaining = conductThrough(player.getOffhandItem(), remaining, player, runes);

        context.setConduitOverflow(remaining);
    }

    private double conductThrough(ItemStack stack,
                                  double requested,
                                  ServerPlayer player,
                                  List<String> runes) {
        if (requested <= EPSILON || stack.isEmpty()) {
            return requested;
        }
        if (stack.getItem() instanceof SpellConduitItem conduit) {
            return conduit.conduct(stack, requested, player, runes);
        }
        return requested;
    }
}

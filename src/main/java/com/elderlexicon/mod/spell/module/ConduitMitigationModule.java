package com.elderlexicon.mod.spell.module;

import net.minecraft.world.entity.LivingEntity;

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
        LivingEntity player = context.player();
        if (player == null) {
            context.setConduitOverflow(context.payableCost());
            return;
        }
        double payable = context.payableCost();
        // In a vivid dream nothing flows through the body (docs/aprendizado-runas-design.md).
        if (payable <= EPSILON || (player instanceof net.minecraft.server.level.ServerPlayer mage && com.elderlexicon.mod.nahva.Dreams.isDreaming(mage))) {
            context.setConduitOverflow(0.0D);
            return;
        }

        // A focus favours the runes the spell says, a shorthand counting as the runes it stands for.
        List<String> runes = context.words();
        double remaining = payable;
        remaining = conductThrough(player.getMainHandItem(), remaining, player, runes);
        remaining = conductThrough(player.getOffhandItem(), remaining, player, runes);

        context.setConduitOverflow(remaining);
    }

    private double conductThrough(ItemStack stack,
                                  double requested,
                                  LivingEntity player,
                                  List<String> runes) {
        if (requested <= EPSILON || stack.isEmpty()) {
            return requested;
        }
        if (stack.getItem() instanceof SpellConduitItem conduit) {
            return player instanceof net.minecraft.world.entity.player.Player mage
                    ? conduit.conduct(stack, requested, mage, runes) : requested;
        }
        return requested;
    }
}

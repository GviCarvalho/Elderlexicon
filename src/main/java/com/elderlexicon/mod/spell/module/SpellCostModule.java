package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import java.util.Objects;

public final class SpellCostModule implements SpellModule {

    private static final double EPSILON = 1.0E-4D;
    private static final String SATURATION_FRACTION_TAG = ExampleMod.MODID + "_umusatfraction";

    @Override
    public void apply(SpellContext context) {
        double payable = context.payableCost();
        if (payable <= EPSILON) {
            return;
        }
        consumeSpellCost(context.player(), payable, context.primaryElement());
    }

    private void consumeSpellCost(ServerPlayer player, double umuCost, com.elderlexicon.mod.vita.VitaElement element) {
        if (player == null || umuCost <= EPSILON) {
            return;
        }

        double remaining = consumeElementReserve(player, element, umuCost);
        if (remaining <= EPSILON) {
            return;
        }

        remaining = drainExperience(player, remaining);
        remaining = drainSaturation(player, remaining);

        if (remaining <= EPSILON) {
            return;
        }

        float hpDamage = (float) (remaining / com.elderlexicon.mod.vita.VitaSystem.UMU_PER_HP);
        if (hpDamage <= EPSILON) {
            return;
        }
        com.elderlexicon.mod.vita.VitaSystem.consumeLifeEnergy(player, hpDamage, element);
    }

    private double consumeElementReserve(ServerPlayer player, com.elderlexicon.mod.vita.VitaElement element, double umuCost) {
        if (player == null || umuCost <= EPSILON) {
            return umuCost;
        }
        com.elderlexicon.mod.vita.VitaElement target = element == null ? com.elderlexicon.mod.vita.VitaElement.BALANCED : element;
        if (target.isBalanced()) {
            return umuCost;
        }
        double available = com.elderlexicon.mod.vita.VitaSystem.getLifeEnergy(player, target);
        if (available <= EPSILON) {
            return umuCost;
        }
        double spent = Math.min(available, umuCost);
        com.elderlexicon.mod.vita.VitaSystem.consumeElementReserve(player, target, spent);
        return Math.max(0.0D, umuCost - spent);
    }

    private double drainExperience(ServerPlayer player, double umuCost) {
        if (umuCost <= EPSILON) {
            return umuCost;
        }
        int totalExperience = Math.max(0, player.totalExperience);
        if (totalExperience <= 0) {
            return umuCost;
        }
        double xpRequired = umuCost * com.elderlexicon.mod.vita.VitaSystem.XP_PER_UMU;
        int xpToSpend = (int) Math.min(totalExperience, Math.floor(xpRequired + 1.0E-4D));
        if (xpToSpend <= 0) {
            return umuCost;
        }
        player.giveExperiencePoints(-xpToSpend);
        double paid = xpToSpend / com.elderlexicon.mod.vita.VitaSystem.XP_PER_UMU;
        return Math.max(0.0D, umuCost - paid);
    }

    private double drainSaturation(ServerPlayer player, double umuCost) {
        if (umuCost <= EPSILON) {
            return umuCost;
        }

        FoodData foodData = player.getFoodData();
        double fractionalReserve = player.getPersistentData().getDouble(SATURATION_FRACTION_TAG);
        if (fractionalReserve > EPSILON) {
            double headroom = Math.max(0.0D, 20 - foodData.getFoodLevel());
            fractionalReserve = Math.min(fractionalReserve, headroom);
        } else {
            fractionalReserve = 0.0D;
        }
        double totalFood = foodData.getFoodLevel() + fractionalReserve;
        if (totalFood <= EPSILON) {
            return umuCost;
        }

        double paid = Math.min(umuCost, totalFood);
        double remainingFood = Math.max(0.0D, totalFood - paid);

        int newFoodLevel = (int) Math.floor(remainingFood + 1.0E-4D);
        double newFraction = remainingFood - newFoodLevel;

        int clampedFoodLevel = Math.max(0, Math.min(20, newFoodLevel));
        foodData.setFoodLevel(clampedFoodLevel);
        if (foodData.getSaturationLevel() > clampedFoodLevel) {
            foodData.setSaturation(clampedFoodLevel);
        }

        if (newFraction <= EPSILON) {
            player.getPersistentData().remove(SATURATION_FRACTION_TAG);
        } else {
            double cappedFraction = Math.min(newFraction, Math.max(0.0D, 20 - clampedFoodLevel));
            player.getPersistentData().putDouble(SATURATION_FRACTION_TAG, cappedFraction);
        }

        return Math.max(0.0D, umuCost - paid);
    }
}

package com.elderlexicon.mod.spell.module;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

public final class SpellCostModule implements SpellModule {

    private static final double EPSILON = 1.0E-4D;
    private static final String SATURATION_FRACTION_TAG = ElderLexicon.MODID + "_umusatfraction";
    /** The part of an experience point already owed but not yet taken: experience only goes in whole points. */
    private static final String EXPERIENCE_FRACTION_TAG = ElderLexicon.MODID + "_umuxpfraction";
    private static final double OVERFLOW_PAYMENT_RATIO = 0.10D;
    private final VitaGateway vita;

    public SpellCostModule() {
        this(new DefaultVitaGateway());
    }

    SpellCostModule(VitaGateway vita) {
        this.vita = vita;
    }

    @Override
    public void apply(SpellContext context) {
        double payable = context.payableCost();
        if (payable <= EPSILON) {
            return;
        }
        consumeSpellCost(context.player(), payable, context.primaryElement(), context.focusActive());
    }

    private void consumeSpellCost(ServerPlayer player, double umuCost, com.elderlexicon.mod.vita.VitaElement element,
                                  boolean focusActive) {
        if (player == null || umuCost <= EPSILON) {
            return;
        }

        double remaining = settleElementalStage(vita, player, element, umuCost, focusActive);
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
        vita.consumeLifeEnergy(player, hpDamage, element);
    }

    /** Elemental excess is drawn from the caster's Vita, so a focus skips this stage entirely. */
    static double settleElementalStage(VitaGateway vita,
                                       ServerPlayer player,
                                       com.elderlexicon.mod.vita.VitaElement element,
                                       double umuCost,
                                       boolean focusActive) {
        return focusActive ? umuCost : settleElementalCost(vita, player, element, umuCost);
    }

    static double settleElementalCost(VitaGateway vita,
                                      ServerPlayer player,
                                      com.elderlexicon.mod.vita.VitaElement element,
                                      double umuCost) {
        return consumeElementOverflow(vita, player, element, umuCost);
    }

    private static double consumeElementOverflow(VitaGateway vita,
                                                 ServerPlayer player,
                                                 com.elderlexicon.mod.vita.VitaElement element,
                                                 double umuCost) {
        if (umuCost <= EPSILON) {
            return umuCost;
        }
        com.elderlexicon.mod.vita.VitaElement target = element == null ? com.elderlexicon.mod.vita.VitaElement.BALANCED : element;
        if (target.isBalanced()) {
            return umuCost;
        }
        double requested = Math.min(umuCost, umuCost * OVERFLOW_PAYMENT_RATIO);
        if (requested <= EPSILON) {
            return umuCost;
        }
        double leftover = vita.consumeElementExcess(player, target, requested);
        double paid = requested - leftover;
        return Math.max(0.0D, umuCost - paid);
    }

    private double drainExperience(ServerPlayer player, double umuCost) {
        if (umuCost <= EPSILON) {
            return umuCost;
        }
        double carried = player.getPersistentData().getDouble(EXPERIENCE_FRACTION_TAG);
        ExperienceCharge charge = chargeExperience(Math.max(0, player.totalExperience), umuCost, carried);
        if (charge.points() > 0) {
            player.giveExperiencePoints(-charge.points());
        }
        if (charge.carried() > EPSILON) {
            player.getPersistentData().putDouble(EXPERIENCE_FRACTION_TAG, charge.carried());
        } else {
            player.getPersistentData().remove(EXPERIENCE_FRACTION_TAG);
        }
        return charge.unpaidUmu();
    }

    /**
     * What paying {@code umuCost} from {@code experience} points takes: whole points now, the fraction of a point left
     * owed for next time, and the UMU the experience could not cover. Small, frequent costs (a bond or a hidden block,
     * each second) are worth less than a point; without carrying the fraction they skipped experience and fell on food.
     */
    static ExperienceCharge chargeExperience(int experience, double umuCost, double carried) {
        if (experience <= 0) {
            return new ExperienceCharge(0, carried, umuCost);
        }
        double owed = umuCost * com.elderlexicon.mod.vita.VitaSystem.XP_PER_UMU + Math.max(0.0D, carried);
        int whole = (int) Math.floor(owed + 1.0E-6D);
        if (whole <= experience) {
            return new ExperienceCharge(whole, Math.max(0.0D, owed - whole), 0.0D);
        }
        // Not enough experience: all of it goes, and what it could not cover is left for food and life.
        double unpaid = (owed - experience) / com.elderlexicon.mod.vita.VitaSystem.XP_PER_UMU;
        return new ExperienceCharge(experience, 0.0D, Math.min(umuCost, unpaid));
    }

    /** Whole experience points to take, the fraction left owed, and the UMU still unpaid. */
    record ExperienceCharge(int points, double carried, double unpaidUmu) {
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

    /**
     * Charges a player outside of a cast (a Ligabis link paying for what it does): XP first, then food,
     * then their own life. If they do not have enough even counting their life, everything they have is
     * taken (which can kill them) and false is returned. Creative players pay nothing.
     */
    public boolean payOutsideCast(ServerPlayer player, double umuCost, boolean focusActive) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (umuCost <= EPSILON || player.isCreative()) {
            return true;
        }
        double available = Math.max(0, player.totalExperience) / com.elderlexicon.mod.vita.VitaSystem.XP_PER_UMU
                + player.getFoodData().getFoodLevel()
                + Math.max(0.0F, player.getHealth()) * com.elderlexicon.mod.vita.VitaSystem.UMU_PER_HP;
        if (umuCost > available + EPSILON) {
            consumeSpellCost(player, available, com.elderlexicon.mod.vita.VitaElement.BALANCED, focusActive);
            return false;
        }
        consumeSpellCost(player, umuCost, com.elderlexicon.mod.vita.VitaElement.BALANCED, focusActive);
        return true;
    }

    interface VitaGateway {
        double consumeElementExcess(ServerPlayer player, com.elderlexicon.mod.vita.VitaElement element, double umuAmount);

        void consumeLifeEnergy(ServerPlayer player, float hpDamage, com.elderlexicon.mod.vita.VitaElement element);
    }

    private static final class DefaultVitaGateway implements VitaGateway {

        @Override
        public double consumeElementExcess(ServerPlayer player, com.elderlexicon.mod.vita.VitaElement element, double umuAmount) {
            return com.elderlexicon.mod.vita.VitaSystem.consumeElementExcess(player, element, umuAmount);
        }

        @Override
        public void consumeLifeEnergy(ServerPlayer player, float hpDamage, com.elderlexicon.mod.vita.VitaElement element) {
            com.elderlexicon.mod.vita.VitaSystem.consumeLifeEnergy(player, hpDamage, element);
        }
    }
}

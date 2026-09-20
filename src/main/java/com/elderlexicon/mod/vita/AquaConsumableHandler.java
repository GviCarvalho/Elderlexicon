package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.vita.recovery.VitaRecoverySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Grants/removes Aqua energy when specific consumables are used.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class AquaConsumableHandler {

    private AquaConsumableHandler() {
    }

    @SubscribeEvent
    public static void handleItemConsumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER) {
            VitaRecoverySystem.handleWaterDrink(player);
        }
    }
}

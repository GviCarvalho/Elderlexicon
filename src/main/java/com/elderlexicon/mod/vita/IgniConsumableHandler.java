package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adjusts Igni reserves when hot/cold consumables are used.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class IgniConsumableHandler {

    private IgniConsumableHandler() {
    }

    @SubscribeEvent
    public static void handleItemConsumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.FIRE_RESISTANCE) {
            VitaSystem.restoreElementEnergy(player, VitaElement.IGNI, 0.8D);
            return;
        }
        if (stack.is(Items.MILK_BUCKET)) {
            VitaSystem.consumeElementReserve(player, VitaElement.IGNI, 0.8D);
        }
    }
}

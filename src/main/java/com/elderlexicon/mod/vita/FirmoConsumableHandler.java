package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.vita.recovery.VitaRecoverySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adjusts Firmo reserves when certain foods are consumed.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class FirmoConsumableHandler {

    private FirmoConsumableHandler() {
    }

    @SubscribeEvent
    public static void handleItemConsumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.is(VitaTags.Items.FIRMO_RECOVERY_FOODS)) {
            VitaRecoverySystem.handleFirmoFood(player);
        }
    }
}

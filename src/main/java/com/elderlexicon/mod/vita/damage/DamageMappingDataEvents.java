package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ExampleMod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers Vita damage datapack loaders.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class DamageMappingDataEvents {

    private DamageMappingDataEvents() {
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DamageMappingDatapackLoader());
    }
}

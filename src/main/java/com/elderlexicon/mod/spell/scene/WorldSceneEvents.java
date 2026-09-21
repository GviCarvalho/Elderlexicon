package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives {@link WorldScenes}: one step per world per server tick, and cleanup when a world unloads.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class WorldSceneEvents {

    private WorldSceneEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        WorldScenes.tick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WorldScenes.forget(level);
        }
    }
}

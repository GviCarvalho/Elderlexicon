package com.elderlexicon.mod.ligabis.world;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge events the Ligabis manager listens to. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class LigabisWorldEvents {

    /** How often, in ticks, marked blocks are checked to see whether they still exist. */
    private static final int BLOCK_CHECK_INTERVAL = 5;

    private LigabisWorldEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LigabisManager.start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LigabisManager.stop();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingDamageEvent event) {
        LigabisManager manager = LigabisManager.get();
        if (manager != null) {
            manager.onDamage(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LigabisManager manager = LigabisManager.get();
        if (manager != null) {
            manager.onDeath(event);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        LigabisManager manager = LigabisManager.get();
        if (manager != null) {
            manager.entityJoined(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        LigabisManager manager = LigabisManager.get();
        if (manager != null) {
            manager.entityLeft(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        LigabisManager manager = LigabisManager.get();
        if (manager == null) {
            return;
        }
        manager.tickIgni(level);
        manager.tickAqua(level);
        manager.tickAura(level);
        if (level.getGameTime() % BLOCK_CHECK_INTERVAL == 0) {
            manager.tickBlocks(level);
        }
    }
}

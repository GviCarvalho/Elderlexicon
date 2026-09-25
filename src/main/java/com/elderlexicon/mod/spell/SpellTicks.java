package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.ExampleMod;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Runs spell actions a number of server ticks later. Minecraft's own {@code TickTask} is no delay: the server
 * runs one as soon as it has spare time in a tick ({@code MinecraftServer.shouldRun}), which on an idle server is
 * at once. This counts real ticks instead.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class SpellTicks {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TickQueue QUEUE = new TickQueue();

    private SpellTicks() {
    }

    /** Runs {@code action} on the server thread {@code delayTicks} ticks from now; zero or less runs it right away. */
    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        if (server == null || action == null || server.isStopped()) {
            return;
        }
        if (delayTicks <= 0) {
            server.execute(action);
            return;
        }
        QUEUE.schedule(server.getTickCount(), delayTicks, action);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Runnable action : QUEUE.takeDue(event.getServer().getTickCount())) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Scheduled spell action failed", exception);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }
}

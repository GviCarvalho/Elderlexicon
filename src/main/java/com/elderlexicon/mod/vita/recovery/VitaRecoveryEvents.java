package com.elderlexicon.mod.vita.recovery;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class VitaRecoveryEvents {

    private VitaRecoveryEvents() {
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(VitaRecoveryCapability.ID, new VitaRecoveryProvider());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        Player clone = event.getEntity();
        original.reviveCaps();
        LazyOptional<VitaRecoveryTracker> oldCap = VitaRecoveryCapability.get(original);
        LazyOptional<VitaRecoveryTracker> newCap = VitaRecoveryCapability.get(clone);
        oldCap.ifPresent(oldData -> newCap.ifPresent(newData -> newData.load(oldData.save())));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VitaRecoverySystem.handleSleep(player);
    }
}

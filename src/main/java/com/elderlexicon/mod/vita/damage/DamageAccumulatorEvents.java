package com.elderlexicon.mod.vita.damage;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks the damage accumulator capability into player lifecycle events.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class DamageAccumulatorEvents {

    private DamageAccumulatorEvents() {
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(DamageAccumulatorCapability.ID, new DamageAccumulatorProvider());
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
        LazyOptional<DamageAccumulator> oldCap = DamageAccumulatorCapability.get(original);
        LazyOptional<DamageAccumulator> newCap = DamageAccumulatorCapability.get(clone);
        oldCap.ifPresent(oldData -> newCap.ifPresent(newData -> newData.load(oldData.save())));
        original.invalidateCaps();
    }
}

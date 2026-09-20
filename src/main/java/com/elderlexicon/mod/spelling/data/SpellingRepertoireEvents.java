package com.elderlexicon.mod.spelling.data;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Binds the repertoire capability to players and keeps it persistent across deaths.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class SpellingRepertoireEvents {

    private SpellingRepertoireEvents() {
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(SpellingRepertoireCapability.ID, new SpellingRepertoireProvider());
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
        LazyOptional<SpellingRepertoire> oldCap = SpellingRepertoireCapability.get(original);
        LazyOptional<SpellingRepertoire> newCap = SpellingRepertoireCapability.get(clone);
        oldCap.ifPresent(oldData -> newCap.ifPresent(newData -> newData.copyFrom(oldData)));
        original.invalidateCaps();
    }
}

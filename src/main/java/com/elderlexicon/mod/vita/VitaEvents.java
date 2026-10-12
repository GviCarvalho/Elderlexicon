package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.nahva.Dreams;
import com.elderlexicon.mod.vita.recovery.VitaRecoverySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks Vita logic into the Forge event bus.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class VitaEvents {

    private VitaEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        // In Nahvá by dream or by death the mage is a body of vis alone: the vita waits, as it was, for their return.
        if (Dreams.isDreaming(player)) {
            return;
        }
        VitaSystem.tick(player);
        VitaRecoverySystem.tick(player);
    }

    /** Every other living body (docs/feiticeiro-design.md, 7.4): ticked only while it is out of whole. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (living instanceof Player || living.level().isClientSide()) {
            return;
        }
        VitaSystem.tick(living);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VitaSystem.initialize(player);
    }
}

package com.elderlexicon.mod.item;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.client.MarkEditScreen;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.mark.MarkTarget;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Elder Brush marks scrolls too. Item frames and placed scrolls are not living things, so right-clicking them
 * never reaches {@link ElderBrushItem#interactLivingEntity}: the frame would take the brush as its item instead.
 * This catches that click first, opens the mark prompt for the scroll's carrier and stops the frame from reacting.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class ElderBrushEvents {

    private ElderBrushEvents() {
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof ElderBrushItem) || !LigabisManager.isScrollCarrier(event.getTarget())) {
            return;
        }
        if (event.getLevel().isClientSide) {
            MarkEditScreen.open(MarkTarget.entity(event.getTarget().getId()));
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }
}

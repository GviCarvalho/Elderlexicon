package com.elderlexicon.mod.spell.matter.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlockEntity;
import com.elderlexicon.mod.spell.matter.MatterBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Formless matter is seen in the colour of what it is made of: brown earth, blue water, pale air, orange fire. */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FormlessMatterColors {

    private static final int UNKNOWN = 0x9A9A9A;

    private FormlessMatterColors() {
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) {
                return UNKNOWN;
            }
            return level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless ? formless.color() : UNKNOWN;
        }, MatterBlocks.FORMLESS_SOLID.get(), MatterBlocks.FORMLESS_LIQUID.get());
    }
}

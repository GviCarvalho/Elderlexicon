package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Visibility;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.OptionalInt;

/**
 * Draws creatures seen less than whole (surgit m1 quantum N): at 0 nothing of them is drawn, not their armor, what they
 * hold or their name; between 1 and 9 they are drawn see-through, all of it at once. The mage's own body, seen from
 * outside, never fades past a ghost, so the mage knows where it stands. A revelation sees them whole.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class VisibilityRenderer {

    /** The least of itself a hidden player still sees of its own body, seen from outside. */
    private static final float OWN_GHOST = 0.25F;

    private static boolean drawingGhost;

    private VisibilityRenderer() {
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void fade(RenderLivingEvent.Pre event) {
        if (drawingGhost || ClientRevelation.active()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        OptionalInt level = ClientVisibility.of(entity.getId());
        if (level.isEmpty()) {
            return;
        }
        float opacity = Visibility.opacity(level.getAsInt());
        if (entity == Minecraft.getInstance().player) {
            opacity = Math.max(opacity, OWN_GHOST);
        }
        event.setCanceled(true);
        if (opacity <= 0.0F) {
            return;
        }
        float yaw = Mth.rotLerp(event.getPartialTick(), entity.yRotO, entity.getYRot());
        drawingGhost = true;
        try {
            ((net.minecraft.client.renderer.entity.LivingEntityRenderer) event.getRenderer()).render(entity, yaw,
                    event.getPartialTick(), event.getPoseStack(),
                    new GhostBuffers(event.getMultiBufferSource(), opacity), event.getPackedLight());
        } finally {
            drawingGhost = false;
        }
    }
}

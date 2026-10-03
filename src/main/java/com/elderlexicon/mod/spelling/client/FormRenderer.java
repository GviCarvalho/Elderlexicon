package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * A player in another body is drawn as that body (docs/particulas-design.md, stage 6): the creature of its kind, where
 * the player stands, turned as the player is and walking as the player walks. Its own hands are not drawn: the body
 * has none of a person's.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class FormRenderer {

    private FormRenderer() {
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void draw(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Optional<LivingEntity> body = ClientForms.bodyOf(player);
        if (body.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        LivingEntity shown = body.get();
        ClientForms.follow(shown, player);
        float partialTick = event.getPartialTick();
        float yaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(shown);
        renderer.render(shown, yaw, partialTick, event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight());
    }

    @SubscribeEvent
    public static void hands(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && ClientForms.bodyOf(player).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientForms.tick();
        }
    }

    @SubscribeEvent
    public static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientForms.clear();
    }
}

package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.network.IncorporationInputPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mage living in a body without a kern, on this client (docs/vita-design.md): the camera is in its eyes and turns
 * with the mouse, the walking keys, jump and the attack button go to it (the server moves it), and the mage's own
 * body stands still in its trance.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class ClientIncorporation {

    private static Entity body;
    private static int ticksLeft;
    private static CameraType cameraBefore;
    private static float forward;
    private static float strafe;
    private static boolean jump;
    private static int strike = -1;

    private ClientIncorporation() {
    }

    public static boolean active() {
        return body != null;
    }

    public static void start(int entityId, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Entity found = minecraft.level.getEntity(entityId);
        if (found == null) {
            return;
        }
        if (body == null) {
            cameraBefore = minecraft.options.getCameraType();
        }
        body = found;
        ticksLeft = ticks;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(found);
    }

    public static void end() {
        Minecraft minecraft = Minecraft.getInstance();
        body = null;
        ticksLeft = 0;
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        if (cameraBefore != null) {
            minecraft.options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || body == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || body.isRemoved() || !body.isAlive() || --ticksLeft <= 0) {
            end();
            return;
        }
        SpellingNetwork.sendIncorporationInput(new IncorporationInputPacket(forward, strafe, jump, player.getYRot(),
                player.getXRot(), strike));
        strike = -1;
    }

    /** The body looks where the mage turns, smoothly between ticks (the mouse still turns the mage's own head). */
    @SubscribeEvent
    public static void turn(TickEvent.RenderTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.phase != TickEvent.Phase.START || body == null || player == null) {
            return;
        }
        body.yRotO = player.yRotO;
        body.xRotO = player.xRotO;
        body.setYRot(player.getYRot());
        body.setXRot(player.getXRot());
        if (body instanceof LivingEntity living) {
            living.yHeadRotO = player.yRotO;
            living.yHeadRot = player.getYRot();
            living.yBodyRot = player.getYRot();
        }
    }

    /** The walking keys and jump go to the borrowed body; the mage's own stands still. */
    @SubscribeEvent
    public static void holdStill(MovementInputUpdateEvent event) {
        if (body == null) {
            return;
        }
        Input input = event.getInput();
        forward = input.forwardImpulse;
        strafe = input.leftImpulse;
        jump = input.jumping;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** The attack button strikes with the borrowed hands; the mage's own do nothing. */
    @SubscribeEvent
    public static void hands(InputEvent.InteractionKeyMappingTriggered event) {
        if (body == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isAttack()) {
            Entity aimed = Minecraft.getInstance().crosshairPickEntity;
            strike = aimed == null ? -1 : aimed.getId();
        }
    }

    @SubscribeEvent
    public static void noHands(RenderHandEvent event) {
        if (body != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        body = null;
        ticksLeft = 0;
    }
}

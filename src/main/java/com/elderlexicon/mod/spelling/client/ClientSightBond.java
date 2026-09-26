package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Projection;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mage's sight away from its body, on this client. Two ways:
 * <ul>
 *   <li>the bond of sight ({@code surgit m1 ligabis}): the camera goes into the eyes of what bears the mark and looks
 *       where it looks; the mage does not turn it;</li>
 *   <li>astral projection ({@code 10 ubis surgit}): the spirit appears at the place and the mage turns it with the
 *       mouse and drifts it with the walking keys (up and down with jump and crouch), through anything, within a leash
 *       of where it appeared.</li>
 * </ul>
 * Either way the body stands where it was, blind to what is around it: it does not walk, jump or crouch, and its hands
 * are not seen. When the time is up, or what was seen through is gone, the sight comes home.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class ClientSightBond {

    /** How fast the spirit drifts, in blocks a tick. */
    private static final double SPIRIT_SPEED = 0.4D;
    /** A standing player's eyes, above its feet. */
    private static final double EYE_HEIGHT = 1.62D;
    /** The server is told where the spirit is this often, in ticks. */
    private static final int REPORT_TICKS = 5;

    private static Entity seen;
    private static int ticksLeft;
    private static CameraType cameraBefore;
    /** For a projection: where the spirit appeared, and the keys held this tick. */
    private static Vec3 anchor;
    private static float forward;
    private static float sideways;
    private static float rising;

    private ClientSightBond() {
    }

    public static boolean active() {
        return seen != null;
    }

    /** Binds the sight to the entity {@code entityId} for {@code ticks}. */
    public static void start(int entityId, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(entityId);
        if (target == null) {
            minecraft.player.displayClientMessage(Component.literal("A tua visao nao alcanca o que tem essa marca."), true);
            return;
        }
        anchor = null;
        look(target, ticks);
    }

    /** Sends the spirit out to {@code at} for {@code ticks}. */
    public static void project(Vec3 at, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // The spirit is a body no one else knows of: it is not put in the world, only looked out of. It is a bare marker,
        // not a creature: the camera turns a creature by its head, which would leave the view stuck. A marker has no
        // eyes of its own, so it floats at the height a standing mage's eyes would be.
        Vec3 eyes = at.add(0.0D, EYE_HEIGHT, 0.0D);
        Marker spirit = new Marker(EntityType.MARKER, minecraft.level);
        spirit.noPhysics = true;
        spirit.setPos(eyes.x, eyes.y, eyes.z);
        spirit.xo = eyes.x;
        spirit.yo = eyes.y;
        spirit.zo = eyes.z;
        faceAsPlayer(spirit, minecraft.player);
        anchor = eyes;
        look(spirit, ticks);
    }

    /** The body was hurt: the spirit comes home at once. */
    public static void callBack() {
        if (anchor != null) {
            end();
        }
    }

    private static void look(Entity through, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (seen == null) {
            cameraBefore = minecraft.options.getCameraType();
        }
        seen = through;
        ticksLeft = ticks;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(through);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || seen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean projecting = anchor != null;
        boolean gone = !projecting && (seen.isRemoved() || !seen.isAlive() || seen.level() != minecraft.level);
        if (--ticksLeft <= 0 || gone || player == null || minecraft.level == null) {
            end();
            return;
        }
        if (projecting) {
            drift(player);
        }
    }

    /** Moves the spirit as the keys ask, the way the mage looks, never past its leash. */
    private static void drift(LocalPlayer player) {
        seen.xo = seen.getX();
        seen.yo = seen.getY();
        seen.zo = seen.getZ();
        faceAsPlayer(seen, player);
        Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        Vec3 side = Vec3.directionFromRotation(0.0F, player.getYRot() - 90.0F);
        Vec3 step = look.scale(forward).add(side.scale(sideways)).add(0.0D, rising, 0.0D);
        if (step.lengthSqr() > 1.0E-6D) {
            Vec3 next = seen.position().add(step.normalize().scale(SPIRIT_SPEED));
            Vec3 fromAnchor = next.subtract(anchor);
            if (fromAnchor.length() > Projection.LEASH) {
                next = anchor.add(fromAnchor.normalize().scale(Projection.LEASH));
            }
            seen.setPos(next.x, next.y, next.z);
        }
        if (ticksLeft % REPORT_TICKS == 0) {
            SpellingNetwork.sendSpiritPosition(seen.position().subtract(0.0D, EYE_HEIGHT, 0.0D));
        }
    }

    /** The spirit looks where the mage turns: the mouse still turns the body's head, and the spirit takes it. */
    private static void faceAsPlayer(Entity spirit, LocalPlayer player) {
        spirit.yRotO = player.yRotO;
        spirit.xRotO = player.xRotO;
        spirit.setYRot(player.getYRot());
        spirit.setXRot(player.getXRot());
    }

    /** Keeps the spirit's view turning smoothly between ticks, as fast as the mouse moves. */
    @SubscribeEvent
    public static void turn(TickEvent.RenderTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.phase == TickEvent.Phase.START && anchor != null && seen != null && player != null) {
            faceAsPlayer(seen, player);
        }
    }

    /** The body stands still while its sight is elsewhere; a projected spirit takes the keys instead. */
    @SubscribeEvent
    public static void holdStill(MovementInputUpdateEvent event) {
        if (seen == null) {
            return;
        }
        Input input = event.getInput();
        forward = input.forwardImpulse;
        sideways = input.leftImpulse;
        rising = (input.jumping ? 1.0F : 0.0F) - (input.shiftKeyDown ? 1.0F : 0.0F);
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** Seeing from elsewhere, the mage's own hands and what they hold are not in sight. */
    @SubscribeEvent
    public static void noHands(RenderHandEvent event) {
        if (seen != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        seen = null;
        anchor = null;
        ticksLeft = 0;
    }

    private static void end() {
        Minecraft minecraft = Minecraft.getInstance();
        seen = null;
        anchor = null;
        ticksLeft = 0;
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        if (cameraBefore != null) {
            minecraft.options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
    }
}

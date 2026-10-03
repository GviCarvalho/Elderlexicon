package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.life.Beings;
import com.elderlexicon.mod.spell.sight.SightBond;
import com.elderlexicon.mod.spelling.network.IncorporationPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A mage's kern in a body that has none (docs/vita-design.md): a body the spirit made has no will, and nothing in it
 * resists a kern coming in, so the mage lives in it as in their own. They see with its eyes, walk, jump and swim with
 * its legs, strike with its hands and what it holds; their own body stands in a trance where it was. It lasts what
 * chronos says (two seconds without it) and is paid as an open tap. If the borrowed body dies, the kern comes home with
 * a shock; if the mage's own body is hurt, the kern comes home at once.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Incorporations {

    /** Living in another body costs this much for each second it lasts. */
    public static final double UMU_PER_SECOND = 1.0D;
    /** How far the borrowed hands reach, in blocks. */
    private static final double REACH = 3.5D;

    /** What the mage does with the borrowed body this tick, as their client reports it. */
    public record Input(float forward, float strafe, boolean jump, float yaw, float pitch, int strike) {
    }

    private record Held(ServerPlayer player, Mob body, long until) {
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();
    private static final Map<UUID, Input> INPUTS = new HashMap<>();

    private Incorporations() {
    }

    /** The body without a kern nearest the mage that bears {@code mark}, in their world and in reach of their sight. */
    static Optional<Mob> soullessBearing(ServerPlayer player, String mark) {
        return MarkTargets.find(player.server, mark).stream()
                .map(thing -> thing.entity)
                .filter(entity -> entity instanceof Mob && entity.isAlive() && entity.level() == player.level()
                        && Beings.isSoulless(entity)
                        && entity.distanceTo(player) <= entity.getType().clientTrackingRange() * 16.0D)
                .map(entity -> (Mob) entity)
                .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
    }

    /** The mage's kern goes into {@code body} for as long as chronos says, paid now. */
    static void start(SpellContext context, Mob body, Double chronos) {
        ServerPlayer player = context.player();
        double seconds = SightBond.seconds(chronos);
        int ticks = SightBond.ticks(seconds);
        context.addTotalCost(UMU_PER_SECOND * seconds);
        HELD.put(player.getUUID(), new Held(player, body, player.level().getGameTime() + ticks));
        INPUTS.remove(player.getUUID());
        SpellingNetwork.sendIncorporation(player, new IncorporationPacket(body.getId(), ticks));
        player.displayClientMessage(Component.literal("O teu kern entra em " + body.getName().getString() + "."), true);
    }

    /** What the mage's client says they do with the body they are in. */
    public static void input(ServerPlayer player, Input input) {
        if (HELD.containsKey(player.getUUID())) {
            INPUTS.put(player.getUUID(), input);
            if (input.strike() >= 0) {
                strike(HELD.get(player.getUUID()).body(), input.strike());
            }
        }
    }

    public static boolean inhabits(ServerPlayer player) {
        return HELD.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HELD.isEmpty()) {
            return;
        }
        for (Held held : Map.copyOf(HELD).values()) {
            ServerPlayer player = held.player();
            Mob body = held.body();
            if (player.isRemoved() || !player.isAlive() || body.isRemoved() || !body.isAlive()
                    || body.level() != player.level()) {
                end(player, false);
                continue;
            }
            if (player.level().getGameTime() >= held.until()) {
                end(player, false);
                continue;
            }
            Input input = INPUTS.get(player.getUUID());
            if (input != null) {
                move(body, input);
            }
        }
    }

    /** The body goes where the mage walks it, looking where they look, as a player walks. */
    private static void move(Mob body, Input input) {
        body.setYRot(input.yaw());
        body.setYHeadRot(input.yaw());
        body.setYBodyRot(input.yaw());
        body.setXRot(Mth.clamp(input.pitch(), -90.0F, 90.0F));
        double forward = input.forward();
        double strafe = input.strafe();
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0D) {
            forward /= length;
            strafe /= length;
        }
        double speed = 0.9D * body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float yaw = input.yaw() * ((float) Math.PI / 180.0F);
        double worldX = (strafe * Mth.cos(yaw) - forward * Mth.sin(yaw)) * speed;
        double worldZ = (forward * Mth.cos(yaw) + strafe * Mth.sin(yaw)) * speed;
        Vec3 motion = body.getDeltaMovement();
        double grip = body.onGround() || body.isInWater() ? 1.0D : 0.25D;
        double x = motion.x + (worldX - motion.x) * grip;
        double z = motion.z + (worldZ - motion.z) * grip;
        double y = motion.y;
        if (input.jump()) {
            if (body.isInWater() || body.isInLava()) {
                y = Math.min(0.2D, y + 0.06D); // swimming up
            } else if (body.onGround()) {
                y = 0.42D;
            }
        }
        body.setDeltaMovement(x, y, z);
    }

    /** The borrowed hands strike what the mage aims at, with what they hold. */
    private static void strike(Mob body, int targetId) {
        Entity target = body.level().getEntity(targetId);
        if (!(target instanceof LivingEntity living) || target == body || !living.isAlive()
                || target.distanceTo(body) > REACH) {
            return;
        }
        body.swing(InteractionHand.MAIN_HAND);
        if (body.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            body.doHurtTarget(target);
        } else {
            living.hurt(body.damageSources().mobAttack(body), 1.0F);
        }
    }

    /** The kern comes home; with a shock when the body it was in died. */
    private static void end(ServerPlayer player, boolean shock) {
        if (HELD.remove(player.getUUID()) == null) {
            return;
        }
        INPUTS.remove(player.getUUID());
        SpellingNetwork.sendIncorporation(player, new IncorporationPacket(-1, 0));
        if (shock) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            player.displayClientMessage(Component.literal("O corpo morreu com o teu kern nele: ele volta a ti num choque."),
                    true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        for (Held held : Map.copyOf(HELD).values()) {
            if (held.body() == event.getEntity()) {
                end(held.player(), true);
            } else if (held.player() == event.getEntity()) {
                end(held.player(), false);
            }
        }
    }

    /** The mage's own body hurt while the kern is away: it comes home at once. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HELD.containsKey(player.getUUID())) {
            end(player, false);
        }
    }
}

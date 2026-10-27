package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.vita.network.BodyPacket;
import com.elderlexicon.mod.vita.network.VitaNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a body out of balance feels (docs/vita-sintomas-design.md): no status of a potion, but the body's own signs, mild
 * until the imbalance is extreme. Each element lacking or in excess is a severity from 0 to 1; the severities slow the
 * step, weaken the arm, tire, freeze, take the breath, and now and then bring an episode (a faint, a cramp, a colic, a
 * wave of heat). What the player sees and hears of it is the client's ({@code client/BodySense}); the cause only the
 * Life Compass tells.
 */
public final class Symptoms {

    /** A lack is felt from 15% of the element missing, and is at its worst at 75%. */
    private static final double LACK_FROM = 0.15D;
    private static final double LACK_SPAN = 0.60D;
    /** An excess is felt from 10% past full, at its worst at 60%. */
    private static final double EXCESS_FROM = 0.10D;
    private static final double EXCESS_SPAN = 0.50D;

    private static final UUID SPEED_ID = UUID.fromString("0f6f7d3e-2b1c-4b8e-9a51-7c1f0d6b5a01");
    private static final UUID DAMAGE_ID = UUID.fromString("0f6f7d3e-2b1c-4b8e-9a51-7c1f0d6b5a02");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("0f6f7d3e-2b1c-4b8e-9a51-7c1f0d6b5a03");

    /** One chance in this many each tick, at a severity of one, that an episode comes: about every fifteen seconds. */
    private static final int EPISODE_ODDS = 300;
    private static final int EPISODE_REST = 100;

    /** Something the body does on its own for a moment. */
    public enum Episode {
        NONE(0),
        /** The sight goes dark and the legs give: from blood, breath or hyperventilation. */
        FAINT(40),
        /** The hands lock: from hyperventilation. */
        CRAMP(30),
        /** A pain that stops the body: from calcification. */
        COLIC(25),
        /** A wave of heat that clouds the sight: from fever. */
        HEAT(40),
        /** A bone broke. */
        PAIN(20);

        private final int ticks;

        Episode(int ticks) {
            this.ticks = ticks;
        }

        public int ticks() {
            return ticks;
        }
    }

    /** How far each element is out of balance: a lack and an excess from 0 to 1, one of them always nothing. */
    public record Body(double igniLow, double igniHigh, double aquaLow, double aquaHigh,
                       double auraLow, double auraHigh, double firmoLow, double firmoHigh) {

        public static final Body WHOLE = new Body(0, 0, 0, 0, 0, 0, 0, 0);

        /** The element's severity, signed: below zero a lack, above an excess. */
        public double signed(VitaElement element) {
            return switch (element) {
                case IGNI -> igniHigh - igniLow;
                case AQUA -> aquaHigh - aquaLow;
                case AURA -> auraHigh - auraLow;
                case FIRMO -> firmoHigh - firmoLow;
                case BALANCED -> 0.0D;
            };
        }
    }

    /** What a body is feeling now, kept between its ticks. */
    private static final class Feeling {
        Body body = Body.WHOLE;
        Episode episode = Episode.NONE;
        int episodeLeft;
        int rest;
        double jump = 1.0D;
        double dig = 1.0D;
        double fall = 1.0D;
        BodyPacket sent;
    }

    private static final Map<UUID, Feeling> FEELINGS = new ConcurrentHashMap<>();

    private Symptoms() {
    }

    // ------------------------------------------------------------------ the severities

    static Body read(VitaData data) {
        return new Body(lack(data, VitaElement.IGNI), excess(data, VitaElement.IGNI),
                lack(data, VitaElement.AQUA), excess(data, VitaElement.AQUA),
                lack(data, VitaElement.AURA), excess(data, VitaElement.AURA),
                lack(data, VitaElement.FIRMO), excess(data, VitaElement.FIRMO));
    }

    private static double lack(VitaData data, VitaElement element) {
        double full = data.full(element);
        return full <= 1.0E-6D ? 0.0D : curve(1.0D - data.get(element) / full, LACK_FROM, LACK_SPAN);
    }

    private static double excess(VitaData data, VitaElement element) {
        double full = data.full(element);
        return full <= 1.0E-6D ? 0.0D : curve(data.get(element) / full - 1.0D, EXCESS_FROM, EXCESS_SPAN);
    }

    /** Nothing up to {@code from}, then growing slowly and faster: mild for long, hard only near the end. */
    static double curve(double deviation, double from, double span) {
        if (deviation <= from) {
            return 0.0D;
        }
        double t = Math.min(1.0D, (deviation - from) / span);
        return t * t;
    }

    /** How fragile the bones of {@code living} are now, 0 (whole) to 1. */
    public static double fragility(LivingEntity living) {
        return lack(VitaData.peek(living), VitaElement.FIRMO);
    }

    // ------------------------------------------------------------------ the tick

    /** A tick of a body: its signs, from what it holds now. */
    static void tick(LivingEntity living, VitaData data) {
        Feeling feeling = FEELINGS.computeIfAbsent(living.getUUID(), id -> new Feeling());
        Body body = read(data);
        feeling.body = body;
        Fractures.Bones bones = living instanceof Player player ? Fractures.tick(player, data) : Fractures.Bones.WHOLE;

        episode(living, feeling, body);
        boolean faint = feeling.episode == Episode.FAINT;
        boolean cramp = feeling.episode == Episode.CRAMP;
        boolean stopped = faint || feeling.episode == Episode.COLIC || feeling.episode == Episode.PAIN;

        // The step: cold, thin blood, a swollen body, no breath, stiff or brittle bones; a broken leg limps.
        double speed = 0.15D * body.igniLow() + 0.10D * body.aquaLow() + 0.12D * body.aquaHigh()
                + 0.10D * body.auraLow() + 0.05D * body.firmoLow() + 0.10D * body.firmoHigh();
        if (bones.leg()) {
            speed += 0.35D;
        }
        if (stopped) {
            speed += 0.6D;
        }
        set(living, Attributes.MOVEMENT_SPEED, SPEED_ID, "vita_step", -Math.min(0.85D, speed));

        // The arm: no blood, no breath, numb hands, brittle bones; a broken arm, a cramped hand.
        double damage = 0.20D * body.aquaLow() + 0.10D * body.auraLow() + 0.10D * body.igniLow() + 0.15D * body.firmoLow();
        if (bones.arm()) {
            damage += 0.4D;
        }
        if (cramp || faint) {
            damage += 0.8D;
        }
        set(living, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "vita_arm", -Math.min(0.9D, damage));

        double swing = 0.25D * body.igniLow() + 0.15D * body.auraHigh() + 0.20D * body.firmoHigh();
        if (bones.arm()) {
            swing += 0.3D;
        }
        set(living, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, "vita_swing", -Math.min(0.8D, swing));

        // The hands at work, the legs to jump, the bones to fall on.
        double dig = (1.0D - 0.30D * body.igniLow() - 0.15D * body.aquaLow() - 0.20D * body.auraHigh())
                * (bones.arm() ? 0.6D : 1.0D) * (cramp ? 0.1D : 1.0D) * (faint ? 0.0D : 1.0D);
        double jump = (1.0D - 0.30D * body.firmoHigh()) * (bones.leg() ? 0.6D : 1.0D) * (stopped ? 0.0D : 1.0D);
        feeling.dig = Mth.clamp(dig, 0.0D, 1.0D);
        feeling.jump = Mth.clamp(jump, 0.0D, 1.0D);
        feeling.fall = 1.0D + body.firmoLow() + (bones.leg() ? 0.5D : 0.0D);

        if (living instanceof Player player) {
            feel(player, body);
        }
        if (living instanceof ServerPlayer player) {
            send(player, feeling, bones);
        }
    }

    /** Hunger, cold, sweat and breath: what only a player's body keeps account of. */
    private static void feel(Player player, Body body) {
        // The body burns food against the cold and the fever, and thirst is hunger here; running costs more without
        // blood or breath.
        float exhaustion = (float) (0.004D * body.igniLow() + 0.004D * body.aquaLow() + 0.006D * body.igniHigh());
        if (player.isSprinting()) {
            exhaustion += (float) (0.02D * body.aquaLow() + 0.03D * body.auraLow());
        }
        if (exhaustion > 0.0F) {
            player.causeFoodExhaustion(exhaustion);
        }

        // The fever sweats: a little of the water goes with the heat.
        if (body.igniHigh() > 0.0D && player.tickCount % 20 == 0) {
            VitaSystem.consumeElementReserve(player, VitaElement.AQUA, 0.2D * body.igniHigh());
        }

        // Deep cold freezes from the inside: the frost at the edges, short of the freezing that hurts.
        if (body.igniLow() > 0.5D && player.canFreeze()) {
            int frozen = (int) ((body.igniLow() - 0.5D) / 0.5D * (player.getTicksRequiredToFreeze() - 1));
            if (player.getTicksFrozen() < frozen) {
                player.setTicksFrozen(frozen);
            }
        }

        // Without breath the lungs ask for air out of the water too, short of drowning.
        if (body.auraLow() > 0.4D) {
            int air = Math.max(1, (int) (player.getMaxAirSupply() * (1.0D - (body.auraLow() - 0.4D) / 0.6D * 0.95D)));
            if (player.getAirSupply() > air) {
                player.setAirSupply(air);
            }
        }
    }

    /** Now and then, when the imbalance is deep, the body does something on its own for a moment. */
    private static void episode(LivingEntity living, Feeling feeling, Body body) {
        if (feeling.episodeLeft > 0) {
            if (--feeling.episodeLeft == 0) {
                feeling.episode = Episode.NONE;
                feeling.rest = EPISODE_REST;
            }
            return;
        }
        if (feeling.rest > 0) {
            feeling.rest--;
            return;
        }
        Episode next = Episode.NONE;
        double worst = 0.0D;
        double faint = Math.max(Math.max(body.aquaLow(), body.auraLow()), body.auraHigh());
        if (faint >= 0.75D && faint > worst) {
            next = Episode.FAINT;
            worst = faint;
        }
        if (body.auraHigh() >= 0.6D && body.auraHigh() * 0.9D > worst) {
            next = Episode.CRAMP;
            worst = body.auraHigh() * 0.9D;
        }
        if (body.firmoHigh() >= 0.6D && body.firmoHigh() > worst) {
            next = Episode.COLIC;
            worst = body.firmoHigh();
        }
        if (body.igniHigh() >= 0.7D && body.igniHigh() > worst) {
            next = Episode.HEAT;
            worst = body.igniHigh();
        }
        if (next != Episode.NONE && living.getRandom().nextDouble() < worst / EPISODE_ODDS) {
            begin(feeling, next);
        }
    }

    private static void begin(Feeling feeling, Episode episode) {
        feeling.episode = episode;
        feeling.episodeLeft = episode.ticks();
    }

    /** A bone of {@code living} just broke: the pain stops it for a moment. */
    static void pain(LivingEntity living) {
        begin(FEELINGS.computeIfAbsent(living.getUUID(), id -> new Feeling()), Episode.PAIN);
    }

    private static void send(ServerPlayer player, Feeling feeling, Fractures.Bones bones) {
        BodyPacket packet = new BodyPacket((float) feeling.body.signed(VitaElement.IGNI),
                (float) feeling.body.signed(VitaElement.AQUA), (float) feeling.body.signed(VitaElement.AURA),
                (float) feeling.body.signed(VitaElement.FIRMO), bones.leg(), bones.arm(), (float) feeling.jump,
                (float) feeling.dig, feeling.episode, feeling.episodeLeft);
        if (feeling.sent == null || packet.differs(feeling.sent)) {
            feeling.sent = packet;
            VitaNetwork.sendBody(player, packet);
        }
    }

    // ------------------------------------------------------------------ what the events ask

    /** How high {@code living} can jump now, as a share of a whole body's jump (the server's account). */
    public static double jump(LivingEntity living) {
        Feeling feeling = FEELINGS.get(living.getUUID());
        return feeling == null ? 1.0D : feeling.jump;
    }

    /** How fast {@code living} breaks blocks now, as a share of a whole body's (the server's account). */
    public static double dig(LivingEntity living) {
        Feeling feeling = FEELINGS.get(living.getUUID());
        return feeling == null ? 1.0D : feeling.dig;
    }

    /** How much more a fall hurts {@code living} now: brittle bones, a broken leg. */
    public static double fall(LivingEntity living) {
        Feeling feeling = FEELINGS.get(living.getUUID());
        return feeling == null ? 1.0D : feeling.fall;
    }

    /** What {@code living} feels now (whole when nothing). */
    public static Body body(LivingEntity living) {
        Feeling feeling = FEELINGS.get(living.getUUID());
        return feeling == null ? Body.WHOLE : feeling.body;
    }

    /** The episode {@code living} is in now. */
    public static Episode episode(LivingEntity living) {
        Feeling feeling = FEELINGS.get(living.getUUID());
        return feeling == null ? Episode.NONE : feeling.episode;
    }

    /** The body feels nothing anymore (whole again, dreaming, gone): every sign of it is taken back. */
    public static void clear(LivingEntity living) {
        boolean felt = FEELINGS.remove(living.getUUID()) != null;
        set(living, Attributes.MOVEMENT_SPEED, SPEED_ID, "vita_step", 0.0D);
        set(living, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "vita_arm", 0.0D);
        set(living, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, "vita_swing", 0.0D);
        if (felt && living instanceof ServerPlayer player) {
            VitaNetwork.sendBody(player, BodyPacket.WHOLE);
        }
    }

    /** Forgets {@code living} without touching it: it left the world. */
    public static void forget(UUID id) {
        FEELINGS.remove(id);
    }

    /** Sets a share of an attribute taken away (none: the modifier goes), rewriting it only when it moved. */
    private static void set(LivingEntity living, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(amount) < 0.005D) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current != null) {
            if (Math.abs(current.getAmount() - amount) < 0.005D) {
                return;
            }
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}

package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.sight.Projection;
import com.elderlexicon.mod.spelling.network.ProjectionPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Astral projection on the server ({@code 10 ubis surgit}, docs/surgit-visao-design.md, section 3): sends the mage's
 * spirit to the place written with ubis. The spirit's sight is the client's, as in the bond of sight; the server keeps
 * the body where it stands, ends the projection when the body is hurt, and knows where the spirit drifts (the client
 * tells it) so that a revelation of magic can see it.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class AstralProjections {

    /** The range of an aimed place (no ubis distance): as far as marks reach when thrown. */
    private static final double AIM_RANGE = 64.0D;

    /** A spirit out of its body: where it is now, and until when. */
    private record Spirit(ServerLevel level, Vec3 at, long untilTick) {
    }

    private static final Map<UUID, Spirit> SPIRITS = new ConcurrentHashMap<>();

    private AstralProjections() {
    }

    static void start(SpellContext context, Optional<SpellPlace> place, @Nullable Double chronos) {
        ServerPlayer player = context.player();
        Optional<MarkSpells.Destination> destination = MarkSpells.destination(context, place, AIM_RANGE);
        if (destination.isEmpty()) {
            return;
        }
        if (destination.get().level() != player.serverLevel()) {
            MarkSpells.tell(player, "O espirito nao atravessa dimensoes.");
            return;
        }
        Vec3 at = destination.get().point();
        double seconds = Projection.seconds(chronos);
        int ticks = Projection.ticks(seconds);
        context.addTotalCost(Projection.cost(at.distanceTo(player.position()), seconds));
        SPIRITS.put(player.getUUID(), new Spirit(player.serverLevel(), at, player.server.getTickCount() + ticks));
        SpellingNetwork.sendProjection(player, ProjectionPacket.start(at, ticks));
    }

    /** The client tells where its spirit drifted. */
    public static void moved(ServerPlayer player, Vec3 at) {
        SPIRITS.computeIfPresent(player.getUUID(), (id, spirit) ->
                at.distanceTo(spirit.at()) <= Projection.LEASH * 2.0D ? new Spirit(spirit.level(), at, spirit.untilTick()) : spirit);
    }

    /** Spirits out of their bodies in {@code level} within {@code radius} of {@code center}: whose, and where. */
    static List<Map.Entry<ServerPlayer, Vec3>> spiritsNear(ServerLevel level, Vec3 center, double radius) {
        List<Map.Entry<ServerPlayer, Vec3>> found = new ArrayList<>();
        SPIRITS.forEach((id, spirit) -> {
            if (spirit.level() == level && spirit.at().distanceToSqr(center) <= radius * radius
                    && level.getPlayerByUUID(id) instanceof ServerPlayer owner) {
                found.add(Map.entry(owner, spirit.at()));
            }
        });
        return found;
    }

    /** A body hurt while its spirit is out calls it back at once. */
    @SubscribeEvent
    public static void hurt(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SPIRITS.remove(player.getUUID()) != null) {
            SpellingNetwork.sendProjection(player, ProjectionPacket.end());
        }
    }

    @SubscribeEvent
    public static void expire(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SPIRITS.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        SPIRITS.entrySet().removeIf(entry -> now > entry.getValue().untilTick());
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        SPIRITS.remove(event.getEntity().getUUID());
    }
}

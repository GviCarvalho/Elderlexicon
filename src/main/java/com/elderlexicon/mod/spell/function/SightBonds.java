package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.sight.SightBond;
import com.elderlexicon.mod.spelling.network.SightBondPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Optional;

/**
 * The bond of sight on the server ({@code surgit m1 ligabis}, docs/surgit-visao-design.md, section 2): finds what bears
 * the mark, charges the mage, and tells the mage's client to see through its eyes. The sight itself is the client's:
 * the server keeps the mage's body where it stands (its own camera would drag the body along, as a spectator's does).
 * So what is seen must be near enough for the mage's client to know it.
 */
final class SightBonds {

    private SightBonds() {
    }

    static void start(SpellContext context, String mark, @Nullable Double chronos) {
        ServerPlayer player = context.player();
        Optional<Entity> seen = MarkTargets.find(player.server, mark).stream()
                .map(thing -> thing.entity)
                .filter(entity -> entity != null && entity != player && entity.isAlive() && entity.level() == player.level())
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        if (seen.isEmpty()) {
            MarkSpells.tell(player, "A marca '" + mark + "' nao tem olhos neste mundo para a tua visao se ligar.");
            return;
        }
        Entity target = seen.get();
        com.elderlexicon.mod.ligabis.world.LigabisManager bonds = com.elderlexicon.mod.ligabis.world.LigabisManager.get();
        if (target instanceof net.minecraft.world.entity.Mob body && com.elderlexicon.mod.spell.life.Beings.isSoulless(body)
                && bonds != null && bonds.auraBound(player.getUUID(), mark)) {
            // Bound by movement and by sight to a body without a kern: nothing in it resists, and the mage lives in it.
            Incorporations.start(context, body, chronos);
            return;
        }
        // Past the distance at which the game shows such an entity to players, the mage's client does not know it.
        double reach = target.getType().clientTrackingRange() * 16.0D;
        if (target.distanceTo(player) > reach) {
            MarkSpells.tell(player, "'" + mark + "' esta longe demais para a tua visao alcancar ("
                    + Math.round(reach) + " blocos).");
            return;
        }
        double seconds = SightBond.seconds(chronos);
        context.addTotalCost(SightBond.cost(seconds));
        SpellingNetwork.sendSightBond(player, new SightBondPacket(target.getId(), SightBond.ticks(seconds)));
    }
}

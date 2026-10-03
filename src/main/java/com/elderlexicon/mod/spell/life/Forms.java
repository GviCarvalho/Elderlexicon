package com.elderlexicon.mod.spell.life;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Being;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The body a player's kern is in (docs/particulas-design.md, stage 6). A vertere that changes a player's core changes
 * their body, as it changes any being's: the kind whose proportion is nearest. The kern stays, and so does all the player
 * has (what they carry, their life, their experience): they are themselves in another body, the size of it and seen as
 * it. A person's proportion is a person's body again. Death leaves the body behind, and the player comes back as a
 * person.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Forms {

    /** The kind of being a player's body is, when it is not a person's. */
    private static final String FORM = ElderLexicon.MODID + ":form";
    /** The kind of the table that is a person's proportion: a player of it is in their own body. */
    private static final String PERSON = "homunculus";
    /** What the clients were told of the players they see: entity id, and the creature its body is shown as. */
    private static final Map<Integer, String> SHOWN = new ConcurrentHashMap<>();

    private Forms() {
    }

    /** The kind of being a player's body is now, when it is not a person's. */
    public static Optional<Being> of(Player player) {
        String id = player.getPersistentData().getString(FORM);
        return id.isEmpty() ? Optional.empty() : Materials.get().being(id);
    }

    /** Whether a kind of the table is a person's: a player of it is in their own body. */
    public static boolean person(Being kind) {
        return PERSON.equals(kind.id());
    }

    /** The player's body becomes one of {@code kind}, or theirs again when it is a person's. */
    public static void become(ServerPlayer player, Being kind) {
        if (person(kind)) {
            player.getPersistentData().remove(FORM);
        } else {
            player.getPersistentData().putString(FORM, kind.id());
        }
        player.refreshDimensions();
        sync(player);
    }

    /** The creature a player's body is shown as, as the clients are told: empty for a person's. */
    private static String shownAs(Player player) {
        return of(player).map(Being::entity).orElse("");
    }

    /** Everyone who sees the player, and the player too, is told what body it is. */
    private static void sync(ServerPlayer player) {
        SpellingNetwork.sendForm(player, player.getId(), shownAs(player));
    }

    /** On a client: the body a player it sees is in ({@code ""} for a person's). */
    public static void shown(int entityId, String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            SHOWN.remove(entityId);
        } else {
            SHOWN.put(entityId, entityType);
        }
    }

    /** On a client: what it was told of the player's body; on the server, what the player's body is. */
    private static Optional<EntityType<?>> typeOf(Player player) {
        String type = player.level().isClientSide() ? SHOWN.getOrDefault(player.getId(), "") : shownAs(player);
        return type.isEmpty() ? Optional.empty() : EntityType.byString(type);
    }

    /** A player in another body is as big as it, on both sides, so it fits where that body fits. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player) || player.level() == null) {
            return;
        }
        Optional<EntityType<?>> type = typeOf(player);
        if (type.isEmpty()) {
            return;
        }
        EntityDimensions size = type.get().getDimensions();
        event.setNewSize(size, false);
        event.setNewEyeHeight(size.height * 0.85F);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer seen && event.getEntity() instanceof ServerPlayer viewer
                && of(seen).isPresent()) {
            SpellingNetwork.sendFormTo(viewer, seen.getId(), shownAs(seen));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.refreshDimensions();
            sync(player);
        }
    }

    /** Death leaves the body behind: the player comes back as a person. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.refreshDimensions();
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.refreshDimensions();
            sync(player);
        }
    }
}

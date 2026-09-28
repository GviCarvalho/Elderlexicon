package com.elderlexicon.mod.spelling.inscription;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The runes written on the faces of the blocks of one world. Kept with the world, sent to its players, and wiped
 * when the block they were written on is broken.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Inscriptions extends SavedData {

    private static final String NAME = ElderLexicon.MODID + "_inscriptions";

    private final Map<Long, Inscription> written = new HashMap<>();

    public static Inscriptions of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Inscriptions::load, Inscriptions::new, NAME);
    }

    private static Inscriptions load(CompoundTag tag) {
        Inscriptions inscriptions = new Inscriptions();
        ListTag list = tag.getList("Inscriptions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Inscription inscription = Inscription.load(list.getCompound(i));
            inscriptions.written.put(inscription.key(), inscription);
        }
        return inscriptions;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        written.values().forEach(inscription -> list.add(inscription.save()));
        tag.put("Inscriptions", list);
        return tag;
    }

    public Optional<Inscription> at(BlockPos pos, Direction face) {
        return Optional.ofNullable(written.get(Inscription.key(pos, face)));
    }

    public Collection<Inscription> all() {
        return written.values();
    }

    /** Writes on a face, or wipes it when the text is blank; everyone in the world sees the change. */
    public void write(ServerLevel level, Inscription inscription) {
        if (inscription.text().isBlank()) {
            if (written.remove(inscription.key()) == null) {
                return;
            }
        } else {
            written.put(inscription.key(), inscription);
        }
        setDirty();
        SpellingNetwork.sendInscriptions(level, new InscriptionPacket(List.of(inscription), false));
    }

    /** Every face written on in a block, wiped (the block is gone). */
    public void wipe(ServerLevel level, BlockPos pos) {
        List<Inscription> gone = new ArrayList<>();
        for (Direction face : Direction.values()) {
            Inscription inscription = written.remove(Inscription.key(pos, face));
            if (inscription != null) {
                gone.add(new Inscription(pos, face, "", 0, false));
            }
        }
        if (!gone.isEmpty()) {
            setDirty();
            SpellingNetwork.sendInscriptions(level, new InscriptionPacket(gone, false));
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            of(level).wipe(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendAll(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sendAll(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sendAll(event.getEntity());
    }

    private static void sendAll(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer server) {
            SpellingNetwork.sendInscriptionsTo(server,
                    new InscriptionPacket(List.copyOf(of(server.serverLevel()).all()), true));
        }
    }
}

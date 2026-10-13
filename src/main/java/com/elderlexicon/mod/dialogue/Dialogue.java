package com.elderlexicon.mod.dialogue;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A conversation with someone, as in the games of roleplay (docs/mago-design.md, 4.1): what they say, and the answers
 * the player may give, as choices. It lives on the server: the client only shows the line and says which choice was
 * taken; what follows a choice is worked out here.
 */
@Mod.EventBusSubscriber(modid = com.elderlexicon.mod.ElderLexicon.MODID)
public final class Dialogue {

    /** How far the player may be from who speaks and still answer. */
    private static final double REACH = 8.0D;

    /** An answer, and what follows it: the next line, or null when the talk ends. */
    public record Choice(Component label, Function<ServerPlayer, Line> then) {
    }

    /** What is said, and the answers there are to it. */
    public record Line(Component text, List<Choice> choices) {

        public Line(Component text, Choice... choices) {
            this(text, List.of(choices));
        }
    }

    private record Session(Entity speaker, Line line) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Dialogue() {
    }

    /** {@code speaker} says {@code line} to {@code player}, who sees it on the dialogue screen. */
    public static void open(ServerPlayer player, Entity speaker, Line line) {
        SESSIONS.put(player.getUUID(), new Session(speaker, line));
        DialogueNetwork.sendLine(player, speaker, line);
    }

    /** Whether {@code player} is talking with {@code speaker} now. */
    public static boolean speaking(ServerPlayer player, Entity speaker) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.speaker() == speaker;
    }

    /** What {@code player} is being asked now, for a test. */
    public static java.util.Optional<Line> line(ServerPlayer player) {
        return java.util.Optional.ofNullable(SESSIONS.get(player.getUUID())).map(Session::line);
    }

    /**
     * {@code player} took the choice at {@code index} (out of range, or negative: they walked away): what follows is
     * said, or the talk ends.
     */
    public static void choose(ServerPlayer player, int index) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        List<Choice> choices = session.line().choices();
        boolean near = session.speaker().isAlive() && player.distanceTo(session.speaker()) <= REACH;
        if (index < 0 || index >= choices.size() || !near) {
            end(player, session.speaker());
            return;
        }
        SESSIONS.remove(player.getUUID());
        Line next = choices.get(index).then().apply(player);
        if (next == null) {
            if (!SESSIONS.containsKey(player.getUUID())) {
                end(player, session.speaker());
            }
            return;
        }
        open(player, session.speaker(), next);
    }

    /** The talk ends: the screen closes, and who spoke goes back to what it was doing. */
    public static void end(ServerPlayer player, Entity speaker) {
        SESSIONS.remove(player.getUUID());
        DialogueNetwork.sendEnd(player);
        if (speaker instanceof Listener listener) {
            listener.dialogueEnded(player);
        }
    }

    /** Who speaks, if it wants to know when a talk with it ends. */
    public interface Listener {
        void dialogueEnded(ServerPlayer player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = SESSIONS.remove(event.getEntity().getUUID());
        if (session != null && session.speaker() instanceof Listener listener
                && event.getEntity() instanceof ServerPlayer player) {
            listener.dialogueEnded(player);
        }
    }
}

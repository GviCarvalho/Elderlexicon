package com.elderlexicon.mod.spelling.client;

import net.minecraft.Util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is reciting what, as this client knows it: the local player's own trance and the ones the server reported for
 * players in sight. An ended recitation lingers for a moment and fades, like the last word of a spell.
 */
public final class Recitations {

    /** How long the words stay after the trance ends. */
    public static final long LINGER_MS = 1_500L;
    /** A trance whose end never arrived (a lost packet, a player gone) is dropped after this long. */
    private static final long STALE_MS = 20_000L;

    private static final Map<Integer, Recitation> BY_ENTITY = new ConcurrentHashMap<>();

    private Recitations() {
    }

    /** The runes spoken so far; {@code endedAtMs} is 0 while the trance lasts. */
    public record Recitation(List<String> runes, long updatedAtMs, long endedAtMs) {

        public boolean ended() {
            return endedAtMs > 0L;
        }

        /** 1 while reciting, falling to 0 as the ended words fade. */
        public float opacity(long nowMs) {
            if (!ended()) {
                return 1.0F;
            }
            return Math.max(0.0F, 1.0F - (float) (nowMs - endedAtMs) / LINGER_MS);
        }
    }

    public static void update(int entityId, List<String> runes, boolean finished) {
        long now = Util.getMillis();
        if (finished && (runes == null || runes.isEmpty())) {
            BY_ENTITY.remove(entityId);
            return;
        }
        BY_ENTITY.put(entityId, new Recitation(List.copyOf(runes), now, finished ? now : 0L));
    }

    public static Optional<Recitation> of(int entityId) {
        Recitation recitation = BY_ENTITY.get(entityId);
        if (recitation == null) {
            return Optional.empty();
        }
        long now = Util.getMillis();
        boolean faded = recitation.ended() && now - recitation.endedAtMs() > LINGER_MS;
        boolean stale = !recitation.ended() && now - recitation.updatedAtMs() > STALE_MS;
        if (faded || stale) {
            BY_ENTITY.remove(entityId, recitation);
            return Optional.empty();
        }
        return Optional.of(recitation);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }
}

package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellTicks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phantur, the keeper of Nahvá's gates, who raised the tower of Lig'dar (docs/habilidades-design.md). He wants only that
 * the dead spirit give up and rest on its island: one that went back would go back without the dam (book 2.1.2). He never
 * stops anyone; he speaks. His words are in docs/phantur-falas.md, by occasion, each speech one or more lines said one
 * after another; he does not say the same thing twice until he has said all the rest, and he remembers across lives.
 */
public final class Phantur {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String INDEX = "/data/elderlexicon/lexicon/phantur.json";
    /** Kept through death: what he has said, and how many times the mage has come to the tower. */
    private static final String TAG = ElderLexicon.MODID + "_phantur";
    private static final Style VOICE = Style.EMPTY.withColor(TextColor.fromRgb(0xD9B98F)).withItalic(true);
    /** The least time between two things he says on his own (not when something happens), and the most. */
    private static final int IDLE_MIN = 20 * 150;
    private static final int IDLE_MAX = 20 * 360;
    /** The least time between a speech and one brought on by a passing thing (a room, a step, a pause). */
    private static final int GAP = 20 * 30;
    private static final int STILL_SECONDS = 75;

    private static Map<String, List<int[]>> speeches;
    /** Which speech each mage is hearing, so a new one cuts the old short; and where they stood, and since when. */
    private static final Map<UUID, Integer> SPEAKING = new HashMap<>();
    private static final Map<UUID, Vec3> STOOD = new HashMap<>();
    private static final Map<UUID, Integer> STILL = new HashMap<>();

    private Phantur() {
    }

    static synchronized Map<String, List<int[]>> speeches() {
        if (speeches == null) {
            speeches = new HashMap<>();
            try (InputStream in = Phantur.class.getResourceAsStream(INDEX)) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (Map.Entry<String, JsonElement> occasion : root.entrySet()) {
                        List<int[]> list = new ArrayList<>();
                        for (JsonElement speech : occasion.getValue().getAsJsonArray()) {
                            JsonArray lines = speech.getAsJsonArray();
                            int[] lengths = new int[lines.size()];
                            for (int i = 0; i < lengths.length; i++) {
                                lengths[i] = lines.get(i).getAsInt();
                            }
                            list.add(lengths);
                        }
                        speeches.put(occasion.getKey(), list);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Phantur's words could not be read", e);
            }
        }
        return speeches;
    }

    private static CompoundTag memory(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        CompoundTag memory = persisted.getCompound(TAG);
        persisted.put(TAG, memory);
        return memory;
    }

    /** One more climb: returns which it is (1 the first). */
    static int climbed(ServerPlayer player) {
        CompoundTag memory = memory(player);
        int climbs = memory.getInt("climbs") + 1;
        memory.putInt("climbs", climbs);
        return climbs;
    }

    /** He speaks on {@code occasion}, whatever he was saying. */
    public static void speak(ServerPlayer player, String occasion) {
        List<int[]> list = speeches().get(occasion);
        if (list == null || list.isEmpty()) {
            return;
        }
        CompoundTag memory = memory(player);
        ListTag heard = memory.getList("heard", Tag.TAG_STRING);
        List<Integer> fresh = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!heard.contains(StringTag.valueOf(occasion + "." + i))) {
                fresh.add(i);
            }
        }
        if (fresh.isEmpty()) {
            // All of it said: he begins again, but not with what he said last.
            heard.removeIf(tag -> tag.getAsString().startsWith(occasion + "."));
            String last = memory.getString("last");
            for (int i = 0; i < list.size(); i++) {
                if (list.size() == 1 || !last.equals(occasion + "." + i)) {
                    fresh.add(i);
                }
            }
        }
        int chosen = fresh.get(player.getRandom().nextInt(fresh.size()));
        heard.add(StringTag.valueOf(occasion + "." + chosen));
        memory.put("heard", heard);
        memory.putString("last", occasion + "." + chosen);
        say(player, occasion, chosen, list.get(chosen));
    }

    /** He speaks on {@code occasion} with {@code chance}, unless he spoke a moment ago. */
    static void maybe(ServerPlayer player, String occasion, float chance) {
        if (sinceLast(player) >= GAP && player.getRandom().nextFloat() < chance) {
            speak(player, occasion);
        }
    }

    private static long sinceLast(ServerPlayer player) {
        return player.serverLevel().getGameTime() - memory(player).getLong("spoke");
    }

    private static void say(ServerPlayer player, String occasion, int index, int[] lengths) {
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        int speech = SPEAKING.merge(id, 1, Integer::sum);
        int delay = 0;
        for (int line = 0; line < lengths.length; line++) {
            Component text = Component.translatable("phantur.elderlexicon." + occasion + "." + index + "." + line,
                    player.getName().getString()).withStyle(VOICE);
            boolean first = line == 0;
            Runnable tell = () -> {
                ServerPlayer listener = server.getPlayerList().getPlayer(id);
                if (listener == null || SPEAKING.getOrDefault(id, 0) != speech) {
                    return;
                }
                listener.sendSystemMessage(text);
                if (first) {
                    listener.playNotifySound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.get(), SoundSource.AMBIENT,
                            0.6F, 0.6F);
                }
                memory(listener).putLong("spoke", listener.serverLevel().getGameTime());
            };
            if (first) {
                tell.run();
            } else {
                SpellTicks.schedule(server, delay, tell);
            }
            // Each line is given the time it takes to read it.
            delay += Math.min(200, 30 + lengths[line] * 2);
        }
    }

    /**
     * Each second in the tower or on the island: a mage standing still a long while, one near the end of their strength,
     * and now and then whatever comes to his mind.
     */
    static void everySecond(ServerPlayer player, boolean tower) {
        UUID id = player.getUUID();
        Vec3 at = player.position();
        Vec3 stood = STOOD.get(id);
        int still = stood != null && stood.distanceToSqr(at) < 0.25D ? STILL.getOrDefault(id, 0) + 1 : 0;
        STOOD.put(id, at);
        STILL.put(id, still);
        if (tower && still == STILL_SECONDS) {
            maybe(player, "still", 0.8F);
            return;
        }
        if (tower && player.getHealth() < player.getMaxHealth() * 0.3F && sinceLast(player) >= 20 * 60) {
            maybe(player, "hurt", 0.5F);
            return;
        }
        CompoundTag memory = memory(player);
        long now = player.serverLevel().getGameTime();
        if (!memory.contains("idleAt") || memory.getLong("idleAt") < memory.getLong("spoke")) {
            memory.putLong("idleAt", Math.max(now, memory.getLong("spoke"))
                    + IDLE_MIN + player.getRandom().nextInt(IDLE_MAX - IDLE_MIN));
        }
        if (now >= memory.getLong("idleAt")) {
            memory.remove("idleAt");
            speak(player, tower ? "idle" : "kern_idle");
        }
    }

    static void forget(UUID id) {
        STOOD.remove(id);
        STILL.remove(id);
    }
}

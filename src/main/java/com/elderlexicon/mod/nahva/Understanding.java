package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.vita.VitaImbalanceTier;
import com.elderlexicon.mod.vita.VitaSystem;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Understanding a rune (docs/aprendizado-runas-design.md): knowing what {@code igni} means teaches nothing; the mage
 * must live, in the body, what it is: be cold and freezing, warm and burning. Each second the body is looked at, and
 * every experience of a rune not yet learned that it is living is kept. When all of a rune's are lived, the rune is
 * understood, and only its dream in Nahvá still keeps it from the mage. The experiences each rune asks are data
 * ({@value #RESOURCE}); what each experience is, is here.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Understanding {

    public static final String RESOURCE = "/data/elderlexicon/lexicon/understanding.json";

    /** A rune to be understood: the experiences it asks, and the realm of Nahvá its dream takes the mage to. */
    public record RuneLesson(String rune, List<String> experiences, String realm) {
    }

    /** What each experience is, as the body lives it. */
    private static final Map<String, Predicate<ServerPlayer>> EXPERIENCES = Map.of(
            "igni_cold", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SLIGHTLY_LOW,
            "igni_hypothermia", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SEVERELY_LOW,
            "igni_warm", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SLIGHTLY_HIGH,
            "igni_boiling", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SEVERELY_HIGH,
            "burning", ServerPlayer::isOnFire,
            "freezing", ServerPlayer::isFullyFrozen);

    private static volatile Map<String, RuneLesson> lessons;

    private Understanding() {
    }

    /** Every rune that can be understood, by id. */
    public static Map<String, RuneLesson> lessons() {
        Map<String, RuneLesson> found = lessons;
        if (found == null) {
            synchronized (Understanding.class) {
                found = lessons;
                if (found == null) {
                    found = read();
                    lessons = found;
                }
            }
        }
        return found;
    }

    public static Optional<RuneLesson> lesson(String rune) {
        return Optional.ofNullable(lessons().get(rune));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || player.isCreative() || player.isSpectator()
                // The dead live nothing; and a body a moment dead has already let go of what it knew.
                || !player.isAlive() || Nahva.isNahva(player.level())) {
            return;
        }
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        // Runes are understood only by a mage whose trance is woken.
        if (!repertoire.hasTrance()) {
            return;
        }
        boolean changed = false;
        for (RuneLesson lesson : lessons().values()) {
            if (repertoire.isUnlocked(lesson.rune()) || repertoire.isUnderstood(lesson.rune())) {
                continue;
            }
            for (String experience : lesson.experiences()) {
                Predicate<ServerPlayer> living = EXPERIENCES.get(experience);
                if (living != null && living.test(player) && repertoire.experience(lesson.rune(), experience)) {
                    changed = true;
                    player.displayClientMessage(Component.translatable("experience.elderlexicon." + experience)
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                }
            }
            if (repertoire.experienced(lesson.rune()).containsAll(lesson.experiences())
                    && repertoire.understand(lesson.rune())) {
                changed = true;
                player.sendSystemMessage(Component.translatable("understood.elderlexicon." + lesson.rune())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (changed) {
            SpellingNetwork.syncToPlayer(player, repertoire);
        }
    }

    private static Map<String, RuneLesson> read() {
        InputStream stream = Understanding.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            return Map.of();
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject runes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("runes");
            Map<String, RuneLesson> read = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : runes.entrySet()) {
                JsonObject lesson = entry.getValue().getAsJsonObject();
                List<String> experiences = new ArrayList<>();
                lesson.getAsJsonArray("experiences").forEach(experience -> experiences.add(experience.getAsString()));
                read.put(entry.getKey(), new RuneLesson(entry.getKey(), Collections.unmodifiableList(experiences),
                        lesson.has("realm") ? lesson.get("realm").getAsString() : entry.getKey()));
            }
            return Collections.unmodifiableMap(read);
        } catch (java.io.IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }
}

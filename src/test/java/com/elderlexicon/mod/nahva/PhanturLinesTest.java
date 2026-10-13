package com.elderlexicon.mod.nahva;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phantur's words (docs/phantur-falas.md) reach the game through tools/phantur_lines.py: every line the index promises is
 * in both languages, and every occasion the game speaks on has something to say.
 */
class PhanturLinesTest {

    private static final Set<String> OCCASIONS = Set.of("first", "again", "kern", "kern_idle", "kern_leave", "room",
            "stairs", "descent", "memory", "memory_wrong", "mirror", "mirror_caught", "speech", "still", "door", "hurt", "idle", "summit", "rest", "fall", "won");

    private static JsonObject read(String path) throws Exception {
        try (InputStream in = PhanturLinesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyLineIsSaidInBothTongues() throws Exception {
        JsonObject index = read("/data/elderlexicon/lexicon/phantur.json");
        JsonObject pt = read("/assets/elderlexicon/lang/pt_br.json");
        JsonObject en = read("/assets/elderlexicon/lang/en_us.json");
        for (String occasion : OCCASIONS) {
            assertTrue(index.has(occasion), "he has something to say on " + occasion);
            assertFalse(index.getAsJsonArray(occasion).isEmpty(), occasion);
        }
        for (Map.Entry<String, JsonElement> occasion : index.entrySet()) {
            JsonArray speeches = occasion.getValue().getAsJsonArray();
            for (int i = 0; i < speeches.size(); i++) {
                for (int line = 0; line < speeches.get(i).getAsJsonArray().size(); line++) {
                    String key = "phantur.elderlexicon." + occasion.getKey() + "." + i + "." + line;
                    assertTrue(pt.has(key), "pt_br: " + key);
                    assertTrue(en.has(key), "en_us: " + key);
                }
            }
        }
    }

    @Test
    void heSpeaksBothShortAndLong() throws Exception {
        JsonObject index = read("/data/elderlexicon/lexicon/phantur.json");
        int one = 0;
        int many = 0;
        for (Map.Entry<String, JsonElement> occasion : index.entrySet()) {
            for (JsonElement speech : occasion.getValue().getAsJsonArray()) {
                if (speech.getAsJsonArray().size() == 1) {
                    one++;
                } else {
                    many++;
                }
            }
        }
        assertTrue(one >= 20 && many >= 10, one + " short, " + many + " long");
    }
}

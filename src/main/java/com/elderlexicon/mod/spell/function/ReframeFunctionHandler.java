package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spelling.custom.CustomRuneHelper;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.vita.VitaElement;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Records the current spell sequence into a grimoire, binding it to a custom rune id.
 */
public final class ReframeFunctionHandler implements SpellFunctionHandler {

    private static final String RESOURCE_PATH = "/com/elderlexicon/mod/parser/ParserList.json";
    private static final Type MAP_TYPE = new TypeToken<Map<String, RuneConfig>>() { }.getType();
    private static final Map<String, RuneType> RUNE_TYPES = new HashMap<>();
    private static final List<FusionRule> FUSIONS = new ArrayList<>();

    static {
        loadFusionData();
    }

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (player == null) {
            return;
        }

        List<String> lexemes = sanitize(context.lexemes());
        int reframeIndex = findReframeIndex(lexemes);
        if (reframeIndex < 0 || reframeIndex + 1 >= lexemes.size()) {
            player.sendSystemMessage(Component.literal("Reframe requer um identificador logo em seguida."));
            return;
        }
        String runeId = CustomRuneHelper.normalizeRuneId(lexemes.get(reframeIndex + 1));
        if (runeId == null) {
            player.sendSystemMessage(Component.literal("Nome da runa personalizada é inválido."));
            return;
        }

        List<String> captured = new ArrayList<>(lexemes.subList(0, reframeIndex));
        if (captured.isEmpty()) {
            ItemStack grimoire = findGrimoire(player.getInventory()).orElse(ItemStack.EMPTY);
            if (!(grimoire.getItem() instanceof GrimoireItem)) {
                player.sendSystemMessage(Component.literal("Reframe requer um grimorio no inventario."));
                return;
            }
            boolean removed = CustomRuneHelper.removeCustomRune(grimoire, runeId);
            if (removed) {
                player.getInventory().setChanged();
                removeFromRepertoire(player, runeId);
                player.sendSystemMessage(Component.literal("Runa '" + runeId + "' removida do grimorio."));
            } else {
                player.sendSystemMessage(Component.literal("Nenhuma runa '" + runeId + "' encontrada para remover."));
            }
            return;
        }
        captured = fuseWithTrailingSourceIfPresent(captured, lexemes.subList(reframeIndex + 2, lexemes.size()));

        ItemStack grimoire = findGrimoire(player.getInventory()).orElse(ItemStack.EMPTY);
        if (!(grimoire.getItem() instanceof GrimoireItem)) {
            player.sendSystemMessage(Component.literal("Reframe requer um grimório no inventário."));
            return;
        }

        boolean saved = CustomRuneHelper.saveCustomRune(grimoire, runeId, captured);
        if (!saved) {
            player.sendSystemMessage(Component.literal("Falha ao salvar a runa personalizada."));
            return;
        }
        player.getInventory().setChanged();

        assignToRepertoire(player, runeId);
        player.sendSystemMessage(Component.literal("Runa '" + runeId + "' registrada com " + captured.size() + " símbolos."));
    }

    private static List<String> sanitize(List<String> runes) {
        List<String> sanitized = new ArrayList<>();
        if (runes == null) {
            return sanitized;
        }
        for (String rune : runes) {
            if (rune == null || rune.isBlank()) {
                continue;
            }
            sanitized.add(rune.trim().toLowerCase(Locale.ROOT));
        }
        return sanitized;
    }

    private static int findReframeIndex(List<String> lexemes) {
        for (int i = 0; i < lexemes.size(); i++) {
            if ("reframe".equalsIgnoreCase(lexemes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> fuseWithTrailingSourceIfPresent(List<String> captured, List<String> tail) {
        int lastSourceIndex = findLastSource(captured);
        int trailingSourceIndex = findFirstSource(tail);
        if (lastSourceIndex < 0 || trailingSourceIndex < 0) {
            return captured;
        }
        String left = captured.get(lastSourceIndex);
        String right = tail.get(trailingSourceIndex);
        String fused = fuse(left, right).orElse(null);
        if (fused == null) {
            return captured;
        }
        List<String> updated = new ArrayList<>(captured);
        updated.set(lastSourceIndex, fused);
        return updated;
    }

    private static int findLastSource(List<String> lexemes) {
        for (int i = lexemes.size() - 1; i >= 0; i--) {
            if (isSource(lexemes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstSource(List<String> lexemes) {
        for (int i = 0; i < lexemes.size(); i++) {
            if (isSource(lexemes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSource(String runeId) {
        if (runeId == null) {
            return false;
        }
        RuneType type = RUNE_TYPES.get(runeId.toLowerCase(Locale.ROOT));
        return type == RuneType.SOURCE;
    }

    private static Optional<String> fuse(String a, String b) {
        if (a == null || b == null) {
            return Optional.empty();
        }
        RuneMeta left = new RuneMeta(a.toLowerCase(Locale.ROOT), RUNE_TYPES.getOrDefault(a.toLowerCase(Locale.ROOT), RuneType.UNKNOWN));
        RuneMeta right = new RuneMeta(b.toLowerCase(Locale.ROOT), RUNE_TYPES.getOrDefault(b.toLowerCase(Locale.ROOT), RuneType.UNKNOWN));
        for (FusionRule rule : FUSIONS) {
            if (rule.matches(left, right)) {
                return Optional.of(rule.result());
            }
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> findGrimoire(Inventory inventory) {
        if (inventory == null) {
            return Optional.empty();
        }
        for (ItemStack stack : inventory.items) {
            if (stack.getItem() instanceof GrimoireItem) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    private static void assignToRepertoire(ServerPlayer player, String runeId) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        List<String> slots = repertoire.copySlots();
        if (slots.contains(runeId)) {
            return;
        }
        int targetSlot = findEmptySlot(slots);
        if (targetSlot < 0) {
            player.sendSystemMessage(Component.literal("Sem espaco livre no repertorio para a runa '" + runeId + "'. Ajuste manualmente."));
            return;
        }
        repertoire.assignSlot(targetSlot, runeId);
        SpellingNetwork.syncToPlayer(player, repertoire);
    }

    private static void removeFromRepertoire(ServerPlayer player, String runeId) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        List<String> slots = repertoire.copySlots();
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            String value = slots.get(i);
            if (value != null && !value.isBlank() && value.equalsIgnoreCase(runeId)) {
                repertoire.assignSlot(i, "");
                changed = true;
            }
        }
        if (changed) {
            SpellingNetwork.syncToPlayer(player, repertoire);
        }
    }

    private static int findEmptySlot(List<String> slots) {
        for (int i = 0; i < slots.size(); i++) {
            String value = slots.get(i);
            if (value == null || value.isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static void loadFusionData() {
        var stream = ReframeFunctionHandler.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, RuneConfig> parsed = new Gson().fromJson(reader, MAP_TYPE);
            if (parsed == null) {
                return;
            }
            parsed.forEach((id, cfg) -> {
                String key = id.toLowerCase(Locale.ROOT);
                RUNE_TYPES.put(key, RuneType.from(cfg.type));
                if (cfg.fusionOf != null && cfg.fusionOf.size() == 2) {
                    FUSIONS.add(new FusionRule(key, cfg.fusionOf.get(0), cfg.fusionOf.get(1)));
                }
            });
        } catch (Exception ignored) {
        }
    }

    private record RuneConfig(String type,
                              String translation,
                              @SerializedName("requiresTarget") Boolean requiresTarget,
                              List<String> fusionOf) { }

    private enum RuneType {
        SOURCE,
        FUNCTION,
        SHAPE,
        FILTER,
        UNKNOWN;

        static RuneType from(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            try {
                return RuneType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

    private record RuneMeta(String id, RuneType type) { }

    private record FusionRule(String result, String first, String second) {
        boolean matches(RuneMeta a, RuneMeta b) {
            return (matchesToken(first, a, b) && matchesToken(second, a, b))
                    || (matchesToken(first, b, a) && matchesToken(second, b, a));
        }

        private boolean matchesToken(String token, RuneMeta primary, RuneMeta secondary) {
            if ("source".equalsIgnoreCase(token)) {
                return secondary.type() == RuneType.SOURCE;
            }
            return token.equalsIgnoreCase(primary.id()) || token.equalsIgnoreCase(secondary.id());
        }
    }
}

package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spelling.custom.CustomRuneHelper;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Records the current spell sequence into a grimoire, binding it to a custom rune id.
 */
public final class ReframeFunctionHandler implements SpellFunctionHandler {

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
        if (!CustomRuneHelper.isNameable(runeId)) {
            player.sendSystemMessage(Component.literal("O nome '" + runeId + "' já é uma runa da língua ou um número: "
                    + "uma runa nomeada precisa de um nome próprio (uma palavra, como uma marca)."));
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

    /** Where the naming verb stands in the sentence (reframe): what comes before it is the spell it names. */
    private static int findReframeIndex(List<String> lexemes) {
        for (int i = 0; i < lexemes.size(); i++) {
            if (Lexicons.get().isNaming(lexemes.get(i))) {
                return i;
            }
        }
        return -1;
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
}

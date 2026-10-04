package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.lexicon.Foci;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What a wand takes at the anvil (docs/varinhas-design.md): the material of its grip (or of its haste, when it is only a
 * haste) gives it back all it can conduct; a gem is set in it in place of the one it had, which breaks with what it held.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class WandAnvil {

    /** Levels of experience each anvil work on a wand costs. */
    private static final int LEVEL_COST = 1;

    /** What mends a wand that is only a haste: what the haste was made of. */
    private static final Map<Supplier<Item>, Item> HASTE_MATERIAL = Map.of(
            ElderLexicon.IMPROVISED_WAND, Items.STICK,
            ElderLexicon.IMPROVISED_WAND_BONE, Items.BONE,
            ElderLexicon.IMPROVISED_WAND_BAMBOO, Items.BAMBOO,
            ElderLexicon.IMPROVISED_WAND_BLAZE, Items.BLAZE_ROD
    );

    private WandAnvil() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack wandStack = event.getLeft();
        ItemStack material = event.getRight();
        if (wandStack.isEmpty() || material.isEmpty() || !(wandStack.getItem() instanceof WandItem wand)) {
            return;
        }
        if (mends(wand, wandStack, material)) {
            ItemStack mended = wandStack.copy();
            wand.repair(mended);
            offer(event, mended);
            return;
        }
        Optional<Foci.Setting> gem = WandParts.settingOf(material);
        if (gem.isPresent()) {
            ItemStack reset = wandStack.copy();
            wand.applySetting(reset, gem.get());
            offer(event, reset);
        }
    }

    private static boolean mends(WandItem wand, ItemStack wandStack, ItemStack material) {
        Optional<Foci.Grip> grip = wand.grip(wandStack);
        if (grip.isPresent()) {
            return WandParts.gripOf(material).map(made -> made.id().equals(grip.get().id())).orElse(false);
        }
        return HASTE_MATERIAL.entrySet().stream()
                .anyMatch(entry -> entry.getKey().get() == wandStack.getItem() && material.is(entry.getValue()));
    }

    private static void offer(AnvilUpdateEvent event, ItemStack output) {
        if (event.getName() != null && !event.getName().isBlank()) {
            output.setHoverName(net.minecraft.network.chat.Component.literal(event.getName()));
        }
        event.setOutput(output);
        event.setCost(LEVEL_COST);
        event.setMaterialCost(1);
    }
}

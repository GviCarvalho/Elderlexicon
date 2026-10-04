package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.magic.lexicon.Foci;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Which grip or setting of the foci data (foci.json) an item makes, if any. */
public final class WandParts {

    private WandParts() {
    }

    /** The grip {@code stack} makes: a log of the grip's tag, or the grip's item. */
    public static Optional<Foci.Grip> gripOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String id = idOf(stack);
        for (Foci.Grip grip : Foci.grips().values()) {
            if (!grip.item().isEmpty() && grip.item().equals(id)) {
                return Optional.of(grip);
            }
            if (!grip.tag().isEmpty()) {
                ResourceLocation tag = ResourceLocation.tryParse(grip.tag());
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                    return Optional.of(grip);
                }
            }
        }
        return Optional.empty();
    }

    /** The setting {@code stack} makes: the gem item of a setting. */
    public static Optional<Foci.Setting> settingOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String id = idOf(stack);
        return Foci.settings().values().stream()
                .filter(setting -> !setting.item().isEmpty() && setting.item().equals(id))
                .findFirst();
    }

    private static String idOf(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.toString();
    }
}

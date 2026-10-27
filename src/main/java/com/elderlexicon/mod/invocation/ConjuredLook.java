package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.Qualities;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What every conjured thing shares (docs/invocacao-design.md): it is named by its matter ("espada de fogo"), shows what
 * it is made of and how long it lasts, and comes apart when its time is up.
 */
final class ConjuredLook {

    /** How much a UMU of iron weighs: the matter a format's numbers are given in. */
    static final double IRON_DENSITY = Qualities.of(Materials.get().substance("iron").map(Substance::recipe)
            .orElseThrow()).density();

    /** How tightly iron is packed (ten, as hard as it is), read when asked: the game packs the table once it loads. */
    static double ironPacking() {
        return Materials.get().substance("iron").map(Substance::packing).orElse(1.0D);
    }

    private ConjuredLook() {
    }

    /** What the matter is called: the substance it is, or matter with no name. */
    static Component matterName(Particles particles, double packing) {
        Optional<Substance> substance = particles.present().composition()
                .flatMap(composition -> Materials.get().identify(composition, packing, true));
        return substance.<Component>map(found -> Component.literal(found.name()))
                .orElseGet(() -> Component.translatable("item.elderlexicon.conjured.formless"));
    }

    /** "Espada de fogo": the format's name, and the matter it is made of. */
    static Component name(String key, ItemStack stack) {
        if (!Conjured.conjured(stack)) {
            return Component.translatable(key);
        }
        return Component.translatable(key + ".of", matterName(Conjured.matter(stack), Conjured.packing(stack)));
    }

    static void tooltip(ItemStack stack, List<Component> tooltip) {
        if (!Conjured.conjured(stack)) {
            return;
        }
        Particles matter = Conjured.matter(stack).present();
        long total = Math.max(1L, matter.total());
        MutableComponent shares = Component.literal("");
        boolean first = true;
        for (VitaElement aspect : Particles.ASPECTS) {
            long count = matter.count(aspect);
            if (count <= 0L) {
                continue;
            }
            shares.append(Component.literal((first ? "" : " · ") + aspect.runeId() + " "
                    + Math.round(100.0D * count / total) + "%"));
            first = false;
        }
        tooltip.add(shares.withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.elderlexicon.conjured.amount",
                String.format(Locale.ROOT, "%.1f", matter.umu()), matter.total()).withStyle(ChatFormatting.DARK_GRAY));
        if (Conjured.volatileMatter(matter)) {
            tooltip.add(Component.translatable("tooltip.elderlexicon.conjured.volatile",
                    String.format(Locale.ROOT, "%.1f", Conjured.window(stack) / 20.0D))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    static void tick(ItemStack stack, Level level, Entity holder) {
        Conjured.tick(stack, level, holder);
    }

    /** The item on the ground ticks too; it is gone when it came apart. */
    static boolean tickLying(ItemStack stack, ItemEntity lying) {
        return Conjured.tick(stack, lying.level(), lying);
    }
}

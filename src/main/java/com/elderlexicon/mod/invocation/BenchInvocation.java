package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A vocant whose place is a marked square of an invocation bench (docs/invocacao-design.md, section 2): what it brings
 * goes into the square, as a source invoked into a marked wand goes into its gem. {@code vis quantum 30 vertere igni g2
 * ubis vocant} puts 30 UMU of fire in square g2; {@code lingote_a g2 ubis vocant} puts the marked ingot there.
 */
public final class BenchInvocation {

    private BenchInvocation() {
    }

    /** Whether {@code mark} names a square of a bench. */
    public static boolean isSquare(MinecraftServer server, String mark) {
        return BenchSlots.find(server, mark).isPresent();
    }

    /**
     * {@code umu} of {@code element} invoked into the square {@code mark}. Returns what the square did not take (all of
     * it when the bench is not there or its square holds what is no matter), for it to go back where it came from.
     */
    public static double receive(MinecraftServer server, LivingEntity caster, String mark, VitaElement element,
                                 double umu, int window) {
        Optional<BenchSlots.Slot> slot = BenchSlots.find(server, mark);
        Optional<InvocationBenchBlockEntity> bench = slot.flatMap(found -> BenchSlots.bench(server, found));
        if (bench.isEmpty()) {
            tell(caster, Component.translatable("message.elderlexicon.invocation_bench.gone", mark));
            return umu;
        }
        Particles particles = BenchMatter.of(element, umu);
        if (!bench.get().receive(slot.get().index(), particles, window, caster.getUUID())) {
            tell(caster, Component.translatable("message.elderlexicon.invocation_bench.taken", mark));
            return umu;
        }
        ItemStack now = bench.get().grid().getItem(slot.get().index());
        tell(caster, Component.translatable("message.elderlexicon.invocation_bench.received", mark,
                String.format(Locale.ROOT, "%.1f", umu), element.runeId(), now.getHoverName()));
        return 0.0D;
    }

    /** The things bearing a mark, brought into the square {@code mark}: one item of the first that fits. */
    public static void receive(MinecraftServer server, LivingEntity caster, String mark, List<ItemEntity> things) {
        Optional<BenchSlots.Slot> slot = BenchSlots.find(server, mark);
        Optional<InvocationBenchBlockEntity> bench = slot.flatMap(found -> BenchSlots.bench(server, found));
        if (bench.isEmpty()) {
            tell(caster, Component.translatable("message.elderlexicon.invocation_bench.gone", mark));
            return;
        }
        for (ItemEntity lying : things) {
            ItemStack stack = lying.getItem();
            if (!lying.isAlive() || stack.isEmpty()) {
                continue;
            }
            ItemStack one = stack.copyWithCount(1);
            if (bench.get().receive(slot.get().index(), one, caster.getUUID())) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    lying.discard();
                } else {
                    lying.setItem(stack);
                }
                tell(caster, Component.translatable("message.elderlexicon.invocation_bench.brought", mark,
                        one.getHoverName()));
                return;
            }
        }
        tell(caster, Component.translatable("message.elderlexicon.invocation_bench.taken", mark));
    }

    private static void tell(LivingEntity caster, Component message) {
        if (caster instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }
}

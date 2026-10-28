package com.elderlexicon.mod.university;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.invocation.BenchMatter;
import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * The laboratory of measures of the research tower (docs/torre-pesquisa-design.md, 2). The vessel is a cauldron on a
 * plinth of carved stone, with lodestones round it as the instruments: what one holds over it comes apart there into
 * its particles for a moment, and the instruments say what it is and its core, the four elements in a hundred parts
 * (docs/particulas-design.md, 6). Nothing is spent: it is measured and given back. The vessel is the arrangement, not
 * the place: one who builds it elsewhere has the same instrument.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Measures {

    /** How many lodestones round the vessel make it an instrument. */
    private static final int INSTRUMENTS = 2;

    private Measures() {
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!isVessel(level, pos)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        if (level.isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack one = held.copyWithCount(1);
        Optional<Particles> particles = BenchMatter.of(one);
        ServerLevel server = (ServerLevel) level;
        if (particles.isEmpty() || particles.get().present().total() <= 0L) {
            player.displayClientMessage(Component.literal("Os instrumentos não acham cerne em ")
                    .append(one.getHoverName()).append(": não é matéria natural, é coisa feita. O que faz dela o "
                            + "que ela é não é proporção (a Forma).").withStyle(ChatFormatting.GRAY), false);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6F, 0.7F);
            return;
        }
        report(player, one, particles.get());
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 12,
                0.2D, 0.4D, 0.2D, 0.01D);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    /** A cauldron on carved stone, with at least {@link #INSTRUMENTS} lodestones within two blocks round it. */
    static boolean isVessel(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)
                || !level.getBlockState(pos.below()).is(Blocks.CHISELED_STONE_BRICKS)) {
            return false;
        }
        int instruments = 0;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.getBlockState(at).is(Blocks.LODESTONE)) {
                instruments++;
            }
        }
        return instruments >= INSTRUMENTS;
    }

    /** What the instruments say: the thing, its weight in UMU, its core, and what it lacks if it lacks anything. */
    private static void report(ServerPlayer player, ItemStack thing, Particles particles) {
        Particles present = particles.present();
        Optional<Substance> substance = Materials.get().identify(present);
        MutableComponent head = Component.literal("Os instrumentos medem ").withStyle(ChatFormatting.GOLD)
                .append(thing.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        substance.ifPresent(found -> head.append(Component.literal(": " + found.name()).withStyle(ChatFormatting.GOLD)));
        head.append(Component.literal(String.format(java.util.Locale.ROOT, " (%.2f UMU)", present.umu()))
                .withStyle(ChatFormatting.GRAY));
        player.displayClientMessage(head, false);
        Composition core = present.composition().orElseThrow();
        MutableComponent line = Component.literal("Cerne: ").withStyle(ChatFormatting.GOLD);
        int[] parts = hundredths(core);
        boolean first = true;
        for (int i = 0; i < Particles.ASPECTS.size(); i++) {
            VitaElement aspect = Particles.ASPECTS.get(i);
            if (!first) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            first = false;
            line.append(Component.literal(aspect.runeId() + " " + parts[i]).withStyle(colour(aspect)));
        }
        player.displayClientMessage(line, false);
        if (particles.owes()) {
            Particles owed = particles.owed();
            MutableComponent lack = Component.literal("Falta: ").withStyle(ChatFormatting.DARK_PURPLE);
            for (VitaElement aspect : Particles.ASPECTS) {
                long count = Math.abs(owed.count(aspect));
                if (count > 0L) {
                    lack.append(Component.literal(aspect.runeId() + " " + count + " partículas  ")
                            .withStyle(colour(aspect)));
                }
            }
            player.displayClientMessage(lack, false);
        }
    }

    /** The core in whole parts of a hundred, in the order of {@link Particles#ASPECTS}, summing a hundred. */
    private static int[] hundredths(Composition core) {
        int[] parts = new int[Particles.ASPECTS.size()];
        double[] rest = new double[parts.length];
        int sum = 0;
        for (int i = 0; i < parts.length; i++) {
            double exact = core.share(Particles.ASPECTS.get(i)) * 100.0D;
            parts[i] = (int) Math.floor(exact);
            rest[i] = exact - parts[i];
            sum += parts[i];
        }
        // What the rounding left goes to the largest remainders.
        while (sum < 100) {
            int best = 0;
            for (int i = 1; i < parts.length; i++) {
                if (rest[i] > rest[best]) {
                    best = i;
                }
            }
            parts[best]++;
            rest[best] = -1.0D;
            sum++;
        }
        return parts;
    }

    private static ChatFormatting colour(VitaElement aspect) {
        return switch (aspect) {
            case IGNI -> ChatFormatting.RED;
            case AQUA -> ChatFormatting.BLUE;
            case AURA -> ChatFormatting.WHITE;
            case FIRMO -> ChatFormatting.GREEN;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
    }

}

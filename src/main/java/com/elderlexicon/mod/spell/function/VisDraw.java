package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.spell.Casters;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.matter.FormlessMatterBlockEntity;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@code vis quantum -10 vocant}: vis drawn out of the world. Vis is the four in perfect balance, a quarter of each
 * (book 3.1), and the world has no vis lying about: what the spirit draws is a quarter of the amount of each of the four,
 * out of the particles of the blocks around the hand (or the ubis place), nearest first, all but the mound that holds
 * the mage up. The blocks are what is left of them after that, another thing or, when less than a whole block is
 * left, the same matter rarefied, where it was: nothing of it is lost. What was drawn is fused back into vis, which costs the whole
 * ladder (book: "voltar para ela custa a escada inteira"); only as much fuses as the scarcest of the four allows, and
 * what of the others had no partner goes into the body as what it is.
 */
final class VisDraw {

    /** How far around the hand the blocks are drawn from. */
    private static final int REACH = 5;
    private static final double EPSILON = 1.0E-4D;

    private VisDraw() {
    }

    /** Draws {@code umu} out of the world, a quarter of each of the four, and gives the mage what fuses into vis. */
    static void absorb(SpellContext context, double umu) {
        LivingEntity player = context.player();
        if (!SpellEffects.isPlayerValid(player) || umu <= EPSILON) {
            return;
        }
        ServerLevel level = Casters.level(player);
        Vec3 centre = context.currentAction().flatMap(action -> action.place())
                .flatMap(place -> MarkSpells.destination(context, Optional.of(place), MarkSpells.SUMMON_RANGE))
                .map(MarkSpells.Destination::point)
                .orElseGet(() -> WorldSources.handOf(player));
        long quarter = Particles.ofUmu(umu / 4.0D);
        long[] wanted = {quarter, quarter, quarter, quarter};
        long[] drawn = new long[4];
        Vec3 hand = WorldSources.handOf(player);
        for (BlockPos pos : around(centre)) {
            if (satisfied(wanted, drawn)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || holdsUp(player, pos) || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            BlockEntity kept = level.getBlockEntity(pos);
            if (kept != null && !(kept instanceof FormlessMatterBlockEntity)) {
                continue;
            }
            Optional<Matter> read = WorldMatter.read(level, pos);
            if (read.isEmpty()) {
                continue;
            }
            Particles held = read.get().particles().present();
            long[] take = new long[4];
            long taken = 0L;
            for (int i = 0; i < 4; i++) {
                take[i] = Math.min(wanted[i] - drawn[i], held.count(Particles.ASPECTS.get(i)));
                taken += take[i];
            }
            if (taken <= 0L) {
                continue;
            }
            WorldMatter.rarefy(level, pos, read.get(), new Particles(take[0], take[1], take[2], take[3]));
            for (int i = 0; i < 4; i++) {
                drawn[i] += take[i];
            }
            Gatherings.stream(level, VitaElement.BALANCED, Vec3.atCenterOf(pos), state, hand,
                    (int) Math.max(3L, Math.min(12L, taken / 64L)));
        }
        long fused = Math.min(Math.min(drawn[0], drawn[1]), Math.min(drawn[2], drawn[3]));
        double fusedUmu = 4.0D * fused / Particles.PER_UMU;
        // Fused back into vis, the whole ladder is climbed: the work takes its share of what was drawn.
        double vis = fusedUmu - Conversion.work(fusedUmu, VitaElement.AQUA, VitaElement.BALANCED);
        if (vis > EPSILON) {
            Casters.giveVis(player, vis);
        }
        for (int i = 0; i < 4; i++) {
            long alone = drawn[i] - fused;
            if (alone > 0L) {
                context.absorbIntoBody(Particles.ASPECTS.get(i), (double) alone / Particles.PER_UMU);
            }
        }
        if (fusedUmu <= EPSILON) {
            Casters.status(player, Component.literal(scarcest(drawn) == null
                    ? "Nao ha materia por perto para tirar vis."
                    : "Falta " + scarcest(drawn).runeId() + " por perto: a vis precisa dos quatro."));
            return;
        }
        Casters.status(player, Component.literal("Tiraste do mundo " + SpellCostCalculator.formatCost(fusedUmu)
                + " UMU, um quarto de cada; fundidos, deram " + SpellCostCalculator.formatCost(vis) + " UMU de vis."));
    }

    /** The positions within reach of {@code centre}, nearest first. */
    private static List<BlockPos> around(Vec3 centre) {
        BlockPos middle = BlockPos.containing(centre);
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-REACH, -REACH, -REACH),
                middle.offset(REACH, REACH, REACH))) {
            if (pos.distToCenterSqr(centre) <= (REACH + 0.5D) * (REACH + 0.5D)) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(centre)));
        return found;
    }

    /**
     * Whether a block holds the mage up, and is not drawn from: the ground under the feet and, below it, a cone widening
     * as it goes down (a block as far out as it is deep), so the mage is left on a mound in the middle of the hollow and
     * not on a pillar.
     */
    static boolean holdsUp(LivingEntity player, BlockPos pos) {
        double below = player.getY() - (pos.getY() + 1.0D);
        if (below < -1.0E-3D) {
            return false;
        }
        double dx = pos.getX() + 0.5D - player.getX();
        double dz = pos.getZ() + 0.5D - player.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= below + 1.0D;
    }

    private static boolean satisfied(long[] wanted, long[] drawn) {
        for (int i = 0; i < wanted.length; i++) {
            if (drawn[i] < wanted[i]) {
                return false;
            }
        }
        return true;
    }

    /** The aspect least drawn, the one the fusion lacked; null when nothing at all was drawn. */
    private static VitaElement scarcest(long[] drawn) {
        long total = drawn[0] + drawn[1] + drawn[2] + drawn[3];
        if (total <= 0L) {
            return null;
        }
        int least = 0;
        for (int i = 1; i < drawn.length; i++) {
            if (drawn[i] < drawn[least]) {
                least = i;
            }
        }
        return Particles.ASPECTS.get(least);
    }
}

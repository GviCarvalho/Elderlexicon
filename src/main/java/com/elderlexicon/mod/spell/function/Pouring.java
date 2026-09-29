package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Template;
import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.Qualities;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Matter a spell brings where fluid matter is is poured into it and mixes with it (L4, docs/plano-materia-e-forca.md,
 * stage 5): water summoned into a pool of molten earth, fire thrown into lava. The spirit says what the mixture became,
 * which is how a mage finds the recipes out.
 */
final class Pouring {

    private static final double EPSILON = 1.0E-6D;

    private Pouring() {
    }

    /**
     * Where matter arriving at {@code impact} is poured, when it arrives in fluid matter: the block it struck, or the one
     * before the face it struck. Empty when it arrives anywhere else, or at a creature, which it strikes.
     */
    static Optional<BlockPos> into(ServerLevel level, SpellEffects.SpellImpact impact) {
        if (impact.entity() != null) {
            return Optional.empty();
        }
        if (impact.blockPos() != null && WorldMatter.holdsFluid(level, impact.blockPos())) {
            return Optional.of(impact.blockPos());
        }
        BlockPos spot = Transfer.spotOf(impact);
        return WorldMatter.holdsFluid(level, spot) ? Optional.of(spot) : Optional.empty();
    }

    /**
     * What a source brought out of the body is, as matter (L3): the substance its essence makes (lutum's is mud), as
     * it is found, {@code umu} of it. Empty for what is energy and no matter (vis).
     */
    static Optional<Matter> summoned(String rune, double umu) {
        if (rune == null || umu <= EPSILON) {
            return Optional.empty();
        }
        return Lexicons.get().source(rune.toLowerCase(Locale.ROOT))
                .flatMap(source -> Composition.ofEssence(source.essence()))
                .flatMap(composition -> Materials.get().identify(composition))
                .map(substance -> Matter.natural(substance, umu));
    }

    /**
     * {@code aqua vocant} landing in fluid matter: what it brings out of the body is poured in, instead of appearing
     * beside it. False when it does not land in fluid matter, or brings nothing fluid, and appears as it always does.
     */
    static boolean summon(SpellContext context, ServerPlayer player, String rune, SpellEffects.SpellImpact impact,
                          double umu) {
        ServerLevel level = player.serverLevel();
        Optional<Matter> matter = summoned(rune, umu).filter(summoned -> summoned.state().fluid());
        if (matter.isEmpty()) {
            return false;
        }
        Optional<BlockPos> landing = into(level, impact);
        if (landing.isEmpty()) {
            return false;
        }
        WorldMatter.Placed placed = WorldMatter.pour(level, landing.get(), matter.get());
        keep(context, player, matter.get(), placed);
        tell(player, matter.get(), placed);
        return true;
    }

    /** What found no room, or made no whole block, goes into the mage as energy of the state it was in (L1, L3). */
    static void keep(SpellContext context, ServerPlayer player, Matter poured, WorldMatter.Placed placed) {
        if (placed.leftover() <= EPSILON || context.focusActive()) {
            return;
        }
        Matter left = placed.mixed().orElse(poured);
        VitaSystem.restoreElementEnergy(player, left.state().element(), placed.leftover());
    }

    /**
     * The spirit says what the mixture became, when it became something else than what was poured: a natural thing,
     * by its name, or what it is like; and what of it separated out as a gas.
     */
    static void tell(ServerPlayer player, Matter poured, WorldMatter.Placed placed) {
        Optional<Matter> mixture = placed.mixed();
        if (mixture.isEmpty()) {
            return;
        }
        Optional<Substance> becomes = mixture.get().substance(Materials.get());
        if (becomes.isPresent() && becomes.equals(poured.substance(Materials.get()))
                && !MatterLaws.react(mixture.get()).reacted()) {
            return;
        }
        tell(player, mixture.get());
    }

    /**
     * The spirit says what a mixture is, after its opposites react (docs/plano-materia-emergente.md): what stays, a
     * natural thing by its name or else what it is like, and what separated out as a gas.
     */
    static void tell(ServerPlayer player, Matter mixture) {
        MatterLaws.Reaction reaction = MatterLaws.react(mixture);
        StringBuilder text = new StringBuilder();
        reaction.remains().ifPresent(stays -> text.append(stays.substance(Materials.get())
                .map(substance -> Template.fill(Lexicons.get().note("note.mixture.substance")
                        .orElse("A mistura virou {substance}."), Map.of("substance", substance.name())))
                .orElseGet(() -> Template.fill(Lexicons.get().note("note.mixture.formless")
                        .orElse("A mistura ficou {description}."), Map.of("description", Qualities.describe(stays))))));
        reaction.released().ifPresent(gas -> text.append(text.isEmpty() ? "" : " ").append(Template.fill(
                Lexicons.get().note("note.mixture.released")
                        .orElse("{umu} UMU se separaram e subiram como gas ({description})."),
                Map.of("umu", String.format(java.util.Locale.ROOT, "%.1f", gas.umu()),
                        "description", Qualities.describe(gas)))));
        MarkSpells.tell(player, text.toString());
    }
}

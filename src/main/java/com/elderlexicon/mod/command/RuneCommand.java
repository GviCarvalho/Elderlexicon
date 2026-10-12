package com.elderlexicon.mod.command;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.spelling.data.RuneUnlocks;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * {@code /runas}: unlocks and locks runes by hand (docs/repertorio-design.md), for testing until every rune has the
 * circumstances that teach it. {@code /runas desbloquear <runa|todas> [jogadores]}, {@code /runas bloquear ...},
 * {@code /runas lista [jogador]}, {@code /runas entender <runa>} (as if it had been lived) and {@code /runas sonhar <runa>}
 * (its dream, at once) and {@code /runas catalogo [busca]} (what the mind knows) and {@code /runas mente} (the nearest
 * sorcerer's mind). For operators.
 */
public final class RuneCommand {

    private static final String ALL = "todas";

    private RuneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("runas")
                .requires(source -> source.hasPermission(2))
                .then(change("desbloquear", RuneUnlocks::unlock))
                .then(change("bloquear", RuneUnlocks::lock))
                .then(change("entender", RuneCommand::understand))
                .then(Commands.literal("sonhar")
                        .then(Commands.argument("runa", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        com.elderlexicon.mod.nahva.Understanding.lessons().keySet(), builder))
                                .executes(context -> dream(context.getSource(),
                                        StringArgumentType.getString(context, "runa")))))
                .then(Commands.literal("mente")
                        .executes(context -> mind(context.getSource())))
                .then(Commands.literal("catalogo")
                        .executes(context -> catalog(context.getSource(), ""))
                        .then(Commands.argument("busca", StringArgumentType.greedyString())
                                .executes(context -> catalog(context.getSource(),
                                        StringArgumentType.getString(context, "busca")))))
                .then(Commands.literal("lista")
                        .executes(context -> list(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> list(context.getSource(),
                                        EntityArgument.getPlayer(context, "jogador"))))));
    }

    /** {@code /runas mente}: who the nearest sorcerer is, what it knows and what it read last. */
    private static int mind(CommandSourceStack source) {
        net.minecraft.world.phys.Vec3 at = source.getPosition();
        List<com.elderlexicon.mod.illager.Sorcerer> near = source.getLevel().getEntitiesOfClass(
                com.elderlexicon.mod.illager.Sorcerer.class, new net.minecraft.world.phys.AABB(at, at).inflate(32.0D));
        if (near.isEmpty()) {
            source.sendFailure(Component.literal("Nenhum feiticeiro a 32 blocos."));
            return 0;
        }
        com.elderlexicon.mod.illager.Sorcerer nearest = near.stream()
                .min(java.util.Comparator.comparingDouble(evoker -> evoker.position().distanceToSqr(at))).get();
        source.sendSuccess(() -> Component.literal("Feiticeiro: " + nearest.describe()), false);
        return 1;
    }

    /** How many sentences the catalog shows at once. */
    private static final int CATALOG_SHOWN = 12;

    /**
     * {@code /runas catalogo [busca]}: what the mind knows (docs/feiticeiro-design.md, 3.2). With nothing, how many
     * sentences it knows and how many begin with each operation; with words, the sentences whose signature holds all of
     * them ({@code project igni}, {@code manifest aqua rev}, {@code self v0}), cheapest first.
     */
    private static int catalog(CommandSourceStack source, String search) {
        com.elderlexicon.mod.mind.PhraseCatalog catalog = com.elderlexicon.mod.mind.PhraseCatalog.get();
        if (search.isBlank()) {
            java.util.Map<String, Long> byOperation = new java.util.TreeMap<>(catalog.phrases().stream()
                    .collect(java.util.stream.Collectors.groupingBy(phrase -> phrase.acts().get(0).operation(),
                            java.util.stream.Collectors.counting())));
            source.sendSuccess(() -> Component.literal("O catálogo conhece " + catalog.phrases().size()
                    + " frases. Pela primeira operação: " + byOperation), false);
            return catalog.phrases().size();
        }
        List<String> terms = List.of(search.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+"));
        List<com.elderlexicon.mod.mind.Phrase> found = catalog.phrases().stream()
                .filter(phrase -> terms.stream().allMatch(term -> phrase.signature().contains(term)))
                .toList();
        source.sendSuccess(() -> Component.literal(found.size() + " frases com \"" + search + "\":"), false);
        found.stream().limit(CATALOG_SHOWN).forEach(phrase -> source.sendSuccess(() -> Component.literal(
                "  " + phrase.text() + "  §7(" + com.elderlexicon.mod.command.SpellCostCalculator.formatCost(
                        phrase.cost()) + " UMU: " + phrase.signature() + ")"), false));
        return found.size();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> change(
            String name, BiPredicate<ServerPlayer, String> action) {
        return Commands.literal(name)
                .then(Commands.argument("runa", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Stream.concat(Stream.of(ALL), Lexicons.get().runes().stream().map(Rune::id)), builder))
                        .executes(context -> apply(context.getSource(),
                                List.of(context.getSource().getPlayerOrException()),
                                StringArgumentType.getString(context, "runa"), action))
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(context -> apply(context.getSource(),
                                        EntityArgument.getPlayers(context, "jogadores"),
                                        StringArgumentType.getString(context, "runa"), action))));
    }

    private static int apply(CommandSourceStack source, Collection<ServerPlayer> players, String rune,
                             BiPredicate<ServerPlayer, String> action) {
        List<String> runes = ALL.equalsIgnoreCase(rune)
                ? Lexicons.get().runes().stream().map(Rune::id).toList()
                : List.of(rune.toLowerCase(java.util.Locale.ROOT));
        if (!ALL.equalsIgnoreCase(rune) && !Lexicons.get().isRune(runes.get(0))) {
            source.sendFailure(Component.literal("Não há runa chamada " + rune + "."));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            for (String id : runes) {
                if (action.test(player, id)) {
                    changed++;
                }
            }
        }
        int total = changed;
        source.sendSuccess(() -> Component.literal(total + " runa(s) mudada(s)."), true);
        return total;
    }

    /** Marks a rune understood, as if every experience of it had been lived, so its dream may come. */
    private static boolean understand(ServerPlayer player, String rune) {
        com.elderlexicon.mod.spelling.data.SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        com.elderlexicon.mod.nahva.Understanding.lesson(rune)
                .ifPresent(lesson -> lesson.experiences().forEach(experience -> repertoire.experience(rune, experience)));
        boolean changed = repertoire.understand(rune);
        com.elderlexicon.mod.spelling.network.SpellingNetwork.syncToPlayer(player, repertoire);
        return changed;
    }

    /** Takes the one running it to the dream of a rune at once, as if the night had brought it. */
    private static int dream(CommandSourceStack source, String rune) throws CommandSyntaxException {
        if (!com.elderlexicon.mod.nahva.Dreams.enter(source.getPlayerOrException(), rune.toLowerCase(java.util.Locale.ROOT))) {
            source.sendFailure(Component.literal("Não há sonho para " + rune + " (ou você já está sonhando)."));
            return 0;
        }
        return 1;
    }

    private static int list(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        Collection<String> unlocked = SpellingRepertoireHelper.get(player).unlocked();
        source.sendSuccess(() -> Component.literal(player.getScoreboardName() + " conhece " + unlocked.size() + " de "
                + Lexicons.get().runes().size() + " runas: " + String.join(", ", unlocked)), false);
        return unlocked.size();
    }
}

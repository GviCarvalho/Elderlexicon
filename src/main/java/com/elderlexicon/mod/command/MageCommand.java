package com.elderlexicon.mod.command;

import com.elderlexicon.mod.nahva.TowerRealm;
import com.elderlexicon.mod.spelling.data.MageAbilities;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /mago}: gives or takes the mage's abilities by hand (docs/habilidades-design.md), for testing.
 * {@code /mago transe}, {@code /mago fluxo}, {@code /mago nenhuma}, {@code /mago ilha} (to the mage's island, as if
 * dead) and {@code /mago torre} (to the infinite tower: from the island by its road, or straight away), each with optional
 * players; {@code /mago sala <sala>}, which makes the next climb begin with that room; and {@code /mago matricula}
 * ({@code ver}, {@code vencer}: the period ends now, {@code esquecer}: struck off the university's book), to try the
 * university (docs/mago-design.md, 5h); {@code /mago emissario [convite|retorno]}, an Emissary sent to the player now
 * (5j). For operators.
 */
public final class MageCommand {

    private MageCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mago")
                .requires(source -> source.hasPermission(2))
                .then(ability("transe", MageAbilities::grantTrance))
                .then(ability("fluxo", MageAbilities::grantFlow))
                .then(ability("nenhuma", player -> MageAbilities.revoke(player, true)))
                .then(ability("ilha", player -> com.elderlexicon.mod.nahva.Dreams.enterKern(player)))
                .then(ability("torre", player -> com.elderlexicon.mod.nahva.Dreams.enterTower(player)))
                .then(Commands.literal("matricula")
                        .then(Commands.literal("ver").executes(context -> enrollment(context.getSource(), "ver")))
                        .then(Commands.literal("vencer").executes(context -> enrollment(context.getSource(), "vencer")))
                        .then(Commands.literal("esquecer")
                                .executes(context -> enrollment(context.getSource(), "esquecer"))))
                .then(Commands.literal("emissario")
                        .executes(context -> emissary(context.getSource(),
                                com.elderlexicon.mod.university.Emissary.Message.INVITATION))
                        .then(Commands.literal("convite").executes(context -> emissary(context.getSource(),
                                com.elderlexicon.mod.university.Emissary.Message.INVITATION)))
                        .then(Commands.literal("retorno").executes(context -> emissary(context.getSource(),
                                com.elderlexicon.mod.university.Emissary.Message.RETURN))))
                .then(Commands.literal("sala")
                        .then(Commands.argument("sala", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        TowerRealm.roomNames(), builder))
                                .executes(context -> {
                                    String room = StringArgumentType.getString(context, "sala");
                                    if (!TowerRealm.beginWith(room)) {
                                        context.getSource().sendFailure(Component.literal("Não há sala \"" + room
                                                + "\". Salas: " + String.join(", ", TowerRealm.roomNames())));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("A próxima subida começa"
                                            + " pela sala \"" + room + "\"."), true);
                                    return 1;
                                }))));
    }

    /** An Emissary sent to the player now, with the invitation or the house's call back (docs/mago-design.md, 5j). */
    private static int emissary(CommandSourceStack source,
                                com.elderlexicon.mod.university.Emissary.Message message)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        if (com.elderlexicon.mod.university.Emissaries.send(player, message).isEmpty()) {
            source.sendFailure(Component.literal("Nenhum emissário pôde vir: não há universidade neste mundo, ou não "
                    + "há chão por perto de onde ele chegar."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Um emissário vem a caminho."), false);
        return 1;
    }

    private static int enrollment(CommandSourceStack source, String what)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        com.elderlexicon.mod.university.Enrollments book = com.elderlexicon.mod.university.Enrollments.of(
                player.serverLevel());
        long now = player.getServer().overworld().getGameTime();
        switch (what) {
            case "vencer" -> book.endPeriod(player.getUUID(), now);
            case "esquecer" -> book.forget(player.getUUID());
            default -> {
            }
        }
        String said = book.find(player.getUUID()).map(student -> "Grau: " + student.rank().map(Enum::name)
                .orElse("nenhum") + "; mensalidade " + student.fee() + "; "
                + (student.inGoodStanding(now) ? "em dia por mais " + (student.paidUntil() - now) / 20 + " s"
                : student.owes(now) ? "devendo o período" : student.dueForExamination(now) ? "exame do período pendente"
                : "não admitido") + "; disciplinas " + student.passed()).orElse("Fora do livro da universidade.");
        source.sendSuccess(() -> Component.literal(said), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ability(String name, Consumer<ServerPlayer> action) {
        return Commands.literal(name)
                .executes(context -> apply(context.getSource(), List.of(context.getSource().getPlayerOrException()),
                        name, action))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(context -> apply(context.getSource(), EntityArgument.getPlayers(context, "jogadores"),
                                name, action)));
    }

    private static int apply(CommandSourceStack source, Collection<ServerPlayer> players, String name,
                             Consumer<ServerPlayer> action) {
        players.forEach(action);
        source.sendSuccess(() -> Component.literal("Habilidade \"" + name + "\" aplicada a " + players.size()
                + " jogador(es)."), true);
        return players.size();
    }
}

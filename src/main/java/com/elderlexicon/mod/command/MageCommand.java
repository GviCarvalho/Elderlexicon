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
 * players; and {@code /mago sala <sala>}, which makes the next climb begin with that room. For operators.
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

package com.elderlexicon.mod.command;

import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaImbalanceTier;
import com.elderlexicon.mod.vita.VitaProfile;
import com.elderlexicon.mod.vita.VitaSystem;
import com.elderlexicon.mod.vita.damage.DamageMappingConfig;
import com.elderlexicon.mod.vita.damage.DamageTelemetry;
import com.elderlexicon.mod.vita.recovery.VitaRecoveryCapability;
import com.elderlexicon.mod.vita.recovery.VitaRecoveryConfig;
import com.elderlexicon.mod.vita.recovery.VitaRecoveryTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Provides debug controls to manipulate Vita reserves for testing.
 */
@SuppressWarnings("null")
public final class VitaCommand {

    private VitaCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vita")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("damage")
                        .then(Commands.literal("status").executes(VitaCommand::executeDamageStatus))
                        .then(Commands.literal("list").executes(VitaCommand::executeDamageList))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(context -> executeDamageSet(
                                                        context,
                                                        StringArgumentType.getString(context, "key"),
                                                        DoubleArgumentType.getDouble(context, "value"),
                                                        false))
                                                .then(Commands.literal("persist")
                                                        .executes(context -> executeDamageSet(
                                                                context,
                                                                StringArgumentType.getString(context, "key"),
                                                                DoubleArgumentType.getDouble(context, "value"),
                                                                true))))))
                        .then(Commands.literal("reload").executes(VitaCommand::executeDamageReload))
                        .then(Commands.literal("telemetry")
                                .then(Commands.literal("dump").executes(VitaCommand::executeTelemetryDump))
                                .then(Commands.literal("reset").executes(VitaCommand::executeTelemetryReset))))
                .then(Commands.literal("recovery")
                        .then(Commands.literal("status").executes(VitaCommand::executeRecoveryStatus))
                        .then(Commands.literal("list").executes(VitaCommand::executeRecoveryList))
                        .then(Commands.literal("reload").executes(VitaCommand::executeRecoveryReload))
                        .then(Commands.literal("dump")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> executeRecoveryDump(
                                                context,
                                                EntityArgument.getPlayer(context, "target"))))))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.literal("status")
                                .executes(context -> executeStatus(context, EntityArgument.getPlayer(context, "target"))))
                        .then(Commands.literal("reset")
                                .executes(context -> executeReset(context, EntityArgument.getPlayer(context, "target"))))
                        .then(Commands.argument("element", StringArgumentType.word())
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> executeSet(
                                                        context,
                                                        EntityArgument.getPlayer(context, "target"),
                                                        StringArgumentType.getString(context, "element"),
                                                        DoubleArgumentType.getDouble(context, "amount"))))))));
    }

    private static int executeReset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        VitaSystem.resetStatus(target);
        Component feedback = Component.literal(String.format(
                Locale.ROOT,
                "Vita de %s redefinida para Aqua %.0f / Igni %.0f / Aura %.0f / Firmo %.0f",
                target.getName().getString(),
                VitaSystem.DEFAULT_AQUA,
                VitaSystem.DEFAULT_IGNI,
                VitaSystem.DEFAULT_AURA,
                VitaSystem.DEFAULT_FIRMO));
        context.getSource().sendSuccess(() -> Objects.requireNonNull(feedback), true);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        VitaProfile profile = VitaSystem.fromPlayer(target);
        VitaImbalanceTier aquaTier = VitaSystem.getAquaTier(target);
        VitaImbalanceTier auraTier = VitaSystem.getAuraTier(target);
        VitaImbalanceTier firmoTier = VitaSystem.getFirmoTier(target);
        VitaImbalanceTier igniTier = VitaSystem.getIgniTier(target);
        Component message = Component.literal(String.format(
                Locale.ROOT,
                "%s | Aqua %.1f (%s) | Aura %.1f (%s) | Firmo %.1f (%s) | Igni %.1f (%s)",
                target.getName().getString(),
                profile.aqua(),
                describeTier(aquaTier),
                profile.aura(),
                describeTier(auraTier),
                profile.firmo(),
                describeTier(firmoTier),
                profile.igni(),
                describeTier(igniTier)));
        context.getSource().sendSuccess(() -> Objects.requireNonNull(message), false);
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> context,
                                  ServerPlayer target,
                                  String rawElement,
                                  double amount) throws CommandSyntaxException {
        VitaElement element = resolveElement(rawElement);
        if (element == VitaElement.BALANCED) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        }
        VitaSystem.setElementEnergy(target, element, amount);
        Component feedback = Component.literal(String.format(
                Locale.ROOT,
                "Definido %s para %.2f UMU em %s",
                element.name().toLowerCase(Locale.ROOT),
                amount,
                target.getName().getString()));
        context.getSource().sendSuccess(() -> Objects.requireNonNull(feedback), true);
        return 1;
    }

    private static int executeDamageStatus(CommandContext<CommandSourceStack> context) {
        Component feedback = Component.literal(String.format(
                Locale.ROOT,
                "Vita damage multipliers: %d config entries, %d datapack overrides",
                DamageMappingConfig.configCount(),
                DamageMappingConfig.datapackCount()));
        context.getSource().sendSuccess(() -> Objects.requireNonNull(feedback), false);
        return DamageMappingConfig.configCount();
    }

    private static int executeDamageReload(CommandContext<CommandSourceStack> context) {
        int count = DamageMappingConfig.reload();
        Component feedback = Component.literal(String.format(
                Locale.ROOT,
                "Recarregado damage-mapping.json com %d entradas",
                count));
        context.getSource().sendSuccess(() -> Objects.requireNonNull(feedback), true);
        return count;
    }

    private static int executeDamageList(CommandContext<CommandSourceStack> context) {
        Map<String, Double> snapshot = DamageMappingConfig.snapshot();
        if (snapshot.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Sem multiplicadores configurados."), false);
            return 0;
        }
        snapshot.forEach((key, value) -> context.getSource().sendSuccess(() ->
                Component.literal(String.format(Locale.ROOT, "%s = %.3f", key, value)), false));
        return snapshot.size();
    }

    private static int executeDamageSet(CommandContext<CommandSourceStack> context,
                                        String key,
                                        double value,
                                        boolean persist) {
        boolean updated = DamageMappingConfig.setMultiplier(key, value, persist);
        if (!updated) {
            context.getSource().sendFailure(Component.literal("Chave inválida para damage-mapping."));
            return 0;
        }
        Component feedback = Component.literal(String.format(
                Locale.ROOT,
                "Multiplicador %s definido para %.3f%s",
                key,
                value,
                persist ? " e salvo no config" : ""));
        context.getSource().sendSuccess(() -> feedback, true);
        return 1;
    }

    private static int executeTelemetryDump(CommandContext<CommandSourceStack> context) {
        Map<ResourceLocation, Integer> snapshot = DamageTelemetry.snapshot();
        if (snapshot.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Nenhum evento de telemetria registrado."), false);
            return 0;
        }
        snapshot.forEach((sourceId, count) -> context.getSource().sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "[%s] %d eventos",
                sourceId,
                count)), false));
        return snapshot.size();
    }

    private static int executeTelemetryReset(CommandContext<CommandSourceStack> context) {
        DamageTelemetry.reset();
        context.getSource().sendSuccess(() -> Component.literal("Telemetria de dano redefinida."), true);
        return 1;
    }

    private static int executeRecoveryStatus(CommandContext<CommandSourceStack> context) {
        Component feedback = Component.literal("Configuração de recuperação carregada.");
        context.getSource().sendSuccess(() -> feedback, false);
        return 1;
    }

    private static int executeRecoveryList(CommandContext<CommandSourceStack> context) {
        String json = VitaRecoveryConfig.settings().toJson().toString();
        context.getSource().sendSuccess(() -> Component.literal(json), false);
        return 1;
    }

    private static int executeRecoveryReload(CommandContext<CommandSourceStack> context) {
        VitaRecoveryConfig.reload();
        context.getSource().sendSuccess(() -> Component.literal("Configuração de recuperação recarregada."), true);
        return 1;
    }

    private static int executeRecoveryDump(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        return VitaRecoveryCapability.get(target).map(tracker -> {
            Component info = Component.literal(String.format(
                    Locale.ROOT,
                    "Sun=%d Heat=%d Water=%d Still=%d | AquaLow=%d AquaHigh=%d IgniLow=%d IgniHigh=%d FirmoFood=%d AuraStill=%d AuraAction=%d",
                    tracker.sunlightTimer(),
                    tracker.heatTimer(),
                    tracker.waterTimer(),
                    tracker.stillnessTimer(),
                    tracker.lastAquaLowTick(),
                    tracker.lastAquaHighTick(),
                    tracker.lastIgniLowTick(),
                    tracker.lastIgniHighTick(),
                    tracker.lastFirmoFoodTick(),
                    tracker.lastAuraStillTick(),
                    tracker.lastAuraActionTick()));
            context.getSource().sendSuccess(() -> info, false);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.literal("Tracker de recuperação indisponível."));
            return 0;
        });
    }

    private static VitaElement resolveElement(String token) throws CommandSyntaxException {
        if (token == null || token.isBlank()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        }
        VitaElement element = VitaElement.fromRuneId(token);
        if (element == VitaElement.BALANCED) {
            try {
                element = VitaElement.valueOf(token.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                element = VitaElement.BALANCED;
            }
        }
        if (element == VitaElement.BALANCED) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        }
        return element;
    }

    private static String describeTier(VitaImbalanceTier tier) {
        return switch (tier == null ? VitaImbalanceTier.BALANCED : tier) {
            case SEVERELY_LOW -> "Severamente Baixo";
            case SLIGHTLY_LOW -> "Levemente Baixo";
            case BALANCED -> "Equilibrado";
            case SLIGHTLY_HIGH -> "Levemente Alto";
            case SEVERELY_HIGH -> "Severamente Alto";
        };
    }
}

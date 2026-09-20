package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientSpellingController;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Communicates casting results back to the client so the overlay can react.
 */
public record ServerSpellCastResultPacket(boolean success,
                                          Component message,
                                          List<Component> warnings,
                                          long cooldownMs,
                                          List<String> runes) {

    public static ServerSpellCastResultPacket from(ServerSpellingController.SpellCastResponse response) {
        return new ServerSpellCastResultPacket(
                response.success(),
                response.message(),
                response.warnings(),
                response.cooldownMs(),
                response.runes()
        );
    }

    public static ServerSpellCastResultPacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        Component message = buffer.readComponent();
        int warningCount = buffer.readVarInt();
        List<Component> warnings = new ArrayList<>(warningCount);
        for (int i = 0; i < warningCount; i++) {
            warnings.add(buffer.readComponent());
        }
        long cooldown = buffer.readVarLong();
        int runeCount = buffer.readVarInt();
        List<String> runes = new ArrayList<>(runeCount);
        for (int i = 0; i < runeCount; i++) {
            runes.add(buffer.readUtf(32));
        }
        return new ServerSpellCastResultPacket(success, message, warnings, cooldown, runes);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeComponent(Objects.requireNonNull(message));
        buffer.writeVarInt(warnings.size());
        for (Component warning : warnings) {
            Component safeWarning = warning == null ? Component.literal("") : warning;
            buffer.writeComponent(Objects.requireNonNull(safeWarning));
        }
        buffer.writeVarLong(cooldownMs);
        buffer.writeVarInt(runes.size());
        for (String rune : runes) {
            buffer.writeUtf(rune == null ? "" : rune, 32);
        }
    }

    public static void handle(ServerSpellCastResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            ClientSpellingController.getInstance().handleServerResult(packet);
        });
        context.setPacketHandled(true);
    }
}

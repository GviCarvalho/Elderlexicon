package com.elderlexicon.mod.dialogue;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The channel of the dialogue screen: the line said to the player, the end of the talk, and the choice taken. */
public final class DialogueNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "dialogue"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private DialogueNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, LinePacket.class, LinePacket::encode, LinePacket::decode, LinePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, EndPacket.class, (packet, buffer) -> {
        }, buffer -> new EndPacket(), EndPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, ChoosePacket.class, ChoosePacket::encode, ChoosePacket::decode,
                ChoosePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static void sendLine(ServerPlayer player, Entity speaker, Dialogue.Line line) {
        List<Component> choices = new ArrayList<>();
        line.choices().forEach(choice -> choices.add(choice.label()));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new LinePacket(speaker.getId(), speaker.getDisplayName(), line.text(), choices));
    }

    static void sendEnd(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EndPacket());
    }

    /** The client: the player took the choice at {@code index}, or walked away (-1). */
    public static void sendChoice(int index) {
        CHANNEL.sendToServer(new ChoosePacket(index));
    }

    /** What is said to the player: who says it, the words, and the answers. */
    public record LinePacket(int speaker, Component name, Component text, List<Component> choices) {

        static void encode(LinePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.speaker);
            buffer.writeComponent(packet.name);
            buffer.writeComponent(packet.text);
            buffer.writeCollection(packet.choices, FriendlyByteBuf::writeComponent);
        }

        static LinePacket decode(FriendlyByteBuf buffer) {
            return new LinePacket(buffer.readVarInt(), buffer.readComponent(), buffer.readComponent(),
                    buffer.readList(FriendlyByteBuf::readComponent));
        }

        static void handle(LinePacket packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> com.elderlexicon.mod.dialogue.client.ClientDialogue.show(packet));
            context.get().setPacketHandled(true);
        }
    }

    /** The talk is over. */
    public record EndPacket() {

        static void handle(EndPacket packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(com.elderlexicon.mod.dialogue.client.ClientDialogue::end);
            context.get().setPacketHandled(true);
        }
    }

    /** The answer the player gave. */
    public record ChoosePacket(int index) {

        static void encode(ChoosePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.index);
        }

        static ChoosePacket decode(FriendlyByteBuf buffer) {
            return new ChoosePacket(buffer.readVarInt());
        }

        static void handle(ChoosePacket packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (sender != null) {
                context.get().enqueueWork(() -> Dialogue.choose(sender, packet.index));
            }
            context.get().setPacketHandled(true);
        }
    }
}

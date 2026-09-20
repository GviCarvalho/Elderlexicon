package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.ExampleMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public record RequestImprovisedWandPacket(InteractionHand hand) {

    private static final Map<Item, Supplier<Item>> CONVERSIONS = Map.of(
            Items.STICK, () -> ExampleMod.IMPROVISED_WAND.get(),
            Items.BONE, () -> ExampleMod.IMPROVISED_WAND_BONE.get(),
            Items.BAMBOO, () -> ExampleMod.IMPROVISED_WAND_BAMBOO.get(),
            Items.BLAZE_ROD, () -> ExampleMod.IMPROVISED_WAND_BLAZE.get(),
            Items.WRITABLE_BOOK, () -> ExampleMod.GRIMOIRE.get()
    );

    public static void encode(RequestImprovisedWandPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.hand);
    }

    public static RequestImprovisedWandPacket decode(FriendlyByteBuf buf) {
        return new RequestImprovisedWandPacket(buf.readEnum(InteractionHand.class));
    }

    public static void handle(RequestImprovisedWandPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            InteractionHand hand = packet.hand;
            ItemStack held = player.getItemInHand(hand);
            Supplier<Item> targetSupplier = CONVERSIONS.get(held.getItem());
            if (targetSupplier == null) {
                return;
            }
            Item baseItem = held.getItem();
            int count = held.getCount();
            ItemStack wand = new ItemStack(targetSupplier.get());
            player.setItemInHand(hand, wand);
            if (count > 1) {
                ItemStack leftovers = new ItemStack(baseItem, count - 1);
                player.addItem(leftovers);
            }
        });
        context.setPacketHandled(true);
    }
}

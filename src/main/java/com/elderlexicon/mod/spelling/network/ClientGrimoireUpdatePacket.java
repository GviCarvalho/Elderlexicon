package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.spelling.custom.CustomRuneHelper;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.spelling.render.SpellMapHelper;
import com.elderlexicon.mod.spelling.render.SpellMapSync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sends grimoire edits (including detached pages) from the client to the server.
 */
public record ClientGrimoireUpdatePacket(InteractionHand hand,
                                         List<String> pages,
                                         int currentPage,
                                         boolean detachPage,
                                         String detachedText,
                                         int detachedIndex) {

    private static final int MAX_PAGES = 100;
    private static final int MAX_CHARS = 1024;

    public static ClientGrimoireUpdatePacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        int size = buffer.readVarInt();
        List<String> pages = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pages.add(buffer.readUtf(MAX_CHARS));
        }
        int currentPage = buffer.readVarInt();
        boolean detach = buffer.readBoolean();
        String detached = buffer.readUtf(MAX_CHARS);
        int detachedIndex = buffer.readVarInt();
        return new ClientGrimoireUpdatePacket(hand, pages, currentPage, detach, detached, detachedIndex);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.hand);
        buffer.writeVarInt(this.pages.size());
        for (String page : this.pages) {
            buffer.writeUtf(page == null ? "" : page, MAX_CHARS);
        }
        buffer.writeVarInt(this.currentPage);
        buffer.writeBoolean(this.detachPage);
        buffer.writeUtf(this.detachedText == null ? "" : this.detachedText, MAX_CHARS);
        buffer.writeVarInt(this.detachedIndex);
    }

    public static void handle(ClientGrimoireUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ItemStack stack = sender.getItemInHand(packet.hand);
            if (!(stack.getItem() instanceof GrimoireItem)) {
                return;
            }
            List<String> sanitizedPages = sanitizePages(packet.pages);
            if (sanitizedPages.isEmpty()) {
                sanitizedPages.add("");
            }
            int safePage = Mth.clamp(packet.currentPage, 0, sanitizedPages.size() - 1);
            ListTag listtag = new ListTag();
            sanitizedPages.stream().map(StringTag::valueOf).forEach(listtag::add);
            CompoundTag tag = stack.getOrCreateTag();
            tag.put("pages", listtag);
            GrimoireItem.storeLastPage(stack, safePage);
            CustomRuneHelper.pruneAbsentCustomRunes(stack, sanitizedPages);
            if (packet.detachPage && packet.detachedIndex >= 0) {
                spawnDetachedPage(sender, packet.detachedText, packet.detachedIndex);
            }
        });
        context.setPacketHandled(true);
    }

    private static List<String> sanitizePages(List<String> raw) {
        List<String> sanitized = new ArrayList<>(Math.min(raw.size(), MAX_PAGES));
        for (String page : raw) {
            if (sanitized.size() >= MAX_PAGES) {
                break;
            }
            String cleaned = page == null ? "" : page;
            if (cleaned.length() > MAX_CHARS) {
                cleaned = cleaned.substring(0, MAX_CHARS);
            }
            sanitized.add(cleaned);
        }
        return sanitized;
    }

    private static void spawnDetachedPage(ServerPlayer sender, String rawText, int index) {
        String text = rawText == null ? "" : rawText;
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }
        ItemStack map = SpellScrollItem.create(sender.level(), sender.getBlockX(), sender.getBlockZ(), (byte) 3);
        SpellMapHelper.paintSpell(sender.serverLevel(), map, text);
        SpellMapSync.send(sender, map);
        map.setHoverName(Component.translatable("item.elderlexicon.detached_page", index + 1));
        CompoundTag custom = map.getOrCreateTag();
        custom.putString("DetachedPageText", text);
        custom.putInt("DetachedPageIndex", index);
        if (!sender.addItem(map)) {
            sender.drop(map, false);
        }
    }
}

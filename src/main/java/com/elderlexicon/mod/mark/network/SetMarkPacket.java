package com.elderlexicon.mod.mark.network;

import com.elderlexicon.mod.item.ElderBrushItem;
import com.elderlexicon.mod.mark.MarkTarget;
import com.elderlexicon.mod.spell.function.MarkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetMarkPacket(MarkTarget target, String mark) {

    private static final double MAX_DISTANCE = 12.0D;
    private static final int MAX_LENGTH = 32;

    public static SetMarkPacket decode(FriendlyByteBuf buffer) {
        MarkTarget.Type type = buffer.readEnum(MarkTarget.Type.class);
        BlockPos pos = null;
        int entityId = -1;
        if (type == MarkTarget.Type.BLOCK) {
            pos = buffer.readBlockPos();
        } else {
            entityId = buffer.readVarInt();
        }
        String mark = buffer.readUtf(MAX_LENGTH);
        MarkTarget target = type == MarkTarget.Type.BLOCK ? MarkTarget.block(pos) : MarkTarget.entity(entityId);
        return new SetMarkPacket(target, mark);
    }

    public static void encode(SetMarkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.target.type());
        if (packet.target.type() == MarkTarget.Type.BLOCK) {
            buffer.writeBlockPos(packet.target.blockPos());
        } else {
            buffer.writeVarInt(packet.target.entityId());
        }
        buffer.writeUtf(packet.mark == null ? "" : trim(packet.mark, MAX_LENGTH));
    }

    public static void handle(SetMarkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            return;
        }
        context.enqueueWork(() -> {
            if (!isHoldingBrush(sender)) {
                return;
            }
            ServerLevel level = sender.serverLevel();
            if (packet.target == null) {
                return;
            }
            switch (packet.target.type()) {
                case BLOCK -> handleBlock(sender, level, packet);
                case ENTITY -> handleEntity(sender, level, packet);
                default -> {
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleBlock(ServerPlayer sender, ServerLevel level, SetMarkPacket packet) {
        BlockPos pos = packet.target.blockPos();
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        if (!isWithinRange(sender.position(), Vec3.atCenterOf(pos))) {
            sender.sendSystemMessage(Component.translatable("message.elderlexicon.mark.too_far"));
            return;
        }
        String sanitized = sanitize(packet.mark);
        if (MarkHelper.applyMark(level, pos, sanitized)) {
            if (sanitized == null) {
                sender.sendSystemMessage(Component.translatable("message.elderlexicon.mark.cleared"));
            } else {
                sender.sendSystemMessage(Component.translatable("message.elderlexicon.mark.set", sanitized));
            }
        }
    }

    private static void handleEntity(ServerPlayer sender, ServerLevel level, SetMarkPacket packet) {
        Entity entity = level.getEntity(packet.target.entityId());
        if (entity == null) {
            return;
        }
        if (!isWithinRange(sender.position(), entity.position())) {
            sender.sendSystemMessage(Component.translatable("message.elderlexicon.mark.too_far"));
            return;
        }
        String sanitized = sanitize(packet.mark);
        if (MarkHelper.applyMark(entity, sanitized)) {
            if (sanitized == null) {
                sender.sendSystemMessage(Component.translatable("message.elderlexicon.mark.cleared"));
            } else {
                sender.sendSystemMessage(Component.translatable("message.elderlexicon.mark.set", sanitized));
            }
        }
    }

    private static boolean isHoldingBrush(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof ElderBrushItem || off.getItem() instanceof ElderBrushItem;
    }

    private static boolean isWithinRange(Vec3 origin, Vec3 target) {
        if (origin == null || target == null) {
            return false;
        }
        return origin.distanceToSqr(target) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    private static String sanitize(String raw) {
        String trimmed = trim(raw == null ? "" : raw, MAX_LENGTH);
        return MarkHelper.sanitizeMark(trimmed);
    }

    private static String trim(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        return raw.length() > maxLength ? raw.substring(0, maxLength) : raw;
    }
}

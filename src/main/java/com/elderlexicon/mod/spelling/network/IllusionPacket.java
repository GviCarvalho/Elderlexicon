package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientIllusions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * An illusion appearing or going: the image of a block at a place, or the image of a marked thing. A thing's image
 * travels from {@code from} to {@code to} at {@code speed} blocks a tick and stays there ({@code from} = {@code to} for
 * one that only stands); {@code elapsed} is how many ticks of that it has already gone, for someone who comes to see it
 * late.
 */
public record IllusionPacket(Kind kind, BlockPos pos, int stateId, int imageId, int sourceId, Vec3 from, Vec3 to,
                             double speed, int elapsed) {

    public enum Kind {
        BLOCK,
        BLOCK_GONE,
        THING,
        THING_GONE
    }

    public static IllusionPacket block(BlockPos pos, BlockState state) {
        return new IllusionPacket(Kind.BLOCK, pos.immutable(), Block.getId(state), 0, 0, Vec3.ZERO, Vec3.ZERO, 0.0D, 0);
    }

    public static IllusionPacket blockGone(BlockPos pos) {
        return new IllusionPacket(Kind.BLOCK_GONE, pos.immutable(), 0, 0, 0, Vec3.ZERO, Vec3.ZERO, 0.0D, 0);
    }

    public static IllusionPacket thing(int imageId, int sourceId, Vec3 from, Vec3 to, double speed, int elapsed) {
        return new IllusionPacket(Kind.THING, BlockPos.ZERO, 0, imageId, sourceId, from, to, speed, elapsed);
    }

    public static IllusionPacket thingGone(int imageId) {
        return new IllusionPacket(Kind.THING_GONE, BlockPos.ZERO, 0, imageId, 0, Vec3.ZERO, Vec3.ZERO, 0.0D, 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(kind);
        switch (kind) {
            case BLOCK -> {
                buffer.writeBlockPos(pos);
                buffer.writeVarInt(stateId);
            }
            case BLOCK_GONE -> buffer.writeBlockPos(pos);
            case THING -> {
                buffer.writeVarInt(imageId);
                buffer.writeVarInt(sourceId);
                writeVec(buffer, from);
                writeVec(buffer, to);
                buffer.writeDouble(speed);
                buffer.writeVarInt(elapsed);
            }
            case THING_GONE -> buffer.writeVarInt(imageId);
        }
    }

    public static IllusionPacket decode(FriendlyByteBuf buffer) {
        Kind kind = buffer.readEnum(Kind.class);
        return switch (kind) {
            case BLOCK -> new IllusionPacket(kind, buffer.readBlockPos(), buffer.readVarInt(), 0, 0, Vec3.ZERO, Vec3.ZERO,
                    0.0D, 0);
            case BLOCK_GONE -> new IllusionPacket(kind, buffer.readBlockPos(), 0, 0, 0, Vec3.ZERO, Vec3.ZERO, 0.0D, 0);
            case THING -> {
                int imageId = buffer.readVarInt();
                int sourceId = buffer.readVarInt();
                Vec3 from = readVec(buffer);
                Vec3 to = readVec(buffer);
                yield new IllusionPacket(kind, BlockPos.ZERO, 0, imageId, sourceId, from, to, buffer.readDouble(),
                        buffer.readVarInt());
            }
            case THING_GONE -> new IllusionPacket(kind, BlockPos.ZERO, 0, buffer.readVarInt(), 0, Vec3.ZERO, Vec3.ZERO,
                    0.0D, 0);
        };
    }

    private static void writeVec(FriendlyByteBuf buffer, Vec3 vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(IllusionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            switch (packet.kind) {
                case BLOCK -> ClientIllusions.showBlock(packet.pos, Block.stateById(packet.stateId));
                case BLOCK_GONE -> ClientIllusions.removeBlock(packet.pos);
                case THING -> ClientIllusions.showThing(packet.imageId, packet.sourceId, packet.from, packet.to,
                        packet.speed, packet.elapsed);
                case THING_GONE -> ClientIllusions.removeThing(packet.imageId);
            }
        });
        context.setPacketHandled(true);
    }
}

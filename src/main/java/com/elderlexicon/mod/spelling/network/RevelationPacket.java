package com.elderlexicon.mod.spelling.network;

import com.elderlexicon.mod.spelling.client.ClientRevelation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the spirit sees in a revelation ({@code igni surgit}), sent to the mage alone and refreshed while the gaze
 * lasts: the creatures and blocks that carry what was sought, with the mark they bear when that matters.
 *
 * @param ticksLeft how much longer the gaze lasts; 0 ends it
 * @param color     the tint of what is shown
 */
public record RevelationPacket(int ticksLeft, int color, List<SeenEntity> entities, List<SeenBlock> blocks,
                               List<SeenSpirit> spirits) {

    public RevelationPacket(int ticksLeft, int color, List<SeenEntity> entities, List<SeenBlock> blocks) {
        this(ticksLeft, color, entities, blocks, List.of());
    }

    /** A projected spirit in sight: whose it is (the player's entity) and where it floats. */
    public record SeenSpirit(int playerId, double x, double y, double z) {
    }

    /** A creature in sight; {@code label} is its mark, or empty. */
    public record SeenEntity(int entityId, String label) {
    }

    /**
     * A block in sight; {@code label} is its mark, or empty; {@code strength} is how strongly it is seen, 0 to 255
     * (dense earth brighter than loose soil).
     */
    public record SeenBlock(BlockPos pos, String label, int strength) {

        public SeenBlock(BlockPos pos, String label) {
            this(pos, label, 255);
        }
    }

    private static final int MAX_LABEL = 32;
    private static final int MAX_ENTRIES = 8192;

    public RevelationPacket {
        entities = entities == null ? List.of() : List.copyOf(entities);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        spirits = spirits == null ? List.of() : List.copyOf(spirits);
    }

    public static RevelationPacket end() {
        return new RevelationPacket(0, 0, List.of(), List.of());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ticksLeft);
        buffer.writeInt(color);
        buffer.writeVarInt(Math.min(entities.size(), MAX_ENTRIES));
        for (int i = 0; i < Math.min(entities.size(), MAX_ENTRIES); i++) {
            buffer.writeVarInt(entities.get(i).entityId());
            buffer.writeUtf(entities.get(i).label(), MAX_LABEL);
        }
        buffer.writeVarInt(Math.min(blocks.size(), MAX_ENTRIES));
        for (int i = 0; i < Math.min(blocks.size(), MAX_ENTRIES); i++) {
            buffer.writeBlockPos(blocks.get(i).pos());
            buffer.writeUtf(blocks.get(i).label(), MAX_LABEL);
            buffer.writeByte(blocks.get(i).strength());
        }
        buffer.writeVarInt(Math.min(spirits.size(), MAX_ENTRIES));
        for (int i = 0; i < Math.min(spirits.size(), MAX_ENTRIES); i++) {
            SeenSpirit spirit = spirits.get(i);
            buffer.writeVarInt(spirit.playerId());
            buffer.writeDouble(spirit.x());
            buffer.writeDouble(spirit.y());
            buffer.writeDouble(spirit.z());
        }
    }

    public static RevelationPacket decode(FriendlyByteBuf buffer) {
        int ticksLeft = buffer.readVarInt();
        int color = buffer.readInt();
        int entityCount = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<SeenEntity> entities = new ArrayList<>(entityCount);
        for (int i = 0; i < entityCount; i++) {
            entities.add(new SeenEntity(buffer.readVarInt(), buffer.readUtf(MAX_LABEL)));
        }
        int blockCount = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<SeenBlock> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blocks.add(new SeenBlock(buffer.readBlockPos(), buffer.readUtf(MAX_LABEL), buffer.readUnsignedByte()));
        }
        int spiritCount = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<SeenSpirit> spirits = new ArrayList<>(spiritCount);
        for (int i = 0; i < spiritCount; i++) {
            spirits.add(new SeenSpirit(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        return new RevelationPacket(ticksLeft, color, entities, blocks, spirits);
    }

    public static void handle(RevelationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientRevelation.apply(packet));
        context.setPacketHandled(true);
    }
}

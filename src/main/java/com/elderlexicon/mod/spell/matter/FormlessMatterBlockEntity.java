package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a block of formless matter holds, exactly as it is: its composition, its state and its UMU
 * (docs/plano-materia-e-forca.md, stage 5). An amalgam also knows when it falls apart.
 */
public final class FormlessMatterBlockEntity extends BlockEntity {

    private static final String SHARES = "shares";
    private static final String STATE = "state";
    private static final String UMU = "umu";
    private static final String FALLS_APART_AT = "fallsApartAt";

    private Matter matter;
    /** The game time an amalgam falls apart at; -1 for matter that holds together. */
    private long fallsApartAt = -1L;

    public FormlessMatterBlockEntity(BlockPos pos, BlockState state) {
        super(MatterBlocks.FORMLESS_MATTER.get(), pos, state);
    }

    public Optional<Matter> matter() {
        return Optional.ofNullable(matter);
    }

    /** The game time it falls apart at, or -1 when it holds together. */
    public long fallsApartAt() {
        return fallsApartAt;
    }

    /** Holds {@code matter}; an amalgam falls apart at {@code fallsApartAt} (-1 for matter that holds together). */
    void hold(Matter matter, long fallsApartAt) {
        this.matter = matter;
        this.fallsApartAt = fallsApartAt;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    private void write(CompoundTag tag) {
        if (matter == null) {
            return;
        }
        CompoundTag shares = new CompoundTag();
        matter.composition().shares().forEach((aspect, share) -> shares.putDouble(aspect.runeId(), share));
        tag.put(SHARES, shares);
        tag.putString(STATE, matter.state().name().toLowerCase(Locale.ROOT));
        tag.putDouble(UMU, matter.umu());
        tag.putLong(FALLS_APART_AT, fallsApartAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        matter = null;
        fallsApartAt = tag.contains(FALLS_APART_AT) ? tag.getLong(FALLS_APART_AT) : -1L;
        if (!tag.contains(SHARES)) {
            return;
        }
        CompoundTag shares = tag.getCompound(SHARES);
        Map<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
        for (VitaElement aspect : VitaElement.values()) {
            if (aspect != VitaElement.BALANCED && shares.contains(aspect.runeId())) {
                amounts.put(aspect, shares.getDouble(aspect.runeId()));
            }
        }
        Optional<State> state = State.parse(tag.getString(STATE));
        if (amounts.isEmpty() || state.isEmpty()) {
            return;
        }
        matter = new Matter(Composition.of(amounts), state.get(), tag.getDouble(UMU));
    }

    // ------------------------------------------------------------------ what the client sees: its colour

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        write(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
        redraw();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        redraw();
    }

    /** What it holds changed on the client: its block is drawn again, in its new colour. */
    private void redraw() {
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    /** The colour of what it holds: the colours of its four aspects, blended in its proportion. */
    public int color() {
        if (matter == null) {
            return 0x9A9A9A;
        }
        double red = 0.0D;
        double green = 0.0D;
        double blue = 0.0D;
        for (Map.Entry<VitaElement, Double> share : matter.composition().shares().entrySet()) {
            int rgb = aspectColor(share.getKey());
            red += share.getValue() * ((rgb >> 16) & 0xFF);
            green += share.getValue() * ((rgb >> 8) & 0xFF);
            blue += share.getValue() * (rgb & 0xFF);
        }
        return ((int) Math.round(red) & 0xFF) << 16 | ((int) Math.round(green) & 0xFF) << 8 | ((int) Math.round(blue) & 0xFF);
    }

    private static int aspectColor(VitaElement aspect) {
        return switch (aspect) {
            case FIRMO -> 0x8A5A32;
            case AQUA -> 0x3F76E4;
            case AURA -> 0xDCE8EE;
            case IGNI -> 0xE8561C;
            default -> 0x9A9A9A;
        };
    }
}

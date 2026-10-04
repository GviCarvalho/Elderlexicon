package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
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
import java.util.Map;
import java.util.Optional;

/**
 * What a block of formless matter holds, to the last particle (docs/particulas-design.md, stage 9): how many of each
 * primordial. Its state is its block, solid or liquid; how agitated it is, the drives say. A world saved before kept
 * a proportion and an amount instead, and is read into particles as it loads.
 */
public final class FormlessMatterBlockEntity extends BlockEntity {

    private static final String PARTICLES = "particles";
    /** What worlds saved before the particles kept: the proportion and the UMU. */
    private static final String SHARES = "shares";
    private static final String UMU = "umu";

    private Particles held;

    public FormlessMatterBlockEntity(BlockPos pos, BlockState state) {
        super(MatterBlocks.FORMLESS_MATTER.get(), pos, state);
    }

    /** The particles it holds; empty while it holds nothing. */
    public Optional<Particles> particles() {
        return Optional.ofNullable(held);
    }

    /** What it holds, as matter in the state of its block. */
    public Optional<Matter> matter() {
        return held == null || held.present().total() <= 0L ? Optional.empty()
                : Optional.of(Matter.of(held, state()));
    }

    /** The state of what it holds: the block's, solid or liquid. */
    public State state() {
        return getBlockState().getBlock() instanceof FormlessMatterBlock block && block.liquid()
                ? State.LIQUID : State.SOLID;
    }

    /** Holds exactly {@code particles}, and those who see it are told. */
    void hold(Particles particles) {
        hold(particles, true);
    }

    /**
     * Holds exactly {@code particles}; those who see it are told only when {@code seen}, as when its colour may have
     * changed: what the drives move a little each tick is kept without a word.
     */
    void hold(Particles particles, boolean seen) {
        this.held = particles.present();
        setChanged();
        if (seen && level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    private void write(CompoundTag tag) {
        if (held == null) {
            return;
        }
        CompoundTag counts = new CompoundTag();
        for (VitaElement aspect : Particles.ASPECTS) {
            counts.putLong(aspect.runeId(), held.count(aspect));
        }
        tag.put(PARTICLES, counts);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        held = null;
        if (tag.contains(PARTICLES)) {
            CompoundTag counts = tag.getCompound(PARTICLES);
            long[] each = new long[Particles.ASPECTS.size()];
            for (int a = 0; a < each.length; a++) {
                each[a] = Math.max(0L, counts.getLong(Particles.ASPECTS.get(a).runeId()));
            }
            held = new Particles(each[0], each[1], each[2], each[3]);
        } else if (tag.contains(SHARES)) {
            // Saved before the particles: its proportion and its UMU, read into the whole particles nearest them.
            CompoundTag shares = tag.getCompound(SHARES);
            Map<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
            for (VitaElement aspect : Particles.ASPECTS) {
                if (shares.contains(aspect.runeId())) {
                    amounts.put(aspect, shares.getDouble(aspect.runeId()));
                }
            }
            if (amounts.values().stream().anyMatch(share -> share > 0.0D)) {
                held = Particles.in(Composition.of(amounts), Particles.ofUmu(tag.getDouble(UMU)));
            }
        }
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
        return held == null ? 0x9A9A9A : colorOf(held);
    }

    /** The colour of any matter: the colours of its four aspects, blended in its proportion. */
    public static int colorOf(Matter matter) {
        return colorOf(matter.composition());
    }

    /** The colour of some particles: the colours of the four aspects they hold, blended in their proportion. */
    public static int colorOf(Particles particles) {
        return particles.composition().map(FormlessMatterBlockEntity::colorOf).orElse(0x9A9A9A);
    }

    private static int colorOf(Composition composition) {
        double red = 0.0D;
        double green = 0.0D;
        double blue = 0.0D;
        for (Map.Entry<VitaElement, Double> share : composition.shares().entrySet()) {
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

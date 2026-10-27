package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.spelling.server.ServerSpellingController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An invocation bench (docs/invocacao-design.md): a crafting table whose nine squares the mage marked with the burin.
 * It keeps what is on its grid (nothing falls off when the screen is closed: matter is invoked into it from afar), the
 * mark of each square, and the line engraved in it, read when something is made on it.
 * <p>
 * Matter comes in by spell, often a few lines at once. The bench waits until it has been quiet for a moment, then, if
 * a line is engraved in it, takes off the grid what it makes, or everything on it when it makes nothing (a square put
 * wrong), and keeps it ready until the line may be read: at once for a line with no condition, at the reader's next
 * blow for {@code ferit ille ubis iactare} (or at once, if they just struck). The line is read by whoever invoked into
 * it last, with what was made as its subject.
 */
public final class InvocationBenchBlockEntity extends BlockEntity {

    /** How long the bench waits, quiet, before it sends off what was invoked into it: two seconds. */
    public static final int SETTLE_TICKS = 40;

    private final String[] marks = new String[Formats.SIDE * Formats.SIDE];
    private final SimpleContainer grid = new SimpleContainer(Formats.SIDE * Formats.SIDE) {
        @Override
        public void setChanged() {
            super.setChanged();
            InvocationBenchBlockEntity.this.setChanged();
        }
    };
    private String engraving = "";
    @Nullable
    private UUID caster;
    private long lastInvoked;
    private boolean waiting;
    /** What was last sent off, still bearing the mark it was sent with, and until when. */
    private List<net.minecraft.world.entity.item.ItemEntity> sentOff = List.of();
    private long unmarkAt;
    /** What was made and waits for the line's conditions, to be sent off. */
    private final List<ItemStack> ready = new java.util.ArrayList<>();

    public InvocationBenchBlockEntity(BlockPos pos, BlockState state) {
        super(Invocations.BENCH_ENTITY.get(), pos, state);
        Arrays.fill(marks, "");
    }

    public SimpleContainer grid() {
        return grid;
    }

    /** The mark of each square, "" for one not marked. */
    public String[] marks() {
        return marks.clone();
    }

    public String engraving() {
        return engraving;
    }

    /** Whether any square is marked: a bench with none is a crafting table again. */
    public boolean marked() {
        return Arrays.stream(marks).anyMatch(mark -> !mark.isBlank());
    }

    /** Whether it holds nothing that makes it a bench: no mark, no line, nothing on the grid or waiting. */
    public boolean blank() {
        return !marked() && engraving.isBlank() && grid.isEmpty() && ready.isEmpty();
    }

    public void setMarks(String[] given, String engraved) {
        for (int i = 0; i < marks.length; i++) {
            marks[i] = i < given.length && given[i] != null ? given[i] : "";
        }
        engraving = engraved == null ? "" : engraved;
        setChanged();
        if (level != null && !level.isClientSide) {
            BenchSlots.get(level.getServer()).set(level.dimension(), worldPosition, marks);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ invoked into

    /**
     * {@code particles} invoked into square {@code index} by {@code by}: they join what is there and become the thing
     * they are the code of, or stay matter. False when the square holds something that is no matter (a sword), which
     * takes nothing.
     */
    public boolean receive(int index, Particles particles, int window, @Nullable UUID by) {
        ItemStack there = grid.getItem(index);
        Particles held = Particles.NONE;
        int kept = window;
        if (!there.isEmpty()) {
            Optional<Particles> matter = BenchMatter.of(there);
            if (matter.isEmpty()) {
                return false;
            }
            held = matter.get();
            kept = Math.max(kept, Conjured.writtenWindow(there));
        }
        grid.setItem(index, BenchMatter.become(held.plus(particles), kept));
        invoked(by);
        return true;
    }

    /** A thing brought into square {@code index} as it is (a marked ingot); false when the square is taken. */
    public boolean receive(int index, ItemStack thing, @Nullable UUID by) {
        if (!grid.getItem(index).isEmpty() || thing.isEmpty()) {
            return false;
        }
        grid.setItem(index, thing);
        invoked(by);
        return true;
    }

    private void invoked(@Nullable UUID by) {
        if (by != null) {
            caster = by;
        }
        if (level != null) {
            lastInvoked = level.getGameTime();
        }
        waiting = true;
        setChanged();
    }

    /** What was made and waits to be sent off. */
    public List<ItemStack> ready() {
        return List.copyOf(ready);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InvocationBenchBlockEntity bench) {
        if (bench.waiting && level.getGameTime() - bench.lastInvoked >= SETTLE_TICKS) {
            bench.waiting = false;
            bench.make((ServerLevel) level);
        }
        if (!bench.ready.isEmpty()) {
            bench.send((ServerLevel) level);
        }
        if (!bench.sentOff.isEmpty() && level.getGameTime() >= bench.unmarkAt) {
            BenchDelivery.unmark(bench.sentOff);
            bench.sentOff = List.of();
        }
    }

    /**
     * What was invoked is made, if a line is engraved to send it off: taken off the grid and kept ready. Without a line,
     * it waits on the grid to be taken by hand.
     */
    private void make(ServerLevel level) {
        if (engraving.isBlank() || caster == null || !ready.isEmpty()) {
            return;
        }
        ServerPlayer reader = level.getServer().getPlayerList().getPlayer(caster);
        Optional<BenchCrafting.Made> made = BenchCrafting.made(level, grid);
        if (made.isPresent()) {
            ItemStack thing = made.get().thing().copy();
            BenchCrafting.take(level, grid, made.get(), reader);
            ready.add(thing);
        } else {
            ready.addAll(BenchCrafting.everything(grid));
        }
        setChanged();
    }

    /** What is ready is sent off by the line, once its conditions hold for the one who reads it. */
    private void send(ServerLevel level) {
        ServerPlayer reader = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        if (reader == null || engraving.isBlank() || !ServerSpellingController.getInstance()
                .benchEngravingHolds(reader, engraving)) {
            return;
        }
        List<ItemStack> sent = List.copyOf(ready);
        ready.clear();
        setChanged();
        BenchDelivery.unmark(sentOff);
        sentOff = BenchDelivery.send(level, worldPosition, sent, engraving, reader);
        unmarkAt = level.getGameTime() + BenchDelivery.MARKED_TICKS;
    }

    /** Everything the bench holds, for when it is broken: the grid and what waited to be sent. */
    public List<ItemStack> contents() {
        List<ItemStack> all = new java.util.ArrayList<>(ready);
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (!grid.getItem(i).isEmpty()) {
                all.add(grid.getItem(i));
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ kept

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveMarks(tag);
        NonNullList<ItemStack> items = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, grid.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items);
        if (caster != null) {
            tag.putUUID("Caster", caster);
        }
        ListTag waiting = new ListTag();
        ready.forEach(stack -> waiting.add(stack.save(new CompoundTag())));
        tag.put("Ready", waiting);
    }

    private void saveMarks(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String mark : marks) {
            list.add(StringTag.valueOf(mark));
        }
        tag.put("Marks", list);
        tag.putString("Engraving", engraving);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag list = tag.getList("Marks", Tag.TAG_STRING);
        for (int i = 0; i < marks.length; i++) {
            marks[i] = i < list.size() ? list.getString(i) : "";
        }
        engraving = tag.getString("Engraving");
        if (tag.contains("Items", Tag.TAG_LIST)) {
            NonNullList<ItemStack> items = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items);
            for (int i = 0; i < items.size(); i++) {
                grid.setItem(i, items.get(i));
            }
        }
        caster = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        if (tag.contains("Ready", Tag.TAG_LIST)) {
            ready.clear();
            ListTag waiting = tag.getList("Ready", Tag.TAG_COMPOUND);
            for (int i = 0; i < waiting.size(); i++) {
                ready.add(ItemStack.of(waiting.getCompound(i)));
            }
        }
    }

    /** The client knows the marks and the line, for the screen that marks them; the grid goes by the menu. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveMarks(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

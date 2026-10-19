package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.spell.function.MarkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The marks the mage carved in the squares of an invocation bench with the burin, and the line engraved in it
 * (docs/invocacao-design.md, sections 1 and 5.1), written in the bench's own screen. The burin must be at hand (in
 * either hand or on the hotbar); each mark carved and each rune of a new line wears it by one. A bench left with no
 * mark is a crafting table again when its screen is closed.
 */
public record BenchMarkPacket(BlockPos pos, List<String> marks, String engraving) {

    private static final int MAX_MARK = 32;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(marks.size());
        marks.forEach(mark -> buffer.writeUtf(mark, MAX_MARK));
        buffer.writeUtf(engraving, Engravings.MAX_CHARS);
    }

    public static BenchMarkPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int count = Math.min(buffer.readVarInt(), Formats.SIDE * Formats.SIDE);
        List<String> marks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            marks.add(buffer.readUtf(MAX_MARK));
        }
        return new BenchMarkPacket(pos, marks, buffer.readUtf(Engravings.MAX_CHARS));
    }

    public static void handle(BenchMarkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                packet.apply(player);
            }
        });
        context.setPacketHandled(true);
    }

    /** Carves what was written, if it can be: the table is near, the burin in hand, and every word is one. */
    public void apply(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean table = level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
        boolean bench = level.getBlockState(pos).is(Invocations.BENCH.get());
        Optional<ItemStack> burinAtHand = Burins.atHand(player);
        if ((!table && !bench) || burinAtHand.isEmpty()
                || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }
        String[] carved = new String[Formats.SIDE * Formats.SIDE];
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < carved.length; i++) {
            String raw = i < marks.size() ? marks.get(i).trim() : "";
            if (raw.isEmpty()) {
                carved[i] = "";
                continue;
            }
            Optional<String> mark = MarkHelper.markFromName(raw);
            if (mark.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.elderlexicon.invocation_bench.not_a_mark",
                        raw), true);
                return;
            }
            if (!seen.add(mark.get())) {
                player.displayClientMessage(Component.translatable("message.elderlexicon.invocation_bench.twice",
                        mark.get()), true);
                return;
            }
            carved[i] = mark.get();
        }
        List<String> words = Engravings.words(engraving);
        if (words.size() > Engravings.MAX_COLUMNS || Engravings.unreadable(words)) {
            player.displayClientMessage(Component.translatable("message.elderlexicon.invocation_bench.unreadable"), true);
            return;
        }
        String written = words.isEmpty() ? "" : Engravings.written(words);

        String[] before = bench && level.getBlockEntity(pos) instanceof InvocationBenchBlockEntity was
                ? was.marks() : new String[carved.length];
        String engravedBefore = bench && level.getBlockEntity(pos) instanceof InvocationBenchBlockEntity was
                ? was.engraving() : "";
        int wear = written.equals(engravedBefore) ? 0 : Engravings.runes(words);
        for (int i = 0; i < carved.length; i++) {
            if (!carved[i].isEmpty() && !carved[i].equals(before[i])) {
                wear++;
            }
        }
        ItemStack burin = burinAtHand.get();
        if (!player.getAbilities().instabuild && burin.getMaxDamage() - burin.getDamageValue() < wear) {
            player.displayClientMessage(Component.translatable("message.elderlexicon.invocation_bench.worn"), true);
            return;
        }

        if (table) {
            level.setBlock(pos, Invocations.BENCH.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(pos) instanceof InvocationBenchBlockEntity made) {
            made.setMarks(carved, written);
        }
        if (wear > 0 && !player.getAbilities().instabuild) {
            burin.hurtAndBreak(wear, player, broken -> { });
        }
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 1.6F);
    }
}

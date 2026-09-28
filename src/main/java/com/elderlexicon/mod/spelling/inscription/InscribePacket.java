package com.elderlexicon.mod.spelling.inscription;

import com.elderlexicon.mod.spell.block.RuneTokens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A mage writing on a block face with a quill (a feather in hand) and a pigment from their pack: the server checks
 * both, spends one pigment when something new is written, and keeps the text in the pigment's colour. Glow ink makes
 * the runes shine. Writing nothing wipes the face and spends nothing.
 */
public record InscribePacket(BlockPos pos, Direction face, String text) {

    private static final double REACH = 8.0D;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(face);
        buffer.writeUtf(text, Inscription.MAX_CHARS);
    }

    public static InscribePacket decode(FriendlyByteBuf buffer) {
        return new InscribePacket(buffer.readBlockPos(), buffer.readEnum(Direction.class),
                buffer.readUtf(Inscription.MAX_CHARS));
    }

    public static void handle(InscribePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.distanceToSqr(packet.pos().getCenter()) > REACH * REACH
                    || !holdsQuill(player) || player.level().getBlockState(packet.pos()).isAir()) {
                return;
            }
            ServerLevel level = player.serverLevel();
            Inscriptions inscriptions = Inscriptions.of(level);
            String text = written(packet.text());
            if (text.isBlank()) {
                inscriptions.write(level, new Inscription(packet.pos(), packet.face(), "", 0, false));
                return;
            }
            String before = inscriptions.at(packet.pos(), packet.face()).map(Inscription::text).orElse("");
            if (text.equals(before)) {
                return;
            }
            int slot = pigmentSlot(player);
            if (slot < 0) {
                player.displayClientMessage(Component.literal("Falta pigmento para escrever."), true);
                return;
            }
            ItemStack pigment = player.getInventory().getItem(slot);
            int color = colorOf(pigment);
            boolean glow = pigment.is(Items.GLOW_INK_SAC);
            if (!player.getAbilities().instabuild) {
                pigment.shrink(1);
            }
            inscriptions.write(level, new Inscription(packet.pos(), packet.face(), text, color, glow));
        });
        context.setPacketHandled(true);
    }

    /** What is kept on the face: at most four lines, each word as the grimoire writes it (a rune's name as its glyph). */
    static String written(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : (raw == null ? "" : raw).split("\\r?\\n", -1)) {
            if (lines.size() >= Inscription.MAX_LINES) {
                break;
            }
            List<String> words = new ArrayList<>();
            for (String word : line.trim().split("\\s+")) {
                if (!word.isEmpty()) {
                    String kept = RuneTokens.written(word);
                    words.add(kept.length() == 1 ? kept.toUpperCase(java.util.Locale.ROOT) : kept);
                }
            }
            lines.add(String.join(" ", words));
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        String text = String.join("\n", lines);
        return text.length() > Inscription.MAX_CHARS ? text.substring(0, Inscription.MAX_CHARS) : text;
    }

    static boolean holdsQuill(net.minecraft.world.entity.player.Player player) {
        return player.getMainHandItem().is(Items.FEATHER) || player.getOffhandItem().is(Items.FEATHER);
    }

    static boolean isPigment(ItemStack stack) {
        return stack.is(Tags.Items.DYES) || stack.is(Items.INK_SAC) || stack.is(Items.GLOW_INK_SAC);
    }

    /** The first pigment in the pack, offhand first; -1 when there is none. */
    static int pigmentSlot(net.minecraft.world.entity.player.Player player) {
        var inventory = player.getInventory();
        if (isPigment(inventory.getItem(40))) {
            return 40;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isPigment(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static int colorOf(ItemStack pigment) {
        if (pigment.getItem() instanceof DyeItem dye) {
            return dye.getDyeColor().getTextColor();
        }
        if (pigment.is(Items.GLOW_INK_SAC)) {
            return 0x9FF5E6;
        }
        return 0x1D1D21;
    }
}

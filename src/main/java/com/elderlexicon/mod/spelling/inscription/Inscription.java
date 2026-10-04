package com.elderlexicon.mod.spelling.inscription;

import com.elderlexicon.mod.spell.block.WrittenTexts;
import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Runes written straight on a face of a block, with a quill and a pigment (docs/circulos-design.md): a page with no
 * scroll. It is read like a scroll, and it is a piece of a circle like one.
 */
public record Inscription(BlockPos pos, Direction face, String text, int color, boolean glow) {

    /** The most a face can hold. */
    public static final int MAX_CHARS = 256;
    /** The most lines a face can hold. */
    public static final int MAX_LINES = 4;

    public long key() {
        return key(pos, face);
    }

    public static long key(BlockPos pos, Direction face) {
        return pos.asLong() * 6 + face.get3DDataValue();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        tag.putInt("Face", face.get3DDataValue());
        tag.putString("Text", text);
        WrittenTexts.mark(tag);
        tag.putInt("Color", color);
        tag.putBoolean("Glow", glow);
        return tag;
    }

    public static Inscription load(CompoundTag tag) {
        return new Inscription(BlockPos.of(tag.getLong("Pos")), Direction.from3DDataValue(tag.getInt("Face")),
                tag.getInt(WrittenTexts.KEY) >= WrittenTexts.CURRENT ? tag.getString("Text")
                        : NumberGlyphs.upgrade(tag.getString("Text")),
                tag.getInt("Color"), tag.getBoolean("Glow"));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(face);
        buffer.writeUtf(text, MAX_CHARS);
        buffer.writeInt(color);
        buffer.writeBoolean(glow);
    }

    public static Inscription read(FriendlyByteBuf buffer) {
        return new Inscription(buffer.readBlockPos(), buffer.readEnum(Direction.class), buffer.readUtf(MAX_CHARS),
                buffer.readInt(), buffer.readBoolean());
    }
}

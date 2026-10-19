package com.elderlexicon.mod.invocation;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A format of the invocation bench (docs/invocacao-design.md, section 3): a drawing on the grid, its principal part
 * (the matter of the thing) and the parts that stay what they are.
 *
 * @param id        how the data names it ({@code sword})
 * @param name      how the grimoire names it ({@code espada})
 * @param pattern   the drawing, a row per string; a space is a square left empty
 * @param principal the key of the principal part
 * @param keys      what each other key must hold: an item id, or {@code #} and a tag
 * @param item      the conjured thing it makes
 * @param count     how many it makes
 * @param damage    the bonus to the hand it has made of iron; other matter, as much more as it weighs more
 * @param speed     the attack speed it adds to the hand
 * @param mines     the tag of the blocks it is the tool for, or null for none
 */
public record Format(String id, String name, List<String> pattern, char principal, Map<Character, String> keys,
                     ResourceLocation item, int count, double damage, double speed, @Nullable String mines) {

    public int width() {
        return pattern.stream().mapToInt(String::length).max().orElse(0);
    }

    public int height() {
        return pattern.size();
    }

    /** The key at column {@code x}, row {@code y}; a space past the end of a short row. */
    public char at(int x, int y) {
        String row = pattern.get(y);
        return x < row.length() ? row.charAt(x) : ' ';
    }
}

package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.Qualities;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.physics.Field;
import com.elderlexicon.mod.spell.Mana;
import com.elderlexicon.mod.spell.SpellFlow;
import com.elderlexicon.mod.spelling.flow.FlowState;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the invocation bench made of matter, as it is kept in the thing (docs/invocacao-design.md): the matter of its
 * principal part, the parts that stay what they are, and how long it lasts. Matter that is not solid at rest is
 * volatile: it keeps its form two seconds once it leaves the bench (the default window of an evocation), or as long as
 * the chronos that brought it said, or while the one who holds it keeps the flow and pays for it; then it comes apart,
 * and what was solid (a handle of wood) falls.
 */
public final class Conjured {

    private static final String MATTER = "ConjuredMatter";
    private static final String PARTS = "ConjuredParts";
    private static final String FORMAT = "ConjuredFormat";
    private static final String WINDOW = "ConjuredWindow";
    private static final String UNTIL = "ConjuredUntil";
    private static final String ROOM = "ConjuredRoom";

    /** How long volatile matter keeps its form once it leaves the bench, when no chronos says: two seconds. */
    public static final int DEFAULT_WINDOW = SpellFlow.WINDOW_TICKS;
    /** Kept in flow, it is paid for a second at a time. */
    private static final int KEPT_TICKS = 20;
    /** The temperature at rest, against which matter is solid or not. */
    private static final double AT_REST = 1.0D;

    private static final Map<VitaElement, Integer> COLORS = Map.of(
            VitaElement.FIRMO, 0x7A5230,
            VitaElement.AQUA, 0x2F6BFF,
            VitaElement.AURA, 0xDDEEFF,
            VitaElement.IGNI, 0xFF6A10);

    private Conjured() {
    }

    // ------------------------------------------------------------------ what it keeps

    /** The matter of the thing's principal part; nothing for a thing the bench did not make. */
    public static Particles matter(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MATTER, Tag.TAG_COMPOUND)) {
            return Particles.NONE;
        }
        CompoundTag held = tag.getCompound(MATTER);
        return new Particles(held.getLong("firmo"), held.getLong("aqua"), held.getLong("aura"), held.getLong("igni"));
    }

    public static void setMatter(ItemStack stack, Particles particles) {
        CompoundTag held = new CompoundTag();
        held.putLong("firmo", particles.firmo());
        held.putLong("aqua", particles.aqua());
        held.putLong("aura", particles.aura());
        held.putLong("igni", particles.igni());
        stack.getOrCreateTag().put(MATTER, held);
    }

    public static boolean conjured(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(MATTER, Tag.TAG_COMPOUND);
    }

    /** The format it was made in, if it was made in one. */
    public static Optional<String> format(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(FORMAT, Tag.TAG_STRING) ? Optional.of(tag.getString(FORMAT)) : Optional.empty();
    }

    public static void setFormat(ItemStack stack, String format) {
        stack.getOrCreateTag().putString(FORMAT, format);
    }

    /** The parts that stay what they are (the handle), one of each, to fall when the rest comes apart. */
    public static List<ItemStack> parts(ItemStack stack) {
        List<ItemStack> parts = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(PARTS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(PARTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack part = ItemStack.of(list.getCompound(i));
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
        }
        return parts;
    }

    public static void setParts(ItemStack stack, List<ItemStack> parts) {
        ListTag list = new ListTag();
        for (ItemStack part : parts) {
            list.add(part.save(new CompoundTag()));
        }
        stack.getOrCreateTag().put(PARTS, list);
    }

    /** How long it keeps its form once out of the bench, in ticks. */
    public static int window(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int window = tag == null ? 0 : tag.getInt(WINDOW);
        return window > 0 ? window : DEFAULT_WINDOW;
    }

    /** The chronos that brought it ({@code 0}: none, the default two seconds). */
    public static int writtenWindow(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(WINDOW);
    }

    public static void setWindow(ItemStack stack, int ticks) {
        if (ticks > 0) {
            stack.getOrCreateTag().putInt(WINDOW, ticks);
        }
    }

    /**
     * How many squares of the bench the thing's matter was pressed into, for each one made (a sword, two; one of four
     * arrows, a quarter): a square is the room of one item, which holds one UMU as loosely packed as earth.
     */
    public static double room(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        double room = tag == null ? 0.0D : tag.getDouble(ROOM);
        return room > 0.0D ? room : 1.0D;
    }

    public static void setRoom(ItemStack stack, double squares) {
        if (squares > 0.0D) {
            stack.getOrCreateTag().putDouble(ROOM, squares);
        }
    }

    /**
     * How tightly its matter is packed in its room, as earth's packing is 1 (docs/invocacao-design.md, "O quadrado é
     * um volume"): an ingot of iron, ten UMU in a square, is packed 10, as a block of iron is.
     */
    public static double packing(ItemStack stack) {
        return matter(stack).present().umu() / room(stack);
    }

    /** How tightly {@code particles} are packed in one square: one UMU in it is as loose as earth. */
    public static double packing(Particles particles) {
        return (double) particles.present().total() / Particles.ITEM;
    }

    // ------------------------------------------------------------------ what it is like

    /** Whether matter is not solid at rest: fire, water, air keep no form of their own. */
    public static boolean volatileMatter(Particles particles) {
        Particles present = particles.present();
        return present.total() > 0L && Field.state(present, AT_REST) != State.SOLID;
    }

    public static boolean isVolatile(ItemStack stack) {
        return conjured(stack) && volatileMatter(matter(stack));
    }

    public static Qualities qualities(Particles particles) {
        return particles.present().composition().map(Qualities::of).orElse(new Qualities(0.0D, 0.0D, 0.0D, 0.0D));
    }

    /** The colour of matter: the colours of the four, each in its share. */
    public static int color(Particles particles) {
        Particles present = particles.present();
        long total = present.total();
        if (total <= 0L) {
            return 0xFFFFFF;
        }
        double r = 0.0D;
        double g = 0.0D;
        double b = 0.0D;
        for (VitaElement aspect : Particles.ASPECTS) {
            double share = (double) present.count(aspect) / total;
            int color = COLORS.get(aspect);
            r += share * (color >> 16 & 0xFF);
            g += share * (color >> 8 & 0xFF);
            b += share * (color & 0xFF);
        }
        return (int) Math.round(r) << 16 | (int) Math.round(g) << 8 | (int) Math.round(b);
    }

    // ------------------------------------------------------------------ coming apart

    /**
     * A tick of a conjured thing outside the bench, held by {@code holder} (who carries it, or the item on the ground).
     * The bench keeps it as it is; out of it, volatile matter starts counting the first time it ticks. Returns true when
     * it came apart.
     */
    public static boolean tick(ItemStack stack, Level level, Entity holder) {
        if (level.isClientSide || stack.isEmpty() || !isVolatile(stack)) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        long now = level.getGameTime();
        if (!tag.contains(UNTIL, Tag.TAG_LONG)) {
            tag.putLong(UNTIL, now + window(stack));
            return false;
        }
        if (now < tag.getLong(UNTIL)) {
            return false;
        }
        if (holder instanceof ServerPlayer player && FlowState.flowing(player) && pay(player, upkeep(stack))) {
            tag.putLong(UNTIL, now + KEPT_TICKS);
            return false;
        }
        dissolve(stack, (ServerLevel) level, holder);
        return true;
    }

    /**
     * What a second of keeping it costs: the matter held in a form it does not have, as a tap open on it (what flows in
     * every two seconds of a chronos, SpellFlow), for a second.
     */
    public static double upkeep(ItemStack stack) {
        return matter(stack).present().umu() * stack.getCount() * KEPT_TICKS / SpellFlow.WINDOW_TICKS;
    }

    private static boolean pay(ServerPlayer player, double umu) {
        if (player.isCreative()) {
            return true;
        }
        int points = (int) Math.ceil(umu * Mana.XP_PER_UMU);
        if (Mana.points(player.experienceLevel, player.experienceProgress) < points) {
            return false;
        }
        player.giveExperiencePoints(-points);
        return true;
    }

    /** It comes apart where it is: the volatile matter goes off as what it is, and the solid parts fall. */
    public static void dissolve(ItemStack stack, ServerLevel level, Entity holder) {
        Qualities qualities = qualities(matter(stack));
        ParticleOptions shown = qualities.hot() ? ParticleTypes.FLAME
                : qualities.wet() ? ParticleTypes.SPLASH
                : qualities.light() ? ParticleTypes.CLOUD
                : ParticleTypes.POOF;
        SoundEvent heard = qualities.hot() ? SoundEvents.FIRE_EXTINGUISH
                : qualities.wet() ? SoundEvents.BUCKET_EMPTY
                : SoundEvents.WOOL_BREAK;
        double y = holder.getY() + holder.getBbHeight() * 0.6D;
        level.sendParticles(shown, holder.getX(), y, holder.getZ(), 16, 0.25D, 0.25D, 0.25D, 0.02D);
        level.playSound(null, holder.getX(), y, holder.getZ(), heard, SoundSource.PLAYERS, 0.6F, 1.2F);
        for (ItemStack part : parts(stack)) {
            ItemStack falling = part.copyWithCount(part.getCount() * stack.getCount());
            level.addFreshEntity(new ItemEntity(level, holder.getX(), y, holder.getZ(), falling));
        }
        stack.setCount(0);
        if (holder instanceof ItemEntity lying) {
            lying.discard();
        }
    }
}

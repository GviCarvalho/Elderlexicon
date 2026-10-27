package com.elderlexicon.mod.item;

import com.elderlexicon.mod.spell.function.Dials;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The arcane compass (docs/bussola-arcana-design.md): a dial with up to five marked nuggets set in it. It measures
 * nothing by itself: a spell puts something on a nugget (the image of a source, a push toward a place, the pull of an
 * aura bond) and the dial shows it. The nuggets are the anchors; the spell is the program.
 */
public class ArcaneCompassItem extends Item {

    /** The most nuggets a dial holds. */
    public static final int MAX_NUGGETS = 5;
    private static final String TAG_NUGGETS = "Nuggets";
    /** The metal of each nugget, in the same order (a dial from before keeps its own metal for all). */
    private static final String TAG_METALS = "NuggetMetals";
    private static final String TAG_READINGS = "Readings";

    /** What the dial and its nuggets are made of: what it changes is what it would change in the real world. */
    public enum Metal {
        /** Light (density 7.9): reacts to little force and trembles; drawn by a lodestone. */
        IRON(0.056D, 10.0D, true),
        /** Heavy (density 19.3, 2.4 times iron): steady, slow to turn; nothing draws it. */
        GOLD(0.135D, 25.0D, false);

        private final double nuggetMass;
        private final double fullSpeed;
        private final boolean magnetic;

        Metal(double nuggetMass, double fullSpeed, boolean magnetic) {
            this.nuggetMass = nuggetMass;
            this.fullSpeed = fullSpeed;
            this.magnetic = magnetic;
        }

        /** The mass of one nugget, in the units marked things are weighed in (an item on the ground is 0.5). */
        public double nuggetMass() {
            return nuggetMass;
        }

        /** The speed, in blocks per second, at which the speed needle reaches the end of its scale. */
        public double fullSpeed() {
            return fullSpeed;
        }

        public boolean magnetic() {
            return magnetic;
        }

        /** The metal of a nugget item, or null when it is no nugget of these. */
        @Nullable
        public static Metal ofNugget(ItemStack nugget) {
            if (nugget.is(net.minecraft.world.item.Items.IRON_NUGGET)) {
                return IRON;
            }
            return nugget.is(net.minecraft.world.item.Items.GOLD_NUGGET) ? GOLD : null;
        }
    }

    private final Metal metal;

    public ArcaneCompassItem(Metal metal, Properties properties) {
        super(properties);
        this.metal = metal;
    }

    public Metal metal() {
        return metal;
    }

    // ------------------------------------------------------------------ the nuggets

    /** The marks of the nuggets set in {@code stack}, in their order on the dial (the first is the middle one). */
    public static List<String> nuggets(ItemStack stack) {
        List<String> marks = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_NUGGETS, Tag.TAG_LIST)) {
            return marks;
        }
        ListTag list = tag.getList(TAG_NUGGETS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            marks.add(list.getString(i));
        }
        return marks;
    }

    /**
     * The metal of the nugget at {@code slot}: what it moves and weighs like. The dial's own metal is only its case; a dial
     * made before nuggets kept their metal has nuggets of its own metal.
     */
    public static Metal nuggetMetal(ItemStack stack, int slot) {
        Metal fallback = stack.getItem() instanceof ArcaneCompassItem dial ? dial.metal() : Metal.IRON;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_METALS, Tag.TAG_LIST)) {
            return fallback;
        }
        ListTag metals = tag.getList(TAG_METALS, Tag.TAG_STRING);
        if (slot < 0 || slot >= metals.size()) {
            return fallback;
        }
        try {
            return Metal.valueOf(metals.getString(slot));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    /** Sets one more nugget of the dial's metal, bearing {@code mark}. */
    public static boolean addNugget(ItemStack stack, String mark) {
        Metal own = stack.getItem() instanceof ArcaneCompassItem dial ? dial.metal() : Metal.IRON;
        return addNugget(stack, mark, own);
    }

    /** Sets one more nugget of {@code metal}, bearing {@code mark}; false when the dial is full or already has that mark. */
    public static boolean addNugget(ItemStack stack, String mark, Metal metal) {
        List<String> marks = nuggets(stack);
        if (mark == null || mark.isBlank() || marks.size() >= MAX_NUGGETS || marks.contains(mark)) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        // The metals of the nuggets already set, written out before the list grows (a dial from before has none).
        ListTag metals = new ListTag();
        for (int slot = 0; slot < marks.size(); slot++) {
            metals.add(StringTag.valueOf(nuggetMetal(stack, slot).name()));
        }
        metals.add(StringTag.valueOf(metal.name()));
        ListTag list = tag.getList(TAG_NUGGETS, Tag.TAG_STRING);
        list.add(StringTag.valueOf(mark));
        tag.put(TAG_NUGGETS, list);
        tag.put(TAG_METALS, metals);
        return true;
    }

    /**
     * {@code dial} with {@code nugget} set in it: an iron or gold nugget that bears a mark (renamed at the anvil), in a
     * dial of either metal. Empty when it is no such nugget, bears no mark, or the dial is full or has that mark already.
     */
    public static java.util.Optional<ItemStack> withNugget(ItemStack dial, ItemStack nugget) {
        if (!(dial.getItem() instanceof ArcaneCompassItem) || nugget.isEmpty()) {
            return java.util.Optional.empty();
        }
        Metal metal = Metal.ofNugget(nugget);
        java.util.Optional<String> mark = com.elderlexicon.mod.spell.function.MarkHelper.markForItem(nugget);
        if (metal == null || mark.isEmpty()) {
            return java.util.Optional.empty();
        }
        ItemStack set = dial.copyWithCount(1);
        return addNugget(set, mark.get(), metal) ? java.util.Optional.of(set) : java.util.Optional.empty();
    }

    // ------------------------------------------------------------------ what the nuggets show

    /** What one nugget is showing, and until when (game time). */
    public record Reading(Kind kind, String element, double amount, float yaw, double speed, long until) {

        public enum Kind { IMAGE, NEEDLE, SPEED }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("k", kind.name());
            if (element != null) {
                tag.putString("e", element);
            }
            tag.putDouble("a", amount);
            tag.putFloat("y", yaw);
            tag.putDouble("s", speed);
            tag.putLong("u", until);
            return tag;
        }

        @Nullable
        static Reading load(CompoundTag tag) {
            try {
                return new Reading(Kind.valueOf(tag.getString("k")), tag.contains("e") ? tag.getString("e") : null,
                        tag.getDouble("a"), tag.getFloat("y"), tag.getDouble("s"), tag.getLong("u"));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
    }

    /** Puts {@code reading} on the nugget at {@code slot}. */
    public static void show(ItemStack stack, int slot, Reading reading) {
        CompoundTag readings = stack.getOrCreateTag().getCompound(TAG_READINGS);
        readings.put(Integer.toString(slot), reading.save());
        stack.getOrCreateTag().put(TAG_READINGS, readings);
    }

    /** What the nugget at {@code slot} shows at {@code now}, if anything. */
    @Nullable
    public static Reading reading(ItemStack stack, int slot, long now) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        CompoundTag readings = tag.getCompound(TAG_READINGS);
        String key = Integer.toString(slot);
        if (!readings.contains(key)) {
            return null;
        }
        Reading reading = Reading.load(readings.getCompound(key));
        return reading == null || reading.until() < now ? null : reading;
    }

    /** Forgets what has stopped showing, so the stack does not keep old readings. */
    private static void forgetOld(ItemStack stack, long now) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_READINGS)) {
            return;
        }
        CompoundTag readings = tag.getCompound(TAG_READINGS);
        List<String> old = new ArrayList<>();
        for (String key : readings.getAllKeys()) {
            if (readings.getCompound(key).getLong("u") < now) {
                old.add(key);
            }
        }
        old.forEach(readings::remove);
        if (readings.isEmpty()) {
            tag.remove(TAG_READINGS);
        }
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId,
                              boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (level.getGameTime() % 20L == 0L) {
            forgetOld(stack, level.getGameTime());
        }
        // A nugget bound by an aura bond is pulled every tick: the strain is the speed (docs/bussola-arcana-design.md).
        Dials.pull(player, stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // What the nuggets show changes every second: the dial stays in hand.
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        List<String> marks = nuggets(stack);
        if (marks.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.elderlexicon.arcane_compass.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.elderlexicon.arcane_compass.nuggets", marks.size(), MAX_NUGGETS)
                .withStyle(ChatFormatting.GRAY));
        for (int slot = 0; slot < marks.size(); slot++) {
            Metal nugget = nuggetMetal(stack, slot);
            tooltip.add(Component.literal("  " + marks.get(slot) + " ")
                    .withStyle(nugget == Metal.GOLD ? ChatFormatting.GOLD : ChatFormatting.WHITE)
                    .append(Component.translatable(nugget == Metal.GOLD ? "item.minecraft.gold_nugget"
                            : "item.minecraft.iron_nugget").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.elderlexicon.mod.client.ArcaneCompassRenderer(
                            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                            Minecraft.getInstance().getEntityModels());
                }
                return renderer;
            }
        });
    }
}

package com.elderlexicon.mod.spelling.item;

import com.elderlexicon.mod.magic.lexicon.Foci;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A wand (docs/varinhas-design.md): a haste, maybe a grip and maybe a setting. Only the haste is required. The setting
 * is a gem holding a reserve of vis or of sources, which the caster spends instead of their own; it is set full and is
 * filled again by spells. An echo shard set in it repeats every spell its bearer says in spelling mode.
 */
public abstract class WandItem extends SpellConduitItem {

    /** The kind of reserve that stands for the caster's mana (their experience). */
    public static final String VIS = "vis";
    private static final String TAG_SETTING = "WandSetting";
    private static final String TAG_RESERVE = "SettingReserve";

    protected WandItem(Properties properties, double capacity) {
        super(properties, capacity);
    }

    /** What the wand is held by; a wand that is only a haste has none. */
    public Optional<Foci.Grip> grip(ItemStack stack) {
        return Optional.empty();
    }

    /** Gives the wand back all it can conduct, as when it was made; its parts and what its gem holds stay. */
    public void repair(ItemStack stack) {
        resetCapacity(stack);
        stack.setDamageValue(0);
    }

    /** The gem set in the wand, if any. */
    public Optional<Foci.Setting> setting(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SETTING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(Foci.settings().get(tag.getString(TAG_SETTING)));
    }

    /** Sets {@code setting} in the wand, full. The gem that was there, and what it held, are gone. */
    public void applySetting(ItemStack stack, Foci.Setting setting) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_SETTING, setting.id());
        tag.remove(TAG_RESERVE);
        setting.reserves().forEach((kind, amount) -> writeReserve(stack, kind, amount));
    }

    /**
     * The color of layer {@code layer} of the wand's sprite: the haste is drawn as it is, then the grip, if any, then
     * the mount that holds the gem and the gem, if any. The grip and the mount take the grip's color (a mount on a bare
     * haste stays the grey it is drawn in); the gem takes its own. Returns {@code -1} (no tint) for what is not tinted.
     */
    public int layerColor(ItemStack stack, int layer) {
        Optional<Foci.Grip> grip = grip(stack);
        int gripColor = grip.map(held -> 0xFF000000 | held.color()).orElse(-1);
        int mount = grip.isPresent() ? 2 : 1;
        if (layer >= 1 && layer <= mount) {
            return gripColor;
        }
        if (layer == mount + 1) {
            return setting(stack).map(setting -> 0xFF000000 | setting.color()).orElse(-1);
        }
        return -1;
    }

    /** True when {@code player} holds, in either hand, a wand with an echo shard set in it. */
    public static boolean holdsEcho(Player player) {
        return player != null && (echoes(player.getMainHandItem()) || echoes(player.getOffhandItem()));
    }

    private static boolean echoes(ItemStack stack) {
        return stack.getItem() instanceof WandItem wand && wand.setting(stack).map(Foci.Setting::echo).orElse(false);
    }

    /** True when a gem is set in the wand (even one this world's data no longer knows). */
    public boolean hasSetting(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_SETTING);
    }

    /** Moves the gem set in {@code from}, with what it holds, into {@code to} (a haste made into a held wand). */
    public static void carrySetting(ItemStack from, ItemStack to) {
        CompoundTag source = from.getTag();
        if (source == null || !source.contains(TAG_SETTING)) {
            return;
        }
        CompoundTag target = to.getOrCreateTag();
        target.putString(TAG_SETTING, source.getString(TAG_SETTING));
        if (source.contains(TAG_RESERVE)) {
            target.put(TAG_RESERVE, source.getCompound(TAG_RESERVE).copy());
        }
    }

    /** How much of {@code kind} the setting holds now. */
    public double reserve(ItemStack stack, String kind) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_RESERVE)) {
            return 0.0D;
        }
        return Math.min(reserveCapacity(stack, kind), tag.getCompound(TAG_RESERVE).getDouble(normalize(kind)));
    }

    /** How much of {@code kind} the setting can hold; nothing when it holds no such kind, or there is no setting. */
    public double reserveCapacity(ItemStack stack, String kind) {
        return setting(stack).map(setting -> setting.reserves().getOrDefault(normalize(kind), 0.0D)).orElse(0.0D);
    }

    /** Puts up to {@code amount} of {@code kind} into the setting; returns what went in. */
    public double store(ItemStack stack, String kind, double amount) {
        if (amount <= EPSILON) {
            return 0.0D;
        }
        double held = reserve(stack, kind);
        double stored = Math.min(amount, Math.max(0.0D, reserveCapacity(stack, kind) - held));
        if (stored > EPSILON) {
            writeReserve(stack, kind, held + stored);
        }
        return Math.max(0.0D, stored);
    }

    /** Takes up to {@code amount} of {@code kind} from the setting; returns what was taken. */
    public double draw(ItemStack stack, String kind, double amount) {
        if (amount <= EPSILON) {
            return 0.0D;
        }
        double held = reserve(stack, kind);
        double drawn = Math.min(amount, held);
        if (drawn > EPSILON) {
            writeReserve(stack, kind, held - drawn);
        }
        return Math.max(0.0D, drawn);
    }

    private void writeReserve(ItemStack stack, String kind, double value) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag reserves = tag.getCompound(TAG_RESERVE);
        reserves.putDouble(normalize(kind), Math.max(0.0D, value));
        tag.put(TAG_RESERVE, reserves);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        grip(stack).ifPresent(grip -> tooltip.add(Component.translatable(
                        "tooltip.elderlexicon.wand.grip", Component.translatable(grip.translationKey()))
                .withStyle(ChatFormatting.GRAY)));
        setting(stack).ifPresent(setting -> {
            tooltip.add(Component.translatable(
                            "tooltip.elderlexicon.wand.setting", Component.translatable(setting.translationKey()))
                    .withStyle(ChatFormatting.GRAY));
            for (Map.Entry<String, Double> kind : setting.reserves().entrySet()) {
                tooltip.add(Component.translatable("tooltip.elderlexicon.wand.reserve",
                                Component.translatable("element.elderlexicon." + (VIS.equals(kind.getKey()) ? "balanced" : kind.getKey())),
                                formatAmount(reserve(stack, kind.getKey())),
                                formatAmount(kind.getValue()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (setting.echo()) {
                tooltip.add(Component.translatable("tooltip.elderlexicon.wand.echo").withStyle(ChatFormatting.DARK_PURPLE));
            }
        });
    }

    private static String normalize(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }
}

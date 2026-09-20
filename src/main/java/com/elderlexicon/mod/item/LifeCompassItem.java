package com.elderlexicon.mod.item;

import com.elderlexicon.mod.vita.VitaImbalanceTier;
import com.elderlexicon.mod.vita.VitaProfile;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.elderlexicon.mod.client.LifeCompassRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Utility item that caches Vita readings into its NBT so clients can render a Life Compass without the scoreboard.
 */
@SuppressWarnings("null")
public class LifeCompassItem extends Item {

    private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("0.##");

    public LifeCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(@NotNull java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private LifeCompassRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new LifeCompassRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                            Minecraft.getInstance().getEntityModels());
                }
                return renderer;
            }
        });
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack,
                              @NotNull Level level,
                              @NotNull Entity entity,
                              int slotId,
                              boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }

        boolean inMainHand = selected && player.getMainHandItem() == stack;
        boolean inOffHand = player.getOffhandItem() == stack;
        if (!inMainHand && !inOffHand) {
            return;
        }

        VitaProfile profile = VitaSystem.fromPlayer(player);
        LifeCompassSlices slices = LifeCompassSlices.fromProfile(profile);
        slices.writeToStack(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack,
                                @Nullable Level level,
                                @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        LifeCompassSlices slices = LifeCompassSlices.fromStack(stack);
        if (slices.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.total", format(slices.total()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.aqua", format(slices.aqua()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.aura", format(slices.aura()))
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.igni", format(slices.igni()))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.firmo", format(slices.firmo()))
                .withStyle(ChatFormatting.GREEN));

        VitaImbalanceTier aquaTier = VitaSystem.evaluateAquaTier(slices.aqua(), VitaImbalanceTier.BALANCED);
        if (!aquaTier.isBalanced()) {
            tooltip.add(Component.translatable(
                            "tooltip.elderlexicon.life_compass.aqua_state",
                            Component.translatable(describeAquaTierKey(aquaTier)))
                    .withStyle(ChatFormatting.AQUA));
        }

        VitaImbalanceTier auraTier = VitaSystem.evaluateAuraTier(slices.aura(), VitaImbalanceTier.BALANCED);
        if (!auraTier.isBalanced()) {
            tooltip.add(Component.translatable(
                            "tooltip.elderlexicon.life_compass.aura_state",
                            Component.translatable(describeAuraTierKey(auraTier)))
                    .withStyle(ChatFormatting.YELLOW));
        }

        VitaImbalanceTier firmoTier = VitaSystem.evaluateFirmoTier(slices.firmo(), VitaImbalanceTier.BALANCED);
        if (!firmoTier.isBalanced()) {
            tooltip.add(Component.translatable(
                            "tooltip.elderlexicon.life_compass.firmo_state",
                            Component.translatable(describeFirmoTierKey(firmoTier)))
                    .withStyle(ChatFormatting.GREEN));
        }

        VitaImbalanceTier igniTier = VitaSystem.evaluateIgniTier(slices.igni(), VitaImbalanceTier.BALANCED);
        if (!igniTier.isBalanced()) {
            tooltip.add(Component.translatable(
                            "tooltip.elderlexicon.life_compass.igni_state",
                            Component.translatable(describeIgniTierKey(igniTier)))
                    .withStyle(ChatFormatting.RED));
        }

        if (slices.hasOverflow()) {
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.overflow_header")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.overflow_total", format(slices.totalOverflow()))
                .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.aqua_overflow", format(slices.aquaOverflow()))
                .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.aura_overflow", format(slices.auraOverflow()))
                .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.igni_overflow", format(slices.igniOverflow()))
                .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.elderlexicon.life_compass.firmo_overflow", format(slices.firmoOverflow()))
                .withStyle(ChatFormatting.GREEN));
        }
    }

    private static String format(double value) {
        return VALUE_FORMAT.format(Math.max(0.0D, value));
    }

    private static String describeAquaTierKey(VitaImbalanceTier tier) {
        return switch (tier) {
            case SEVERELY_LOW -> "tooltip.elderlexicon.life_compass.aqua_state.severely_low";
            case SLIGHTLY_LOW -> "tooltip.elderlexicon.life_compass.aqua_state.slightly_low";
            case SLIGHTLY_HIGH -> "tooltip.elderlexicon.life_compass.aqua_state.slightly_high";
            case SEVERELY_HIGH -> "tooltip.elderlexicon.life_compass.aqua_state.severely_high";
            default -> "tooltip.elderlexicon.life_compass.aqua_state.balanced";
        };
    }

    private static String describeAuraTierKey(VitaImbalanceTier tier) {
        return switch (tier) {
            case SEVERELY_LOW -> "tooltip.elderlexicon.life_compass.aura_state.severely_low";
            case SLIGHTLY_LOW -> "tooltip.elderlexicon.life_compass.aura_state.slightly_low";
            case SLIGHTLY_HIGH -> "tooltip.elderlexicon.life_compass.aura_state.slightly_high";
            case SEVERELY_HIGH -> "tooltip.elderlexicon.life_compass.aura_state.severely_high";
            default -> "tooltip.elderlexicon.life_compass.aura_state.balanced";
        };
    }

    private static String describeFirmoTierKey(VitaImbalanceTier tier) {
        return switch (tier) {
            case SEVERELY_LOW -> "tooltip.elderlexicon.life_compass.firmo_state.severely_low";
            case SLIGHTLY_LOW -> "tooltip.elderlexicon.life_compass.firmo_state.slightly_low";
            case SLIGHTLY_HIGH -> "tooltip.elderlexicon.life_compass.firmo_state.slightly_high";
            case SEVERELY_HIGH -> "tooltip.elderlexicon.life_compass.firmo_state.severely_high";
            default -> "tooltip.elderlexicon.life_compass.firmo_state.balanced";
        };
    }

    private static String describeIgniTierKey(VitaImbalanceTier tier) {
        return switch (tier) {
            case SEVERELY_LOW -> "tooltip.elderlexicon.life_compass.igni_state.severely_low";
            case SLIGHTLY_LOW -> "tooltip.elderlexicon.life_compass.igni_state.slightly_low";
            case SLIGHTLY_HIGH -> "tooltip.elderlexicon.life_compass.igni_state.slightly_high";
            case SEVERELY_HIGH -> "tooltip.elderlexicon.life_compass.igni_state.severely_high";
            default -> "tooltip.elderlexicon.life_compass.igni_state.balanced";
        };
    }
}

package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Visibility;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Hides blocks drawn by a renderer of their own (chests, signs, banners, skulls): as for entities, the renderer of each
 * such kind is wrapped while something of that kind is hidden, and the game's own comes back afterwards
 * ({@link HiddenEntities}).
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class HiddenBlockEntities {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Field renderersField;
    private static boolean broken;
    private static Map<BlockEntityType<?>, BlockEntityRenderer<?>> original;
    private static Map<BlockEntityType<?>, BlockEntityRenderer<?>> installed;
    private static Set<BlockEntityType<?>> wrapped = Set.of();

    private HiddenBlockEntities() {
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void wrapHidden(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.phase != TickEvent.Phase.END || level == null || broken) {
            return;
        }
        Set<BlockEntityType<?>> needed = new HashSet<>();
        for (BlockPos pos : ClientVisibility.blocks().keySet()) {
            BlockEntity blockEntity = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (blockEntity != null) {
                needed.add(blockEntity.getType());
            }
        }
        if (needed.isEmpty() && wrapped.isEmpty()) {
            return;
        }
        try {
            BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
            Field field = renderersField(dispatcher);
            Map<BlockEntityType<?>, BlockEntityRenderer<?>> current =
                    (Map<BlockEntityType<?>, BlockEntityRenderer<?>>) field.get(dispatcher);
            if (current != installed) {
                original = current;
                wrapped = Set.of();
            }
            if (needed.equals(wrapped)) {
                return;
            }
            Map<BlockEntityType<?>, BlockEntityRenderer<?>> next = new HashMap<>(original);
            for (BlockEntityType<?> type : needed) {
                BlockEntityRenderer<?> own = original.get(type);
                if (own != null) {
                    next.put(type, new Hiding<>((BlockEntityRenderer<BlockEntity>) own));
                }
            }
            installed = needed.isEmpty() ? original : next;
            field.set(dispatcher, installed);
            wrapped = Set.copyOf(needed);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            LOGGER.warn("Could not wrap block entity renderers; hidden chests and signs will stay visible", exception);
        }
    }

    private static Field renderersField(BlockEntityRenderDispatcher dispatcher) throws ReflectiveOperationException {
        if (renderersField != null) {
            return renderersField;
        }
        for (Field field : BlockEntityRenderDispatcher.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            if (field.get(dispatcher) instanceof Map<?, ?> map && !map.isEmpty()
                    && map.keySet().iterator().next() instanceof BlockEntityType<?>) {
                renderersField = field;
                return field;
            }
        }
        throw new NoSuchFieldException("BlockEntityRenderDispatcher renderers");
    }

    /** Draws a block entity at the sight asked: not at all at 0, see-through between 1 and 9, whole when revealed. */
    private record Hiding<T extends BlockEntity>(BlockEntityRenderer<T> own) implements BlockEntityRenderer<T> {

        private static OptionalInt sight(BlockEntity blockEntity) {
            return ClientRevelation.active() ? OptionalInt.empty() : ClientVisibility.ofBlock(blockEntity.getBlockPos());
        }

        @Override
        public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
                           int overlay) {
            OptionalInt level = sight(blockEntity);
            if (level.isEmpty()) {
                own.render(blockEntity, partialTick, poseStack, buffers, light, overlay);
                return;
            }
            float opacity = Visibility.opacity(level.getAsInt());
            if (opacity > 0.0F) {
                own.render(blockEntity, partialTick, poseStack, new GhostBuffers(buffers, opacity), light, overlay);
            }
        }

        @Override
        public boolean shouldRenderOffScreen(T blockEntity) {
            return own.shouldRenderOffScreen(blockEntity);
        }

        @Override
        public int getViewDistance() {
            return own.getViewDistance();
        }

        @Override
        public boolean shouldRender(T blockEntity, Vec3 cameraPos) {
            return own.shouldRender(blockEntity, cameraPos);
        }
    }
}

package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.sight.Visibility;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * Hides entities that are not creatures (dropped items, frames, placed scrolls, falling blocks, bolts): Forge has no
 * event to skip drawing them, so the renderer of each such kind is wrapped by one that asks how much of the entity is
 * seen. Only kinds with something hidden right now are wrapped, and the game's own renderers come back when nothing of
 * that kind is hidden, so nothing else changes. Creatures are handled by {@link VisibilityRenderer}.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID, value = Dist.CLIENT)
public final class HiddenEntities {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Field renderersField;
    private static boolean broken;
    /** The game's own map, as last found, and the one put in its place. */
    private static Map<EntityType<?>, EntityRenderer<?>> original;
    private static Map<EntityType<?>, EntityRenderer<?>> installed;
    private static Set<EntityType<?>> wrapped = Set.of();

    private HiddenEntities() {
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void wrapHidden(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.phase != TickEvent.Phase.END || level == null || broken) {
            return;
        }
        Set<EntityType<?>> needed = new HashSet<>();
        for (int id : ClientVisibility.entities().keySet()) {
            Entity entity = level.getEntity(id);
            if (entity != null && !(entity instanceof LivingEntity)) {
                needed.add(entity.getType());
            }
        }
        if (needed.isEmpty() && wrapped.isEmpty()) {
            return;
        }
        try {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            Field field = renderersField(dispatcher);
            Map<EntityType<?>, EntityRenderer<?>> current = (Map<EntityType<?>, EntityRenderer<?>>) field.get(dispatcher);
            if (current != installed) {
                // The game built its renderers again (resources reloaded), or this is the first time.
                original = current;
                wrapped = Set.of();
            }
            if (needed.equals(wrapped)) {
                return;
            }
            Map<EntityType<?>, EntityRenderer<?>> next = new HashMap<>(original);
            EntityRendererProvider.Context context = new EntityRendererProvider.Context(dispatcher,
                    minecraft.getItemRenderer(), minecraft.getBlockRenderer(), dispatcher.getItemInHandRenderer(),
                    minecraft.getResourceManager(), minecraft.getEntityModels(), minecraft.font);
            for (EntityType<?> type : needed) {
                EntityRenderer<?> own = original.get(type);
                if (own != null) {
                    next.put(type, new Hiding<>(context, (EntityRenderer<Entity>) own));
                }
            }
            installed = needed.isEmpty() ? original : next;
            field.set(dispatcher, installed);
            wrapped = Set.copyOf(needed);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            LOGGER.warn("Could not wrap entity renderers; hidden non-living entities will stay visible", exception);
        }
    }

    /** The dispatcher's map from entity kind to renderer, found by what it holds rather than by name. */
    private static Field renderersField(EntityRenderDispatcher dispatcher) throws ReflectiveOperationException {
        if (renderersField != null) {
            return renderersField;
        }
        for (Field field : EntityRenderDispatcher.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            if (field.get(dispatcher) instanceof Map<?, ?> map && !map.isEmpty()
                    && map.keySet().iterator().next() instanceof EntityType<?>) {
                renderersField = field;
                return field;
            }
        }
        throw new NoSuchFieldException("EntityRenderDispatcher renderers");
    }

    /** Draws an entity at the sight asked: not at all at 0, see-through between 1 and 9, whole when revealed. */
    private static final class Hiding<T extends Entity> extends EntityRenderer<T> {

        private final EntityRenderer<T> own;
        private final float ownShadow;

        Hiding(EntityRendererProvider.Context context, EntityRenderer<T> own) {
            super(context);
            this.own = own;
            this.ownShadow = shadowOf(own);
            this.shadowRadius = ownShadow;
        }

        private static OptionalInt sight(Entity entity) {
            return ClientRevelation.active() ? OptionalInt.empty() : ClientVisibility.of(entity.getId());
        }

        @Override
        public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
            OptionalInt level = sight(entity);
            if (level.isPresent() && level.getAsInt() <= Visibility.HIDDEN) {
                return false;
            }
            return own.shouldRender(entity, frustum, x, y, z);
        }

        @Override
        public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                           int light) {
            OptionalInt level = sight(entity);
            if (level.isEmpty()) {
                shadowRadius = ownShadow;
                own.render(entity, yaw, partialTick, poseStack, buffers, light);
                return;
            }
            float opacity = Visibility.opacity(level.getAsInt());
            shadowRadius = ownShadow * opacity;
            own.render(entity, yaw, partialTick, poseStack, new GhostBuffers(buffers, opacity), light);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return own.getTextureLocation(entity);
        }

        @Override
        public Vec3 getRenderOffset(T entity, float partialTick) {
            return own.getRenderOffset(entity, partialTick);
        }

        /** The renderer's shadow size, its first float field (the game keeps it protected). */
        private static float shadowOf(EntityRenderer<?> renderer) {
            try {
                for (Field field : EntityRenderer.class.getDeclaredFields()) {
                    if (field.getType() == float.class && !Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        return field.getFloat(renderer);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // No shadow is a fine fallback.
            }
            return 0.0F;
        }
    }
}

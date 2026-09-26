package com.elderlexicon.mod.spelling.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws whatever is rendered through it see-through: every textured entity layer (body, armor, held items) is moved
 * to the translucent entity layer with the same texture, and every vertex keeps only {@code opacity} of its alpha. Glints are
 * dropped: a ghost's enchantments do not shine.
 *
 * <p>Minecraft keeps a render type's texture private; it is read here by reflection, by shape rather than by name (the
 * texture state is the one part of a render type that answers with an optional texture), so it works with the names the
 * game has once built as well.
 */
final class GhostBuffers implements MultiBufferSource {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RenderType, Optional<ResourceLocation>> TEXTURES = new HashMap<>();
    private static boolean warned;

    private final MultiBufferSource delegate;
    private final float opacity;

    GhostBuffers(MultiBufferSource delegate, float opacity) {
        this.delegate = delegate;
        this.opacity = opacity;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (type.toString().contains("glint")) {
            return NOTHING;
        }
        // Only layers already drawn as entities can move to the translucent entity layer: text (a name tag, the words
        // recited above the head) is laid out differently, and moved there it would leave vertices half filled.
        RenderType seeThrough = type;
        if (type.format() == DefaultVertexFormat.NEW_ENTITY) {
            seeThrough = TEXTURES.computeIfAbsent(type, GhostBuffers::textureOf)
                    .map(RenderType::entityTranslucent).orElse(type);
        }
        return new Faded(delegate.getBuffer(seeThrough), opacity);
    }

    private static Optional<ResourceLocation> textureOf(RenderType type) {
        try {
            for (Field stateField : fieldsOf(type.getClass())) {
                Object state = stateField.get(type);
                if (state == null || state instanceof String || state.getClass().isPrimitive()) {
                    continue;
                }
                for (Field shardField : fieldsOf(state.getClass())) {
                    Object shard = shardField.get(state);
                    if (shard == null) {
                        continue;
                    }
                    Optional<ResourceLocation> texture = optionalTexture(shard);
                    if (texture.isPresent()) {
                        return texture;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Could not read a render type's texture; see-through creatures will be drawn opaque", exception);
            }
        }
        return Optional.empty();
    }

    /** The texture a render state shard names, if it is the texture state (a no-argument method with an Optional). */
    private static Optional<ResourceLocation> optionalTexture(Object shard) throws ReflectiveOperationException {
        for (Class<?> type = shard.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == Optional.class
                        && !Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    if (method.invoke(shard) instanceof Optional<?> found && found.isPresent()
                            && found.get() instanceof ResourceLocation texture) {
                        return Optional.of(texture);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Iterable<Field> fieldsOf(Class<?> start) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> type = start; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /** Passes vertices on with their alpha scaled down. */
    private record Faded(VertexConsumer out, float opacity) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            out.color(red, green, blue, Math.round(alpha * opacity));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            out.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float normalX, float normalY, float normalZ) {
            out.vertex(x, y, z, red, green, blue, alpha * opacity, u, v, overlay, light, normalX, normalY, normalZ);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            out.defaultColor(red, green, blue, Math.round(alpha * opacity));
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }

    /** Swallows what is drawn into it. */
    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float normalX, float normalY, float normalZ) {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    };
}

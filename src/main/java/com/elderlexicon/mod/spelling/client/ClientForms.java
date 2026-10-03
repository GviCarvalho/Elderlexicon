package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.spell.life.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bodies of the players this client sees in another body (docs/particulas-design.md, stage 6): for each, a creature
 * of that kind that only this client knows, which follows the player and is drawn in its place
 * ({@link FormRenderer}).
 */
public final class ClientForms {

    private static final Map<Integer, EntityType<?>> TYPES = new HashMap<>();
    private static final Map<Integer, LivingEntity> BODIES = new HashMap<>();

    private ClientForms() {
    }

    /** What the server says of a player's body: the creature it is shown as, or {@code ""} for a person's own. */
    public static void set(int entityId, String entityType) {
        Forms.shown(entityId, entityType);
        BODIES.remove(entityId);
        if (entityType == null || entityType.isEmpty()) {
            TYPES.remove(entityId);
        } else {
            EntityType.byString(entityType).ifPresentOrElse(type -> TYPES.put(entityId, type),
                    () -> TYPES.remove(entityId));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity != null) {
                entity.refreshDimensions();
            }
        }
    }

    /** The body a player is drawn as, when it is not a person's. */
    static Optional<LivingEntity> bodyOf(Player player) {
        EntityType<?> type = TYPES.get(player.getId());
        Minecraft minecraft = Minecraft.getInstance();
        if (type == null || minecraft.level == null) {
            return Optional.empty();
        }
        LivingEntity body = BODIES.get(player.getId());
        if (body == null || body.level() != minecraft.level) {
            Entity made = type.create(minecraft.level);
            if (!(made instanceof LivingEntity living)) {
                return Optional.empty();
            }
            body = living;
            BODIES.put(player.getId(), body);
        }
        return Optional.of(body);
    }

    /** The body stands where the player stands, turned as the player is, for the frame being drawn. */
    static void follow(LivingEntity body, Player player) {
        body.setPos(player.getX(), player.getY(), player.getZ());
        body.xo = player.xo;
        body.yo = player.yo;
        body.zo = player.zo;
        body.xOld = player.xOld;
        body.yOld = player.yOld;
        body.zOld = player.zOld;
        body.setYRot(player.getYRot());
        body.yRotO = player.yRotO;
        body.setXRot(player.getXRot());
        body.xRotO = player.xRotO;
        body.yBodyRot = player.yBodyRot;
        body.yBodyRotO = player.yBodyRotO;
        body.yHeadRot = player.yHeadRot;
        body.yHeadRotO = player.yHeadRotO;
        body.setOnGround(player.onGround());
        body.tickCount = player.tickCount;
        body.hurtTime = player.hurtTime;
        body.setInvisible(player.isInvisible());
    }

    /** Once a tick, each body's legs move as far as its player walked. */
    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            BODIES.clear();
            return;
        }
        BODIES.forEach((id, body) -> {
            Entity entity = minecraft.level.getEntity(id);
            if (entity instanceof Player player) {
                float walked = (float) Mth.length(player.getX() - player.xo, 0.0D, player.getZ() - player.zo);
                body.walkAnimation.update(Math.min(walked * 4.0F, 1.0F), 0.4F);
            }
        });
    }

    public static void clear() {
        TYPES.clear();
        BODIES.clear();
    }
}

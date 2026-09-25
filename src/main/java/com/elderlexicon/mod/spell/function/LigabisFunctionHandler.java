package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Links two or more targets together so their state mirrors while the link is active.
 */
public final class LigabisFunctionHandler implements SpellFunctionHandler {

    private static final double RANGE = 12.0D;
    private static final int DURATION_TICKS = 20 * 20; // 20 seconds of mirrored state
    private static final Map<String, LigabisLink> LINKS = new HashMap<>();

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        LigabisAspect aspect = LigabisAspect.fromElement(element == null ? context.primaryElement() : element);
        // vis m1 ligabis r1 is a reading bond (no bodily effect): it goes to the Ligabis engine like the four aspects.
        boolean readingBond = aspect == null && context.lexemes().stream().anyMatch("vis"::equalsIgnoreCase);
        if (aspect == null && !readingBond) {
            player.sendSystemMessage(Component.literal("Ligabis requer um elemento valido antes da runa."));
            return;
        }
        if (readingBond || aspect == LigabisAspect.FIRMO || aspect == LigabisAspect.IGNI || aspect == LigabisAspect.AQUA
                || aspect == LigabisAspect.AURA) {
            // Firmo, igni, aqua and aura run on the new Ligabis engine.
            LigabisManager manager = LigabisManager.get();
            if (manager == null) {
                player.sendSystemMessage(Component.literal("Ligabis ainda nao esta pronto neste mundo."));
                return;
            }
            SpellEffects.SpellImpact aimed = SpellEffects.findImpact(player, RANGE);
            Entity target = aimed.entity();
            BlockPos blockPos = aimed.blockPos();
            boolean hasBlock = blockPos != null && !player.serverLevel().getBlockState(blockPos).isAir();
            if (target == null && aspect == LigabisAspect.AURA) {
                // findImpact only picks entities that are "pickable"; a dropped item is not, so it needs its own ray.
                target = findLooseItem(player, RANGE);
            }
            if (target == null && !hasBlock) {
                // Mirando pro nada: o mago se marca.
                target = player;
            }
            manager.castLink(player, context.lexemes(), target, hasBlock ? blockPos : null);
            return;
        }
        MarkDescriptor descriptor = resolveMarkDescriptor(context.lexemes());
        SpellEffects.SpellImpact impact = SpellEffects.findImpact(player, RANGE);
        LinkTarget target = LinkTarget.fromImpact(player, impact);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Nenhum alvo valido para conectar."));
            return;
        }
        String mark = descriptor == null ? null : descriptor.mark();
        Direction direction = descriptor == null ? Direction.BIDIRECTIONAL : descriptor.direction();
        if (mark == null) {
            mark = resolvePersistentMark(target);
        }
        if (mark == null) {
            player.sendSystemMessage(Component.literal("Ligabis requer uma Mark (runa ou tag persistente) para criar a ligacao."));
            return;
        }

        long now = player.serverLevel().getGameTime();
        cleanupExpired(now);

        LigabisLink link = LINKS.get(mark);
        if (link == null || link.isExpired(now) || link.aspect() != aspect) {
            link = new LigabisLink(mark, aspect, direction, now + DURATION_TICKS);
            LINKS.put(mark, link);
        } else {
            link.setDirection(direction);
            link.extend(now + DURATION_TICKS);
        }

        if (!link.addTarget(target)) {
            player.sendSystemMessage(Component.literal("Alvo ja estava conectado a Mark '" + mark + "'."));
            return;
        }

        player.sendSystemMessage(Component.literal("Ligacao '" + mark + "' (" + aspect.displayName() + ") possui "
                + link.targetCount() + " alvos."));
        link.ensureActive(player.serverLevel());
        link.syncManager(player.serverLevel());
    }

    /** {@code SpellEffects.findImpact} only picks "pickable" entities, which a dropped item never is. */
    private Entity findLooseItem(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eye,
                end,
                searchBox,
                candidate -> candidate instanceof ItemEntity && candidate != player);
        return hit == null ? null : hit.getEntity();
    }

    private void cleanupExpired(long now) {
        Set<String> stale = new HashSet<>();
        LINKS.forEach((key, link) -> {
            if (link == null || link.isExpired(now) || link.targetCount() < 1) {
                stale.add(key);
            }
        });
        stale.forEach(LINKS::remove);
    }

    private MarkDescriptor resolveMarkDescriptor(List<String> lexemes) {
        if (lexemes == null || lexemes.isEmpty()) {
            return null;
        }
        List<String> tokens = new ArrayList<>(lexemes.size());
        for (String token : lexemes) {
            String sanitized = sanitize(token);
            if (sanitized != null && !sanitized.isBlank()) {
                tokens.add(sanitized);
            }
        }
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!"ligabis".equals(token)) {
                continue;
            }
            if (i + 1 < tokens.size() && "vertere".equals(tokens.get(i + 1))) {
                if (i + 2 < tokens.size()) {
                    return new MarkDescriptor(tokens.get(i + 2), Direction.ONE_WAY);
                }
                return null;
            }
            if (i + 1 < tokens.size()) {
                return new MarkDescriptor(tokens.get(i + 1), Direction.BIDIRECTIONAL);
            }
        }
        return null;
    }

    private String resolvePersistentMark(LinkTarget target) {
        if (target instanceof LivingTarget living && living.entity() != null) {
            return MarkHelper.markForEntity(living.entity()).orElse(null);
        }
        if (target instanceof ItemTarget itemTarget && itemTarget.entity() != null) {
            return MarkHelper.markForEntity(itemTarget.entity()).orElse(null);
        }
        if (target instanceof MovingBlockTarget moving && moving.entity() != null) {
            return MarkHelper.markForEntity(moving.entity()).orElse(null);
        }
        if (target instanceof FrameTarget frameTarget && frameTarget.entity() != null) {
            return MarkHelper.markForEntity(frameTarget.entity())
                    .or(() -> MarkHelper.markForItem(scrollItem(frameTarget.entity())))
                    .orElse(null);
        }
        if (target instanceof BlockTarget blockTarget) {
            ServerLevel level = blockTarget.level();
            BlockPos pos = blockTarget.pos();
            if (level != null && pos != null) {
                return MarkHelper.markForBlock(level, pos).orElse(null);
            }
        }
        return null;
    }

    private String sanitize(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public enum Direction {
        BIDIRECTIONAL,
        ONE_WAY
    }

    private record MarkDescriptor(String mark, Direction direction) { }

    private enum LigabisAspect {
        FIRMO("durabilidade/saturacao"),
        AURA("movimento"),
        IGNI("temperatura"),
        AQUA("condicoes/folego");

        private final String display;

        LigabisAspect(String display) {
            this.display = display;
        }

        String displayName() {
            return display;
        }

        static LigabisAspect fromElement(VitaElement element) {
            if (element == null) {
                return null;
            }
            return switch (element) {
                case FIRMO -> FIRMO;
                case AURA -> AURA;
                case IGNI -> IGNI;
                case AQUA -> AQUA;
                default -> null;
            };
        }
    }

    private static final class LigabisLink {
        private final String mark;
        private final LigabisAspect aspect;
        private Direction direction;
        private final List<LinkTarget> targets = new ArrayList<>();
        private LinkTarget master;
        private long expiryTick;
        private boolean active;
        private FirmoSnapshot lastFirmoSnapshot = FirmoSnapshot.EMPTY;
        private IgniSnapshot lastIgniSnapshot = IgniSnapshot.EMPTY;
        private AquaSnapshot lastAquaSnapshot = AquaSnapshot.EMPTY;
        private Vec3 lastMasterPos;
        private Vec3 masterOriginPos;
        private Vec3 lastMasterDelta = Vec3.ZERO;
        private final Map<LinkTarget, BlockPos> blockOrigins = new HashMap<>();
        private final Map<MovingBlockTarget, StoredBlock> ghostBlocks = new HashMap<>();
        private BlockState lastMasterBlockState;

        private LigabisLink(String mark, LigabisAspect aspect, Direction direction, long expiryTick) {
            this.mark = Objects.requireNonNull(mark, "mark");
            this.aspect = Objects.requireNonNull(aspect, "aspect");
            this.direction = direction == null ? Direction.BIDIRECTIONAL : direction;
            this.expiryTick = expiryTick;
        }

        LigabisAspect aspect() {
            return aspect;
        }

        void setDirection(Direction direction) {
            this.direction = direction == null ? Direction.BIDIRECTIONAL : direction;
        }

        int targetCount() {
            return targets.size();
        }

        boolean isExpired(long now) {
            return now >= expiryTick;
        }

        void extend(long newExpiry) {
            expiryTick = Math.max(expiryTick, newExpiry);
        }

        boolean addTarget(LinkTarget target) {
            if (target == null) {
                return false;
            }
            if (targets.contains(target)) {
                return false;
            }
            if (master == null || !master.isValid()) {
                master = target;
                masterOriginPos = initialPosition(target);
                if (target instanceof BlockTarget blockTarget) {
                    BlockPos pos = blockTarget.pos();
                    ServerLevel level = blockTarget.level();
                    if (level != null && pos != null && level.isLoaded(pos)) {
                        lastMasterPos = Vec3.atCenterOf(pos);
                        lastMasterBlockState = level.getBlockState(pos);
                    }
                } else if (target instanceof LivingTarget living && living.entity() != null) {
                    lastMasterPos = living.entity().position();
                } else if (target instanceof ItemTarget item && item.entity() != null) {
                    lastMasterPos = item.entity().position();
                } else if (target instanceof MovingBlockTarget moving && moving.entity() != null) {
                    lastMasterPos = moving.entity().position();
                } else if (target instanceof FrameTarget frame && frame.entity() != null) {
                    lastMasterPos = frame.entity().position();
                }
            }
            targets.add(target);
            if (target instanceof BlockTarget blockTarget) {
                blockOrigins.put(target, blockTarget.pos());
            }
            return true;
        }

        void ensureActive(ServerLevel schedulerLevel) {
            if (active) {
                return;
            }
            active = true;
            scheduleTick(schedulerLevel);
        }

        void syncManager(ServerLevel level) {
            LigabisLinkManager.register(mark, direction, level, master, targets, expiryTick);
        }

        private void scheduleTick(ServerLevel level) {
            SpellEffects.schedule(level, 1, () -> tick(level));
        }

        private void tick(ServerLevel schedulerLevel) {
            long now = schedulerLevel.getGameTime();

            targets.removeIf(target -> target == null || !target.isValid());
            blockOrigins.keySet().removeIf(target -> target == null || !targets.contains(target));
            ghostBlocks.keySet().removeIf(target -> target == null || !targets.contains(target));

            Vec3 masterDisplacement = sampleMasterDisplacement();
            Vec3 masterVelocity = sampleMasterVelocity(masterDisplacement);
            Vec3 masterCurrent = lastMasterPos;
            Vec3 masterOriginDelta = masterOriginPos != null && masterCurrent != null
                    ? masterCurrent.subtract(masterOriginPos)
                    : Vec3.ZERO;
            lastMasterDelta = masterOriginDelta;

            if (direction == Direction.ONE_WAY) {
                handleOneWayTick(schedulerLevel, now, masterDisplacement, masterVelocity, masterOriginDelta);
            } else {
                handleBidirectionalTick(schedulerLevel, now, masterDisplacement, masterOriginDelta);
            }

            LigabisLinkManager.cleanup(now);
        }

        private void handleOneWayTick(ServerLevel schedulerLevel, long now, Vec3 masterDisplacement, Vec3 masterVelocity, Vec3 masterOriginDelta) {
            if (master == null || !master.isValid()) {
                killFollowers();
                releaseAll();
                active = false;
                return;
            }
            if (master instanceof BlockTarget blockMaster) {
                ServerLevel level = blockMaster.level();
                BlockPos pos = blockMaster.pos();
                if (level == null || pos == null || !level.isLoaded(pos) || level.getBlockState(pos).isAir()) {
                    killFollowers();
                    releaseAll();
                    active = false;
                    return;
                }
            }
            if (targets.size() < 2 || now >= expiryTick) {
                releaseAll();
                active = false;
                return;
            }

            switch (aspect) {
                case FIRMO -> syncFirmoOneWay();
                case AURA -> syncAuraOneWay(masterDisplacement, masterVelocity, masterOriginDelta);
                case IGNI -> syncIgniOneWay();
                case AQUA -> syncAquaOneWay();
                default -> {
                }
            }
            if (active) {
                scheduleTick(schedulerLevel);
            }
        }

        private ServerLevel masterLevel() {
            if (master instanceof BlockTarget blockTarget) {
                return blockTarget.level();
            }
            if (master instanceof FrameTarget frameTarget && frameTarget.entity() != null) {
                return (ServerLevel) frameTarget.entity().level();
            }
            if (master instanceof LivingTarget livingTarget && livingTarget.entity() != null) {
                return (ServerLevel) livingTarget.entity().level();
            }
            if (master instanceof ItemTarget itemTarget && itemTarget.entity() != null) {
                return (ServerLevel) itemTarget.entity().level();
            }
            if (master instanceof MovingBlockTarget movingBlockTarget && movingBlockTarget.entity() != null) {
                return (ServerLevel) movingBlockTarget.entity().level();
            }
            return null;
        }

        private void handleBidirectionalTick(ServerLevel schedulerLevel, long now, Vec3 masterDisplacement, Vec3 masterOriginDelta) {
            boolean blockBroken = false;
            boolean entityGone = false;
            boolean itemGone = false;
            boolean movingBlockGone = false;

            for (LinkTarget target : targets) {
                if (target instanceof BlockTarget blockTarget) {
                    if (!blockTarget.isValid() || blockTarget.level().getBlockState(blockTarget.pos()).isAir()) {
                        blockBroken = true;
                    }
                } else if (target instanceof LivingTarget livingTarget) {
                    if (!livingTarget.isValid()) {
                        entityGone = true;
                    }
                } else if (target instanceof ItemTarget itemTarget) {
                    if (!itemTarget.isValid()) {
                        itemGone = true;
                    }
                } else if (target instanceof MovingBlockTarget movingBlockTarget) {
                    if (!movingBlockTarget.isValid()) {
                        movingBlockGone = true;
                    }
                } else if (target instanceof FrameTarget frameTarget) {
                    if (!frameTarget.isValid()) {
                        itemGone = true;
                    }
                }
            }

            if (blockBroken || entityGone || itemGone || movingBlockGone) {
                propagateFailure(true);
                targets.removeIf(target -> target == null || !target.isValid());
                releaseAll();
                active = false;
                return;
            }

            targets.removeIf(target -> target == null || !target.isValid());
            if (targets.size() < 2 || now >= expiryTick) {
                releaseAll();
                active = false;
                return;
            }

            switch (aspect) {
                case FIRMO -> syncFirmo();
                case AURA -> syncAura(masterDisplacement, masterOriginDelta);
                case IGNI -> syncIgni();
                case AQUA -> syncAqua();
                default -> {
                }
            }
            if (active) {
                scheduleTick(schedulerLevel);
            }
        }

        private void propagateFailure(boolean includeMaster) {
            for (LinkTarget target : targets) {
                if (target == null) {
                    continue;
                }
                if (!includeMaster && target == master) {
                    continue;
                }
                if (target instanceof BlockTarget blockTarget) {
                    ServerLevel level = blockTarget.level();
                    BlockPos pos = blockTarget.pos();
                    if (level != null && pos != null && level.isLoaded(pos)) {
                        level.destroyBlock(pos, false);
                    }
                } else if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    if (entity != null && entity.isAlive()) {
                        entity.hurt(entity.damageSources().generic(), Float.MAX_VALUE);
                    }
                } else if (target instanceof ItemTarget itemTarget) {
                    ItemEntity entity = itemTarget.entity();
                    if (entity != null && entity.isAlive()) {
                        entity.discard();
                    }
                } else if (target instanceof MovingBlockTarget movingBlockTarget) {
                    FallingBlockEntity entity = movingBlockTarget.entity();
                    if (entity != null && entity.isAlive()) {
                        entity.discard();
                    }
                } else if (target instanceof FrameTarget frameTarget) {
                    Entity entity = frameTarget.entity();
                    if (entity != null && entity.isAlive()) {
                        entity.discard();
                    }
                }
            }
        }

        private void killFollowers() {
            propagateFailure(false);
            restoreGhosts(false);
        }

        private void removeSilently(LivingEntity entity) {
            if (entity == null) {
                return;
            }
            if (entity instanceof ServerPlayer player) {
                player.setHealth(0.0F);
                player.discard();
                return;
            }
            entity.remove(Entity.RemovalReason.KILLED);
        }

        private void releaseAll() {
            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    livingTarget.release();
                }
            }
            restoreGhosts(true);
        }

        private void syncFirmo() {
            double totalFood = 0.0D;
            double totalSaturation = 0.0D;
            int playerCount = 0;
            float totalHealth = 0.0F;
            int livingCount = 0;

            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    totalHealth += entity.getHealth();
                    livingCount++;
                    if (entity instanceof ServerPlayer player) {
                        FoodData food = player.getFoodData();
                        totalFood += food.getFoodLevel();
                        totalSaturation += food.getSaturationLevel();
                        playerCount++;
                    }
                }
            }

            double avgFood = playerCount > 0 ? totalFood / playerCount : -1.0D;
            float avgSat = playerCount > 0 ? (float) (totalSaturation / playerCount) : -1.0F;
            float avgHealth = livingCount > 0 ? totalHealth / livingCount : -1.0F;

            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    if (avgHealth > 0.0F) {
                        float clamped = Mth.clamp(avgHealth, 0.0F, entity.getMaxHealth());
                        entity.setHealth(clamped);
                    }
                    if (entity instanceof ServerPlayer player && avgFood >= 0.0D) {
                        FoodData food = player.getFoodData();
                        int foodLevel = Mth.clamp((int) Math.round(avgFood), 0, 20);
                        food.setFoodLevel(foodLevel);
                        if (avgSat >= 0.0F) {
                            float saturation = Mth.clamp(avgSat, 0.0F, (float) foodLevel);
                            food.setSaturation(saturation);
                        }
                    }
                }
            }
        }

        private void syncFirmoOneWay() {
            if (master == null) {
                return;
            }
            if (master instanceof LivingTarget livingMaster) {
                LivingEntity source = livingMaster.entity();
                if (source == null) {
                    return;
                }
                float sourceHealth = source.getHealth();
                int sourceFood = source instanceof ServerPlayer player ? player.getFoodData().getFoodLevel() : -1;
                float sourceSat = source instanceof ServerPlayer player ? player.getFoodData().getSaturationLevel() : -1.0F;

                FirmoSnapshot snapshot = new FirmoSnapshot(sourceHealth, sourceFood, sourceSat);
                if (snapshot.equals(lastFirmoSnapshot)) {
                    return;
                }
                lastFirmoSnapshot = snapshot;

                for (LinkTarget target : targets) {
                    if (target == master) {
                        continue;
                    }
                    if (target instanceof LivingTarget livingTarget) {
                        LivingEntity entity = livingTarget.entity();
                        if (entity == null) {
                            continue;
                        }
                        entity.setHealth(Mth.clamp(sourceHealth, 0.0F, entity.getMaxHealth()));
                        if (entity instanceof ServerPlayer player) {
                            FoodData food = player.getFoodData();
                            if (sourceFood >= 0) {
                                int foodLevel = Mth.clamp(sourceFood, 0, 20);
                                food.setFoodLevel(foodLevel);
                                if (sourceSat >= 0.0F) {
                                    float sat = Mth.clamp(sourceSat, 0.0F, (float) foodLevel);
                                    food.setSaturation(sat);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void syncAura(Vec3 masterDisplacement, Vec3 masterOriginDelta) {
            Vec3 total = Vec3.ZERO;
            int movers = 0;
            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    total = total.add(livingTarget.sampleVelocity());
                    movers++;
                } else if (target instanceof ItemTarget itemTarget) {
                    total = total.add(itemTarget.sampleVelocity());
                    movers++;
                } else if (target instanceof MovingBlockTarget movingBlockTarget) {
                    total = total.add(movingBlockTarget.sampleVelocity());
                    movers++;
                } else if (target instanceof BlockTarget) {
                    // blocks have no velocity; ignore contribution
                }
            }
            Vec3 average = movers > 0 ? total.scale(1.0D / movers) : Vec3.ZERO;

            BlockPos masterDeltaBlocks = toBlockDelta(masterOriginDelta);
            for (int i = 0; i < targets.size(); i++) {
                LinkTarget target = targets.get(i);
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    entity.setDeltaMovement(average);
                    entity.hurtMarked = true;
                } else if (target instanceof ItemTarget itemTarget) {
                    itemTarget.entity().setDeltaMovement(average);
                } else if (target instanceof MovingBlockTarget movingBlockTarget) {
                    BlockPos origin = originFor(movingBlockTarget);
                    if (origin != null) {
                        Vec3 desired = Vec3.atCenterOf(origin.offset(masterDeltaBlocks));
                        syncGhostBlock(movingBlockTarget, desired);
                    }
                } else if (target instanceof BlockTarget blockTarget) {
                    if (masterDeltaBlocks.equals(BlockPos.ZERO)) {
                        continue;
                    }
                    BlockPos origin = originFor(blockTarget);
                    if (origin == null) {
                        continue;
                    }
                    Vec3 desiredPos = Vec3.atCenterOf(origin.offset(masterDeltaBlocks));
                    convertOrMoveGhost(i, blockTarget, desiredPos);
                }
            }
        }

        private Vec3 sampleMasterDisplacement() {
            Vec3 current = null;
            if (master instanceof LivingTarget living && living.entity() != null) {
                current = living.entity().position();
            } else if (master instanceof ItemTarget item && item.entity() != null) {
                current = item.entity().position();
            } else if (master instanceof MovingBlockTarget moving && moving.entity() != null) {
                current = moving.entity().position();
            } else if (master instanceof FrameTarget frame && frame.entity() != null) {
                current = frame.entity().position();
            } else if (master instanceof BlockTarget blockTarget) {
                BlockPos pos = blockTarget.pos();
                ServerLevel level = blockTarget.level();
                BlockTarget originalMaster = blockTarget;
                if (level != null && pos != null && level.isLoaded(pos)) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        lastMasterBlockState = state;
                        current = Vec3.atCenterOf(pos);
                    } else if (lastMasterBlockState != null) {
                        BlockPos relocated = findAdjacentBlock(level, pos, lastMasterBlockState);
                        if (relocated != null) {
                            BlockTarget updated = new BlockTarget(level, relocated);
                            master = updated;
                            int idx = targets.indexOf(originalMaster);
                            if (idx >= 0) {
                                targets.set(idx, updated);
                            }
                            BlockPos origin = blockOrigins.getOrDefault(originalMaster, relocated);
                            blockOrigins.remove(originalMaster);
                            blockOrigins.put(updated, origin);
                            current = Vec3.atCenterOf(relocated);
                        }
                    }
                }
                if (current == null && pos != null) {
                    current = Vec3.atCenterOf(pos);
                }
            }
            if (masterOriginPos == null && current != null) {
                masterOriginPos = current;
            }
            Vec3 displacement = lastMasterPos == null || current == null ? Vec3.ZERO : current.subtract(lastMasterPos);
            lastMasterPos = current;
            return displacement;
        }

        private Vec3 sampleMasterVelocity(Vec3 displacement) {
            Vec3 velocity = Vec3.ZERO;
            if (master instanceof LivingTarget living && living.entity() != null) {
                velocity = living.entity().getDeltaMovement();
            } else if (master instanceof ItemTarget item && item.entity() != null) {
                velocity = item.entity().getDeltaMovement();
            } else if (master instanceof MovingBlockTarget moving && moving.entity() != null) {
                velocity = moving.entity().getDeltaMovement();
            } else if (master instanceof FrameTarget) {
                velocity = Vec3.ZERO;
            }
            if (velocity.lengthSqr() <= 1.0E-6D) {
                velocity = displacement == null ? Vec3.ZERO : displacement;
            }
            return velocity;
        }

        private Vec3 initialPosition(LinkTarget target) {
            if (target instanceof LivingTarget living && living.entity() != null) {
                return living.entity().position();
            }
            if (target instanceof ItemTarget item && item.entity() != null) {
                return item.entity().position();
            }
            if (target instanceof MovingBlockTarget moving && moving.entity() != null) {
                return moving.entity().position();
            }
            if (target instanceof FrameTarget frame && frame.entity() != null) {
                return frame.entity().position();
            }
            if (target instanceof BlockTarget blockTarget) {
                BlockPos pos = blockTarget.pos();
                return pos == null ? null : Vec3.atCenterOf(pos);
            }
            return null;
        }

        private void syncAuraOneWay(Vec3 masterDisplacement, Vec3 masterVelocity, Vec3 masterOriginDelta) {
            if (master == null) {
                return;
            }
            Vec3 displacement = masterDisplacement == null ? Vec3.ZERO : masterDisplacement;
            Vec3 sourceVelocity = masterVelocity == null ? Vec3.ZERO : masterVelocity;
            BlockPos deltaPos = toBlockDelta(masterOriginDelta);
            for (int i = 0; i < targets.size(); i++) {
                LinkTarget target = targets.get(i);
                if (target == master) {
                    continue;
                }
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity follower = livingTarget.entity();
                    follower.setDeltaMovement(sourceVelocity);
                    follower.hurtMarked = true;
                    if (displacement.lengthSqr() > 1.0E-6D) {
                        follower.teleportTo(follower.getX() + displacement.x, follower.getY() + displacement.y, follower.getZ() + displacement.z);
                    }
                } else if (target instanceof ItemTarget itemTarget) {
                    ItemEntity item = itemTarget.entity();
                    item.setDeltaMovement(sourceVelocity);
                    if (displacement.lengthSqr() > 1.0E-6D) {
                        item.setPos(item.getX() + displacement.x, item.getY() + displacement.y, item.getZ() + displacement.z);
                    }
                } else if (target instanceof MovingBlockTarget movingBlock) {
                    BlockPos origin = originFor(movingBlock);
                    if (origin != null) {
                        Vec3 desired = Vec3.atCenterOf(origin.offset(deltaPos));
                        syncGhostBlock(movingBlock, desired);
                    }
                } else if (target instanceof BlockTarget blockTarget) {
                    if (deltaPos.equals(BlockPos.ZERO)) {
                        continue;
                    }
                    BlockPos origin = originFor(blockTarget);
                    if (origin == null) {
                        continue;
                    }
                    Vec3 desiredPos = Vec3.atCenterOf(origin.offset(deltaPos));
                    convertOrMoveGhost(i, blockTarget, desiredPos);
                }
            }
        }

        private void syncIgni() {
            int maxFireTicks = 0;
            int maxFreezeTicks = 0;

            BlockState canonicalBlock = null;
            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    maxFireTicks = Math.max(maxFireTicks, entity.getRemainingFireTicks());
                    maxFreezeTicks = Math.max(maxFreezeTicks, entity.getTicksFrozen());
                } else if (target instanceof ItemTarget itemTarget) {
                    maxFireTicks = Math.max(maxFireTicks, itemTarget.entity().getRemainingFireTicks());
                } else if (target instanceof BlockTarget blockTarget && canonicalBlock == null) {
                    canonicalBlock = blockTarget.level().getBlockState(blockTarget.pos());
                }
            }

            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    entity.setRemainingFireTicks(maxFireTicks);
                    entity.setTicksFrozen(maxFreezeTicks);
                    entity.hurtMarked = true;
                } else if (target instanceof ItemTarget itemTarget) {
                    itemTarget.entity().setRemainingFireTicks(maxFireTicks);
                } else if (target instanceof BlockTarget blockTarget && canonicalBlock != null) {
                    ServerLevel level = blockTarget.level();
                    BlockPos pos = blockTarget.pos();
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (isHot(canonicalBlock)) {
                        heatBlock(level, pos, state);
                    } else {
                        extinguishBlock(level, pos, state);
                    }
                }
            }
        }

        private void syncIgniOneWay() {
            if (master == null) {
                return;
            }
            int fireTicks = 0;
            int freezeTicks = 0;
            BlockState sourceState = null;
            boolean sourceHot = false;

            if (master instanceof LivingTarget livingMaster && livingMaster.isValid()) {
                LivingEntity entity = livingMaster.entity();
                fireTicks = entity.getRemainingFireTicks();
                freezeTicks = entity.getTicksFrozen();
                sourceHot = fireTicks > 0 || entity.isOnFire();
            } else if (master instanceof ItemTarget itemMaster && itemMaster.isValid()) {
                fireTicks = itemMaster.entity().getRemainingFireTicks();
                sourceHot = fireTicks > 0 || itemMaster.entity().isOnFire();
            } else if (master instanceof BlockTarget blockMaster && blockMaster.isValid()) {
                sourceState = blockMaster.level().getBlockState(blockMaster.pos());
                sourceHot = isHot(sourceState);
            } else {
                return;
            }

            IgniSnapshot snapshot = new IgniSnapshot(fireTicks, freezeTicks, sourceState == null ? null : sourceState.getBlock().builtInRegistryHolder().key());
            if (snapshot.equals(lastIgniSnapshot)) {
                return;
            }
            lastIgniSnapshot = snapshot;

            for (LinkTarget target : targets) {
                if (target == master) {
                    continue;
                }
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    if (sourceHot) {
                        entity.setRemainingFireTicks(fireTicks);
                        entity.setTicksFrozen(freezeTicks);
                        entity.hurtMarked = true;
                    } else {
                        entity.clearFire();
                        entity.setRemainingFireTicks(0);
                    }
                } else if (target instanceof ItemTarget itemTarget) {
                    if (sourceHot) {
                        itemTarget.entity().setRemainingFireTicks(fireTicks);
                    } else {
                        itemTarget.entity().setRemainingFireTicks(0);
                    }
                } else if (target instanceof BlockTarget blockTarget && sourceState != null) {
                    ServerLevel level = blockTarget.level();
                    BlockPos pos = blockTarget.pos();
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState current = level.getBlockState(pos);
                    if (sourceHot) {
                        heatBlock(level, pos, current);
                    } else {
                        extinguishBlock(level, pos, current);
                    }
                }
            }
        }

        private void syncAqua() {
            int minAir = Integer.MAX_VALUE;
            Map<MobEffect, MobEffectInstance> mergedEffects = new HashMap<>();
            boolean hasLiving = false;

            BlockState canonicalBlock = null;
            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    hasLiving = true;
                    minAir = Math.min(minAir, entity.getAirSupply());
                    for (MobEffectInstance instance : entity.getActiveEffects()) {
                        mergedEffects.merge(instance.getEffect(),
                                new MobEffectInstance(instance),
                                (left, right) -> right.getAmplifier() > left.getAmplifier() || right.getDuration() > left.getDuration()
                                        ? right
                                        : left);
                    }
                } else if (target instanceof BlockTarget blockTarget && canonicalBlock == null) {
                    canonicalBlock = blockTarget.level().getBlockState(blockTarget.pos());
                }
            }

            for (LinkTarget target : targets) {
                if (target instanceof LivingTarget livingTarget && hasLiving) {
                    LivingEntity entity = livingTarget.entity();
                    if (minAir != Integer.MAX_VALUE) {
                        entity.setAirSupply(minAir);
                    }
                    syncEffects(entity, mergedEffects);
                } else if (target instanceof BlockTarget blockTarget && canonicalBlock != null) {
                    ServerLevel level = blockTarget.level();
                    BlockPos pos = blockTarget.pos();
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    boolean canonicalWater = isWet(canonicalBlock);
                    if (canonicalWater) {
                        applyWater(level, pos, state);
                    } else {
                        clearWater(level, pos, state);
                    }
                }
            }
        }

        private void syncAquaOneWay() {
            if (master == null) {
                return;
            }
            Integer air = null;
            Map<MobEffect, MobEffectInstance> desiredEffects = new HashMap<>();
            BlockState sourceState = null;

            if (master instanceof LivingTarget livingMaster && livingMaster.isValid()) {
                LivingEntity entity = livingMaster.entity();
                air = entity.getAirSupply();
                for (MobEffectInstance instance : entity.getActiveEffects()) {
                    desiredEffects.put(instance.getEffect(), new MobEffectInstance(instance));
                }
            } else if (master instanceof BlockTarget blockMaster && blockMaster.isValid()) {
                sourceState = blockMaster.level().getBlockState(blockMaster.pos());
            } else {
                return;
            }

            AquaSnapshot snapshot = new AquaSnapshot(air, sourceState == null ? null : sourceState.getBlock().builtInRegistryHolder().key(), desiredEffects.keySet());
            if (snapshot.equals(lastAquaSnapshot)) {
                return;
            }
            lastAquaSnapshot = snapshot;

            for (LinkTarget target : targets) {
                if (target == master) {
                    continue;
                }
                if (target instanceof LivingTarget livingTarget) {
                    LivingEntity entity = livingTarget.entity();
                    if (air != null) {
                        entity.setAirSupply(air);
                    }
                    if (!desiredEffects.isEmpty()) {
                        syncEffects(entity, desiredEffects);
                    }
                } else if (target instanceof BlockTarget blockTarget && sourceState != null) {
                    ServerLevel level = blockTarget.level();
                    BlockPos pos = blockTarget.pos();
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    boolean sourceWet = isWet(sourceState);
                    if (sourceWet) {
                        applyWater(level, pos, state);
                    } else {
                        clearWater(level, pos, state);
                    }
                }
            }
        }

        private record FirmoSnapshot(float health, int food, float saturation) {
            static final FirmoSnapshot EMPTY = new FirmoSnapshot(-1.0F, -1, -1.0F);
        }

        private record IgniSnapshot(int fire, int freeze, net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> blockKey) {
            static final IgniSnapshot EMPTY = new IgniSnapshot(0, 0, null);
        }

        private record AquaSnapshot(Integer air, net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> blockKey, Set<MobEffect> effects) {
            static final AquaSnapshot EMPTY = new AquaSnapshot(null, null, Set.of());
        }

        private void syncEffects(LivingEntity entity, Map<MobEffect, MobEffectInstance> desired) {
            Set<MobEffect> desiredKeys = desired.keySet();
            List<MobEffectInstance> active = new ArrayList<>(entity.getActiveEffects());
            for (MobEffectInstance current : active) {
                if (!desiredKeys.contains(current.getEffect())) {
                    entity.removeEffect(current.getEffect());
                }
            }
            for (MobEffectInstance effect : desired.values()) {
                MobEffectInstance existing = entity.getEffect(effect.getEffect());
                if (existing == null
                        || existing.getAmplifier() < effect.getAmplifier()
                        || existing.getDuration() + 10 < effect.getDuration()) {
                    entity.addEffect(new MobEffectInstance(effect));
                }
            }
        }

        private boolean isHot(BlockState state) {
            if (state == null) {
                return false;
            }
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
                return true;
            }
            if (state.hasProperty(BlockStateProperties.LIT)) {
                return Boolean.TRUE.equals(state.getValue(BlockStateProperties.LIT));
            }
            return false;
        }

        private void heatBlock(ServerLevel level, BlockPos pos, BlockState current) {
            if (current.hasProperty(BlockStateProperties.LIT)) {
                if (!current.getValue(BlockStateProperties.LIT)) {
                    level.setBlock(pos, current.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
                }
                return;
            }
            if (current.isAir()) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            BlockPos above = pos.above();
            if (level.isEmptyBlock(above)) {
                level.setBlock(above, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        private void extinguishBlock(ServerLevel level, BlockPos pos, BlockState current) {
            if (current.hasProperty(BlockStateProperties.LIT) && current.getValue(BlockStateProperties.LIT)) {
                level.setBlock(pos, current.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
                return;
            }
            if (current.is(Blocks.FIRE) || current.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(pos, false);
            }
        }

        private boolean isWet(BlockState state) {
            if (state == null) {
                return false;
            }
            FluidState fluid = state.getFluidState();
            if (fluid != null && fluid.isSourceOfType(Fluids.WATER)) {
                return true;
            }
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                return Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED));
            }
            return false;
        }

        private void applyWater(ServerLevel level, BlockPos pos, BlockState current) {
            if (current.hasProperty(BlockStateProperties.WATERLOGGED)) {
                if (!current.getValue(BlockStateProperties.WATERLOGGED)) {
                    level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
                }
                return;
            }
            if (current.isAir()) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        private void clearWater(ServerLevel level, BlockPos pos, BlockState current) {
            if (current.hasProperty(BlockStateProperties.WATERLOGGED) && current.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
                return;
            }
            FluidState fluid = current.getFluidState();
            if (fluid != null && fluid.isSourceOfType(Fluids.WATER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        private BlockPos toBlockDelta(Vec3 displacement) {
            if (displacement == null) {
                return BlockPos.ZERO;
            }
            int dx = (int) Math.round(displacement.x);
            int dy = (int) Math.round(displacement.y);
            int dz = (int) Math.round(displacement.z);
            if (dx == 0 && dy == 0 && dz == 0) {
                return BlockPos.ZERO;
            }
            return new BlockPos(dx, dy, dz);
        }

        private BlockPos findAdjacentBlock(ServerLevel level, BlockPos origin, BlockState state) {
            if (level == null || origin == null || state == null) {
                return null;
            }
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos candidate = origin.relative(direction);
                if (!level.isLoaded(candidate)) {
                    continue;
                }
                if (state.equals(level.getBlockState(candidate))) {
                    return candidate;
                }
            }
            return null;
        }

        private BlockPos originFor(LinkTarget target) {
            if (target == null) {
                return null;
            }
            BlockPos origin = blockOrigins.get(target);
            if (origin == null && target instanceof BlockTarget blockTarget) {
                origin = blockTarget.pos();
                blockOrigins.put(target, origin);
            }
            return origin;
        }

        private void convertOrMoveGhost(int index, BlockTarget blockTarget, Vec3 desiredPos) {
            if (blockTarget == null || desiredPos == null) {
                return;
            }
            ServerLevel level = blockTarget.level();
            if (level == null) {
                return;
            }
            BlockPos pos = blockTarget.pos();
            if (pos == null || !level.isLoaded(pos)) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            BlockEntity be = level.getBlockEntity(pos);
            CompoundTag tag = be != null ? be.saveWithFullMetadata() : null;

            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
            if (entity == null) {
                return;
            }
            entity.setNoGravity(true);
            entity.setHurtsEntities(0.0F, 0);
            entity.dropItem = false;
            entity.time = 1;
            entity.setPos(desiredPos.x, desiredPos.y, desiredPos.z);

            MovingBlockTarget moving = new MovingBlockTarget(entity);
            BlockPos origin = originFor(blockTarget);
            blockOrigins.remove(blockTarget);
            if (origin != null) {
                blockOrigins.put(moving, origin);
            }
            ghostBlocks.put(moving, new StoredBlock(state, tag, origin == null ? pos : origin));
            targets.set(index, moving);
            if (master == blockTarget) {
                master = moving;
            }
        }

        private void syncGhostBlock(MovingBlockTarget target, Vec3 desiredPos) {
            if (target == null || desiredPos == null) {
                return;
            }
            FallingBlockEntity entity = target.entity();
            if (entity == null) {
                return;
            }
            entity.setNoGravity(true);
            entity.setHurtsEntities(0.0F, 0);
            entity.dropItem = false;
            entity.time = 1;
            entity.setPos(desiredPos.x, desiredPos.y, desiredPos.z);
            target.captureState();
        }

        private void restoreGhosts(boolean placeBlocks) {
            for (Map.Entry<MovingBlockTarget, StoredBlock> entry : ghostBlocks.entrySet()) {
                MovingBlockTarget target = entry.getKey();
                StoredBlock stored = entry.getValue();
                FallingBlockEntity entity = target.entity();
                if (entity != null) {
                    if (placeBlocks && stored != null && entity.level() instanceof ServerLevel level) {
                        BlockPos delta = toBlockDelta(lastMasterDelta);
                        BlockPos restorePos = stored.origin().offset(delta);
                        level.removeBlockEntity(restorePos);
                        level.setBlock(restorePos, stored.state(), Block.UPDATE_ALL);
                        if (stored.tag() != null) {
                            BlockEntity newBe = level.getBlockEntity(restorePos);
                            if (newBe != null) {
                                newBe.load(stored.tag());
                                newBe.setChanged();
                            }
                        }
                    }
                    entity.discard();
                }
            }
            ghostBlocks.clear();
        }

    }

    private record StoredBlock(BlockState state, CompoundTag tag, BlockPos origin) { }

    public sealed interface LinkTarget permits BlockTarget, LivingTarget, ItemTarget, MovingBlockTarget, FrameTarget {
        boolean isValid();

        static LinkTarget fromImpact(ServerPlayer caster, SpellEffects.SpellImpact impact) {
            if (impact == null) {
                return caster == null ? null : new LivingTarget(caster);
            }
            Entity entity = impact.entity();
            if (entity instanceof ItemFrame || entity instanceof PlacedScrollEntity) {
                return new FrameTarget(entity);
            }
            if (entity instanceof LivingEntity living) {
                return new LivingTarget(living);
            }
            if (entity instanceof ItemEntity itemEntity) {
                return new ItemTarget(itemEntity);
            }
            BlockPos pos = impact.blockPos();
            if (pos != null && caster != null) {
                return new BlockTarget(caster.serverLevel(), pos.immutable());
            }
            return caster == null ? null : new LivingTarget(caster);
        }
        }

        public record BlockTarget(ServerLevel level, BlockPos pos) implements LinkTarget {
            @Override
            public boolean isValid() {
                return level != null
                        && pos != null
                        && level.isLoaded(pos)
                        && !level.getBlockState(pos).isAir();
            }
        }

    public static final class LivingTarget implements LinkTarget {
        private LivingEntity entity;
        private Vec3 lastKnownPosition;
        private Boolean prevNoAi;

        private LivingTarget(LivingEntity entity) {
            this.entity = entity;
            suspendAi();
            captureState();
        }

        LivingEntity entity() {
            return entity;
        }

        void captureState() {
            if (entity != null && !entity.isRemoved()) {
                lastKnownPosition = entity.position();
            }
        }

        Vec3 sampleVelocity() {
            if (entity == null) {
                return Vec3.ZERO;
            }
            Vec3 current = entity.position();
            Vec3 delta = lastKnownPosition == null ? entity.getDeltaMovement() : current.subtract(lastKnownPosition);
            lastKnownPosition = current;
            return delta;
        }

        void release() {
            restoreAi();
        }

        boolean ensureAlive(ServerLevel level) {
            if (entity == null || level == null) {
                return false;
            }
            if (entity.isRemoved()) {
                return false;
            }
            if (entity.isDeadOrDying()) {
                float healAmount = Math.max(1.0F, entity.getMaxHealth() * 0.5F);
                entity.setHealth(healAmount);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.fallDistance = 0.0F;
                entity.invulnerableTime = 20;
                resetDeathTime(entity);
                applyTotemBuffs(entity);
            }
            captureState();
            return true;
        }

        @Override
        public boolean isValid() {
            return entity != null && entity.isAlive() && !entity.isRemoved();
        }

        private void applyTotemBuffs(LivingEntity target) {
            target.removeAllEffects();
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1));
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0));
        }

        private void suspendAi() {
            if (entity instanceof Mob mob) {
                prevNoAi = mob.isNoAi();
                mob.setNoAi(true);
                mob.getNavigation().stop();
            }
        }

        private void restoreAi() {
            if (entity instanceof Mob mob && prevNoAi != null) {
                mob.setNoAi(prevNoAi);
            }
        }
    }

    private static Field deathTimeField;
    private static boolean deathTimeFieldResolved = false;

    private static void resetDeathTime(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        if (!deathTimeFieldResolved) {
            try {
                Field field = LivingEntity.class.getDeclaredField("deathTime");
                field.setAccessible(true);
                deathTimeField = field;
            } catch (NoSuchFieldException exception) {
                // obfuscated name fallback
                try {
                    Field field = LivingEntity.class.getDeclaredField("f_20915_");
                    field.setAccessible(true);
                    deathTimeField = field;
                } catch (NoSuchFieldException ignored) {
                    deathTimeField = null;
                }
            }
            deathTimeFieldResolved = true;
        }
        if (deathTimeField != null) {
            try {
                deathTimeField.setInt(entity, 0);
            } catch (IllegalAccessException ignored) {
                // ignore silently
            }
        }
    }

    public static final class ItemTarget implements LinkTarget {
        private final ItemEntity entity;
        private Vec3 lastKnownPosition;

        private ItemTarget(ItemEntity entity) {
            this.entity = entity;
            captureState();
        }

        ItemEntity entity() {
            return entity;
        }

        void captureState() {
            if (entity != null && !entity.isRemoved()) {
                lastKnownPosition = entity.position();
            }
        }

        Vec3 sampleVelocity() {
            if (entity == null) {
                return Vec3.ZERO;
            }
            Vec3 current = entity.position();
            Vec3 delta = lastKnownPosition == null ? entity.getDeltaMovement() : current.subtract(lastKnownPosition);
            lastKnownPosition = current;
            return delta;
        }

        @Override
        public boolean isValid() {
            return entity != null && entity.isAlive() && !entity.isRemoved();
        }
    }

    public static final class MovingBlockTarget implements LinkTarget {
        private final FallingBlockEntity entity;
        private Vec3 lastKnownPosition;

        private MovingBlockTarget(FallingBlockEntity entity) {
            this.entity = entity;
            captureState();
        }

        FallingBlockEntity entity() {
            return entity;
        }

        void captureState() {
            if (entity != null && !entity.isRemoved()) {
                lastKnownPosition = entity.position();
            }
        }

        Vec3 sampleVelocity() {
            if (entity == null) {
                return Vec3.ZERO;
            }
            Vec3 current = entity.position();
            Vec3 delta = lastKnownPosition == null ? entity.getDeltaMovement() : current.subtract(lastKnownPosition);
            lastKnownPosition = current;
            return delta;
        }

        @Override
        public boolean isValid() {
            return entity != null && entity.isAlive() && !entity.isRemoved();
        }
    }

    public static final class FrameTarget implements LinkTarget {
        private final Entity entity;

        private FrameTarget(Entity entity) {
            this.entity = entity;
        }

        Entity entity() {
            return entity;
        }

        @Override
        public boolean isValid() {
            return entity != null && entity.isAlive() && !entity.isRemoved();
        }
    }

    private static net.minecraft.world.item.ItemStack scrollItem(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (entity instanceof PlacedScrollEntity placedScroll) {
            return placedScroll.getScroll();
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}

package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.vita.ElementAffinityService;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repels nearby entities away from the caster, optionally filtered by the preceding element.
 */
public final class ImpediuntFunctionHandler implements SpellFunctionHandler {

    private static final double DEFAULT_RADIUS = 3.0D;
    private static final double PUSH_SPEED = 0.35D;
    private static final double VERTICAL_BOOST = 0.2D;
    private static final double MIN_DISTANCE_SQ = 1.0E-4D;

    private final Gateway gateway;

    public ImpediuntFunctionHandler() {
        this(new ServerGateway());
    }

    ImpediuntFunctionHandler(Gateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!gateway.isPlayerValid(player)) {
            return;
        }
        Optional<SpellAction> action = context.currentAction();
        if (action.flatMap(SpellAction::subjectMark).isPresent()) {
            // m1 impediunt: the marked thing is pushed away from the mage (or from the ubis place).
            MarkSpells.push(context, action.get().subjectMark().get(), action.get().place(), MarkSpells.Push.AWAY_FROM_CASTER,
                    action.get().quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY), Chronos.window(action.get()));
            return;
        }

        Vec3 origin = gateway.resolveOrigin(player);
        List<Target> targets = gateway.findTargets(player, DEFAULT_RADIUS);
        if (targets.isEmpty()) {
            gateway.notifyNoTargets(player);
            return;
        }

        VitaElement filterElement = resolveFilterElement(context, element);
        int affected = 0;
        for (Target target : targets) {
            if (!target.matches(filterElement)) {
                continue;
            }
            Vec3 impulse = computeImpulse(origin, target.position());
            if (impulse.lengthSqr() <= MIN_DISTANCE_SQ) {
                continue;
            }
            target.push(impulse.x, VERTICAL_BOOST, impulse.z);
            if (target.consumesSource() && filterElement != null && !filterElement.isBalanced()) {
                gateway.spawnSourceParticles(player, filterElement, context.elementRuneId(), target.position());
            }
            affected++;
        }

        if (affected == 0) {
            gateway.notifyNoMatchingTargets(player);
        } else {
            gateway.onRepel(player, filterElement, context.elementRuneId(), affected);
        }
    }

    private static VitaElement resolveFilterElement(SpellContext context, VitaElement provided) {
        if (provided != null && !provided.isBalanced()) {
            return provided;
        }
        VitaElement contextElement = context == null ? VitaElement.BALANCED : context.primaryElement();
        return contextElement == null ? VitaElement.BALANCED : contextElement;
    }

    static Vec3 computeImpulse(Vec3 origin, Vec3 targetPosition) {
        if (origin == null || targetPosition == null) {
            return Vec3.ZERO;
        }
        Vec3 delta = targetPosition.subtract(origin);
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        double lengthSq = horizontal.lengthSqr();
        if (lengthSq <= MIN_DISTANCE_SQ) {
            return Vec3.ZERO;
        }
        return horizontal.scale(1.0D / Math.sqrt(lengthSq)).scale(PUSH_SPEED);
    }

    interface Target {
        Vec3 position();

        void push(double x, double y, double z);

        boolean matches(VitaElement element);

        default boolean consumesSource() {
            return false;
        }
    }

    interface Gateway {
        boolean isPlayerValid(ServerPlayer player);

        Vec3 resolveOrigin(ServerPlayer player);

        List<Target> findTargets(ServerPlayer player, double radius);

        void notifyNoTargets(ServerPlayer player);

        void notifyNoMatchingTargets(ServerPlayer player);

        void onRepel(ServerPlayer player, VitaElement element, String elementRuneId, int affectedTargets);

        void spawnSourceParticles(ServerPlayer player, VitaElement element, String elementRuneId, Vec3 location);
    }

    private static final class ServerGateway implements Gateway {

        @Override
        public boolean isPlayerValid(ServerPlayer player) {
            return SpellEffects.isPlayerValid(player);
        }

        @Override
        public Vec3 resolveOrigin(ServerPlayer player) {
            return player == null ? Vec3.ZERO : player.position();
        }

        @Override
        public List<Target> findTargets(ServerPlayer player, double radius) {
            if (player == null) {
                return List.of();
            }
            ServerLevel level = player.serverLevel();
            AABB area = player.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area,
                    candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator());
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area,
                    candidate -> ElementAffinityService.hasItemAffinity(candidate.getItem()));
            List<Target> targets = new ArrayList<>(entities.size() + drops.size());
            for (LivingEntity entity : entities) {
                targets.add(new EntityTarget(entity));
            }
            for (ItemEntity drop : drops) {
                targets.add(new ItemTarget(drop));
            }
            collectBlockTargets(player, level, radius, targets);
            return targets.isEmpty() ? List.of() : Collections.unmodifiableList(targets);
        }

        private void collectBlockTargets(ServerPlayer player, ServerLevel level, double radius, List<Target> targets) {
            int search = Mth.ceil(radius);
            double radiusSq = radius * radius;
            BlockPos playerPos = player.blockPosition();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = -search; x <= search; x++) {
                for (int y = -search; y <= search; y++) {
                    for (int z = -search; z <= search; z++) {
                        cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                        double dx = (cursor.getX() + 0.5D) - player.getX();
                        double dy = (cursor.getY() + 0.5D) - player.getY();
                        double dz = (cursor.getZ() + 0.5D) - player.getZ();
                        if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSq) {
                            continue;
                        }
                        if (!level.isLoaded(cursor)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir() || level.getBlockEntity(cursor) != null) {
                            continue;
                        }
                        if (!ElementAffinityService.hasBlockAffinity(state)) {
                            continue;
                        }
                        targets.add(new BlockTarget(level, cursor.immutable(), state));
                    }
                }
            }
        }

        @Override
        public void notifyNoTargets(ServerPlayer player) {
            if (player == null) {
                return;
            }
            player.sendSystemMessage(Component.literal("Impediunt nao encontrou alvos."));
        }

        @Override
        public void notifyNoMatchingTargets(ServerPlayer player) {
            if (player == null) {
                return;
            }
            player.sendSystemMessage(Component.literal("Nenhum alvo alinhado ao elemento foi encontrado."));
        }

        @Override
        public void onRepel(ServerPlayer player, VitaElement element, String elementRuneId, int affectedTargets) {
            if (player == null) {
                return;
            }
            SpellEffects.spawnImpediuntPulse(player, element, elementRuneId);
            SpellEffects.playImpediuntSound(player);
        }

        @Override
        public void spawnSourceParticles(ServerPlayer player, VitaElement element, String elementRuneId, Vec3 location) {
            SpellEffects.spawnSourceDrift(player, element, elementRuneId, location);
        }
    }

    private record EntityTarget(LivingEntity entity) implements Target {

        @Override
        public Vec3 position() {
            return entity.position();
        }

        @Override
        public void push(double x, double y, double z) {
            entity.push(x, y, z);
        }

        @Override
        public boolean matches(VitaElement element) {
            return ElementAffinityService.matches(element, entity);
        }
    }

    private record ItemTarget(ItemEntity entity) implements Target {

        @Override
        public Vec3 position() {
            return entity.position();
        }

        @Override
        public void push(double x, double y, double z) {
            entity.push(x, y, z);
        }

        @Override
        public boolean matches(VitaElement element) {
            return ElementAffinityService.matchesItem(element, entity.getItem());
        }

        @Override
        public boolean consumesSource() {
            return true;
        }
    }

    private static final class BlockTarget implements Target {
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState state;

        private BlockTarget(ServerLevel level, BlockPos pos, BlockState state) {
            this.level = level;
            this.pos = pos;
            this.state = state;
        }

        @Override
        public Vec3 position() {
            return Vec3.atCenterOf(pos);
        }

        @Override
        public void push(double x, double y, double z) {
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                return;
            }
            if (current.getBlock() != state.getBlock()) {
                return;
            }
            if (current.getDestroySpeed(level, pos) < 0.0F) {
                return;
            }
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, current);
            if (falling != null) {
                double vertical = Math.max(y, 0.25D);
                falling.setDeltaMovement(x, vertical, z);
                falling.time = 1;
                return;
            }
            Block.popResource(level, pos, new ItemStack(current.getBlock()));
        }

        @Override
        public boolean matches(VitaElement element) {
            return ElementAffinityService.matchesBlock(element, state);
        }

        @Override
        public boolean consumesSource() {
            return true;
        }
    }
}

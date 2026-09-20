package com.elderlexicon.mod.spell.function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central registry for active Ligabis links so global events can query protection.
 */
public final class LigabisLinkManager {

    private static final Map<String, LinkData> LINKS_BY_MARK = new HashMap<>();
    private static final Map<UUID, String> MARK_BY_MEMBER = new HashMap<>();

    private LigabisLinkManager() {
    }

    static synchronized void register(String mark,
                                      LigabisFunctionHandler.Direction direction,
                                      ServerLevel level,
                                      LigabisFunctionHandler.LinkTarget master,
                                      java.util.List<LigabisFunctionHandler.LinkTarget> targets,
                                      long expiresAt) {
        if (mark == null || mark.isBlank() || level == null || master == null || targets == null) {
            return;
        }
        LinkData existing = LINKS_BY_MARK.get(mark);
        if (existing != null) {
            removeLink(existing);
        }

        LinkDescriptor descriptor = buildDescriptor(direction, level, master, targets, expiresAt);
        if (descriptor == null || descriptor.protectedMembers().isEmpty()) {
            return;
        }
        LinkData data = new LinkData(mark, descriptor);
        LINKS_BY_MARK.put(mark, data);
        for (UUID member : descriptor.allMembers()) {
            MARK_BY_MEMBER.put(member, mark);
        }
    }

    static synchronized void cleanup(long gameTime) {
        Set<String> stale = new HashSet<>();
        for (Map.Entry<String, LinkData> entry : LINKS_BY_MARK.entrySet()) {
            LinkData data = entry.getValue();
            if (data == null || data.expiresAt() <= gameTime) {
                stale.add(entry.getKey());
            }
        }
        for (String mark : stale) {
            LinkData removed = LINKS_BY_MARK.remove(mark);
            if (removed != null) {
                removed.allMembers().forEach(MARK_BY_MEMBER::remove);
            }
        }
    }

    public static synchronized LinkData find(Entity entity, long gameTime) {
        if (entity == null) {
            return null;
        }
        String mark = MARK_BY_MEMBER.get(entity.getUUID());
        if (mark == null) {
            return null;
        }
        LinkData data = LINKS_BY_MARK.get(mark);
        if (data == null) {
            MARK_BY_MEMBER.remove(entity.getUUID());
            return null;
        }
        if (data.expiresAt() <= gameTime) {
            removeLink(data);
            return null;
        }
        return data;
    }

    static synchronized LinkData find(LivingEntity entity, long gameTime) {
        return find((Entity) entity, gameTime);
    }

    static synchronized boolean shouldSuppressDrops(LivingEntity entity, long gameTime) {
        LinkData data = find(entity, gameTime);
        if (data == null) {
            return false;
        }
        Long procTick = data.lastProcTick(entity.getUUID());
        return procTick != null && procTick >= gameTime;
    }

    static boolean masterIsHot(LinkData link, ServerLevel level) {
        if (link == null || level == null) {
            return false;
        }
        if (link.masterIsBlock()) {
            BlockPos pos = link.masterBlock();
            if (pos == null || !level.isLoaded(pos)) {
                return false;
            }
            return isHotBlock(level.getBlockState(pos));
        }
        if (link.masterId() == null) {
            return false;
        }
        Entity candidate = level.getEntity(link.masterId());
        if (candidate instanceof LivingEntity living) {
            return living.getRemainingFireTicks() > 0 || living.isOnFire();
        }
        return false;
    }

    private static boolean isHotBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.is(net.minecraft.world.level.block.Blocks.FIRE)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                || state.is(net.minecraft.world.level.block.Blocks.LAVA)
                || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
            return true;
        }
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            return Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT));
        }
        return false;
    }

    static synchronized void removeLink(LinkData data) {
        if (data == null) {
            return;
        }
        LINKS_BY_MARK.remove(data.mark());
        data.allMembers().forEach(MARK_BY_MEMBER::remove);
    }

    private static LinkDescriptor buildDescriptor(LigabisFunctionHandler.Direction direction,
                                                  ServerLevel level,
                                                  LigabisFunctionHandler.LinkTarget masterTarget,
                                                  java.util.List<LigabisFunctionHandler.LinkTarget> targets,
                                                  long expiresAt) {
        boolean isBlockMaster = masterTarget instanceof LigabisFunctionHandler.BlockTarget;
        UUID masterId = null;
        BlockPos masterPos = null;
        if (masterTarget instanceof LigabisFunctionHandler.LivingTarget livingMaster) {
            LivingEntity masterEntity = livingMaster.entity();
            if (masterEntity == null) {
                return null;
            }
            masterId = masterEntity.getUUID();
        } else if (masterTarget instanceof LigabisFunctionHandler.FrameTarget frameMaster) {
            Entity masterEntity = frameMaster.entity();
            if (masterEntity == null) {
                return null;
            }
            masterId = masterEntity.getUUID();
        } else if (masterTarget instanceof LigabisFunctionHandler.BlockTarget blockTarget) {
            masterPos = blockTarget.pos();
        }

        Set<UUID> allMembers = new HashSet<>();
        Set<UUID> protectedMembers = new HashSet<>();
        for (LigabisFunctionHandler.LinkTarget target : targets) {
            if (target instanceof LigabisFunctionHandler.LivingTarget livingTarget) {
                LivingEntity ent = livingTarget.entity();
                if (ent != null) {
                    allMembers.add(ent.getUUID());
                }
            } else if (target instanceof LigabisFunctionHandler.FrameTarget frameTarget) {
                Entity ent = frameTarget.entity();
                if (ent != null) {
                    allMembers.add(ent.getUUID());
                }
            }
        }

        if (masterId != null) {
            allMembers.add(masterId);
        }

        if (direction == LigabisFunctionHandler.Direction.ONE_WAY) {
            for (UUID member : allMembers) {
                if (!member.equals(masterId)) {
                    protectedMembers.add(member);
                }
            }
        } else {
            protectedMembers.addAll(allMembers);
        }

        if (protectedMembers.isEmpty()) {
            return null;
        }

        return new LinkDescriptor(masterId,
                isBlockMaster,
                masterPos,
                level.dimension(),
                direction,
                expiresAt,
                allMembers,
                protectedMembers);
    }

    record LinkDescriptor(UUID masterId,
                          boolean masterIsBlock,
                          BlockPos masterBlock,
                          ResourceKey<Level> dimension,
                          LigabisFunctionHandler.Direction direction,
                          long expiresAt,
                          Set<UUID> allMembers,
                          Set<UUID> protectedMembers) {
    }

    public static final class LinkData {
        private final String mark;
        private final UUID masterId;
        private final boolean masterIsBlock;
        private final BlockPos masterBlock;
        private final ResourceKey<Level> dimension;
        private final LigabisFunctionHandler.Direction direction;
        private final long expiresAt;
        private final Set<UUID> allMembers;
        private final Set<UUID> protectedMembers;
        private final Map<UUID, Long> procTicks = new HashMap<>();

        LinkData(String mark, LinkDescriptor descriptor) {
            this.mark = mark;
            this.masterId = descriptor.masterId();
            this.masterIsBlock = descriptor.masterIsBlock();
            this.masterBlock = descriptor.masterBlock();
            this.dimension = descriptor.dimension();
            this.direction = descriptor.direction();
            this.expiresAt = descriptor.expiresAt();
            this.allMembers = Collections.unmodifiableSet(new HashSet<>(descriptor.allMembers()));
            this.protectedMembers = Collections.unmodifiableSet(new HashSet<>(descriptor.protectedMembers()));
        }

        String mark() {
            return mark;
        }

        public Set<UUID> allMembers() {
            return allMembers;
        }

        public Set<UUID> protectedMembers() {
            return protectedMembers;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public LigabisFunctionHandler.Direction direction() {
            return direction;
        }

        boolean isProtected(LivingEntity entity) {
            return entity != null && protectedMembers.contains(entity.getUUID());
        }

        boolean isMaster(LivingEntity entity) {
            return entity != null && masterId != null && masterId.equals(entity.getUUID());
        }

        public boolean isMaster(Entity entity) {
            return entity != null && masterId != null && masterId.equals(entity.getUUID());
        }

        public boolean isProtected(Entity entity) {
            return entity != null && protectedMembers.contains(entity.getUUID());
        }

        boolean masterIsBlock() {
            return masterIsBlock;
        }

        BlockPos masterBlock() {
            return masterBlock;
        }

        public UUID masterId() {
            return masterId;
        }

        boolean masterIntact(ServerLevel level) {
            if (level == null) {
                return false;
            }
            if (!level.dimension().equals(dimension)) {
                return false;
            }
            if (masterIsBlock) {
                if (masterBlock == null) {
                    return false;
                }
                if (!level.isLoaded(masterBlock)) {
                    return false;
                }
                BlockState state = level.getBlockState(masterBlock);
                return !state.isAir();
            }
            if (masterId == null) {
                return false;
            }
            Entity candidate = level.getEntity(masterId);
            return candidate != null && candidate.isAlive() && !candidate.isRemoved();
        }

        void markProc(UUID entityId, long gameTime) {
            if (entityId != null) {
                procTicks.put(entityId, gameTime);
            }
        }

        Long lastProcTick(UUID entityId) {
            return procTicks.get(entityId);
        }
    }
}

package com.elderlexicon.mod.ligabis.world;

import com.elderlexicon.mod.ExampleMod;
import com.elderlexicon.mod.ligabis.Aspect;
import com.elderlexicon.mod.ligabis.BlockIntegrity;
import com.elderlexicon.mod.ligabis.CostPolicy;
import com.elderlexicon.mod.ligabis.Effect;
import com.elderlexicon.mod.ligabis.Link;
import com.elderlexicon.mod.ligabis.LigabisGrammar;
import com.elderlexicon.mod.ligabis.LinkEngine;
import com.elderlexicon.mod.ligabis.LinkEvent;
import com.elderlexicon.mod.ligabis.LinkGraph;
import com.elderlexicon.mod.ligabis.MemberId;
import com.elderlexicon.mod.ligabis.MemberProfile;
import com.elderlexicon.mod.ligabis.Members;
import com.elderlexicon.mod.ligabis.Owners;
import com.elderlexicon.mod.ligabis.ReadingBond;
import com.elderlexicon.mod.ligabis.Vec;
import com.elderlexicon.mod.ligabis.world.golem.GolemEntity;
import com.elderlexicon.mod.ligabis.world.golem.GolemStructure;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spell.module.SpellCostModule;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Joins the Ligabis engine to the game for the firmo, igni, aqua and aura aspects: it keeps the marks of
 * loaded entities and of marked blocks in the engine's graph, feeds it damage, death, heat, breath,
 * wetness and motion, and carries out what it answers. One instance lives as long as the server.
 */
public final class LigabisManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ParserDictionary DICTIONARY = ParserDictionary.load();
    private static final ResourceKey<DamageType> REFLECTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ExampleMod.MODID, "ligabis_reflection"));
    private static final ResourceKey<DamageType> DEATH =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ExampleMod.MODID, "ligabis_death"));

    private static LigabisManager instance;

    private final MinecraftServer server;
    private final LigabisData data;
    private final LinkGraph graph = new LinkGraph();
    private final LinkEngine engine;
    private final Map<UUID, GolemRecord> golems = new HashMap<>();
    private final Map<UUID, Boolean> golemSwingState = new HashMap<>();
    private final Map<UUID, float[]> playerInputs = new HashMap<>();

    private LigabisManager(MinecraftServer server) {
        this.server = server;
        this.data = LigabisData.get(server);
        this.engine = new LinkEngine(graph, new WorldMembers(), new WorldOwners(), CostPolicy.DEFAULT);
    }

    /** The running manager, or null while no world is loaded. */
    @Nullable
    public static LigabisManager get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        LigabisManager manager = new LigabisManager(server);
        manager.load();
        instance = manager;
    }

    public static void stop() {
        instance = null;
    }

    private void load() {
        for (Link link : data.links()) {
            graph.addLink(link);
        }
        for (Map.Entry<MemberId, LigabisData.StoredBlock> entry : data.blocks().entrySet()) {
            graph.mark(entry.getKey(), entry.getValue().mark());
            engine.restoreWear(entry.getKey(), entry.getValue().wear());
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                entityJoined(entity);
            }
        }
        LOGGER.info("Ligabis: {} links and {} marked blocks loaded", graph.links().size(), data.blocks().size());
    }

    // ------------------------------------------------------------------ marks

    /** A living entity or dropped item entered the world (or its mark changed): tell the graph what it carries. */
    public void entityJoined(Entity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        if (isScrollCarrier(entity)) {
            rememberScroll(entity);
            return;
        }
        if (!isMarkable(entity)) {
            return;
        }
        MemberId id = MemberKeys.entity(entity.getUUID());
        graph.forget(id);
        data.removeEntity(entity.getUUID());
        MarkHelper.markForEntity(entity).ifPresent(mark -> graph.mark(id, mark));
    }

    /**
     * An entity left the loaded world (died, unloaded, changed dimension): it is no longer a member. If
     * only its chunk was unloaded, where it stood is remembered so a mark spell can still reach it.
     */
    public void entityLeft(Entity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        if (entity instanceof FallingBlockEntity falling) {
            landThrownBlock(falling);
            return;
        }
        if (isScrollCarrier(entity)) {
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason != null && reason.shouldDestroy()) {
                data.removeScroll(entity.getUUID()); // taken down or broken: the item keeps the mark, not this place
            } else {
                rememberScroll(entity); // only unloaded: remember where it waits
            }
            return;
        }
        if (!isMarkable(entity)) {
            return;
        }
        graph.forget(MemberKeys.entity(entity.getUUID()));
        if (entity.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            MarkHelper.markForEntity(entity).ifPresent(mark -> data.putEntity(entity.getUUID(), new LigabisData.StoredEntity(
                    mark, entity.level().dimension().location().toString(), entity.getX(), entity.getY(), entity.getZ(),
                    entity instanceof LivingEntity living ? living.getMaxHealth() : 0.0D)));
        }
    }

    /** A marked block thrown by a spell flies as a falling block; where it lands, it gets its mark back. */
    private static void landThrownBlock(FallingBlockEntity falling) {
        Entity.RemovalReason reason = falling.getRemovalReason();
        if (reason == null || !reason.shouldDestroy() || !(falling.level() instanceof ServerLevel level)) {
            return;
        }
        MarkHelper.markForEntity(falling).ifPresent(mark -> {
            BlockPos landed = falling.blockPosition();
            if (level.getBlockState(landed).is(falling.getBlockState().getBlock())) {
                MarkHelper.applyMark(level, landed, mark);
            }
        });
    }

    /** Everything loaded or kept by this manager that carries {@code mark}: living things, items and blocks. */
    public List<MemberId> membersOf(String mark) {
        return graph.members(mark);
    }

    /** Marked entities whose chunk is unloaded, by id, with where they were last seen. */
    public Map<UUID, LigabisData.StoredEntity> rememberedEntities(String mark) {
        String normalized = MarkHelper.sanitizeMark(mark);
        Map<UUID, LigabisData.StoredEntity> found = new LinkedHashMap<>();
        data.entities().forEach((id, stored) -> {
            if (stored.mark().equals(normalized)) {
                found.put(id, stored);
            }
        });
        return found;
    }

    /** Living things and dropped items can carry a mark; aura also moves the latter. */
    private static boolean isMarkable(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof ItemEntity;
    }

    public void entityMarkChanged(Entity entity) {
        entityJoined(entity);
    }

    /**
     * The client's own report of a player's walking input (see {@code PlayerMotionInputPacket}), since
     * Minecraft does not give the server this for ordinary on-foot movement. Used by {@link #puppetGolems}
     * to puppet a golem whose parent is a player.
     */
    public void setPlayerInput(UUID playerId, float xxa, float zza) {
        playerInputs.put(playerId, new float[] {xxa, zza});
    }

    /**
     * Whether {@code player} is bound to the scrolls carrying {@code scrollMark} by a vis link (see
     * {@link com.elderlexicon.mod.ligabis.ReadingBond}), so the spirit can read them anywhere in the dimension.
     */
    public boolean boundForReading(ServerPlayer player, String scrollMark) {
        Set<String> carried = MarkHelper.markForEntity(player).map(Set::of).orElse(Set.of());
        return ReadingBond.bound(graph.links(), carried, scrollMark);
    }

    /** Keeps the index of marked scrolls up to date for {@code carrier} (a frame or a placed scroll). */
    private void rememberScroll(Entity carrier) {
        ItemStack scroll = carrier instanceof ItemFrame frame ? frame.getItem()
                : carrier instanceof PlacedScrollEntity placed ? placed.getScroll() : ItemStack.EMPTY;
        Optional<String> mark = MarkHelper.markForEntity(carrier).or(() -> MarkHelper.markForItem(scroll));
        if (mark.isEmpty() || scroll.isEmpty()) {
            data.removeScroll(carrier.getUUID());
            return;
        }
        data.putScroll(carrier.getUUID(), new LigabisData.StoredEntity(mark.get(),
                carrier.level().dimension().location().toString(), carrier.getX(), carrier.getY(), carrier.getZ(), 0.0D));
    }

    /** Marked scrolls of {@code mark} in {@code dimension}, loaded or not, by id with where they are. */
    public Map<UUID, LigabisData.StoredEntity> scrollsMarked(String mark, ResourceKey<Level> dimension) {
        String normalized = MarkHelper.sanitizeMark(mark);
        String dimensionName = dimension.location().toString();
        Map<UUID, LigabisData.StoredEntity> found = new LinkedHashMap<>();
        data.scrolls().forEach((id, stored) -> {
            if (stored.mark().equals(normalized) && stored.dimension().equals(dimensionName)) {
                found.put(id, stored);
            }
        });
        return found;
    }

    /** Things a scroll can be read from: an item frame holding it, or a scroll placed on a surface. */
    public static boolean isScrollCarrier(@Nullable Entity entity) {
        return entity instanceof ItemFrame || entity instanceof PlacedScrollEntity;
    }

    /** The last walking input ({@code xxa}, {@code zza}) a player's client reported, or null when none came yet. */
    @Nullable
    public float[] playerInput(UUID playerId) {
        return playerInputs.get(playerId);
    }

    /**
     * Marks (or, with a null mark, unmarks) a block.
     *
     * @return false when the block cannot carry a mark (air)
     */
    public boolean blockMarkChanged(ServerLevel level, BlockPos pos, @Nullable String mark) {
        MemberId id = MemberKeys.block(level.dimension(), pos);
        graph.forget(id);
        engine.clearWear(id);
        if (mark == null) {
            data.removeBlock(id);
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        data.putBlock(id, new LigabisData.StoredBlock(blockName(state.getBlock()), mark, 0.0D));
        graph.mark(id, mark);
        return true;
    }

    // ------------------------------------------------------------------ casting

    /** The spell itself: read the aspect and marks, create the links, and mark what the mage is looking at. */
    public void castLink(ServerPlayer player, List<String> lexemes, @Nullable Entity entity, @Nullable BlockPos blockPos) {
        LigabisGrammar.Result result = LigabisGrammar.parse(lexemes, word -> DICTIONARY.lookup(word).isPresent());
        if (!result.ok()) {
            tell(player, result.error());
            return;
        }
        Aspect aspect = result.parsed().aspect();
        List<String> marks = result.parsed().marks();
        ServerLevel level = player.serverLevel();

        GolemStructure.Found golemStructure = null;
        String label;
        if (entity instanceof LivingEntity living) {
            label = living.getName().getString();
        } else if (aspect == Aspect.AURA && entity instanceof ItemEntity item) {
            label = item.getItem().getHoverName().getString();
        } else if (aspect == Aspect.VIS && isScrollCarrier(entity)) {
            label = "o pergaminho"; // a reading bond: the spirit may read it from anywhere in the dimension
        } else if (entity != null) {
            String reach = aspect == Aspect.AURA ? "seres vivos ou itens no chao" : "seres vivos ou blocos";
            tell(player, "Esse vinculo so alcanca " + reach + ", por enquanto.");
            return;
        } else if (aspect == Aspect.AURA && blockPos != null && !level.getBlockState(blockPos).isAir()) {
            golemStructure = GolemStructure.find(level, blockPos).orElse(null);
            if (golemStructure == null) {
                tell(player, "Aura so alcanca blocos que formem um ritual de golem valido.");
                return;
            }
            label = "um golem de " + golemStructure.block().getName().getString();
        } else if (blockPos != null && !level.getBlockState(blockPos).isAir()) {
            label = level.getBlockState(blockPos).getBlock().getName().getString();
        } else {
            tell(player, "Nenhum alvo valido para marcar.");
            return;
        }

        List<Link> added = new ArrayList<>();
        for (Link link : Link.chain(player.getUUID(), aspect, marks)) {
            if (sameShapeExists(link)) {
                continue; // an existing link is joined, not duplicated; its owner keeps paying
            }
            if (!graph.addLink(link)) {
                for (Link undo : added) {
                    graph.removeLink(undo);
                    data.removeLink(undo);
                }
                tell(player, "Esse vinculo faria uma marca ser ancestral de si mesma.");
                return;
            }
            data.addLink(link);
            added.add(link);
        }

        String mark = marks.get(marks.size() - 1);
        if (golemStructure != null) {
            String parentMark = marks.size() >= 2 ? marks.get(marks.size() - 2) : null;
            entity = golemify(level, blockPos, golemStructure, mark, parentMark);
            blockPos = null;
        }

        boolean marked = entity != null
                ? MarkHelper.applyMark(entity, mark)
                : MarkHelper.applyMark(level, blockPos, mark);
        if (!marked) {
            tell(player, "Nao foi possivel marcar " + label + ".");
            return;
        }
        tell(player, "Marca '" + mark + "' gravada em " + label + ". "
                + (added.isEmpty() ? "Voce entrou num vinculo que ja existia." : "Vinculo criado: " + describe(added) + "."));
    }

    /** Tears down a valid golem structure and replaces it with the living statue described in the design doc. */
    private GolemEntity golemify(ServerLevel level, BlockPos head, GolemStructure.Found structure,
                                 String ownMark, @Nullable String parentMark) {
        for (BlockPos offset : GolemStructure.offsets(structure.axis())) {
            level.setBlock(head.offset(offset), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos feet = head.offset(GolemStructure.feetOffset());
        GolemEntity golem = new GolemEntity(ExampleMod.LIGABIS_GOLEM.get(), level);
        golem.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0.0F, 0.0F);
        golem.setBlockId(blockName(structure.block()));
        level.addFreshEntity(golem);
        golems.put(golem.getUUID(), new GolemRecord(ownMark, parentMark, structure.block(), structure.axis()));
        return golem;
    }

    /** Puts a golem's blocks back where it currently stands, and forgets it as a Ligabis member. */
    private void revertGolem(GolemEntity golem, GolemRecord record) {
        ServerLevel level = (ServerLevel) golem.level();
        BlockPos head = golem.blockPosition().offset(0, -GolemStructure.feetOffset().getY(), 0);
        for (BlockPos offset : GolemStructure.offsets(record.axis())) {
            level.setBlock(head.offset(offset), record.block().defaultBlockState(), Block.UPDATE_ALL);
        }
        graph.forget(MemberKeys.entity(golem.getUUID()));
        golems.remove(golem.getUUID());
        golemSwingState.remove(golem.getUUID());
        golem.discard();
    }

    /** A broken aura link may be the one holding a golem together; if so, it reverts too. */
    private void revertGolemOf(Link link) {
        if (link.aspect() != Aspect.AURA || link.second() == null) {
            return;
        }
        for (Map.Entry<UUID, GolemRecord> entry : Map.copyOf(golems).entrySet()) {
            if (!entry.getValue().ownMark().equals(link.second())) {
                continue;
            }
            Entity entity = findEntity(entry.getKey());
            if (entity instanceof GolemEntity golem) {
                revertGolem(golem, entry.getValue());
            }
        }
    }

    /** Checks every tracked golem's parent mark and reverts it if nobody living still carries it. */
    private void checkGolemsWithoutAParent(ServerLevel level) {
        if (golems.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(golems.keySet())) {
            GolemRecord record = golems.get(id);
            if (record == null || record.parentMark() == null) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (!(entity instanceof GolemEntity golem)) {
                continue;
            }
            boolean hasLivingParent = false;
            for (MemberId parent : graph.members(record.parentMark())) {
                if (living(parent) != null) {
                    hasLivingParent = true;
                    break;
                }
            }
            if (!hasLivingParent) {
                revertGolem(golem, record);
            }
        }
    }

    private record GolemRecord(String ownMark, @Nullable String parentMark, Block block, Direction.Axis axis) {
    }

    private boolean sameShapeExists(Link link) {
        for (Link existing : graph.links()) {
            if (existing.aspect() == link.aspect() && existing.first().equals(link.first())
                    && Objects.equals(existing.second(), link.second())) {
                return true;
            }
        }
        return false;
    }

    private static String describe(List<Link> links) {
        List<String> parts = new ArrayList<>();
        for (Link link : links) {
            parts.add(link.isMirror()
                    ? "espelho em '" + link.first() + "'"
                    : "'" + link.first() + "' (pai) -> '" + link.second() + "' (filho)");
        }
        return String.join(", ", parts);
    }

    // ------------------------------------------------------------------ events

    public void onDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        MemberId id = MemberKeys.entity(entity.getUUID());
        if (!graph.hasAnyMark(id)) {
            return;
        }
        List<Effect> effects = engine.handle(new LinkEvent.Damaged(id, event.getAmount(), LigabisGuard.active()));
        apply(effects, event);
    }

    public void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        MemberId id = MemberKeys.entity(entity.getUUID());
        if (!graph.hasAnyMark(id)) {
            return;
        }
        apply(engine.handle(new LinkEvent.Died(id)), null);
    }

    /** Blocks give no event when they are broken by water, pistons or fire, so marked blocks are checked. */
    public void tickBlocks(ServerLevel level) {
        for (Map.Entry<MemberId, LigabisData.StoredBlock> entry : data.blocks().entrySet()) {
            MemberId id = entry.getKey();
            if (!MemberKeys.blockDimension(id).equals(level.dimension())) {
                continue;
            }
            BlockPos pos = MemberKeys.blockPos(id);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!blockName(level.getBlockState(pos).getBlock()).equals(entry.getValue().block())) {
                apply(engine.handle(new LinkEvent.Died(id)), null);
            }
        }
    }

    /** Igni: once a tick, tells the engine whether every marked member in this level is hot right now. */
    public void tickIgni(ServerLevel level) {
        Set<MemberId> members = graph.markedMembers();
        if (members.isEmpty()) {
            return;
        }
        Map<MemberId, Boolean> observations = new LinkedHashMap<>();
        for (MemberId member : members) {
            if (MemberKeys.isEntity(member)) {
                Entity found = level.getEntity(MemberKeys.entityId(member));
                if (found instanceof LivingEntity living) {
                    observations.put(member, living.getRemainingFireTicks() > 0);
                }
            } else if (MemberKeys.blockDimension(member).equals(level.dimension())) {
                BlockPos pos = MemberKeys.blockPos(member);
                if (level.isLoaded(pos)) {
                    observations.put(member, isHot(level.getBlockState(pos)));
                }
            }
        }
        if (!observations.isEmpty()) {
            apply(engine.observeHeat(observations), null);
        }
    }

    /**
     * Aqua: once a tick, reports the air of every marked living member (breath channel) and the wetness
     * of every marked block (waterlogged channel) in this level. The two never mix on the engine side.
     */
    public void tickAqua(ServerLevel level) {
        Set<MemberId> members = graph.markedMembers();
        if (members.isEmpty()) {
            return;
        }
        Map<MemberId, Integer> air = new LinkedHashMap<>();
        Map<MemberId, Boolean> wet = new LinkedHashMap<>();
        for (MemberId member : members) {
            if (MemberKeys.isEntity(member)) {
                Entity found = level.getEntity(MemberKeys.entityId(member));
                if (found instanceof LivingEntity living) {
                    air.put(member, living.getAirSupply());
                }
            } else if (MemberKeys.blockDimension(member).equals(level.dimension())) {
                BlockPos pos = MemberKeys.blockPos(member);
                if (level.isLoaded(pos)) {
                    wet.put(member, isWet(level.getBlockState(pos)));
                }
            }
        }
        if (!air.isEmpty()) {
            apply(engine.observeAir(air), null);
        }
        if (!wet.isEmpty()) {
            apply(engine.observeWet(wet), null);
        }
    }

    /**
     * Aura: once a tick, reports the position of every marked living entity or dropped item in this level
     * (except golems, which are puppeted directly — see {@link #puppetGolems}). A member's own movement
     * this tick is compared with what its links demand, and the difference comes back as a velocity nudge
     * for the world to apply.
     */
    public void tickAura(ServerLevel level) {
        Set<MemberId> members = graph.markedMembers();
        if (members.isEmpty()) {
            return;
        }
        Map<MemberId, Vec> observations = new LinkedHashMap<>();
        for (MemberId member : members) {
            if (!MemberKeys.isEntity(member)) {
                continue;
            }
            Entity found = level.getEntity(MemberKeys.entityId(member));
            if (found instanceof GolemEntity) {
                continue;
            }
            if (found instanceof LivingEntity || found instanceof ItemEntity) {
                observations.put(member, new Vec(found.getX(), found.getY(), found.getZ()));
            }
        }
        if (!observations.isEmpty()) {
            apply(engine.observeMotion(observations), null);
        }
        checkGolemsWithoutAParent(level);
        puppetGolems(level);
    }

    /**
     * A golem is a puppet, not an ordinary aura member: instead of comparing positions and correcting the
     * difference, it copies the parent's own movement <b>input</b> — the same {@code xxa}/{@code zza} a
     * walking mob or a player's own steps represent — and aim, every tick, and lets its own body resolve
     * the result. A parent shoving into a fence still pushes the golem's legs the same way; whether the
     * golem actually goes anywhere is its own collision to solve.
     * <p>
     * The world-space velocity is worked out here, by hand, using the same rotation math vanilla's own
     * input-to-motion conversion uses — see {@link GolemEntity#travel} for why it is not handed to the
     * mob-AI movement system that conversion normally feeds.
     */
    private void puppetGolems(ServerLevel level) {
        if (golems.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, GolemRecord> entry : Map.copyOf(golems).entrySet()) {
            String parentMark = entry.getValue().parentMark();
            if (parentMark == null) {
                continue;
            }
            if (!(level.getEntity(entry.getKey()) instanceof GolemEntity golem)) {
                continue;
            }
            LivingEntity parent = null;
            for (MemberId candidate : graph.members(parentMark)) {
                LivingEntity found = living(candidate);
                if (found != null) {
                    parent = found;
                    break;
                }
            }
            if (parent == null) {
                continue;
            }
            golem.setYRot(parent.getYRot());
            golem.setYBodyRot(parent.getYRot());
            golem.setYHeadRot(parent.getYHeadRot());
            golem.setXRot(parent.getXRot());

            float[] input = parent instanceof ServerPlayer player ? playerInputs.get(player.getUUID()) : null;
            double localX = input != null ? input[0] : parent.xxa;
            double localZ = input != null ? input[1] : parent.zza;
            double lengthSqr = localX * localX + localZ * localZ;
            if (lengthSqr > 1.0D) {
                double length = Math.sqrt(lengthSqr);
                localX /= length;
                localZ /= length;
            }
            double speed = parent.getSpeed();
            float yRotRad = parent.getYRot() * ((float) Math.PI / 180F);
            double sin = Mth.sin(yRotRad);
            double cos = Mth.cos(yRotRad);
            double worldX = (localX * cos - localZ * sin) * speed;
            double worldZ = (localZ * cos + localX * sin) * speed;
            golem.setMoveIntent(worldX, worldZ);
            LOGGER.info("Ligabis debug: puppet compute localX={} localZ={} speed={} yRot={} -> worldX={} worldZ={}",
                    localX, localZ, speed, parent.getYRot(), worldX, worldZ);

            boolean wasSwinging = golemSwingState.getOrDefault(entry.getKey(), Boolean.FALSE);
            if (parent.swinging && !wasSwinging) {
                golem.swing(parent.swingingArm);
            }
            golemSwingState.put(entry.getKey(), parent.swinging);
        }
    }

    // ------------------------------------------------------------------ effects

    private void apply(List<Effect> effects, @Nullable LivingDamageEvent damageEvent) {
        ServerPlayer blamed = null;
        for (Effect effect : effects) {
            if (effect instanceof Effect.Charged charged) {
                blamed = player(charged.owner());
            } else if (effect instanceof Effect.CancelDamage) {
                if (damageEvent != null) {
                    damageEvent.setCanceled(true);
                }
            } else if (effect instanceof Effect.DealDamage deal) {
                LivingEntity target = living(deal.target());
                if (target != null && target.isAlive()) {
                    DamageSource source = source(target, REFLECTION, blamed);
                    LigabisGuard.run(() -> target.hurt(source, (float) deal.amount()));
                }
            } else if (effect instanceof Effect.Kill kill) {
                kill(kill.target(), blamed);
            } else if (effect instanceof Effect.SetHeat heat) {
                applyHeat(heat.target(), heat.hot());
            } else if (effect instanceof Effect.SetAir setAir) {
                LivingEntity target = living(setAir.target());
                if (target != null) {
                    target.setAirSupply(setAir.air());
                }
            } else if (effect instanceof Effect.SetWet setWet) {
                applyWet(setWet.target(), setWet.wet());
            } else if (effect instanceof Effect.Displace displace) {
                applyDisplace(displace.target(), displace.delta());
            } else if (effect instanceof Effect.Worn worn) {
                data.setWear(worn.member(), worn.wear());
            } else if (effect instanceof Effect.LinkBroken broken) {
                data.removeLink(broken.link());
                ServerPlayer owner = player(broken.link().owner());
                if (owner != null) {
                    String aspectName = broken.link().aspect().name().toLowerCase(Locale.ROOT);
                    tell(owner, "Seu vinculo " + aspectName + " (" + describe(List.of(broken.link())) + ") se rompeu: voce nao conseguiu pagar.");
                }
                revertGolemOf(broken.link());
            } else if (effect instanceof Effect.MemberGone gone) {
                forgetMember(gone.member());
            }
        }
    }

    private void kill(MemberId member, @Nullable ServerPlayer blamed) {
        if (MemberKeys.isEntity(member)) {
            LivingEntity target = living(member);
            if (target != null && target.isAlive()) {
                DamageSource source = source(target, DEATH, blamed);
                LigabisGuard.run(() -> target.hurt(source, Float.MAX_VALUE));
            }
            return;
        }
        ServerLevel level = server.getLevel(MemberKeys.blockDimension(member));
        BlockPos pos = MemberKeys.blockPos(member);
        if (level != null && level.isLoaded(pos)) {
            level.destroyBlock(pos, false);
        }
    }

    /** Igni: sets a living member on fire or clears it, or flips a block's own lit/fire state. */
    private void applyHeat(MemberId member, boolean hot) {
        if (MemberKeys.isEntity(member)) {
            LivingEntity target = living(member);
            if (target != null) {
                if (hot) {
                    target.setSecondsOnFire(8);
                } else {
                    target.clearFire();
                }
            }
            return;
        }
        ServerLevel level = server.getLevel(MemberKeys.blockDimension(member));
        BlockPos pos = MemberKeys.blockPos(member);
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            engine.syncHeat(member, isHot(current));
            return;
        }
        if (current.hasProperty(BlockStateProperties.LIT)) {
            if (current.getValue(BlockStateProperties.LIT) != hot) {
                level.setBlock(pos, current.setValue(BlockStateProperties.LIT, hot), Block.UPDATE_ALL);
            }
            return;
        }
        if (!hot && (current.is(Blocks.FIRE) || current.is(Blocks.SOUL_FIRE))) {
            level.removeBlock(pos, false);
            return;
        }
        // No existing lit/fire state to flip, and igni never places fire on a block that never had one.
        engine.syncHeat(member, isHot(current));
    }

    /** Whether a block's own state already counts as hot, for igni's baseline and outcome checks. */
    private boolean isHot(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            return state.getValue(BlockStateProperties.LIT);
        }
        return false;
    }

    /** Aqua: waterlogs or dries a block, the same mold as igni's heat. */
    private void applyWet(MemberId member, boolean wet) {
        ServerLevel level = server.getLevel(MemberKeys.blockDimension(member));
        BlockPos pos = MemberKeys.blockPos(member);
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            engine.syncWet(member, isWet(current));
            return;
        }
        if (current.hasProperty(BlockStateProperties.WATERLOGGED)) {
            if (current.getValue(BlockStateProperties.WATERLOGGED) != wet) {
                level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, wet), Block.UPDATE_ALL);
            }
            return;
        }
        if (!wet && current.getFluidState().isSourceOfType(Fluids.WATER)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        // No existing waterlogged/water state to flip, and aqua never places water on a block that never had one.
        engine.syncWet(member, isWet(current));
    }

    /** Whether a block's own state already counts as wet, for aqua's baseline and outcome checks. */
    private boolean isWet(BlockState state) {
        if (state.getFluidState().isSourceOfType(Fluids.WATER)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state.getValue(BlockStateProperties.WATERLOGGED);
        }
        return false;
    }

    /**
     * Aura: nudges a member's velocity towards the correction the engine asked for, instead of
     * teleporting it — the next tick's observation folds in whatever this did not fully achieve.
     */
    private void applyDisplace(MemberId member, Vec delta) {
        Entity entity = findEntity(MemberKeys.entityId(member));
        if (entity == null || !(entity instanceof LivingEntity || entity instanceof ItemEntity)) {
            return;
        }
        entity.setDeltaMovement(entity.getDeltaMovement().add(delta.x(), delta.y(), delta.z()));
        entity.hurtMarked = true;
    }

    /** The member is dead or destroyed, so its mark goes with it. */
    private void forgetMember(MemberId member) {
        if (MemberKeys.isBlock(member)) {
            data.removeBlock(member);
            engine.clearWear(member);
            return;
        }
        Entity entity = findEntity(MemberKeys.entityId(member));
        if (entity != null) {
            MarkHelper.applyMark(entity, null);
        }
    }

    private DamageSource source(Entity target, ResourceKey<DamageType> type, @Nullable ServerPlayer blamed) {
        Holder<DamageType> holder = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        return blamed == null ? new DamageSource(holder) : new DamageSource(holder, blamed, blamed);
    }

    // ------------------------------------------------------------------ lookups

    @Nullable
    private ServerPlayer player(UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    @Nullable
    private Entity findEntity(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    @Nullable
    private LivingEntity living(MemberId member) {
        return MemberKeys.isEntity(member) && findEntity(MemberKeys.entityId(member)) instanceof LivingEntity living
                ? living
                : null;
    }

    private static String blockName(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "minecraft:air" : key.toString();
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    // ------------------------------------------------------------------ what the engine asks the game

    private final class WorldMembers implements Members {

        @Override
        public MemberProfile profile(MemberId member) {
            if (MemberKeys.isEntity(member)) {
                return MemberProfile.living();
            }
            LigabisData.StoredBlock stored = data.block(member);
            Block block = stored == null ? null : ForgeRegistries.BLOCKS.getValue(new ResourceLocation(stored.block()));
            return MemberProfile.object(BlockIntegrity.capacity(block == null ? 0.0D : block.getExplosionResistance()));
        }

        @Override
        public double health(MemberId member) {
            LivingEntity entity = living(member);
            return entity == null ? 0.0D : entity.getHealth();
        }

        @Override
        public int maxAir(MemberId member) {
            LivingEntity entity = living(member);
            return entity == null ? 300 : entity.getMaxAirSupply();
        }
    }

    private final class WorldOwners implements Owners {

        private final SpellCostModule payment = new SpellCostModule();

        @Override
        public boolean isOnline(UUID owner) {
            return player(owner) != null;
        }

        @Override
        public boolean pay(UUID owner, double umu) {
            ServerPlayer player = player(owner);
            return player != null && payment.payOutsideCast(player, umu, SpellConduitItem.holdsReadyConduit(player));
        }
    }
}

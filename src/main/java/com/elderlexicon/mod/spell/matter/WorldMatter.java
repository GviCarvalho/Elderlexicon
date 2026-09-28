package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Form;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.Placement;
import com.elderlexicon.mod.magic.matter.Substance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The world read as matter, and matter put into the world, by the material table in force
 * (docs/plano-materia-e-forca.md, stages 3 and 5). Nothing here knows one substance from another: a block is what the
 * table reads it as, and matter shows as the form the table gives its substance in its state, or as formless matter
 * ({@link FormlessMatterBlock}) when it has none there. Fluid matter put where fluid matter is mixes with it (L4); an
 * amalgam falls apart with time (L5).
 */
public final class WorldMatter {

    /** How far matter spreads from where it is put, at most, looking for room. */
    private static final int MAX_SPREAD = 4096;
    /**
     * How much of a body of fluid matter a portion poured into it mixes with: the blocks of it nearest where it lands, up
     * to this many (a pool, not a sea).
     */
    public static final int MAX_BODY = 64;
    private static final double EPSILON = 1.0E-9D;

    private WorldMatter() {
    }

    // ------------------------------------------------------------------ reading

    /**
     * What a block is, as matter. A fluid is matter only where it is a whole block (its source); what flows from it is
     * the same water spreading.
     */
    public static Optional<Matter> read(BlockState state) {
        if (state == null || !state.getFluidState().isEmpty() && !state.getFluidState().isSource()) {
            return Optional.empty();
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return Optional.empty();
        }
        return table().read(Form.Kind.BLOCK, id.toString()).map(MaterialTable.Reading::matter);
    }

    public static Optional<Matter> read(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless) {
            return formless.matter();
        }
        return read(level.getBlockState(pos));
    }

    /** What a stack of items is, as matter: as many of its item, or of the block it places. */
    public static Optional<Matter> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Optional<MaterialTable.Reading> reading = id == null ? Optional.empty()
                : table().read(Form.Kind.ITEM, id.toString());
        if (reading.isEmpty() && stack.getItem() instanceof BlockItem block) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block.getBlock());
            reading = blockId == null ? Optional.empty() : table().read(Form.Kind.BLOCK, blockId.toString());
        }
        return reading.map(found -> found.matter().withUmu(found.umu() * stack.getCount()));
    }

    /** What a thing in the world is, as matter: a dropped stack is its items; a creature is no matter of the table. */
    public static Optional<Matter> read(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return read(item.getItem());
        }
        if (entity == null) {
            return Optional.empty();
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? Optional.empty()
                : table().read(Form.Kind.ENTITY, id.toString()).map(MaterialTable.Reading::matter);
    }

    /** Takes a block out of the world as matter: it is read, and the place is left empty. */
    public static Optional<Matter> take(ServerLevel level, BlockPos pos) {
        Optional<Matter> matter = read(level, pos);
        matter.ifPresent(found -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        return matter;
    }

    // ------------------------------------------------------------------ placing

    /**
     * What putting matter into the world did: how much went in, how much is left for whoever put it, and, when it was
     * poured into fluid matter, what the two became together.
     *
     * @param mixture what the matter and the body it was poured into became; null when it was only put down
     */
    public record Placed(double placed, double leftover, List<BlockPos> blocks, Matter mixture) {

        public Placed(double placed, double leftover, List<BlockPos> blocks) {
            this(placed, leftover, blocks, null);
        }

        public Optional<Matter> mixed() {
            return Optional.ofNullable(mixture);
        }
    }

    /**
     * Puts matter into the world at {@code at}. Fluid matter put where fluid matter is (not the open air) is poured into
     * it and mixes with it (L4, {@link #pour}); otherwise it is laid down: whole blocks from there into the free room
     * nearest it, items dropped there, a gas shown there, formless matter where it has no look. What finds no room, or
     * does not make a whole unit, is left over (L1).
     */
    public static Placed place(ServerLevel level, BlockPos at, Matter matter) {
        if (matter.state().fluid() && holdsFluid(level, at)) {
            return pour(level, at, matter);
        }
        return lay(level, at, matter);
    }

    /** Whether the block at {@code pos} is fluid matter to pour into: a fluid's source, formless liquid; never air. */
    public static boolean holdsFluid(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            return false;
        }
        return read(level, pos).map(matter -> matter.state().fluid()).orElse(false);
    }

    /**
     * L4: {@code poured} mixes with the body of fluid matter at {@code at} (the blocks of the same matter joined to it,
     * nearest first, up to {@link #MAX_BODY}). When the mixture is still what the body was, as with water poured into
     * water, the body stays and what was poured is added to it as more of it; otherwise the body is taken up and the
     * mixture put in its place: a substance, if it matches a recipe, or an amalgam.
     */
    public static Placed pour(ServerLevel level, BlockPos at, Matter poured) {
        Optional<Matter> there = read(level, at);
        if (there.isEmpty() || !there.get().state().fluid()) {
            return lay(level, at, poured);
        }
        List<BlockPos> body = bodyAt(level, at, there.get());
        double held = 0.0D;
        for (BlockPos pos : body) {
            held += read(level, pos).map(Matter::umu).orElse(0.0D);
        }
        Matter whole = there.get().withUmu(held);
        Optional<Matter> mixed = MatterLaws.mix(List.of(whole, poured));
        if (mixed.isEmpty()) {
            return lay(level, at, poured);
        }
        MaterialTable table = table();
        Optional<Substance> was = whole.substance(table);
        Optional<Substance> becomes = mixed.get().substance(table);
        boolean formless = level.getBlockEntity(at) instanceof FormlessMatterBlockEntity;
        if (!formless && becomes.isPresent() && becomes.equals(was) && mixed.get().state() == whole.state()) {
            // Still what it was: the body takes in what was poured as more of itself, and only that needs room.
            Placed grown = lay(level, at, Matter.of(becomes.get(), whole.state(), poured.umu()));
            return new Placed(grown.placed(), grown.leftover(), grown.blocks(), mixed.get());
        }
        for (BlockPos pos : body) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        Placed made = lay(level, at, mixed.get());
        return new Placed(made.placed(), made.leftover(), made.blocks(), mixed.get());
    }

    /** The body of fluid matter {@code matter} at {@code at}: it and the blocks of the same matter joined to it. */
    private static List<BlockPos> bodyAt(ServerLevel level, BlockPos at, Matter matter) {
        List<BlockPos> body = new ArrayList<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(at.immutable());
        seen.add(at.immutable());
        while (!open.isEmpty() && body.size() < MAX_BODY) {
            BlockPos pos = open.poll();
            Optional<Matter> here = level.isLoaded(pos) && !level.getBlockState(pos).isAir()
                    ? read(level, pos) : Optional.empty();
            if (here.isEmpty() || !same(here.get(), matter)) {
                continue;
            }
            body.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next)) {
                    open.add(next);
                }
            }
        }
        return body;
    }

    /**
     * Lays matter down at {@code at}, mixing with nothing: whole blocks from there into the free room nearest it, items
     * dropped there, a gas shown there, formless matter where it has no look.
     */
    private static Placed lay(ServerLevel level, BlockPos at, Matter matter) {
        double placed = 0.0D;
        double leftover = 0.0D;
        List<BlockPos> blocks = new ArrayList<>();
        for (Placement placement : Placement.plan(table(), matter)) {
            leftover += placement.leftover();
            if (placement.formless()) {
                List<BlockPos> room = formless(level, at, placement);
                double each = placement.placed() / placement.units();
                placed += each * room.size();
                leftover += each * (placement.units() - room.size());
                blocks.addAll(room);
                continue;
            }
            Form form = placement.form();
            if (form == null || placement.units() <= 0) {
                continue;
            }
            switch (form.kind()) {
                case BLOCK -> {
                    Optional<BlockState> state = block(form.id());
                    if (state.isEmpty()) {
                        leftover += placement.placed();
                        continue;
                    }
                    if (state.get().isAir()) {
                        // Air let out joins the air around: there is no block to lay.
                        placed += placement.placed();
                        continue;
                    }
                    List<BlockPos> room = room(level, at, state.get(), placement.units());
                    for (BlockPos pos : room) {
                        level.setBlock(pos, state.get(), Block.UPDATE_ALL);
                    }
                    blocks.addAll(room);
                    double unit = placement.placed() / placement.units();
                    placed += unit * room.size();
                    leftover += unit * (placement.units() - room.size());
                }
                case ITEM -> {
                    Optional<Item> item = item(form.id());
                    if (item.isEmpty()) {
                        leftover += placement.placed();
                        continue;
                    }
                    int left = placement.units();
                    while (left > 0) {
                        int count = Math.min(left, item.get().getMaxStackSize());
                        Vec3 center = Vec3.atCenterOf(at);
                        level.addFreshEntity(new ItemEntity(level, center.x, center.y, center.z,
                                new ItemStack(item.get(), count)));
                        left -= count;
                    }
                    placed += placement.placed();
                }
                case PARTICLE -> {
                    particle(form.id()).ifPresent(options -> {
                        Vec3 center = Vec3.atCenterOf(at);
                        int count = (int) Math.max(4, Math.min(60, Math.round(8.0D * placement.placed())));
                        level.sendParticles(options, center.x, center.y, center.z, count, 0.6D, 0.6D, 0.6D, 0.02D);
                    });
                    placed += placement.placed();
                }
                case ENTITY -> {
                    Optional<Entity> entity = entity(level, form.id());
                    if (entity.isEmpty()) {
                        leftover += placement.placed();
                        continue;
                    }
                    entity.get().moveTo(Vec3.atBottomCenterOf(at));
                    level.addFreshEntity(entity.get());
                    placed += placement.placed();
                }
            }
        }
        return new Placed(placed, leftover, blocks);
    }

    // ------------------------------------------------------------------ formless matter

    /**
     * Lays formless matter: as many blocks as the placement fills, from {@code at} into the free room nearest it, each
     * holding its share exactly. An amalgam is set to fall apart when its time is up (L5). Returns where it went.
     */
    private static List<BlockPos> formless(ServerLevel level, BlockPos at, Placement placement) {
        boolean liquid = placement.state() != com.elderlexicon.mod.magic.matter.State.SOLID;
        BlockState block = (liquid ? MatterBlocks.FORMLESS_LIQUID.get() : MatterBlocks.FORMLESS_SOLID.get())
                .defaultBlockState().setValue(FormlessMatterBlock.UNSTABLE, placement.amalgam());
        List<BlockPos> room = room(level, at, block, placement.units());
        Matter share = placement.matter().withUmu(placement.placed() / placement.units());
        int ticks = amalgamTicks();
        long fallsApartAt = placement.amalgam() ? level.getGameTime() + ticks : -1L;
        for (BlockPos pos : room) {
            level.setBlock(pos, block, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless) {
                formless.hold(share, fallsApartAt);
            }
            if (placement.amalgam()) {
                level.scheduleTick(pos, block.getBlock(), ticks);
            }
        }
        return room;
    }

    /** How long an amalgam holds together, in ticks. */
    public static int amalgamTicks() {
        return (int) Math.max(1L, Math.round(table().amalgamSeconds() * 20.0D));
    }

    /**
     * The time asked for the formless matter at {@code pos} has come. An amalgam whose time is up falls apart (L5); one
     * mixed again since waits for its new time; matter that became a substance holds together.
     */
    static void ripen(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity formless)) {
            return;
        }
        Optional<Matter> matter = formless.matter();
        BlockState state = level.getBlockState(pos);
        if (matter.isEmpty() || !matter.get().amalgam(table())) {
            if (state.hasProperty(FormlessMatterBlock.UNSTABLE) && state.getValue(FormlessMatterBlock.UNSTABLE)) {
                level.setBlock(pos, state.setValue(FormlessMatterBlock.UNSTABLE, false), Block.UPDATE_CLIENTS);
            }
            return;
        }
        long left = formless.fallsApartAt() - level.getGameTime();
        if (left > 0L) {
            level.scheduleTick(pos, state.getBlock(), (int) Math.min(Integer.MAX_VALUE, left));
            return;
        }
        // The whole of it falls apart at once, as one portion, so its parts come out whole.
        Matter whole = takeBody(level, pos, matter.get(), 0.0D);
        for (Matter part : MatterLaws.decay(table(), whole)) {
            disperse(level, pos, lay(level, pos, part).leftover());
        }
    }

    /**
     * Formless matter broken or blown up (already gone from {@code pos}, holding {@code matter}) settles into what it is,
     * with the body of it it was part of: an amalgam into its primordials (L5), a substance into the state it is found
     * in.
     */
    static void settle(ServerLevel level, BlockPos pos, Matter matter) {
        Matter whole = matter;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            Optional<Matter> there = level.getBlockEntity(next) instanceof FormlessMatterBlockEntity formless
                    ? formless.matter() : Optional.empty();
            if (there.isPresent() && same(there.get(), matter)) {
                whole = takeBody(level, next, there.get(), whole.umu());
                break;
            }
        }
        Matter settled = whole;
        List<Matter> parts = settled.substance(table())
                .map(substance -> List.of(Matter.natural(substance, settled.umu())))
                .orElseGet(() -> MatterLaws.decay(table(), settled));
        for (Matter part : parts) {
            disperse(level, pos, lay(level, pos, part).leftover());
        }
    }

    /**
     * Takes up the body of formless matter at {@code at} ({@code matter} and the blocks of the same matter joined to it)
     * and gives it back as one portion, with {@code more} UMU added.
     */
    private static Matter takeBody(ServerLevel level, BlockPos at, Matter matter, double more) {
        double held = more;
        for (BlockPos pos : bodyAt(level, at, matter)) {
            held += read(level, pos).map(Matter::umu).orElse(0.0D);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return matter.withUmu(held);
    }

    /** Whether two portions are the same matter: the same composition, in the same state. */
    private static boolean same(Matter one, Matter other) {
        return one.state() == other.state() && one.composition().distance(other.composition()) <= 1.0E-6D;
    }

    /** What makes no whole block when matter settles, with no one to keep it, is scattered into the air around. */
    private static void disperse(ServerLevel level, BlockPos pos, double umu) {
        if (umu <= EPSILON) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        int count = (int) Math.max(2, Math.min(20, Math.round(4.0D * umu)));
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, count, 0.3D, 0.3D, 0.3D, 0.01D);
    }

    /**
     * Where {@code units} blocks go: from {@code at} outward, the nearest places that can take a block (air, plants,
     * flowing fluids, never a fluid's source) and do not already hold this one, reached through free room or through
     * blocks of this one.
     */
    private static List<BlockPos> room(ServerLevel level, BlockPos at, BlockState block, int units) {
        List<BlockPos> room = new ArrayList<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(at.immutable());
        seen.add(at.immutable());
        while (room.size() < units && !open.isEmpty() && seen.size() < MAX_SPREAD) {
            BlockPos pos = open.poll();
            BlockState there = level.getBlockState(pos);
            // A whole block of fluid is matter too: nothing is laid over a source (it would be destroyed).
            boolean free = there.canBeReplaced() && !there.getFluidState().isSource() && !there.equals(block)
                    && level.isInWorldBounds(pos);
            if (free) {
                room.add(pos);
            }
            // It spreads through free room and through a body of itself, to its surface (water poured into a lake).
            if (!free && !pos.equals(at) && !there.equals(block)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next)) {
                    open.add(next);
                }
            }
        }
        return room;
    }

    // ------------------------------------------------------------------ the game's registries

    private static Optional<BlockState> block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block block = location == null ? null : ForgeRegistries.BLOCKS.getValue(location);
        if (block == null || block == Blocks.AIR && !"minecraft:air".equals(id)) {
            return Optional.empty();
        }
        return Optional.of(block.defaultBlockState());
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        return item == null || item == net.minecraft.world.item.Items.AIR ? Optional.empty() : Optional.of(item);
    }

    private static Optional<Entity> entity(ServerLevel level, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
        return type == null ? Optional.empty() : Optional.ofNullable(type.create(level));
    }

    /** A particle by id: {@code block:<id>} and {@code item:<id>} are the particles of a block or an item. */
    static Optional<ParticleOptions> particle(String id) {
        if (id.startsWith("block:")) {
            return block(id.substring("block:".length()))
                    .map(state -> new BlockParticleOption(ParticleTypes.BLOCK, state));
        }
        if (id.startsWith("item:")) {
            return item(id.substring("item:".length()))
                    .map(item -> new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item)));
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        ParticleType<?> type = location == null ? null : ForgeRegistries.PARTICLE_TYPES.getValue(location);
        return type instanceof SimpleParticleType simple ? Optional.of(simple) : Optional.empty();
    }

    private static MaterialTable table() {
        return Materials.get();
    }
}

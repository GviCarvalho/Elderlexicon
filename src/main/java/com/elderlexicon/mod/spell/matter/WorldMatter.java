package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Form;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Placement;
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
 * (docs/plano-materia-e-forca.md, stage 3). Nothing here knows one substance from another: a block is what the table
 * reads it as, and matter shows as the form the table gives its substance in its state.
 */
public final class WorldMatter {

    /** How far matter spreads from where it is put, at most, looking for room. */
    private static final int MAX_SPREAD = 4096;

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

    /** What putting matter into the world did: how much went in, and how much is left for whoever put it. */
    public record Placed(double placed, double leftover, List<BlockPos> blocks) {
    }

    /**
     * Puts matter into the world at {@code at}: whole blocks from there into the free room nearest it, items dropped
     * there, a gas shown there. What finds no room, or does not make a whole unit, is left over (L1).
     */
    public static Placed place(ServerLevel level, BlockPos at, Matter matter) {
        double placed = 0.0D;
        double leftover = 0.0D;
        List<BlockPos> blocks = new ArrayList<>();
        for (Placement placement : Placement.plan(table(), matter)) {
            leftover += placement.leftover();
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

    /**
     * Where {@code units} blocks go: from {@code at} outward, the nearest places that can take a block (air, plants,
     * flowing fluids, never a fluid's source) and do not already hold this one.
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
            if (!free && !pos.equals(at)) {
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

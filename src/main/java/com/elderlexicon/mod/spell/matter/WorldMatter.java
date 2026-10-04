package com.elderlexicon.mod.spell.matter;

import com.elderlexicon.mod.magic.matter.Form;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.Placement;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.magic.physics.Field;
import com.elderlexicon.mod.spell.nature.NatureWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The world read as matter, and matter put into the world, by the material table in force
 * (docs/plano-materia-e-forca.md, stages 3 and 5, and docs/particulas-design.md, stage 9). Nothing here knows one
 * substance from another: a block is what the table reads it as, and matter shows as the form the table gives its
 * substance in its state, or as formless matter ({@link FormlessMatterBlock}) holding its particles exactly when it has
 * none there. What floats goes into the air, where the drives take it. A liquid put where liquid is mixes with it (L4)
 * with the agitation each brought; and whatever matter does once it is in the world (boil, burn, set, fly as dust) is
 * the drives' to do, never a law of the way in.
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
    /** An agitation this close to what matter has by its nature is its nature (about 6 K). */
    private static final double REST = 0.02D;

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
     * @param mixture  what the matter and the body it was poured into became; null when it was only put down
     * @param entities the items dropped and the creatures that showed it
     */
    public record Placed(double placed, double leftover, List<BlockPos> blocks, Matter mixture, List<Entity> entities) {

        public Placed(double placed, double leftover, List<BlockPos> blocks) {
            this(placed, leftover, blocks, null, List.of());
        }

        public Placed(double placed, double leftover, List<BlockPos> blocks, Matter mixture) {
            this(placed, leftover, blocks, mixture, List.of());
        }

        public Optional<Matter> mixed() {
            return Optional.ofNullable(mixture);
        }
    }

    /**
     * Puts matter into the world at {@code at}, as agitated as it is by its nature: what floats into the air there, a
     * liquid put where liquid is poured into it (L4, {@link #pour}), anything else laid down: whole blocks from there
     * into the free room nearest it, items dropped there, formless matter where it has no look. What finds no room, or
     * does not make a whole unit, is left over (L1).
     */
    public static Placed place(ServerLevel level, BlockPos at, Matter matter) {
        return place(level, at, matter, natural(matter));
    }

    /**
     * As {@link #place(ServerLevel, BlockPos, Matter)}, at {@code temperature} (a mixture's): where that is not its
     * nature, what it went into is awake, and the drives carry it on from there.
     */
    public static Placed place(ServerLevel level, BlockPos at, Matter matter, double temperature) {
        if (MaterialTable.floats(matter.state())) {
            return air(level, at, matter, temperature);
        }
        if (matter.state() == State.LIQUID && holdsFluid(level, at)) {
            return pour(level, at, matter, temperature);
        }
        Placed laid = lay(level, at, matter);
        settle(level, laid.blocks(), matter, temperature);
        return laid;
    }

    /** Whether the block at {@code pos} is fluid matter to pour into: a fluid's source, formless liquid; never air. */
    public static boolean holdsFluid(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            return false;
        }
        return read(level, pos).map(matter -> matter.state().fluid()).orElse(false);
    }

    /** How agitated matter is by its nature: as the world at rest, or as its state needs (molten, frozen). */
    public static double natural(Matter matter) {
        return MaterialTable.floats(matter.state()) ? 1.0D : Field.natural(matter.particles(), matter.state());
    }

    /** Matter, and how agitated it is. */
    public record Mixed(Matter matter, double temperature) {
    }

    /**
     * Portions of matter brought to one place become one (L4): all their particles, as agitated as what each brought
     * (each by its nature) makes the whole, in the state the drives give it there. Empty when there is nothing.
     */
    public static Optional<Mixed> mix(List<Matter> portions) {
        List<Field.Portion> lots = new ArrayList<>();
        for (Matter portion : portions) {
            Particles held = portion.particles().present();
            if (held.total() > 0L) {
                lots.add(new Field.Portion(held, natural(portion)));
            }
        }
        return mixed(lots);
    }

    private static Optional<Mixed> mixed(List<Field.Portion> lots) {
        if (lots.isEmpty()) {
            return Optional.empty();
        }
        Field.Mixture mixture = Field.mix(lots);
        return Optional.of(new Mixed(Matter.of(mixture.held(), mixture.state()), mixture.temperature()));
    }

    /**
     * L4: {@code poured} mixes with the body of fluid matter at {@code at} (the blocks of the same matter joined to it,
     * nearest first, up to {@link #MAX_BODY}), with the agitation each has. When the mixture is still what the body was,
     * as with water poured into water, the body stays and what was poured is added to it as more of it; otherwise the
     * body is taken up and the mixture put in its place, in the state the drives give it: a natural thing near its code,
     * formless matter, or what floats into the air. What floats does not mix: fire poured into water is agitation that
     * warms it, air bubbles through it.
     */
    public static Placed pour(ServerLevel level, BlockPos at, Matter poured) {
        return pour(level, at, poured, natural(poured));
    }

    private static Placed pour(ServerLevel level, BlockPos at, Matter poured, double temperature) {
        if (MaterialTable.floats(poured.state())) {
            return air(level, at, poured, temperature);
        }
        Optional<Matter> there = read(level, at);
        if (there.isEmpty() || !there.get().state().fluid()) {
            Placed laid = lay(level, at, poured);
            settle(level, laid.blocks(), poured, temperature);
            return laid;
        }
        List<BlockPos> body = bodyAt(level, at, there.get());
        List<Field.Portion> lots = new ArrayList<>();
        for (BlockPos pos : body) {
            read(level, pos).ifPresent(part -> lots.add(new Field.Portion(part.particles(),
                    NatureWorld.temperature(level, pos))));
        }
        lots.add(new Field.Portion(poured.particles(), temperature));
        Mixed mixed = mixed(lots).orElseThrow();
        Matter mixture = mixed.matter();
        MaterialTable table = table();
        Optional<Substance> was = there.get().substance(table);
        Optional<Substance> becomes = mixture.substance(table);
        boolean formless = level.getBlockEntity(at) instanceof FormlessMatterBlockEntity;
        if (!formless && becomes.isPresent() && becomes.equals(was) && mixture.state() == there.get().state()) {
            // Still what it was: the body takes in what was poured as more of itself, and only that needs room.
            Placed grown = lay(level, at, Matter.of(becomes.get(), mixture.state(), poured.umu()));
            List<BlockPos> all = new ArrayList<>(body);
            all.addAll(grown.blocks());
            settle(level, all, mixture, mixed.temperature());
            return new Placed(grown.placed(), grown.leftover(), grown.blocks(), mixture);
        }
        for (BlockPos pos : body) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        Placed made = place(level, at, mixture, mixed.temperature());
        return new Placed(made.placed(), made.leftover(), made.blocks(), mixture, made.entities());
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

    /** Whether two portions are the same matter: the same composition, in the same state. */
    private static boolean same(Matter one, Matter other) {
        return one.state() == other.state() && one.composition().distance(other.composition()) <= 1.0E-6D;
    }

    /**
     * Lays matter down at {@code at}, mixing with nothing: whole blocks from there into the free room nearest it, items
     * dropped there, formless matter where it has no look, each block of it holding its share of the particles exactly;
     * what floats goes into the air.
     */
    private static Placed lay(ServerLevel level, BlockPos at, Matter matter) {
        Optional<Placement> plan = Placement.plan(table(), matter);
        if (plan.isEmpty()) {
            return new Placed(0.0D, 0.0D, List.of());
        }
        Placement placement = plan.get();
        if (placement.floats()) {
            return air(level, at, matter, natural(matter));
        }
        if (placement.formless()) {
            BlockState block = formless(placement.state(), natural(matter));
            List<Particles> shares = matter.particles().present().split(placement.units());
            List<BlockPos> room = room(level, at, block, placement.units());
            double placed = 0.0D;
            for (int unit = 0; unit < room.size(); unit++) {
                showFormless(level, room.get(unit), block, shares.get(unit));
                placed += shares.get(unit).umu();
            }
            return new Placed(placed, Math.max(0.0D, matter.umu() - placed), room);
        }
        Form form = placement.form();
        if (form == null || placement.units() <= 0) {
            return new Placed(0.0D, placement.leftover(), List.of());
        }
        double unit = placement.placed() / placement.units();
        switch (form.kind()) {
            case BLOCK -> {
                Optional<BlockState> state = block(form.id());
                if (state.isEmpty()) {
                    return new Placed(0.0D, matter.umu(), List.of());
                }
                List<BlockPos> room = room(level, at, state.get(), placement.units());
                for (BlockPos pos : room) {
                    level.setBlock(pos, state.get(), Block.UPDATE_ALL);
                }
                return new Placed(unit * room.size(), placement.leftover() + unit * (placement.units() - room.size()),
                        room);
            }
            case ITEM -> {
                Optional<Item> item = item(form.id());
                if (item.isEmpty()) {
                    return new Placed(0.0D, matter.umu(), List.of());
                }
                List<Entity> dropped = drop(level, Vec3.atCenterOf(at), item.get(), placement.units());
                return new Placed(placement.placed(), placement.leftover(), List.of(), null, dropped);
            }
            default -> {
                return new Placed(0.0D, matter.umu(), List.of());
            }
        }
    }

    /**
     * What floats goes into the air at {@code at} whole, as agitated as {@code temperature}: its earth as dust, its water
     * as vapour keeping the fire boiling took, its air, and its fire as agitation, which may be a flame, or lightning.
     * It is seen as the table shows it, or as a cloud of its colour when it has no look. Nothing goes in where the
     * drives cannot reach (a wall, too much awake at once), and then all of it is left over.
     */
    private static Placed air(ServerLevel level, BlockPos at, Matter matter, double temperature) {
        Particles gas = matter.particles().present();
        if (gas.total() <= 0L) {
            return new Placed(0.0D, 0.0D, List.of());
        }
        if (!NatureWorld.blow(level, at, gas)) {
            return new Placed(0.0D, matter.umu(), List.of());
        }
        long warmer = Field.agitation(Particles.NONE, gas, temperature);
        if (warmer > 0L) {
            NatureWorld.heat(level, at, warmer);
        }
        Optional<Placement> plan = Placement.plan(table(), matter);
        Form form = plan.map(Placement::form).orElse(null);
        if (form == null) {
            cloud(level, at, matter);
        } else if (form.kind() == Form.Kind.PARTICLE) {
            particle(form.id()).ifPresent(options -> {
                Vec3 center = Vec3.atCenterOf(at);
                int count = (int) Math.max(4, Math.min(60, Math.round(8.0D * matter.umu())));
                level.sendParticles(options, center.x, center.y, center.z, count, 0.6D, 0.6D, 0.6D, 0.02D);
            });
        }
        return new Placed(matter.umu(), 0.0D, List.of());
    }

    /**
     * What was put at {@code temperature} where that is not its nature goes into the drives there, as agitated as it
     * came: a quenched melt still hot, a mixture warmer than it rests at.
     */
    private static void settle(ServerLevel level, List<BlockPos> blocks, Matter matter, double temperature) {
        if (blocks.isEmpty() || Math.abs(temperature - natural(matter)) <= REST) {
            return;
        }
        for (BlockPos pos : blocks) {
            NatureWorld.settle(level, pos, temperature);
        }
    }

    /** Drops {@code count} of an item at {@code at}, in stacks as full as the item allows; returns the stacks. */
    public static List<Entity> drop(ServerLevel level, Vec3 at, Item item, long count) {
        List<Entity> dropped = new ArrayList<>();
        long left = count;
        while (left > 0L) {
            int stack = (int) Math.min(left, item.getMaxStackSize());
            ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item, stack));
            level.addFreshEntity(entity);
            dropped.add(entity);
            left -= stack;
        }
        return dropped;
    }

    /** An item that shows a substance, and the particles one of it holds. */
    public record AsItem(Item item, long particles) {
    }

    /**
     * The item matter of a substance is in a state, for what was items and stays items: the substance's own item there,
     * or else the item of its block (a block of stone, 4096 particles); empty when it has neither, as water, fire and air
     * have none.
     */
    public static Optional<AsItem> asItem(Substance substance, com.elderlexicon.mod.magic.matter.State state) {
        for (Form form : substance.declared(state)) {
            if (form.kind() == Form.Kind.ITEM) {
                Optional<Item> item = item(form.id());
                if (item.isPresent()) {
                    return Optional.of(new AsItem(item.get(), form.particles()));
                }
            }
        }
        for (Form form : substance.declared(state)) {
            if (form.kind() == Form.Kind.BLOCK) {
                Optional<Item> item = block(form.id()).map(found -> found.getBlock().asItem())
                        .filter(found -> found != net.minecraft.world.item.Items.AIR);
                if (item.isPresent()) {
                    return Optional.of(new AsItem(item.get(), form.particles()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The block formless matter in {@code state} shows as, glowing as {@code temperature} makes it: solid, or liquid
     * for anything that flows.
     */
    public static BlockState formless(State state, double temperature) {
        Block block = state == State.SOLID ? MatterBlocks.FORMLESS_SOLID.get() : MatterBlocks.FORMLESS_LIQUID.get();
        return block.defaultBlockState().setValue(FormlessMatterBlock.GLOW, FormlessMatterBlock.glowOf(temperature));
    }

    /**
     * Shows formless matter at {@code pos}, whatever was there: {@code block} (one of {@link #formless}), holding
     * exactly {@code held} (what the drives made of a block, docs/particulas-design.md).
     */
    public static void showFormless(ServerLevel level, BlockPos pos, BlockState block, Particles held) {
        level.setBlock(pos, block, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity entity) {
            entity.hold(held);
        }
    }

    /**
     * The formless matter at {@code pos} holds {@code held} now, as the drives moved it: kept without a word to those
     * who see it, since its block has not changed.
     */
    public static void keep(ServerLevel level, BlockPos pos, Particles held) {
        if (level.getBlockEntity(pos) instanceof FormlessMatterBlockEntity entity
                && !entity.particles().map(held.present()::equals).orElse(false)) {
            entity.hold(held, false);
        }
    }

    /** The block the table shows {@code matter} as, or empty when it shows as formless matter or not as a block. */
    public static Optional<BlockState> blockOf(Matter matter) {
        MaterialTable table = table();
        return matter.substance(table)
                .flatMap(substance -> table.form(substance, matter.state()))
                .filter(form -> form.kind() == Form.Kind.BLOCK)
                .flatMap(form -> block(form.id()));
    }

    // ------------------------------------------------------------------ formless matter

    /** A gas with no look of its own shows as a cloud of its colour at {@code at}, and spreads into the air. */
    private static void cloud(ServerLevel level, BlockPos at, Matter matter) {
        int rgb = FormlessMatterBlockEntity.colorOf(matter);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F), 1.5F);
        Vec3 center = Vec3.atCenterOf(at);
        int count = (int) Math.max(6, Math.min(80, Math.round(10.0D * matter.umu())));
        level.sendParticles(dust, center.x, center.y, center.z, count, 0.8D, 0.8D, 0.8D, 0.02D);
    }

    /**
     * Formless matter broken or blown up (already gone from {@code pos}): nothing holds it together any more, and its
     * particles go into the air there, earth as dust carrying its fire, water as mist, where the drives take them.
     */
    static void scatter(ServerLevel level, BlockPos pos, Particles held) {
        NatureWorld.scatter(level, pos, held);
        Vec3 center = Vec3.atCenterOf(pos);
        int count = (int) Math.max(2, Math.min(20, Math.round(4.0D * held.umu())));
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

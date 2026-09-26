package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.Capture;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.sight.Revelation;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.command.SpellCostCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Exsugat, absorbing (book 8.2): it pulls a source from outside the body, "o fogo de uma fogueira próxima, a água de
 * um lago próximo". Written before other functions it captures what they spend, so the mage's own Vita is spared
 * ({@code igni exsugat iactare}); written last, what it pulls goes into the body ({@code igni exsugat}, when cold).
 * The sources are taken whole, the nearest to where the mage aims first (or to the mage, aiming at nothing).
 */
public final class ExsugatFunctionHandler implements SpellFunctionHandler {

    /** How far from the aim, or the mage, a source is pulled from. */
    private static final int RANGE = 5;
    /** Without a quantum, a bare exsugat pulls what a function spends by default (book 4.3.2: ten UMU). */
    public static final double DEFAULT_ABSORBED_UMU = EmissionRecorder.DEFAULT_QUANTITY_UMU;
    private static final double EPSILON = 1.0E-4D;
    private static Field furnaceData;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        Optional<SpellAction> action = context.currentAction();
        if (action.isPresent() && action.get().image() && SpellEffects.isPlayerValid(context.player())) {
            ImageSpells.absorb(context, element, action.get());
            return;
        }
        if (action.flatMap(SpellAction::subjectMark).isPresent() && SpellEffects.isPlayerValid(context.player())) {
            // m1 exsugat: the marked thing is pulled toward the mage, like a magnet.
            MarkSpells.push(context, action.get().subjectMark().get(), action.get().place(), MarkSpells.Push.TOWARD_CASTER,
                    action.get().quantity().orElse(MarkCost.DEFAULT_THROW_ENERGY), Chronos.window(action.get()));
            return;
        }
        // Nothing after it to feed: what is pulled goes into the body.
        absorb(context, element, element, action.map(SpellAction::quantity).orElse(OptionalDouble.empty())
                .orElse(DEFAULT_ABSORBED_UMU));
    }

    /**
     * Pulls {@code amount} of {@code element} into the mage's body, as {@code becomes} (converted on the way when a
     * vertere came after: {@code igni exsugat vertere aqua}, with nothing to spend it on, quenches thirst).
     */
    public static void absorb(SpellContext context, VitaElement element, VitaElement becomes, double amount) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return;
        }
        double pulled = pull(context, element, amount);
        if (pulled > EPSILON) {
            context.absorbIntoBody(becomes, pulled);
            context.player().displayClientMessage(Component.literal("Absorveste "
                    + SpellCostCalculator.formatCost(pulled) + " UMU de " + becomes.runeId() + "."), true);
        } else {
            nothingFound(context, element);
        }
    }

    /**
     * Pays the spell with what is pulled from outside: the functions after the exsugat spend the captured source instead
     * of the mage's Vita. What the last source brings beyond the cost goes into the body, as {@code becomes}.
     */
    public static void capture(SpellContext context, VitaElement element, VitaElement becomes) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return;
        }
        double needed = context.payableCost();
        if (needed <= EPSILON) {
            return;
        }
        double pulled = pull(context, element, needed);
        if (pulled <= EPSILON) {
            nothingFound(context, element);
            return;
        }
        context.addAmbientEnergy(becomes, Math.min(pulled, needed));
        if (pulled > needed + EPSILON) {
            context.absorbIntoBody(becomes, pulled - needed);
        }
    }

    /**
     * Pulls every source of {@code element} in reach at once, for a function written after a bare quantum
     * ({@code igni exsugat quantum iactare}: "o fogo é completamente extraído e lançado de uma só vez", book 4.3.2).
     * Returns how much it held, all of it spent by that function.
     */
    public static double captureAll(SpellContext context, VitaElement element, VitaElement becomes) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return 0.0D;
        }
        double pulled = pull(context, element, Double.MAX_VALUE);
        if (pulled <= EPSILON) {
            nothingFound(context, element);
            return 0.0D;
        }
        context.addAmbientEnergy(becomes, pulled);
        return pulled;
    }

    private static void nothingFound(SpellContext context, VitaElement element) {
        MarkSpells.tell(context.player(), "Exsugat: nao ha " + element.runeId() + " por perto para puxar; o corpo pagou.");
    }

    /** Takes whole sources of {@code element}, nearest first, until {@code needed} UMU; returns what they held. */
    private static double pull(SpellContext context, VitaElement element, double needed) {
        List<Source> taken = take(context, element, needed);
        for (Source source : taken) {
            SpellEffects.spawnDrainParticles(context.player(), element, context.elementRuneId(), source.pos(),
                    source.value());
        }
        return taken.stream().mapToDouble(Source::value).sum();
    }

    /** Drains whole sources of {@code element}, nearest to the aim first, until {@code needed} UMU; returns them. */
    private static List<Source> take(SpellContext context, VitaElement element, double needed) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Optional<Revelation.Kind> kind = kindOf(element);
        if (kind.isEmpty() || needed <= EPSILON) {
            return List.of();
        }
        Optional<BlockPos> aimed = aimedBlock(player);
        Vec3 center = aimed.map(Vec3::atCenterOf).orElse(player.position());
        // Standing in an impediunt of the element, what surrounds the mage is its ring: the reach takes it all in.
        double range = RANGE;
        Optional<ImpediuntZones.Edge> zone = ImpediuntZones.zoneAt(level, player.position(), element);
        if (zone.isPresent()) {
            center = zone.get().center();
            range = Math.max(RANGE, zone.get().radius() + 2.0D);
        }
        // Aiming at nothing, the ground the mage stands on is not pulled out from under the feet.
        AABB feet = aimed.isPresent() || kind.get() != Revelation.Kind.FIRMO ? null
                : player.getBoundingBox().expandTowards(0.0D, -0.6D, 0.0D);

        List<Source> sources = new ArrayList<>();
        BlockPos origin = BlockPos.containing(center);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double rangeSq = range * range + 1.0D;
        int reach = (int) Math.ceil(range);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -RANGE; dy <= RANGE; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    double distanceSq = center.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (distanceSq > rangeSq || !level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (feet != null && feet.intersects(new AABB(cursor))) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    double value = valueOf(kind.get(), level, cursor, state);
                    if (value > EPSILON) {
                        sources.add(new Source(cursor.immutable(), distanceSq, value, state));
                    }
                }
            }
        }
        sources.sort(Comparator.comparingDouble(Source::distanceSq));
        Capture.Taken taken = Capture.take(sources.stream().map(Source::value).toList(), needed);
        List<Source> drained = List.copyOf(sources.subList(0, taken.sources()));
        for (Source source : drained) {
            drain(kind.get(), level, source.pos(), source.state());
        }
        return drained;
    }

    /**
     * The captured source turned into another where it is ({@code firmo exsugat vertere igni}, with nothing after to
     * spend it): the earth nearby becomes fire, the fire water, taking whole sources nearest the aim until
     * {@code amount} UMU of the source have been converted.
     */
    public static void convertInPlace(SpellContext context, VitaElement element, VitaElement becomes, double amount) {
        if (!SpellEffects.isPlayerValid(context.player())) {
            return;
        }
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        List<Source> taken = take(context, element, amount);
        if (taken.isEmpty()) {
            nothingFound(context, element);
            return;
        }
        for (Source source : taken) {
            become(level, source.pos(), element, becomes);
        }
        boolean one = taken.size() == 1;
        player.displayClientMessage(Component.literal(taken.size() + (one ? " fonte de " : " fontes de ")
                + element.runeId() + (one ? " convertida em " : " convertidas em ") + becomes.runeId() + "."), true);
    }

    /** Where a source was drained, what it was converted into appears. */
    private static void become(ServerLevel level, BlockPos pos, VitaElement from, VitaElement to) {
        BlockState now = level.getBlockState(pos);
        Vec3 at = Vec3.atCenterOf(pos);
        switch (to) {
            case AQUA -> {
                if (now.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    // A quenched campfire, a candle: the water stays in the block.
                    level.setBlock(pos, now.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
                } else if (now.canBeReplaced()) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                }
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 12, 0.3D, 0.3D, 0.3D, 0.0D);
            }
            case IGNI -> {
                if (now.canBeReplaced()) {
                    // Fire where it can burn; where it would hang in the air, the earth's heat stays as magma.
                    BlockState fire = BaseFireBlock.getState(level, pos);
                    level.setBlock(pos, fire.canSurvive(level, pos) ? fire : Blocks.MAGMA_BLOCK.defaultBlockState(),
                            Block.UPDATE_ALL);
                }
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.3D, 0.3D, 0.3D, 0.01D);
            }
            case FIRMO -> {
                if (now.canBeReplaced()) {
                    // Water turned to earth is mud; anything else, plain soil.
                    BlockState earth = from == VitaElement.AQUA ? Blocks.MUD.defaultBlockState()
                            : Blocks.DIRT.defaultBlockState();
                    level.setBlock(pos, earth, Block.UPDATE_ALL);
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                        at.x, at.y, at.z, 16, 0.3D, 0.3D, 0.3D, 0.0D);
            }
            default -> level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 10, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    private record Source(BlockPos pos, double distanceSq, double value, BlockState state) {
    }

    private static Optional<Revelation.Kind> kindOf(VitaElement element) {
        return switch (element) {
            case IGNI -> Optional.of(Revelation.Kind.IGNI);
            case AQUA -> Optional.of(Revelation.Kind.AQUA);
            case FIRMO -> Optional.of(Revelation.Kind.FIRMO);
            default -> Optional.empty();
        };
    }

    /** The block the mage aims at within touch, water and lava included. */
    private static Optional<BlockPos> aimedBlock(ServerPlayer player) {
        double reach = Math.max(1.0D, player.getBlockReach());
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit.getBlockPos()) : Optional.empty();
    }

    // ------------------------------------------------------------------ what each source holds, and what it leaves

    /** The UMU a block gives up to exsugat, 0 when it holds none it can give. */
    static double valueOf(Revelation.Kind kind, ServerLevel level, BlockPos pos, BlockState state) {
        return switch (kind) {
            case IGNI -> igniValue(level, pos, state);
            case AQUA -> aquaValue(state);
            case FIRMO -> firmoValue(level, pos, state);
            default -> 0.0D;
        };
    }

    private static double igniValue(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.FIRE) || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
            return 1.0D;
        }
        if (state.getBlock() instanceof AbstractCandleBlock && state.hasProperty(AbstractCandleBlock.LIT)
                && state.getValue(AbstractCandleBlock.LIT)) {
            return 0.5D;
        }
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            return 3.0D;
        }
        if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
            return 2.0D;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return 2.0D;
        }
        if (state.getFluidState().is(FluidTags.LAVA) && state.getFluidState().isSource()) {
            return 10.0D;
        }
        return 0.0D;
    }

    private static double aquaValue(BlockState state) {
        if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()) {
            return 3.0D;
        }
        if (state.is(Blocks.WATER_CAULDRON)) {
            return state.getValue(LayeredCauldronBlock.LEVEL);
        }
        if (state.is(Blocks.WET_SPONGE)) {
            return 3.0D;
        }
        if (state.is(Blocks.MUD)) {
            return 1.0D;
        }
        if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) > 0) {
            return 1.0D;
        }
        return 0.0D;
    }

    /**
     * Earth gives what the revelation sees in it, its hardness (docs/surgit-visao-design.md): loose soil little, rock
     * more, obsidian a great deal. What holds things (a chest, a furnace) is not torn apart for its earth.
     */
    private static double firmoValue(ServerLevel level, BlockPos pos, BlockState state) {
        if (!RevelationSight.carries(Revelation.Kind.FIRMO, state) || state.hasBlockEntity()) {
            return 0.0D;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness < 0.0F ? 0.0D : Revelation.earthUmu(hardness);
    }

    /** Takes the source out of the block: the flame goes out, the water dries, the earth crumbles away. */
    private static void drain(Revelation.Kind kind, ServerLevel level, BlockPos pos, BlockState state) {
        switch (kind) {
            case IGNI -> drainIgni(level, pos, state);
            case AQUA -> drainAqua(level, pos, state);
            case FIRMO -> level.destroyBlock(pos, false);
            default -> {
            }
        }
    }

    private static void drainIgni(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
        } else if (state.is(Blocks.JACK_O_LANTERN)) {
            level.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
                    Block.UPDATE_ALL);
        } else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            // The flame goes; the stick it burned on is left.
            level.removeBlock(pos, false);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D,
                    new ItemStack(Items.STICK));
        } else if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
            level.removeBlock(pos, false);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D,
                    new ItemStack(Items.IRON_NUGGET, 8));
        } else if (state.getBlock() instanceof AbstractCandleBlock) {
            level.setBlock(pos, state.setValue(AbstractCandleBlock.LIT, false), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof CampfireBlock) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof AbstractFurnaceBlock) {
            quenchFurnace(level, pos);
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, false), Block.UPDATE_ALL);
        } else if (state.is(Blocks.MAGMA_BLOCK)) {
            level.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.getFluidState().is(FluidTags.LAVA)) {
            // Lava without its heat is rock.
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void drainAqua(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.WATER_CAULDRON)) {
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.WET_SPONGE)) {
            level.setBlock(pos, Blocks.SPONGE.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.MUD)) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.FARMLAND)) {
            level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 0), Block.UPDATE_ALL);
        } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * A lit furnace only looks lit while its fuel burns, so the burning itself is put out, or it would light again at
     * once and its fire could be pulled forever. The game keeps the burn time private, behind the furnace's data.
     */
    private static void quenchFurnace(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }
        try {
            if (furnaceData == null) {
                for (Field field : AbstractFurnaceBlockEntity.class.getDeclaredFields()) {
                    if (field.getType() == ContainerData.class) {
                        field.setAccessible(true);
                        furnaceData = field;
                        break;
                    }
                }
            }
            if (furnaceData != null) {
                ((ContainerData) furnaceData.get(furnace)).set(0, 0); // 0 is the burn time left
                furnace.setChanged();
            }
        } catch (ReflectiveOperationException ignored) {
            // The furnace just lights again; the flame was still taken this once.
        }
    }
}

package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.invocation.ThrownThing;
import com.elderlexicon.mod.spell.Casters;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code m1 iactare} when what bears m1 is a weapon or a tool, or a thing the invocation bench made
 * (docs/invocacao-design.md, section 5.1): it is not pushed, as other marked things are, but thrown as a blow, point
 * first, at the aim or at the ubis place ({@code ille ubis}: the one just struck). The quantity is the strength of the
 * throw, as for any marked thing.
 * <p>
 * Kept things answer too, all of them at once in one blow: lying on the ground they rise from where they lie; carried
 * by the mage they come out around the mage, fanned behind the shoulders; carried by someone else, from that one; in a
 * chest, a barrel or any container loaded near the mage, from over it.
 */
final class Strikes {

    /** Thrown things rise from where they lie this much, to clear the ground or the bench they are on. */
    private static final double LIFT = 0.3D;
    /** How far from the mage a container is looked into for kept things. */
    private static final double CONTAINER_REACH = 32.0D;
    /** Things that come out of the mage's own pack: how high, how far behind, how far apart. */
    private static final double SHOULDER = 1.7D;
    private static final double BEHIND = 0.4D;
    private static final double APART = 0.7D;

    private Strikes() {
    }

    /** One thing that will be thrown: what it is, where it leaves from, and how it is taken from where it is kept. */
    private record Striker(ItemStack thing, Vec3 from, Runnable take) {
    }

    /** Throws, as blows, the things bearing {@code mark} that strike; false when none does (they are pushed instead). */
    static boolean launch(SpellContext context, String mark, Optional<SpellPlace> place, double energy) {
        LivingEntity player = context.player();
        ServerLevel level = Casters.level(player);
        List<Striker> strikers = new ArrayList<>();
        lying(level, mark, strikers);
        carried(level, player, mark, strikers);
        contained(level, player, mark, strikers);
        if (strikers.isEmpty()) {
            return false;
        }
        Optional<Aim> aim = aim(context, place);
        if (aim.isEmpty()) {
            MarkSpells.tell(player, "Nada ao alcance para lancar '" + mark + "'.");
            return true;
        }
        for (Striker striker : strikers) {
            Vec3 way = aim.get().point().subtract(striker.from());
            if (way.lengthSqr() < 1.0E-6D) {
                continue;
            }
            MarkCost.Throw thrown = MarkCost.throwWith(energy, MarkCost.ITEM_MASS, MarkCost.MAX_ENTITY_SPEED);
            context.addTotalCost(thrown.cost());
            striker.take().run();
            ThrownThing blow = new ThrownThing(level, striker.thing(), player, striker.from());
            blow.shoot(way.x, way.y, way.z, (float) thrown.speed(), 0.0F);
            blow.homeOn(aim.get().creature());
            level.addFreshEntity(blow);
            level.playSound(null, striker.from().x, striker.from().y, striker.from().z, SoundEvents.TRIDENT_THROW,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        return true;
    }

    /** Marked things lying in the world: each rises from where it lies. */
    private static void lying(ServerLevel level, String mark, List<Striker> strikers) {
        for (MarkTargets.Marked thing : MarkTargets.find(level.getServer(), mark)) {
            if (thing.entity instanceof ItemEntity lying && lying.isAlive() && lying.level() == level
                    && ThrownThing.strikes(lying.getItem())) {
                strikers.add(new Striker(lying.getItem().copy(), lying.position().add(0.0D, LIFT, 0.0D),
                        lying::discard));
            }
        }
    }

    /**
     * Marked things carried by anyone in the mage's world, one throw for each of them (a marked stack of arrows is so
     * many arrows): the mage's own fanned around the mage, another's from the one carrying them.
     */
    private static void carried(ServerLevel level, LivingEntity mage, String mark, List<Striker> strikers) {
        List<Striker> own = new ArrayList<>();
        for (ServerPlayer bearer : level.players()) {
            for (List<ItemStack> compartment : List.of(bearer.getInventory().items, bearer.getInventory().offhand)) {
                for (int slot = 0; slot < compartment.size(); slot++) {
                    ItemStack stack = compartment.get(slot);
                    if (!marked(stack, mark)) {
                        continue;
                    }
                    for (int one = 0; one < stack.getCount(); one++) {
                        Striker striker = new Striker(stack.copyWithCount(1), bearer.getEyePosition(),
                                () -> stack.shrink(1));
                        (bearer == mage ? own : strikers).add(striker);
                    }
                }
            }
        }
        for (int i = 0; i < own.size(); i++) {
            strikers.add(new Striker(own.get(i).thing(), fanned(mage, i, own.size()), own.get(i).take()));
        }
    }

    /** Marked things kept in containers loaded near the mage: each from over its container. */
    private static void contained(ServerLevel level, LivingEntity mage, String mark, List<Striker> strikers) {
        ChunkPos centre = new ChunkPos(mage.blockPosition());
        int chunks = (int) Math.ceil(CONTAINER_REACH / 16.0D);
        for (int x = centre.x - chunks; x <= centre.x + chunks; x++) {
            for (int z = centre.z - chunks; z <= centre.z + chunks; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity kept : chunk.getBlockEntities().values()) {
                    BlockPos pos = kept.getBlockPos();
                    if (!(kept instanceof Container container)
                            || pos.distToCenterSqr(mage.position()) > CONTAINER_REACH * CONTAINER_REACH) {
                        continue;
                    }
                    Vec3 over = Vec3.atCenterOf(pos).add(0.0D, 0.9D, 0.0D);
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!marked(stack, mark)) {
                            continue;
                        }
                        int taken = slot;
                        for (int one = 0; one < stack.getCount(); one++) {
                            strikers.add(new Striker(stack.copyWithCount(1), over, () -> {
                                container.removeItem(taken, 1);
                                container.setChanged();
                            }));
                        }
                    }
                }
            }
        }
    }

    private static boolean marked(ItemStack stack, String mark) {
        return !stack.isEmpty() && ThrownThing.strikes(stack)
                && MarkHelper.markForItem(stack).filter(mark::equals).isPresent();
    }

    /** The {@code index}th of {@code count} things out of the mage's pack: a row behind the shoulders, across. */
    private static Vec3 fanned(LivingEntity mage, int index, int count) {
        Vec3 look = Vec3.directionFromRotation(0.0F, mage.getYRot());
        Vec3 across = new Vec3(-look.z, 0.0D, look.x);
        double offset = (index - (count - 1) / 2.0D) * APART;
        double rise = 0.25D * Math.abs(index - (count - 1) / 2.0D);
        return mage.position().add(0.0D, SHOULDER + rise, 0.0D).subtract(look.scale(BEHIND)).add(across.scale(offset));
    }

    /** Where a blow goes, and the creature there, if one is, to be followed. */
    private record Aim(Vec3 point, Entity creature) {
    }

    /** Where the blow goes: the ubis place, or the aim; a creature there is struck in the middle of its body. */
    private static Optional<Aim> aim(SpellContext context, Optional<SpellPlace> place) {
        if (place.isPresent()) {
            return MarkSpells.destination(context, place, MarkSpells.THROW_AIM_RANGE)
                    .map(found -> new Aim(middleOf(found.entity(), found.point()), living(found.entity())));
        }
        SpellEffects.SpellImpact impact = SpellEffects.findImpact(context.player(), MarkSpells.THROW_AIM_RANGE);
        return Optional.of(new Aim(middleOf(impact.entity(), impact.location()), living(impact.entity())));
    }

    private static Entity living(Entity entity) {
        return entity instanceof LivingEntity ? entity : null;
    }

    private static Vec3 middleOf(Entity entity, Vec3 point) {
        return entity == null ? point : entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }
}

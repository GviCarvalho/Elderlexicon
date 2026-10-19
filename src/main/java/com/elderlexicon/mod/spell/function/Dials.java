package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.item.ArcaneCompassItem;
import com.elderlexicon.mod.ligabis.Link;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.spell.Mana;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import com.elderlexicon.mod.spell.sight.Illusion;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The nuggets of arcane compasses as places of the spirit (docs/bussola-arcana-design.md): a nugget set in a dial bears
 * its mark, and what a spell puts on it is shown on the dial. Three things land there:
 * <ul>
 *   <li>{@code igni quantum ego surgit p1 ubis vocant}: the image of how much fire ego holds, small, on the nugget;</li>
 *   <li>{@code p1 alvo ubis iactare}: the nugget pushed toward a place, held in the dial: a needle;</li>
 *   <li>{@code aura alvo ligabis p1}: the nugget pulled along with what it is bound to, held in the dial: its speed.</li>
 * </ul>
 */
public final class Dials {

    /** A nugget is a ninth of an ingot, an ingot a ninth of a block: what is shown in it is that much smaller. */
    static final double NUGGET_SCALE = 1.0D / 81.0D;
    /** How long a push keeps the needle where it pointed, when no chronos says otherwise: one second. */
    private static final int NEEDLE_TICKS = 20;
    /** How far a lodestone draws an iron needle, in blocks. */
    private static final double LODESTONE_REACH = 6.0D;
    /** How often the speed needle is written on the dial, in ticks. */
    private static final int SPEED_STEP = 5;

    private Dials() {
    }

    /** A nugget bearing a mark, set in a dial that someone has. */
    record Anchor(String mark, ItemStack stack, ArcaneCompassItem.Metal metal, int slot, Entity holder) {
    }

    /** The nugget bearing {@code mark}, in a dial a player carries. */
    static Optional<Anchor> find(MinecraftServer server, String mark) {
        if (server == null || mark == null || mark.isBlank()) {
            return Optional.empty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (List<ItemStack> compartment : List.of(player.getInventory().items, player.getInventory().offhand)) {
                for (ItemStack stack : compartment) {
                    if (stack.getItem() instanceof ArcaneCompassItem) {
                        int slot = ArcaneCompassItem.nuggets(stack).indexOf(mark);
                        if (slot >= 0) {
                            return Optional.of(new Anchor(mark, stack, ArcaneCompassItem.nuggetMetal(stack, slot),
                                    slot, player));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Whether a nugget set in a dial bears {@code mark}. */
    public static boolean bears(MinecraftServer server, String mark) {
        return find(server, mark).isPresent();
    }

    private static long now(Anchor anchor) {
        return anchor.holder().level().getGameTime();
    }

    // ------------------------------------------------------------------ the image of a measure

    /**
     * {@code igni quantum ego surgit … vocant}: the spirit reveals how much of the source the subject holds, and its image
     * is brought where the vocant brings it (docs/bussola-arcana-design.md): on a nugget, small, in the dial; anywhere
     * else, as big as that much would be. Returns false when the spell is no measure.
     */
    static boolean measure(SpellContext context, VitaElement element, SpellAction action) {
        if (!action.quantityAll()) {
            return false;
        }
        LivingEntity player = context.player();
        // With no subject written, the body is the caster's, as for any source with no tenet.
        Optional<String> subject = action.subjectMark();
        Optional<LivingEntity> body = subject.isEmpty() ? Optional.of(player)
                : MarkTargets.find(player.getServer(), subject.get()).stream()
                .map(thing -> thing.entity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .findFirst();
        if (body.isEmpty()) {
            MarkSpells.tell(player, "A marca '" + subject.get() + "' nao responde.");
            return true;
        }
        double amount = amountIn(body.get(), element);
        Double chronos = action.seconds().isPresent() ? action.seconds().getAsDouble() : null;
        double seconds = Illusion.seconds(chronos);
        Optional<SpellPlace> place = action.place();
        Optional<Anchor> nugget = place.filter(written -> written.kind() == SpellPlace.Kind.MARK)
                .flatMap(written -> find(player.getServer(), written.mark()));
        if (nugget.isPresent()) {
            Anchor anchor = nugget.get();
            ArcaneCompassItem.show(anchor.stack(), anchor.slot(), new ArcaneCompassItem.Reading(
                    ArcaneCompassItem.Reading.Kind.IMAGE, context.elementRuneId(), amount, 0.0F, 0.0D,
                    now(anchor) + Illusion.ticks(chronos)));
            context.addTotalCost(Illusion.cost(amount * NUGGET_SCALE, seconds));
            return true;
        }
        Optional<MarkSpells.Destination> destination = MarkSpells.destination(context, place, MarkSpells.SUMMON_RANGE);
        if (destination.isEmpty()) {
            return true;
        }
        context.addTotalCost(Illusion.cost(amount, seconds));
        Invocation.imagine(player, element, context.elementRuneId(), destination.get().impact(),
                amount / VocantFunctionHandler.DEFAULT_UMU, Illusion.ticks(chronos));
        return true;
    }

    /** How much of a source a body holds, in UMU: the four of the Vita, and the mana for vis (a player's). */
    static double amountIn(LivingEntity body, VitaElement element) {
        if (element == null || element.isBalanced()) {
            return body instanceof Player mage ? Mana.umuOf(Mana.points(mage.experienceLevel, mage.experienceProgress))
                    : 0.0D;
        }
        return VitaSystem.holds(body, element);
    }

    // ------------------------------------------------------------------ the needle

    /**
     * {@code p1 alvo ubis iactare}: the nugget is pushed toward the place and, held in the dial, points at it. An iron
     * nugget near a lodestone is drawn to it too. Returns false when {@code mark} is no nugget in a dial.
     */
    static boolean push(SpellContext context, String mark, Optional<SpellPlace> place, double energy, int window) {
        LivingEntity player = context.player();
        Optional<Anchor> nugget = find(player.getServer(), mark);
        if (nugget.isEmpty()) {
            return false;
        }
        Anchor anchor = nugget.get();
        Vec3 from = anchor.holder().position();
        Vec3 aim = place.isPresent()
                ? MarkSpells.destination(context, place, MarkSpells.THROW_AIM_RANGE).map(MarkSpells.Destination::point)
                .orElse(null)
                : SpellEffects.findImpact(player, MarkSpells.THROW_AIM_RANGE).location();
        if (aim == null) {
            return true;
        }
        Vec3 way = new Vec3(aim.x - from.x, 0.0D, aim.z - from.z);
        if (anchor.metal().magnetic() && anchor.holder().level() instanceof ServerLevel level) {
            way = drawnByLodestones(level, from, way);
        }
        if (way.lengthSqr() < 1.0E-6D) {
            return true;
        }
        MarkCost.Throw thrown = MarkCost.throwWith(energy, anchor.metal().nuggetMass(), MarkCost.MAX_ENTITY_SPEED);
        context.addTotalCost(thrown.cost());
        float yaw = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
        int ticks = window > 0 ? window : NEEDLE_TICKS;
        ArcaneCompassItem.show(anchor.stack(), anchor.slot(), new ArcaneCompassItem.Reading(
                ArcaneCompassItem.Reading.Kind.NEEDLE, null, 0.0D, yaw, thrown.speed(), now(anchor) + ticks));
        return true;
    }

    /** An iron needle near a lodestone: the nearer it is, the more the lodestone draws it from where it was pushed. */
    private static Vec3 drawnByLodestones(ServerLevel level, Vec3 from, Vec3 way) {
        BlockPos center = BlockPos.containing(from);
        int reach = (int) Math.ceil(LODESTONE_REACH);
        Vec3 pull = Vec3.ZERO;
        double strongest = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (!level.getBlockState(pos).is(Blocks.LODESTONE)) {
                continue;
            }
            Vec3 toward = new Vec3(pos.getX() + 0.5D - from.x, 0.0D, pos.getZ() + 0.5D - from.z);
            double distance = Math.sqrt(toward.lengthSqr() + Math.pow(pos.getY() + 0.5D - from.y, 2));
            if (distance > LODESTONE_REACH || toward.lengthSqr() < 1.0E-6D) {
                continue;
            }
            double strength = Math.pow(1.0D - distance / LODESTONE_REACH, 2);
            if (strength > strongest) {
                strongest = strength;
                pull = toward.normalize();
            }
        }
        if (strongest <= 0.0D) {
            return way;
        }
        Vec3 pushed = way.lengthSqr() < 1.0E-6D ? Vec3.ZERO : way.normalize();
        return pushed.scale(1.0D - strongest).add(pull.scale(strongest));
    }

    // ------------------------------------------------------------------ the speed

    /** What a pulled nugget remembers between ticks: where its pullers were, its speed, what its owners owe. */
    private static final class Pull {
        final Map<UUID, Vec3> last = new HashMap<>();
        final Map<Link, Double> owed = new HashMap<>();
        double speed;
    }

    private static final Map<String, Pull> PULLS = new ConcurrentHashMap<>();

    /**
     * A tick of the nuggets of {@code stack}: each one that an aura bond binds to something is pulled along with it and,
     * held in the dial, strains as fast as it goes. Holding it back is moving it, so the owner of the bond pays for the
     * nugget's mass carried that far (MarkCost.movement), once a second; a bond that cannot be paid breaks.
     */
    public static void pull(ServerPlayer holder, ItemStack stack) {
        LigabisManager manager = LigabisManager.get();
        if (manager == null) {
            return;
        }
        List<String> marks = ArcaneCompassItem.nuggets(stack);
        long now = holder.level().getGameTime();
        for (int slot = 0; slot < marks.size(); slot++) {
            String key = holder.getUUID() + "/" + marks.get(slot);
            // A bond whose owner is away is suspended: it neither pulls nor is paid for (docs/ligabis-design.md, 4).
            List<Link> links = manager.auraParentsOf(marks.get(slot)).stream()
                    .filter(link -> manager.ownerOnline(link.owner())).toList();
            if (links.isEmpty()) {
                PULLS.remove(key);
                continue;
            }
            Pull pull = PULLS.computeIfAbsent(key, k -> new Pull());
            ArcaneCompassItem.Metal metal = ArcaneCompassItem.nuggetMetal(stack, slot);
            double moved = 0.0D;
            for (Link link : links) {
                double step = 0.0D;
                for (MarkTargets.Marked thing : MarkTargets.find(holder.getServer(), link.first())) {
                    if (thing.entity == null) {
                        continue;
                    }
                    Vec3 before = pull.last.put(thing.entity.getUUID(), thing.entity.position());
                    if (before != null) {
                        step = Math.max(step, thing.entity.position().distanceTo(before));
                    }
                }
                pull.owed.merge(link, MarkCost.movement(metal.nuggetMass(), step), Double::sum);
                moved = Math.max(moved, step);
            }
            // Blocks a second, eased: a needle has weight, and the heavier it is the slower it follows.
            double ease = metal == ArcaneCompassItem.Metal.IRON ? 0.35D : 0.15D;
            pull.speed += (moved * 20.0D - pull.speed) * ease;
            if (now % 20L == 0L) {
                for (Map.Entry<Link, Double> debt : pull.owed.entrySet()) {
                    if (!manager.payForBond(debt.getKey().owner(), debt.getValue())) {
                        manager.breakBond(debt.getKey());
                    }
                }
                pull.owed.clear();
            }
            if (now % SPEED_STEP == 0L) {
                ArcaneCompassItem.show(stack, slot, new ArcaneCompassItem.Reading(ArcaneCompassItem.Reading.Kind.SPEED,
                        null, 0.0D, 0.0F, pull.speed, now + SPEED_STEP * 2L));
            }
        }
    }
}

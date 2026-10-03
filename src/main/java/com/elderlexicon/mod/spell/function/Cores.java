package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Template;
import com.elderlexicon.mod.magic.matter.Being;
import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Core;
import com.elderlexicon.mod.magic.matter.Identity;
import com.elderlexicon.mod.magic.matter.MaterialTable;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.magic.matter.Qualities;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.magic.matter.Substance;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.function.MarkTargets.Marked;
import com.elderlexicon.mod.spell.life.Animation;
import com.elderlexicon.mod.spell.life.Beings;
import com.elderlexicon.mod.spell.life.Forms;
import com.elderlexicon.mod.spell.matter.WorldMatter;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The vertere on a core ({@code aqua quantum 16 vertere m1}, docs/particulas-design.md, section 6): the source written
 * is the primordial whose parts change, the quantum how many of the hundred it takes, and the mark what changes. What
 * carries the mark is converted from what it already holds, as many particles in all as before, and becomes what its
 * new core is ({@link Core}): a block or an item another natural thing, a creature another kind of being (its body is
 * its life, 5 UMU for each point, held by the anchor of 100 of Vis). Every change asked of one thing in one instant (the
 * lines of a column) is weighed together at the end of it, so the lines of a code make that code in whatever order the
 * spirit reads them. Each line pays, when it is cast, the work of what it converts, as any vertere does (L2).
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Cores {

    private static final double EPSILON = 1.0E-9D;

    /**
     * One line asking a thing's core: who asked, which primordial, and how many parts of the hundred; with no number, it
     * shares what the numbers leave with the other lines written without one.
     */
    private record Ask(ServerPlayer caster, Marked thing, VitaElement aspect, OptionalDouble parts) {
    }

    /** What the lines of one instant ask of one thing: the parts given a number, and the primordials given none. */
    private record Asked(Map<VitaElement, Double> parts, Set<VitaElement> even) {

        Optional<Composition> reshape(Composition core) {
            return Core.reshape(core, parts, even);
        }
    }

    /** A thing's matter with its core changed, and the particles it now holds. */
    private record Change(Matter was, Matter becomes, Particles particles, boolean same) {
    }

    /** What a thing is now, for the work of a line: its core, and the particles it holds in it. */
    private record Now(Composition core, Particles held) {
    }

    private static final List<Ask> PENDING = new ArrayList<>();

    private Cores() {
    }

    /**
     * What carries {@code mark} is to hold {@code parts} of its hundred as {@code aspect} (with no number, an even share
     * of what the numbers leave): asked now, or after {@code delayTicks}, and changed at the end of that instant with
     * whatever else is asked of it then. The line pays now the work of converting what it asks, on the things as they
     * are.
     */
    static void ask(SpellContext context, String mark, VitaElement aspect, OptionalDouble parts, int delayTicks) {
        ServerPlayer player = context.player();
        if (State.of(aspect).isEmpty()) {
            MarkSpells.tell(player, note("note.core.vis",
                    "A vis nao e parte de cerne nenhum: escreva terra, agua, ar ou fogo antes do vertere.", Map.of()));
            return;
        }
        List<Marked> things = MarkTargets.find(player.server, mark);
        if (things.isEmpty()) {
            MarkSpells.tell(player, "A marca '" + mark + "' nao responde.");
            return;
        }
        double work = 0.0D;
        for (Marked thing : things) {
            work += workOf(thing, aspect, parts);
        }
        context.addTotalCost(work);
        Runnable asking = () -> MarkTargets.load(things, () -> {
            for (Marked thing : things) {
                if (thing.present()) {
                    PENDING.add(new Ask(player, thing, aspect, parts));
                }
            }
        });
        if (delayTicks > 0) {
            SpellEffects.schedule(player.serverLevel(), delayTicks, asking);
        } else {
            asking.run();
        }
    }

    /**
     * The work of one line on one thing as it is now (L2): what this vertere alone converts, by the rungs of the ladder
     * each particle crosses, 5% of a UMU for each (a line with no number, alone, takes the whole hundred). Nothing for a
     * thing with no core to change, or parts that make none.
     */
    private static double workOf(Marked thing, VitaElement aspect, OptionalDouble parts) {
        Optional<Now> now = now(thing);
        if (now.isEmpty()) {
            return 0.0D;
        }
        Particles held = now.get().held();
        Asked alone = parts.isPresent() ? new Asked(Map.of(aspect, parts.getAsDouble()), Set.of())
                : new Asked(Map.of(), Set.of(aspect));
        return alone.reshape(now.get().core())
                .map(core -> Core.work(held, Core.convert(held, core)))
                .orElse(0.0D);
    }

    private static Optional<Now> now(Marked thing) {
        if (!thing.present()) {
            return Optional.empty();
        }
        if (thing.isBlock()) {
            return WorldMatter.read(thing.level, thing.blockPos)
                    .map(matter -> new Now(matter.composition(), matter.particles()));
        }
        if (thing.entity instanceof ItemEntity item) {
            return WorldMatter.read(item).map(matter -> new Now(matter.composition(), matter.particles()));
        }
        if (thing.entity instanceof ServerPlayer player) {
            return Optional.of(new Now(playerCore(player), playerBody(player)));
        }
        if (thing.entity instanceof LivingEntity living) {
            return Beings.coreOf(living).map(core -> new Now(core, bodyOf(living, core)));
        }
        return Optional.empty();
    }

    /** A player's core is the Vita's: a person's until a vertere changes it. */
    private static Composition playerCore(ServerPlayer player) {
        return Composition.of(VitaSystem.core(player));
    }

    /** A player's body is what their Vita holds of each element now. */
    private static Particles playerBody(ServerPlayer player) {
        Particles body = Particles.NONE;
        for (Map.Entry<VitaElement, Double> held : VitaSystem.body(player).entrySet()) {
            body = body.plus(Particles.of(held.getKey(), Particles.ofUmu(Math.max(0.0D, held.getValue()))));
        }
        return body;
    }

    /** A creature's body: its life, 5 UMU for each point, in the proportion of its core. */
    private static Particles bodyOf(LivingEntity living, Composition core) {
        return Particles.in(core, Particles.ofUmu(Math.max(0.0F, living.getHealth()) * Animation.UMU_PER_HP));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<Ask> asks = new ArrayList<>(PENDING);
        PENDING.clear();
        Map<Object, List<Ask>> byThing = new LinkedHashMap<>();
        for (Ask ask : asks) {
            byThing.computeIfAbsent(key(ask.thing()), key -> new ArrayList<>()).add(ask);
        }
        byThing.values().forEach(Cores::change);
    }

    private static Object key(Marked thing) {
        return thing.isBlock() ? List.of(thing.level.dimension(), thing.blockPos) : thing.entityId;
    }

    /** Everything asked of one thing in one instant, together; the same primordial asked twice takes the last. */
    private static void change(List<Ask> asks) {
        Marked thing = asks.get(0).thing();
        Map<VitaElement, Double> parts = new EnumMap<>(VitaElement.class);
        Set<VitaElement> even = EnumSet.noneOf(VitaElement.class);
        Map<VitaElement, OptionalDouble> last = new EnumMap<>(VitaElement.class);
        for (Ask ask : asks) {
            OptionalDouble before = last.put(ask.aspect(), ask.parts());
            if (before != null && !before.equals(ask.parts())) {
                MarkSpells.tell(ask.caster(), note("note.core.twice",
                        "{aspect} pedido duas vezes para '{mark}' no mesmo instante: vale o ultimo.",
                        Map.of("aspect", name(ask.aspect()), "mark", thing.mark)));
            }
            parts.remove(ask.aspect());
            even.remove(ask.aspect());
            if (ask.parts().isPresent()) {
                parts.put(ask.aspect(), ask.parts().getAsDouble());
            } else {
                even.add(ask.aspect());
            }
        }
        Asked asked = new Asked(parts, even);
        if (!thing.present()) {
            return;
        }
        if (thing.isBlock()) {
            changeBlock(asks, thing, asked);
        } else if (thing.entity instanceof ItemEntity item) {
            changeItems(asks, thing, item, asked);
        } else if (thing.entity instanceof ServerPlayer player) {
            changePlayer(asks, thing, player, asked);
        } else if (thing.entity instanceof LivingEntity living) {
            changeBeing(asks, thing, living, asked);
        } else {
            tell(asks, made(thing));
        }
    }

    /** A block becomes what its new core is, where it was. */
    private static void changeBlock(List<Ask> asks, Marked thing, Asked asked) {
        ServerLevel level = thing.level;
        BlockPos pos = thing.blockPos;
        Optional<Matter> read = WorldMatter.read(level, pos);
        if (read.isEmpty()) {
            tell(asks, made(thing));
            return;
        }
        Optional<Composition> core = asked.reshape(read.get().composition());
        if (core.isEmpty()) {
            tell(asks, impossible(thing, asked));
            return;
        }
        Change change = convert(read.get(), core.get());
        if (change.same()) {
            tell(asks, same(thing, change));
            return;
        }
        WorldMatter.take(level, pos);
        WorldMatter.Placed placed = WorldMatter.place(level, pos, change.becomes());
        remark(level, placed.blocks(), placed.entities(), thing.mark);
        Vec3 at = Vec3.atCenterOf(pos);
        double light = release(level, at, placed.leftover());
        show(level, at);
        tell(asks, became(thing, change, light));
    }

    /**
     * Items stay items when what they become has an item, or a block that is one (as many whole ones as the particles
     * make); otherwise they go into the world as the matter they are, as water, fire or formless matter.
     */
    private static void changeItems(List<Ask> asks, Marked thing, ItemEntity item, Asked asked) {
        Optional<Matter> read = WorldMatter.read(item);
        if (read.isEmpty()) {
            tell(asks, made(thing));
            return;
        }
        Optional<Composition> core = asked.reshape(read.get().composition());
        if (core.isEmpty()) {
            tell(asks, impossible(thing, asked));
            return;
        }
        Change change = convert(read.get(), core.get());
        if (change.same()) {
            tell(asks, same(thing, change));
            return;
        }
        ServerLevel level = thing.level;
        Vec3 at = item.position();
        item.discard();
        Optional<WorldMatter.AsItem> asItem = change.becomes().substance(Materials.get())
                .flatMap(substance -> WorldMatter.asItem(substance, change.becomes().state()));
        double light;
        if (asItem.isPresent() && asItem.get().particles() > 0L) {
            long held = change.particles().present().total();
            long each = asItem.get().particles();
            long count = held / each;
            remark(level, List.of(), WorldMatter.drop(level, at, asItem.get().item(), count), thing.mark);
            light = release(level, at, (double) (held - count * each) / Particles.PER_UMU);
        } else {
            WorldMatter.Placed placed = WorldMatter.place(level, BlockPos.containing(at), change.becomes());
            remark(level, placed.blocks(), placed.entities(), thing.mark);
            light = release(level, at, placed.leftover());
        }
        show(level, at);
        tell(asks, became(thing, change, light));
    }

    /**
     * A creature is the kind its new core is nearest, by the law of animation, its body held by the anchor every living
     * thing has (docs/particulas-design.md, stage 5): of another kind, the creature of that kind takes its place with its
     * life; of its own kind still, only how far off its body is changes.
     */
    private static void changeBeing(List<Ask> asks, Marked thing, LivingEntity living, Asked asked) {
        Optional<Composition> core = Beings.coreOf(living);
        if (core.isEmpty()) {
            tell(asks, note("note.core.unknown", "A tabela nao diz de que '{mark}' e feito: o cerne dele nao se conhece.",
                    Map.of("mark", thing.mark)));
            return;
        }
        Optional<Composition> reshaped = asked.reshape(core.get());
        if (reshaped.isEmpty()) {
            tell(asks, impossible(thing, asked));
            return;
        }
        Particles body = Core.convert(bodyOf(living, core.get()), reshaped.get());
        Optional<Animation.Being> being = Animation.quicken(body, Animation.ANCHOR);
        if (being.isEmpty()) {
            tell(asks, note("note.core.small", "O corpo de '{mark}' e pequeno demais para o espirito o reler.",
                    Map.of("mark", thing.mark)));
            return;
        }
        Optional<Being> was = Beings.kindOf(living);
        Vec3 at = living.position().add(0.0D, living.getBbHeight() / 2.0D, 0.0D);
        if (was.isPresent() && was.get().equals(being.get().kind())) {
            Beings.reform(living, being.get(), reshaped.get());
            show(thing.level, at);
            tell(asks, note("note.core.still", "'{mark}' continua {kind}.", Map.of("mark", thing.mark,
                    "kind", being.get().kind().name())) + Beings.imperfections(being.get()));
            return;
        }
        Optional<Mob> made = Beings.become(living, being.get(), reshaped.get());
        if (made.isEmpty()) {
            tell(asks, note("note.core.nobody", "Nao ha criatura no mundo para ser {kind}: '{mark}' ficou como estava.",
                    Map.of("mark", thing.mark, "kind", being.get().kind().name())));
            return;
        }
        MarkHelper.applyMark(made.get(), thing.mark);
        show(thing.level, at);
        tell(asks, note("note.core.became", "'{mark}': {was} virou {becomes}.", Map.of("mark", thing.mark,
                "was", was.map(Being::name).orElseGet(() -> living.getName().getString()),
                "becomes", being.get().kind().name())) + Beings.imperfections(being.get()));
    }

    /**
     * A player's core changes as any being's (docs/particulas-design.md, stage 6): their Vita is converted into the new
     * proportion, and their body becomes the kind the proportion is nearest, held by their kern; a person's proportion is
     * their own body again. They keep all they have: what they carry, their life, their experience.
     */
    private static void changePlayer(List<Ask> asks, Marked thing, ServerPlayer player, Asked asked) {
        Optional<Composition> reshaped = asked.reshape(playerCore(player));
        if (reshaped.isEmpty()) {
            tell(asks, impossible(thing, asked));
            return;
        }
        Particles body = Core.convert(playerBody(player), reshaped.get());
        Map<VitaElement, Double> shares = new EnumMap<>(VitaElement.class);
        reshaped.get().shares().forEach(shares::put);
        VitaSystem.reshape(player, shares);
        if (!(Materials.get().identify(body, true) instanceof Identity.Creature creature)) {
            return; // a table with no beings: the body only holds another proportion
        }
        Optional<Being> was = Forms.of(player);
        Animation.Being being = new Animation.Being(creature, body.present().umu(), 0.0D);
        Beings.deviate(player, being);
        Forms.become(player, creature.being());
        show(thing.level, player.position().add(0.0D, player.getBbHeight() / 2.0D, 0.0D));
        String text = Forms.person(creature.being())
                ? note("note.core.person", "'{mark}' tem corpo de gente.", Map.of("mark", thing.mark))
                : note("note.core.became", "'{mark}': {was} virou {becomes}.", Map.of("mark", thing.mark,
                        "was", was.map(Being::name).orElse(note("note.core.people", "gente", Map.of())),
                        "becomes", creature.being().name()));
        tell(asks, text + Beings.imperfections(being));
        if (asks.stream().noneMatch(ask -> ask.caster() == player)) {
            MarkSpells.tell(player, text + Beings.imperfections(being));
        }
    }

    /**
     * The matter with its core changed: its particles converted, what they are now, and the state it is in. Nothing
     * changes when it is still the same natural thing in the same state, since the world keeps a natural thing only by its
     * code.
     */
    private static Change convert(Matter matter, Composition core) {
        MaterialTable table = Materials.get();
        Particles held = matter.particles();
        Particles converted = Core.convert(held, core);
        Composition made = converted.composition().orElse(core);
        Optional<Substance> was = matter.substance(table);
        Optional<Substance> becomes = table.identify(made);
        State state = Core.state(matter.state(), was, becomes);
        boolean same = state == matter.state() && (was.isPresent() ? was.equals(becomes)
                : becomes.isEmpty() && made.distance(matter.composition()) <= EPSILON);
        return new Change(matter, new Matter(made, state, converted.umu()), converted, same);
    }

    /** What the thing became carries its mark: the blocks it is now, and the items. */
    private static void remark(ServerLevel level, List<BlockPos> blocks, List<Entity> entities, String mark) {
        for (BlockPos pos : blocks) {
            MarkHelper.applyMark(level, pos, mark);
        }
        for (Entity entity : entities) {
            MarkHelper.applyMark(entity, mark);
        }
    }

    /** What makes no whole one of what the thing became leaves it as Vis, as light (user, 30/09/2026). */
    private static double release(ServerLevel level, Vec3 at, double umu) {
        if (umu > EPSILON) {
            VisSpots.light(level, at, umu);
            return umu;
        }
        return 0.0D;
    }

    private static void show(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 40, 0.4D, 0.4D, 0.4D, 0.6D);
    }

    // ------------------------------------------------------------------ what the spirit says

    private static void tell(List<Ask> asks, String text) {
        Set<ServerPlayer> told = new LinkedHashSet<>();
        for (Ask ask : asks) {
            if (told.add(ask.caster())) {
                MarkSpells.tell(ask.caster(), text);
            }
        }
    }

    private static String became(Marked thing, Change change, double light) {
        Optional<Substance> becomes = change.becomes().substance(Materials.get());
        String text = becomes.isPresent()
                ? note("note.core.became", "'{mark}': {was} virou {becomes}.", Map.of("mark", thing.mark,
                        "was", what(change.was()), "becomes", what(change.becomes())))
                : note("note.core.formless", "'{mark}': {was} virou materia sem nome, {description}.",
                        Map.of("mark", thing.mark, "was", what(change.was()),
                                "description", Qualities.describe(change.becomes())));
        if (light > EPSILON) {
            text += " " + note("note.core.light", "{umu} UMU que nao fecharam um inteiro sairam como luz.",
                    Map.of("umu", number(light)));
        }
        return text;
    }

    private static String same(Marked thing, Change change) {
        return note("note.core.same", "'{mark}' continua {was}: a mudanca nao basta para fazer outra coisa.",
                Map.of("mark", thing.mark, "was", what(change.was())));
    }

    private static String made(Marked thing) {
        return note("note.core.made",
                "'{mark}' nao e materia natural: o que e feito nao tem cerne que o vertere mude.",
                Map.of("mark", thing.mark));
    }

    private static String impossible(Marked thing, Asked asked) {
        return note("note.core.impossible",
                "As partes pedidas para '{mark}' somam {sum}: o cerne tem cem partes, e nada mudou.",
                Map.of("mark", thing.mark, "sum", number(Core.asked(asked.parts()))));
    }

    /** A natural thing by its name, with the state it is in when that is not its own; formless matter as such. */
    private static String what(Matter matter) {
        Optional<Substance> substance = matter.substance(Materials.get());
        if (substance.isEmpty()) {
            return note("note.core.unnamed", "materia sem nome", Map.of());
        }
        if (matter.state() == substance.get().nature()) {
            return substance.get().name();
        }
        String state = switch (matter.state()) {
            case SOLID -> "em estado solido";
            case LIQUID -> "em estado liquido";
            case GAS -> "em estado gasoso";
            case PLASMA -> "em plasma";
        };
        return substance.get().name() + " "
                + note("note.core.state." + matter.state().name().toLowerCase(Locale.ROOT), state, Map.of());
    }

    private static String name(VitaElement aspect) {
        return Materials.get().primordial(aspect).name();
    }

    private static String number(double value) {
        return SpellCostCalculator.formatCost(value);
    }

    private static String note(String key, String fallback, Map<String, String> values) {
        return Template.fill(Lexicons.get().note(key).orElse(fallback), values);
    }
}

package com.elderlexicon.mod.university;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.dialogue.Dialogue.Choice;
import com.elderlexicon.mod.dialogue.Dialogue.Line;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.nahva.Understanding;
import com.elderlexicon.mod.spell.SpellWitnessedEvent;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The lessons of the university (docs/universidade-cursos-design.md, 8, step 2): each a part of the mod's tutorial,
 * given by a Magister as a list of steps ({@link Curriculum.Step}). Before the master, he speaks, and reads a spell for
 * the student to see; out in the world, the student wakes the trance, lives a rune, casts, dreams; then comes back.
 * A student is in one lesson at a time, kept with them. At the house, the dream a lesson asks for comes for sure (the
 * dormitory, docs/mago-design.md, 5k). A chair whose lessons are all done, and that has no examination yet, is passed.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Lessons {

    private static final String TAG = ElderLexicon.MODID + "_lesson";
    private static final TagKey<Structure> UNIVERSITIES = TagKey.create(Registries.STRUCTURE,
            new ResourceLocation(ElderLexicon.MODID, "university"));

    /**
     * Which chairs each Magister of today gives, until every course has its own (docs/universidade-cursos-design.md, 8,
     * step 5): the first year, by the old disciplines nearest to each chair.
     */
    private static final Map<Discipline, List<String>> TEACHES = Map.of(
            Discipline.FONTES, List.of("fundamentos_1"),
            Discipline.FUNCOES, List.of("fundamentos_2"),
            Discipline.NAHVA, List.of("transe_1"),
            Discipline.VITA, List.of("corpo_e_calice"),
            Discipline.ARQUIVO, List.of("a_pagina", "caligrafia", "lingua_antiga_1"),
            Discipline.FATOS, List.of("seguranca_e_etica"));

    /** Where a student is: the chair, the lesson, the step. */
    public record Current(String chair, String lesson, int step) {
    }

    private Lessons() {
    }

    /** The lesson {@code player} is in, if any. */
    public static Optional<Current> current(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        if (!tag.contains("Chair")) {
            return Optional.empty();
        }
        return Optional.of(new Current(tag.getString("Chair"), tag.getString("Lesson"), tag.getInt("Step")));
    }

    private static void set(ServerPlayer player, Current current) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Chair", current.chair());
        tag.putString("Lesson", current.lesson());
        tag.putInt("Step", current.step());
        player.getPersistentData().put(TAG, tag);
    }

    /** {@code player} leaves the lesson they are in, without finishing it: for a command. */
    public static void leave(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
    }

    private static Optional<Curriculum.Lesson> lesson(Current current) {
        return Curriculum.get().chair(current.chair()).flatMap(chair -> chair.lessons().stream()
                .filter(lesson -> lesson.id().equals(current.lesson())).findFirst());
    }

    private static Optional<Curriculum.Step> step(Current current) {
        return lesson(current).filter(lesson -> current.step() < lesson.steps().size())
                .map(lesson -> lesson.steps().get(current.step()));
    }

    // ------------------------------------------------------------------ before the master

    /** The chairs {@code master} gives to {@code student}: the first year's to anyone admitted; a course's, to its own. */
    private static List<String> givenBy(Discipline master, Enrollments.Student student) {
        List<String> given = new ArrayList<>();
        Curriculum curriculum = Curriculum.get();
        for (String chair : TEACHES.getOrDefault(master, List.of())) {
            boolean firstYear = curriculum.firstYear().contains(chair);
            boolean ofTheCourse = student.course().flatMap(curriculum::course)
                    .map(info -> info.chairs().contains(chair)).orElse(false);
            if (firstYear || ofTheCourse) {
                given.add(chair);
            }
        }
        return given;
    }

    /** What a Magister offers a student in good standing about his lessons: to go on with one, or to begin the next. */
    public static List<Choice> choices(ServerPlayer player, Wizard master, Discipline chair,
                                       Enrollments.Student student) {
        List<Choice> choices = new ArrayList<>();
        List<String> given = givenBy(chair, student);
        Optional<Current> current = current(player);
        if (current.isPresent()) {
            if (given.contains(current.get().chair())) {
                String title = lesson(current.get()).map(Curriculum.Lesson::title).orElse(current.get().lesson());
                choices.add(new Choice(Component.literal("Continuar a aula: " + title + "."), p -> resume(p, master)));
            }
            return choices;
        }
        for (String id : given) {
            next(student, id).ifPresent(lesson -> choices.add(new Choice(Component.literal("Quero a aula: "
                    + lesson.title() + "."), p -> begin(p, master, id, lesson))));
        }
        return choices;
    }

    /** The first lesson of {@code chair} that {@code student} has not done and may do now. */
    private static Optional<Curriculum.Lesson> next(Enrollments.Student student, String chair) {
        return Curriculum.get().chair(chair).flatMap(info -> info.lessons().stream()
                .filter(lesson -> !student.did(chair, lesson.id()) && !lesson.steps().isEmpty())
                .filter(lesson -> lesson.requires().stream().allMatch(required -> {
                    String[] parts = required.split("/", 2);
                    return parts.length == 2 && student.did(parts[0], parts[1]);
                })).findFirst());
    }

    private static Line begin(ServerPlayer player, Wizard master, String chair, Curriculum.Lesson lesson) {
        set(player, new Current(chair, lesson.id(), 0));
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, player.getSoundSource(), 1.0F, 1.0F);
        return resume(player, master);
    }

    /** The lesson where the student is: what the master says now, up to a step the student must do in the world. */
    private static Line resume(ServerPlayer player, Wizard master) {
        Optional<Current> found = current(player);
        if (found.isEmpty()) {
            return new Line(Component.literal("Não há aula agora."), bye());
        }
        Current current = found.get();
        Optional<Curriculum.Lesson> lesson = lesson(current);
        if (lesson.isEmpty()) {
            leave(player);
            return new Line(Component.literal("Essa aula não existe mais nesta casa."), bye());
        }
        List<Curriculum.Step> steps = lesson.get().steps();
        int at = current.step();
        while (at < steps.size()) {
            Curriculum.Step step = steps.get(at);
            if (step.inTheWorld()) {
                if (!done(player, step, lesson.get())) {
                    set(player, new Current(current.chair(), current.lesson(), at));
                    return new Line(Component.literal(step.text()), new Choice(Component.literal("Vou."), p -> null));
                }
                at++;
                continue;
            }
            int after = at + 1;
            set(player, new Current(current.chair(), current.lesson(), after));
            if (step.kind() == Curriculum.StepKind.DEMONSTRA) {
                demonstrate(master, player, step.spell());
            }
            return new Line(Component.literal(step.text()), new Choice(Component.literal(after < steps.size()
                    ? "Continuar." : "Obrigado, mestre."), p -> resume(p, master)));
        }
        return finish(player, current, lesson.get());
    }

    /** The lesson is over: done in the house's book, and the chair passed if it was the last and there is no exam. */
    private static Line finish(ServerPlayer player, Current current, Curriculum.Lesson lesson) {
        leave(player);
        Enrollments book = Enrollments.of(player.serverLevel());
        book.lessonDone(player.getUUID(), current.chair(), lesson.id());
        Enrollments.Student student = book.student(player.getUUID());
        Curriculum.Chair chair = Curriculum.get().chair(current.chair()).orElse(null);
        StringBuilder said = new StringBuilder("A aula acabou: " + lesson.title() + ".");
        if (chair != null && chair.exam().isEmpty() && !student.chairs().containsKey(chair.id())
                && chair.lessons().stream().allMatch(each -> student.did(chair.id(), each.id()))) {
            book.chairPassed(player.getUUID(), chair.id(), Enrollments.MASTER_QUESTIONS);
            said.append(" Com ela, você passou em ").append(chair.title()).append(".");
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.0F);
            if (student.course().isEmpty() && student.firstYearDone()) {
                said.append(" E no primeiro ano inteiro: escolha o seu curso com o Rector.");
            }
        } else {
            player.playNotifySound(SoundEvents.VILLAGER_YES, player.getSoundSource(), 1.0F, 0.85F);
        }
        return new Line(Component.literal(said.toString()), bye());
    }

    /** The master reads {@code spell} toward a point between him and the student, for the student to see. */
    private static void demonstrate(Wizard master, ServerPlayer player, String spell) {
        if (spell.isBlank()) {
            return;
        }
        Vec3 between = master.position().add(player.position().subtract(master.position()).scale(0.5D));
        Option option = new Option("aula", List.of(new Option.Line(List.of(spell.trim().split("\\s+")), 0)),
                List.of(), 1.0D, false);
        master.casterMind().readToward(option, between);
    }

    private static Choice bye() {
        return new Choice(Component.literal("Até logo."), p -> null);
    }

    // ------------------------------------------------------------------ out in the world

    /** Whether {@code player} has done {@code step} of {@code lesson}. */
    private static boolean done(ServerPlayer player, Curriculum.Step step, Curriculum.Lesson lesson) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        return switch (step.kind()) {
            case TRANSE -> repertoire.hasTrance();
            case VIVER -> {
                if (repertoire.isUnlocked(step.rune()) || repertoire.isUnderstood(step.rune())) {
                    yield true;
                }
                List<String> asked = !step.experiences().isEmpty() ? step.experiences()
                        : Understanding.lesson(step.rune()).map(Understanding.RuneLesson::experiences)
                        .orElse(List.of());
                yield !asked.isEmpty() && repertoire.experienced(step.rune()).containsAll(asked);
            }
            case CONJURAR -> player.getPersistentData().getCompound(TAG).getBoolean("Cast");
            case SONHAR -> repertoire.isUnlocked(step.rune());
            default -> false;
        };
    }

    /** Each second: a step of the world done goes on to the next; the student is told what comes. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        Optional<Current> found = current(player);
        if (found.isEmpty()) {
            return;
        }
        Current current = found.get();
        Optional<Curriculum.Lesson> lesson = lesson(current);
        Optional<Curriculum.Step> step = step(current);
        if (lesson.isEmpty() || step.isEmpty() || !step.get().inTheWorld()
                || !done(player, step.get(), lesson.get())) {
            return;
        }
        Current next = new Current(current.chair(), current.lesson(), current.step() + 1);
        set(player, next);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.0F, 1.2F);
        Optional<Curriculum.Step> then = step(next);
        if (then.isPresent() && then.get().inTheWorld()) {
            player.sendSystemMessage(Component.literal(then.get().text()).withStyle(ChatFormatting.GRAY,
                    ChatFormatting.ITALIC));
        } else {
            player.displayClientMessage(Component.literal("Feito. Volte ao seu mestre: "
                    + lesson.get().title() + ".").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /** A spell the student cast: the step that asks for one with those runes is done. */
    @SubscribeEvent
    public static void onCast(SpellWitnessedEvent event) {
        if (!(event.caster() instanceof ServerPlayer player)) {
            return;
        }
        current(player).flatMap(Lessons::step).filter(step -> step.kind() == Curriculum.StepKind.CONJURAR)
                .filter(step -> Set.copyOf(event.words()).containsAll(step.runes()))
                .ifPresent(step -> player.getPersistentData().getCompound(TAG).putBoolean("Cast", true));
    }

    /**
     * The rune {@code player} must dream for their lesson, if they sleep at the house now: there, in the dormitory, the
     * dream comes for sure.
     */
    public static Optional<String> dreamDue(ServerPlayer player) {
        Optional<Curriculum.Step> step = current(player).flatMap(Lessons::step)
                .filter(found -> found.kind() == Curriculum.StepKind.SONHAR);
        if (step.isEmpty() || !atTheHouse(player)
                || SpellingRepertoireHelper.get(player).isUnlocked(step.get().rune())) {
            return Optional.empty();
        }
        return Optional.of(step.get().rune());
    }

    /** Whether {@code player} is within a university. */
    public static boolean atTheHouse(ServerPlayer player) {
        return player.serverLevel().structureManager().getStructureWithPieceAt(player.blockPosition(), UNIVERSITIES)
                .isValid();
    }
}

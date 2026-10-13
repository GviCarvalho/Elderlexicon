package com.elderlexicon.mod.university;

import com.elderlexicon.mod.dialogue.Dialogue;
import com.elderlexicon.mod.dialogue.Dialogue.Choice;
import com.elderlexicon.mod.dialogue.Dialogue.Line;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the masters of a university say to a player (docs/mago-design.md, 4.1 and 4.2), on the dialogue screen.
 * <ul>
 *   <li><b>The Rector</b> admits: the council's examination (six questions on everything) sets the fee for the next
 *       seven days, by how well it went; a fee is paid to him in emeralds, and one who missed nothing is paid instead.
 *       Every period, a new examination and a new fee. He also makes Lectors of the Scribas a master accepts.</li>
 *   <li><b>A Magister</b> examines the students in good standing in his discipline (five questions; four right pass):
 *       the first discipline passed makes an Auditor a Scriba. He also trades, as any wizard.</li>
 * </ul>
 * One who fails waits a day to be examined again by the same.
 */
public final class Council {

    /** The examination each player is sitting, while they sit it. */
    private static final Map<java.util.UUID, Exam> SITTING = new java.util.HashMap<>();

    private Council() {
    }

    /** The examination {@code player} is sitting now, if any: for a test. */
    public static java.util.Optional<Exam> sitting(ServerPlayer player) {
        return java.util.Optional.ofNullable(SITTING.get(player.getUUID())).filter(exam -> !exam.over());
    }

    /** {@code player} speaks to {@code master}: whatever they may say to each other now. */
    public static void talk(ServerPlayer player, Wizard master) {
        master.chair().ifPresent(chair -> Dialogue.open(player, master,
                chair == Discipline.REITOR ? rector(player) : magister(player, master, chair)));
    }

    private static long now(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    private static Enrollments book(ServerPlayer player) {
        return Enrollments.of(player.serverLevel());
    }

    // ------------------------------------------------------------------ the Rector

    static Line rector(ServerPlayer player) {
        long now = now(player);
        Enrollments.Student student = book(player).find(player.getUUID()).orElse(null);
        if (student == null || student.rank().isEmpty()) {
            if (student != null && student.waiting(now)) {
                return new Line(Component.literal("O conselho já o ouviu hoje. Leia os guias do Arquivo, viva as "
                        + "runas, e volte amanhã."), bye());
            }
            return new Line(Component.literal("Sou o Rector desta casa. Quem estuda aqui é examinado pelo conselho, "
                    + "e paga pelo que sabe: quem sabe pouco paga caro; quem sabe muito paga pouco, ou nada. A cada sete "
                    + "dias, um novo exame."),
                    new Choice(Component.literal("Peço admissão."), Council::councilOpening),
                    new Choice(Component.literal("Só estou de passagem."), p -> null));
        }
        if (student.owes(now)) {
            return payment(player, student, Component.literal("O exame deste período já foi feito. "));
        }
        if (student.dueForExamination(now)) {
            if (student.waiting(now)) {
                return new Line(Component.literal("O conselho já o ouviu hoje, e não o aprovou. Até ser aprovado, "
                        + "você não está em dia com a casa. Volte amanhã."), bye());
            }
            return new Line(Component.literal("O seu período acabou, ").append(title(student)).append(". A cada "
                    + "período, um novo exame: ele fixa o que você paga pelos próximos sete dias."),
                    new Choice(Component.literal("Estou pronto para o exame."), Council::councilOpening),
                    new Choice(Component.literal("Depois."), p -> null));
        }
        return standing(player, student, now);
    }

    private static Line councilOpening(ServerPlayer player) {
        Exam exam = Exam.of(Discipline.REITOR, Enrollments.COUNCIL_QUESTIONS, player.getRandom());
        return new Line(Component.literal("Seis perguntas sobre a Língua Antiga. Responda o que sabe; o que não "
                + "sabe, não invente."), new Choice(Component.literal("Comecemos."),
                p -> ask(p, exam, Component.empty(), done -> councilVerdict(done, exam))),
                new Choice(Component.literal("Ainda não."), p -> null));
    }

    private static Line councilVerdict(ServerPlayer player, Exam exam) {
        long now = now(player);
        Enrollments book = book(player);
        boolean wasStudent = book.find(player.getUUID()).flatMap(Enrollments.Student::rank).isPresent();
        book.examined(player.getUUID(), exam.score(), exam.size(), now);
        Enrollments.Student student = book.student(player.getUUID());
        MutableComponent score = Component.literal("Você acertou " + exam.score() + " de " + exam.size() + ". ");
        if (exam.score() < Enrollments.ADMITTED) {
            player.playNotifySound(SoundEvents.VILLAGER_NO, player.getSoundSource(), 1.0F, 0.85F);
            return new Line(score.append(wasStudent
                    ? "Não basta. Até ser aprovado, você não está em dia com a casa. Volte amanhã."
                    : "Ainda não. Leia os guias do Arquivo, viva as runas, e volte amanhã."), bye());
        }
        MutableComponent text = score.append(wasStudent ? "O conselho o aprova para mais um período. "
                : "O conselho o admite: de hoje em diante, você é ").copy();
        if (!wasStudent) {
            text.append(title(student)).append(". ");
        }
        return payment(player, student, text);
    }

    /** The fee the last examination set, to be paid; or paid to them, or nothing to pay. */
    private static Line payment(ServerPlayer player, Enrollments.Student student, MutableComponent opening) {
        int fee = student.fee();
        if (fee <= 0) {
            book(player).paid(player.getUUID(), now(player));
            if (fee < 0) {
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.EMERALD, -fee));
            }
            player.playNotifySound(SoundEvents.VILLAGER_YES, player.getSoundSource(), 1.0F, 0.85F);
            return new Line(opening.append(fee < 0 ? "Quem não erra nada não paga: é pago. Tome " + -fee
                    + " esmeraldas, para os próximos sete dias." : "Nestes sete dias, você não paga nada."), bye());
        }
        int held = player.getInventory().countItem(Items.EMERALD);
        List<Choice> choices = new ArrayList<>();
        if (held >= fee) {
            choices.add(new Choice(Component.literal("Pagar " + fee + " esmeraldas."), p -> pay(p, fee)));
        }
        choices.add(new Choice(Component.literal(held >= fee ? "Pago depois." : "Não tenho tudo isso agora."),
                p -> null));
        return new Line(opening.append("A mensalidade destes sete dias: " + fee + " esmeraldas. Enquanto não "
                + "pagar, você não está em dia com a casa."), choices);
    }

    private static Line pay(ServerPlayer player, int fee) {
        if (!take(player, fee)) {
            return new Line(Component.literal("Isso não são " + fee + " esmeraldas."), bye());
        }
        book(player).paid(player.getUUID(), now(player));
        player.playNotifySound(SoundEvents.VILLAGER_YES, player.getSoundSource(), 1.0F, 0.85F);
        Enrollments.Student student = book(player).student(player.getUUID());
        return new Line(Component.literal("Pago. Você está em dia por sete dias, ").append(title(student))
                .append(". As aulas são dos Magisters, na Ala de Pesquisa; o exame de um deles faz um Auditor "
                        + "Scriba."), bye());
    }

    /** One in good standing: how they stand, and the rank of Lector if a master accepts them. */
    private static Line standing(ServerPlayer player, Enrollments.Student student, long now) {
        long days = Math.max(1L, (student.paidUntil() - now + 23999L) / 24000L);
        MutableComponent text = Component.literal("Você é ").append(title(student)).append(", em dia por mais "
                + days + (days == 1 ? " dia." : " dias."));
        text.append(passedText(student));
        List<Choice> choices = new ArrayList<>();
        if (student.mayRead()) {
            choices.add(new Choice(Component.literal("Peço o grau de Lector."), Council::lector));
        }
        choices.add(new Choice(Component.literal("Como se sobe de grau?"), p -> new Line(Component.literal(
                "Auditor é quem ouve: foi admitido. Scriba é quem escreve: passou no exame de um Magister. Lector é "
                        + "quem lê: passou em três disciplinas, uma delas sem errar nada, e esse mestre o aceita. "
                        + "Magister, só quem tem cadeira."), bye())));
        choices.add(new Choice(Component.literal("Até logo."), p -> null));
        return new Line(text, choices);
    }

    private static Line lector(ServerPlayer player) {
        Enrollments.Student student = book(player).student(player.getUUID());
        Discipline master = student.master().orElse(Discipline.REITOR);
        if (!book(player).makeLector(player.getUUID())) {
            return new Line(Component.literal("Ainda não."), bye());
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.0F);
        return new Line(Component.literal("O ").append(Component.translatable("university.elderlexicon.chair."
                        + master.id())).append(" o aceita como seu leitor. De hoje em diante, você é Lector."),
                bye());
    }

    // ------------------------------------------------------------------ a Magister

    static Line magister(ServerPlayer player, Wizard master, Discipline chair) {
        long now = now(player);
        Enrollments.Student student = book(player).find(player.getUUID()).orElse(null);
        Component discipline = discipline(chair);
        List<Choice> choices = new ArrayList<>();
        MutableComponent text = Component.literal("Sou o ").append(Component.translatable(
                "university.elderlexicon.chair." + chair.id())).append(". ");
        if (student == null || !student.inGoodStanding(now)) {
            text.append(student != null && student.rank().isPresent()
                    ? "Só examino quem está em dia com a casa. Fale com o Rector, no Salão Central."
                    : "Examino quem estuda nesta casa. Quem quer estudar fala com o Rector, no Salão Central.");
        } else if (student.passed().containsKey(chair)) {
            text.append("Você já passou no meu exame, com " + student.passed().get(chair) + " de "
                    + Enrollments.MASTER_QUESTIONS + ".");
            if (student.passed().get(chair) < Enrollments.MASTER_QUESTIONS && !student.waiting(chair, now)) {
                choices.add(new Choice(Component.literal("Quero tentar de novo, sem errar nada."),
                        p -> masterOpening(p, chair)));
            }
        } else if (student.waiting(chair, now)) {
            text.append("Já o examinei hoje. Volte amanhã.");
        } else {
            text.append("Ensino ").append(discipline).append(". Quer ser examinado?");
            choices.add(new Choice(Component.literal("Quero o exame de ").append(discipline).append("."),
                    p -> masterOpening(p, chair)));
        }
        if (!master.getOffers().isEmpty()) {
            choices.add(new Choice(Component.literal("Quero ver o que tem para trocar."), p -> {
                master.setTradingPlayer(p);
                master.openTradingScreen(p, master.getDisplayName(), 1);
                return null;
            }));
        }
        choices.add(bye());
        return new Line(text, choices);
    }

    private static Line masterOpening(ServerPlayer player, Discipline chair) {
        Exam exam = Exam.of(chair, Enrollments.MASTER_QUESTIONS, player.getRandom());
        return ask(player, exam, Component.literal("Cinco perguntas. Quatro certas, e passa. "),
                done -> masterVerdict(done, chair, exam));
    }

    private static Line masterVerdict(ServerPlayer player, Discipline chair, Exam exam) {
        Enrollments book = book(player);
        boolean wasAuditor = book.student(player.getUUID()).rank().filter(Rank.AUDITOR::equals).isPresent();
        boolean passed = book.examinedBy(player.getUUID(), chair, exam.score(), now(player));
        MutableComponent text = Component.literal("Você acertou " + exam.score() + " de " + exam.size() + ". ");
        if (!passed) {
            player.playNotifySound(SoundEvents.VILLAGER_NO, player.getSoundSource(), 1.0F, 0.85F);
            return new Line(text.append("Não passou. Estude, e volte amanhã."), bye());
        }
        player.playNotifySound(wasAuditor ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_YES,
                player.getSoundSource(), 0.8F, 1.0F);
        text.append("Passou em ").append(discipline(chair)).append(".");
        if (wasAuditor) {
            text.append(" De hoje em diante, você é Scriba.");
        }
        if (exam.score() >= Enrollments.MASTER_QUESTIONS) {
            text.append(" Sem errar nada: se um dia pedir o grau de Lector, eu o aceito.");
        }
        return new Line(text, bye());
    }

    // ------------------------------------------------------------------ asking

    /** The examination's next question, after {@code before} (how the last answer went); then its verdict. */
    private static Line ask(ServerPlayer player, Exam exam, Component before,
                            java.util.function.Function<ServerPlayer, Line> verdict) {
        if (exam.size() == 0) {
            return new Line(Component.literal("Hoje não há o que perguntar."), bye());
        }
        SITTING.put(player.getUUID(), exam);
        Exam.Question question = exam.current().orElseThrow();
        MutableComponent text = before.copy().append(Component.literal("Pergunta " + exam.number() + " de "
                + exam.size() + ". ").withStyle(ChatFormatting.GRAY)).append(question.prompt());
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < question.answers().size(); i++) {
            int index = i;
            choices.add(new Choice(question.answers().get(i), p -> {
                boolean right = exam.answer(index);
                if (exam.over()) {
                    SITTING.remove(p.getUUID());
                }
                Component said = Component.literal(right ? "Certo. " : "Não. ")
                        .withStyle(right ? ChatFormatting.GREEN : ChatFormatting.RED);
                return exam.over() ? verdictAfter(p, said, verdict) : ask(p, exam, said, verdict);
            }));
        }
        return new Line(text, choices);
    }

    private static Line verdictAfter(ServerPlayer player, Component said,
                                     java.util.function.Function<ServerPlayer, Line> verdict) {
        Line line = verdict.apply(player);
        return new Line(said.copy().append(line.text()), line.choices());
    }

    // ------------------------------------------------------------------ words

    private static Choice bye() {
        return new Choice(Component.literal("Até logo."), p -> null);
    }

    private static Component title(Enrollments.Student student) {
        return student.rank().map(rank -> (Component) Component.translatable("university.elderlexicon.rank."
                + rank.id()).withStyle(ChatFormatting.GOLD)).orElse(Component.literal("estudante"));
    }

    private static Component discipline(Discipline discipline) {
        return Component.translatable("university.elderlexicon.discipline." + discipline.id())
                .withStyle(ChatFormatting.AQUA);
    }

    private static Component passedText(Enrollments.Student student) {
        if (student.passed().isEmpty()) {
            return Component.literal(" Ainda não passou no exame de nenhum Magister.");
        }
        MutableComponent text = Component.literal(" Passou em: ");
        boolean first = true;
        for (Map.Entry<Discipline, Integer> entry : student.passed().entrySet()) {
            if (!first) {
                text.append(", ");
            }
            text.append(discipline(entry.getKey()));
            first = false;
        }
        return text.append(".");
    }

    /** Takes {@code count} emeralds from {@code player}, if they have them. */
    private static boolean take(ServerPlayer player, int count) {
        if (player.getInventory().countItem(Items.EMERALD) < count) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.EMERALD)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        player.getInventory().setChanged();
        return true;
    }
}

package com.elderlexicon.mod.university;

import com.elderlexicon.mod.library.RuneGuides;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.mind.Phrase;
import com.elderlexicon.mod.mind.PhraseCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * An examination before the council or a master (docs/mago-design.md, 4.1 and 4.2): questions on the Old Tongue, each
 * with four answers, one of them right. Most are made from the lexicon (what a rune is, which rune says a thing, what
 * family it is of, what a sentence does), so a rune an addon brings is asked about too; the rest are what the house
 * teaches of its arts, the same in every university.
 */
public final class Exam {

    /** A question, its answers as shown, and which of them is right. */
    public record Question(Component prompt, List<Component> answers, int right) {
    }

    private final List<Question> questions;
    private int asked;
    private int score;

    private Exam(List<Question> questions) {
        this.questions = questions;
    }

    /** An examination of {@code count} questions on {@code discipline} ({@link Discipline#REITOR}: on everything). */
    public static Exam of(Discipline discipline, int count, RandomSource random) {
        Lexicon lexicon = Lexicons.get();
        Maker maker = new Maker(lexicon, random);
        List<Function<Maker, Optional<Question>>> kinds = kinds(discipline);
        List<Question> questions = new ArrayList<>();
        List<String[]> lore = new ArrayList<>(Doctrine.of(discipline));
        Collections.shuffle(lore, new java.util.Random(random.nextLong()));
        for (int tries = 0; questions.size() < count && tries < count * 12; tries++) {
            Optional<Question> question;
            if (!lore.isEmpty() && (kinds.isEmpty() || random.nextInt(3) == 0)) {
                question = Optional.of(maker.taught(lore.remove(0)));
            } else if (!kinds.isEmpty()) {
                question = kinds.get(random.nextInt(kinds.size())).apply(maker);
            } else {
                question = maker.meaning(maker.all());
            }
            question.ifPresent(questions::add);
        }
        return new Exam(questions);
    }

    /** What is asked of each discipline, made from the lexicon. */
    private static List<Function<Maker, Optional<Question>>> kinds(Discipline discipline) {
        return switch (discipline) {
            case FONTES, FUNCOES, FILTROS, FATOS -> {
                RuneGuides.Family family = discipline.family().orElseThrow();
                yield List.of(maker -> maker.meaning(maker.of(family)), maker -> maker.which(maker.of(family)),
                        maker -> maker.does(maker.of(family)));
            }
            case VITA -> List.of(maker -> maker.meaning(maker.of(RuneGuides.Family.FONTS)),
                    maker -> maker.does(maker.of(RuneGuides.Family.FONTS)));
            case GALDRARIA -> List.of(maker -> maker.does(maker.all()));
            case NAHVA -> List.of();
            case ARQUIVO -> List.of(maker -> maker.family(maker.all()), maker -> maker.which(maker.all()));
            case REITOR -> List.of(maker -> maker.family(maker.all()), maker -> maker.meaning(maker.all()),
                    maker -> maker.which(maker.all()), maker -> maker.does(maker.all()));
        };
    }

    public List<Question> questions() {
        return questions;
    }

    /** The question being asked, if any is left. */
    public Optional<Question> current() {
        return asked < questions.size() ? Optional.of(questions.get(asked)) : Optional.empty();
    }

    /** The number of the question being asked, from one. */
    public int number() {
        return asked + 1;
    }

    /** The answer at {@code index} is given to the current question: whether it was right. */
    public boolean answer(int index) {
        Question question = questions.get(asked++);
        boolean right = index == question.right();
        if (right) {
            score++;
        }
        return right;
    }

    public boolean over() {
        return asked >= questions.size();
    }

    public int score() {
        return score;
    }

    public int size() {
        return questions.size();
    }

    // ------------------------------------------------------------------ making questions

    /** Makes questions from the lexicon, never asking twice of the same thing. */
    private static final class Maker {

        private final Lexicon lexicon;
        private final RandomSource random;
        private final Set<String> asked = new HashSet<>();

        Maker(Lexicon lexicon, RandomSource random) {
            this.lexicon = lexicon;
            this.random = random;
        }

        List<Rune> of(RuneGuides.Family family) {
            List<Rune> runes = new ArrayList<>();
            RuneGuides.runesOf(family).forEach(id -> lexicon.rune(id).ifPresent(runes::add));
            return runes;
        }

        List<Rune> all() {
            List<Rune> runes = new ArrayList<>();
            for (RuneGuides.Family family : RuneGuides.Family.values()) {
                runes.addAll(of(family));
            }
            return runes;
        }

        /** "What is the rune X?": its name, among the names of others. */
        Optional<Question> meaning(List<Rune> runes) {
            return pick(runes, "meaning", rune -> !rune.name().isBlank()).flatMap(rune -> {
                List<String> others = distinct(all(), rune.name(), Rune::name);
                return question(Component.literal("O que é a runa ").append(word(rune.id())).append("?"),
                        capital(rune.name()), others.stream().map(Exam::capital).toList());
            });
        }

        /** "Which rune says X?": the rune, among others. */
        Optional<Question> which(List<Rune> runes) {
            return pick(runes, "which", rune -> !rune.name().isBlank()).flatMap(rune -> {
                List<String> others = new ArrayList<>(distinct(runes.size() >= 4 ? runes : all(), rune.id(), Rune::id));
                return questionOf(Component.literal("Qual destas runas diz «" + rune.name() + "»?"),
                        word(rune.id()), others.stream().map(Exam::word).toList());
            });
        }

        /** "What family is X of?": one of the four. */
        Optional<Question> family(List<Rune> runes) {
            return pick(runes, "family", rune -> true).flatMap(rune -> {
                RuneGuides.Family right = RuneGuides.Family.of(rune);
                List<String> others = new ArrayList<>();
                for (RuneGuides.Family family : RuneGuides.Family.values()) {
                    if (family != right) {
                        others.add(family.title);
                    }
                }
                return question(Component.literal("De que família é a runa ").append(word(rune.id())).append("?"),
                        right.title, others);
            });
        }

        /** "What does this sentence do?": what it does, among what others do. */
        Optional<Question> does(List<Rune> runes) {
            Optional<PhraseCatalog> catalog = PhraseCatalog.ready();
            if (catalog.isEmpty()) {
                return Optional.empty();
            }
            Set<String> ids = new HashSet<>();
            runes.forEach(rune -> ids.add(rune.id()));
            Map<String, Phrase> byDeed = new LinkedHashMap<>();
            for (Phrase phrase : catalog.get().phrases()) {
                if (phrase.words().size() <= 4) {
                    RuneGuides.describe(lexicon, phrase).ifPresent(deed -> byDeed.putIfAbsent(deed, phrase));
                }
            }
            List<Phrase> candidates = byDeed.values().stream()
                    .filter(phrase -> phrase.runes().stream().anyMatch(ids::contains))
                    .filter(phrase -> !asked.contains("does:" + phrase.text()))
                    .toList();
            if (candidates.isEmpty() || byDeed.size() < 4) {
                return Optional.empty();
            }
            Phrase phrase = candidates.get(random.nextInt(candidates.size()));
            asked.add("does:" + phrase.text());
            String deed = RuneGuides.describe(lexicon, phrase).orElseThrow();
            List<String> others = new ArrayList<>(byDeed.keySet());
            others.remove(deed);
            return question(Component.literal("O que faz a frase ").append(word(phrase.text())).append("?"),
                    capital(deed), others.stream().map(Exam::capital).toList());
        }

        /** A question of what the house teaches: the prompt, the right answer, the wrong ones. */
        Question taught(String[] lore) {
            List<String> wrong = new ArrayList<>(List.of(lore).subList(2, lore.length));
            return question(Component.literal(lore[0]), lore[1], wrong).orElseThrow();
        }

        private Optional<Rune> pick(List<Rune> runes, String kind, java.util.function.Predicate<Rune> fit) {
            List<Rune> left = runes.stream().filter(fit).filter(rune -> !asked.contains(kind + ":" + rune.id()))
                    .toList();
            if (left.isEmpty()) {
                return Optional.empty();
            }
            Rune rune = left.get(random.nextInt(left.size()));
            asked.add(kind + ":" + rune.id());
            return Optional.of(rune);
        }

        private static List<String> distinct(List<Rune> runes, String not, Function<Rune, String> by) {
            List<String> found = new ArrayList<>();
            for (Rune rune : runes) {
                String value = by.apply(rune);
                if (!value.isBlank() && !value.equalsIgnoreCase(not) && !found.contains(value)) {
                    found.add(value);
                }
            }
            return found;
        }

        /** Four answers, the right one among three wrong ones drawn from {@code wrong}, in an order by chance. */
        private Optional<Question> question(Component prompt, String right, List<String> wrong) {
            return questionOf(prompt, Component.literal(right), wrong.stream().map(text -> (Component)
                    Component.literal(text)).toList());
        }

        private Optional<Question> questionOf(Component prompt, Component right, List<Component> wrong) {
            List<Component> pool = new ArrayList<>(wrong);
            pool.removeIf(answer -> answer.getString().equalsIgnoreCase(right.getString()));
            if (pool.size() < 3) {
                return Optional.empty();
            }
            Collections.shuffle(pool, new java.util.Random(random.nextLong()));
            List<Component> answers = new ArrayList<>(pool.subList(0, 3));
            int at = random.nextInt(4);
            answers.add(at, right);
            return Optional.of(new Question(prompt, List.copyOf(answers), at));
        }
    }

    private static Component word(String text) {
        return Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    // ------------------------------------------------------------------ what the house teaches

    /**
     * What each master asks of its art, as the house teaches it: the question, the right answer, then the wrong ones.
     * The Rector asks of everything.
     */
    private static final class Doctrine {

        private static final Map<Discipline, List<String[]>> TAUGHT = Map.of(
                Discipline.FONTES, List.of(
                        new String[]{"Que aspecto da energia é o fogo?", "O calor", "A coesão", "A expansão", "A massa"},
                        new String[]{"Que aspecto da energia é a água?", "A coesão", "O calor", "A expansão", "A massa"},
                        new String[]{"Que aspecto da energia é o ar?", "A expansão", "O calor", "A coesão", "A massa"},
                        new String[]{"Que aspecto da energia é a terra?", "A massa", "O calor", "A coesão", "A expansão"},
                        new String[]{"O que é a vis?", "Os quatro em equilíbrio", "Uma quinta energia, a mais forte",
                                "O fogo condensado", "A energia dos mortos"},
                        new String[]{"O que faz uma frase sem fonte?", "Lança só luz: não fere ninguém",
                                "Fere com a energia de quem conjura", "Não se lê", "Cura quem conjura"}),
                Discipline.FUNCOES, List.of(
                        new String[]{"O que faz uma quantidade negativa à maioria das funções?",
                                "Vira a função ao contrário", "Dobra a sua força", "Anula a frase",
                                "Faz a função agir em quem conjura"},
                        new String[]{"Com uma quantidade negativa, o que lança passa a...", "Puxar", "Queimar",
                                "Sumir", "Esperar"}),
                Discipline.FILTROS, List.of(
                        new String[]{"Onde se escreve um filtro?", "Antes da função que ele muda",
                                "Depois da função", "No fim da frase", "Em outra linha"},
                        new String[]{"O que dizem os filtros?", "Quanto, quando, onde e de onde",
                                "Com que energia se fala", "Quem conjura", "O que se faz com a energia"}),
                Discipline.FATOS, List.of(
                        new String[]{"Quando vale uma linha com um fato?", "Só quando ele acontece", "Sempre",
                                "Só de noite", "Só dentro de casa"},
                        new String[]{"Quem é ego, numa frase?", "Quem conjura", "O outro do acontecimento",
                                "O mais próximo", "Quem tem a marca"},
                        new String[]{"Quem é ille, numa frase?", "O outro do acontecimento", "Quem conjura",
                                "O Rector", "Ninguém"}),
                Discipline.GALDRARIA, List.of(
                        new String[]{"O que é a galdraria?", "Gravar runas nas coisas", "Curar pela Vita",
                                "Sonhar as runas", "Chamar o que tem a marca"},
                        new String[]{"Onde se grava uma frase numa varinha?", "Na mesa de galdraria",
                                "No altar de encantamentos", "Na bigorna", "No suporte de poções"},
                        new String[]{"Como a oficina paga quem grava?", "Com uma comissão do que vende",
                                "Com runas", "Com um grau", "Não paga"}),
                Discipline.VITA, List.of(
                        new String[]{"O que é curar com diagnóstico?", "Dar exatamente o que falta de cada elemento",
                                "Dar muita vis", "Tirar o que sobra de fogo", "Dormir ao lado do ferido"},
                        new String[]{"De que é feito um corpo, para a Vita?", "Das quatro energias, em proporção",
                                "Só de vis", "Só de água", "De runas"}),
                Discipline.NAHVA, List.of(
                        new String[]{"Como se aprende uma runa?", "Vivendo-a no corpo, e depois sonhando-a",
                                "Lendo o guia dela", "Copiando-a num grimório", "Pagando a um mestre"},
                        new String[]{"Ler o guia de uma runa a ensina?", "Não: diz o que ela é e como vivê-la",
                                "Sim, lido inteiro", "Sim, lido em voz alta", "Só a quem já é Scriba"},
                        new String[]{"Aonde leva o sonho de uma runa já vivida?", "À sua dimensão em Nahvá, de onde é "
                                + "preciso escapar", "Ao Arquivo", "À casa de quem a escreveu", "A lugar nenhum"},
                        new String[]{"O que se faz na dimensão de uma runa, em Nahvá?", "Escapa-se dela",
                                "Dorme-se", "Escreve-se o grimório", "Espera-se o Rector"}),
                Discipline.ARQUIVO, List.of(
                        new String[]{"O que é falta grave dentro do Arquivo?", "Ser pego com fogo",
                                "Ler em voz alta", "Sentar no chão", "Pedir um guia"},
                        new String[]{"Quem entra nas salas fechadas do Arquivo?", "Os Lectores", "Qualquer Auditor",
                                "Só o Rector", "Ninguém"},
                        new String[]{"O que guarda o Arquivo?", "Guias, gramáticas e grimórios antigos",
                                "Esmeraldas", "Poções", "As varinhas dos mestres"}),
                Discipline.REITOR, List.of());

        static List<String[]> of(Discipline discipline) {
            if (discipline != Discipline.REITOR) {
                return TAUGHT.getOrDefault(discipline, List.of());
            }
            List<String[]> everything = new ArrayList<>();
            TAUGHT.values().forEach(everything::addAll);
            return everything;
        }
    }
}

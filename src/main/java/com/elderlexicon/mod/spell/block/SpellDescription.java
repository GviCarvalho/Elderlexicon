package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.parser.ParserDictionary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The grimoire's own description of a page of spells, written once the spirit has cast it: a name for it, what each
 * spell does (grouped by the instant it is released) and notes on what the elements may do where they meet, from the
 * aspects of energy (docs/interacoes-design.md). Only words; what really happens is up to the laws of nature.
 */
public final class SpellDescription {

    /** What the grimoire writes about a page. */
    public record Text(String name, List<String> paragraphs, List<String> notes) {
    }

    /** Energy a spell spends when no quantum says otherwise (book 4.3.2). */
    private static final int DEFAULT_UMU = 10;

    private static final Map<String, String> ELEMENT = Map.of(
            "igni", "fogo", "aqua", "água", "aura", "ar", "firmo", "terra", "vis", "mana");
    private static final Set<String> SOURCES = Set.of("igni", "aqua", "aura", "firmo", "vis");
    private static final Set<String> FUNCTIONS = Set.of("iactare", "vocant", "impediunt", "surgit", "ligabis",
            "reframe", "transvocatio");

    /** One spell of the page, as the grimoire understands it. */
    private record Spell(int release, String source, String mark, boolean capture, String amount, List<String> turns,
                         boolean condensed, String seconds, String place, String function) {

        String element() {
            return turns.isEmpty() ? source : turns.get(turns.size() - 1);
        }
    }

    private final ParserDictionary dictionary;
    private final Map<String, List<String>> named;

    public SpellDescription(ParserDictionary dictionary, Map<String, List<String>> named) {
        this.dictionary = dictionary;
        this.named = named == null ? Map.of() : Map.copyOf(named);
    }

    /** The description of a page: its rows as normalized ids ({@code ""} for an empty cell). */
    public Text describe(List<List<String>> rows) {
        List<Spell> spells = new ArrayList<>();
        for (List<String> row : rows) {
            Spell spell = read(row);
            if (spell != null) {
                spells.add(spell);
            }
        }
        if (spells.isEmpty()) {
            return new Text("Página em branco", List.of(), List.of());
        }
        TreeMap<Integer, List<Spell>> byInstant = new TreeMap<>();
        for (Spell spell : spells) {
            byInstant.computeIfAbsent(spell.release(), key -> new ArrayList<>()).add(spell);
        }
        List<String> paragraphs = new ArrayList<>();
        Set<String> notes = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<Spell>> instant : byInstant.entrySet()) {
            String when = seconds(instant.getKey());
            List<Spell> together = instant.getValue();
            if (together.size() == 1) {
                paragraphs.add(sentence(together.get(0)) + " Solto em " + when + " s.");
            } else {
                paragraphs.add("Ao mesmo tempo, em " + when + " s:");
                for (Spell spell : together) {
                    paragraphs.add("• " + sentence(spell));
                }
                notes.addAll(meetings(together));
            }
            for (Spell spell : together) {
                notes.addAll(own(spell));
            }
        }
        return new Text(name(byInstant, spells), paragraphs, new ArrayList<>(notes));
    }

    // ------------------------------------------------------------------ reading a spell

    private Spell read(List<String> row) {
        List<String> words = new ArrayList<>();
        int release = -1;
        for (int i = 0; i < row.size(); i++) {
            String id = row.get(i) == null ? "" : row.get(i);
            if (id.isEmpty()) {
                continue;
            }
            release = i;
            // A named rune is read as the spell it holds.
            words.addAll(named.getOrDefault(id, List.of(id)));
        }
        if (words.isEmpty()) {
            return null;
        }
        String source = null;
        String mark = null;
        boolean capture = false;
        String amount = null;
        List<String> turns = new ArrayList<>();
        boolean condensed = false;
        String time = null;
        String place = null;
        String function = null;
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            boolean number = word.matches("-?\\d+");
            if ((number || isMark(word)) && "ubis".equals(next)) {
                place = number ? "a " + word + " blocos na direção mirada" : "onde está o que tem a marca “" + word
                        + "”";
                i++;
                continue;
            }
            if (isMark(word)) {
                mark = word;
                continue;
            }
            if (SOURCES.contains(word)) {
                if (source == null) {
                    source = word;
                }
                continue;
            }
            switch (word) {
                case "exsugat" -> capture = true;
                case "vertere" -> {
                    if (SOURCES.contains(next)) {
                        turns.add(next);
                        i++;
                    }
                }
                case "quantum" -> {
                    if (next.matches("-?\\d+")) {
                        amount = next;
                        i++;
                    } else {
                        amount = "all";
                    }
                }
                case "chronos" -> {
                    if ("0".equals(next)) {
                        condensed = true;
                        i++;
                    } else if (next.matches("-?\\d+")) {
                        time = next;
                        i++;
                    }
                }
                default -> {
                    if (FUNCTIONS.contains(word)) {
                        function = word;
                    }
                }
            }
        }
        if (function == null && capture) {
            function = "exsugat";
        }
        return new Spell(release, source, mark, capture, amount, turns, condensed, time, place, function);
    }

    private boolean isMark(String word) {
        return !word.matches("-?\\d+") && !named.containsKey(word) && dictionary.lookup(word).isEmpty();
    }

    // ------------------------------------------------------------------ writing it

    private static String sentence(Spell spell) {
        StringBuilder text = new StringBuilder();
        if (spell.source() == null && spell.mark() != null) {
            text.append(capitalize(action(spell, "o que tem a marca “" + spell.mark() + "”")));
            return text.append('.').toString();
        }
        if (spell.source() == null && spell.amount() == null) {
            // No energy named: only what is done (surgit reads, a function on its own).
            String done = action(spell, null);
            return capitalize(done.startsWith("e ") ? done.substring(2) : done) + ".";
        }
        String element = elementName(spell.source());
        String amount = "all".equals(spell.amount()) ? "toda a energia de " + element
                : (spell.amount() == null ? DEFAULT_UMU : spell.amount()) + " UMU de " + element;
        String origin = spell.capture() ? " do mundo ao redor da mão"
                : "vis".equals(spell.source()) ? " do corpo (a experiência)" : " do corpo";
        if (!spell.turns().isEmpty()) {
            List<String> into = spell.turns().stream().map(turn -> elementName(turn)).toList();
            text.append(spell.capture() ? "Captura " : "Converte ").append(amount).append(origin)
                    .append(spell.capture() ? ", converte em " : " em ").append(String.join(" e depois em ", into));
        } else {
            text.append(spell.capture() ? "Captura " : "Usa ").append(amount).append(origin);
        }
        if (spell.condensed()) {
            text.append(", condensa tudo num só ponto");
        }
        if (spell.seconds() != null) {
            text.append(", por ").append(spell.seconds()).append(" s");
        }
        text.append(' ').append(action(spell, null));
        return text.append('.').toString();
    }

    /** What the spell does with its energy; {@code subject} names it when it is a marked thing. */
    private static String action(Spell spell, String subject) {
        String what = subject == null ? "" : subject + " ";
        String where = spell.place() == null ? "" : " " + spell.place();
        if (spell.function() == null) {
            return "e não faz nada com ela: falta uma função";
        }
        return switch (spell.function()) {
            case "iactare" -> subject == null ? "e lança na direção mirada" + where
                    : "lança " + what + "na direção mirada" + where;
            case "vocant" -> subject == null ? "e faz surgir" + (where.isEmpty() ? " no ponto mirado" : where)
                    : "faz " + what + "surgir" + (where.isEmpty() ? " no ponto mirado" : where);
            case "impediunt" -> "e afasta esse elemento de uma zona ao redor" + (where.isEmpty() ? " do ponto" : where);
            case "exsugat" -> "e absorve para o corpo";
            case "surgit" -> "e faz o espírito ler e revelar";
            case "ligabis" -> "e liga o que foi marcado";
            case "reframe" -> "e guarda o feitiço com um nome";
            case "transvocatio" -> "e transfere o que foi marcado";
            default -> "e " + spell.function();
        };
    }

    /** What the elements released in the same instant may do to each other, by their aspects. */
    private static List<String> meetings(List<Spell> together) {
        Set<String> elements = new LinkedHashSet<>();
        for (Spell spell : together) {
            if (spell.element() != null) {
                elements.add(spell.element());
            }
        }
        List<String> notes = new ArrayList<>();
        if (elements.contains("aqua") && elements.contains("aura")) {
            notes.add("A água levada pelo ar agitado se desfaz em gotículas. O atrito entre elas (e o gelo em que "
                    + "congelam, se o ar esfriar) acumula carga estática, que pode descarregar num relâmpago.");
        }
        if (elements.contains("igni") && elements.contains("aqua")) {
            notes.add("O calor encontra a água: ela ferve, e o vapor, se não tiver por onde sair, explode.");
        }
        if (elements.contains("igni") && elements.contains("aura")) {
            notes.add("O ar alimenta o fogo, e o calor expande o ar em rajadas quentes.");
        }
        if (elements.contains("igni") && elements.contains("firmo")) {
            notes.add("Calor bastante derrete a rocha em lava, que endurece ao esfriar: obsidiana se esfriar de repente.");
        }
        if (elements.contains("aqua") && elements.contains("firmo")) {
            notes.add("Água e terra viram lama; a água que congela nas fendas racha a pedra.");
        }
        if (elements.contains("aura") && elements.contains("firmo")) {
            notes.add("A pressão arremessa a terra como estilhaços.");
        }
        return notes;
    }

    /** What a spell alone is worth noting. */
    private static List<String> own(Spell spell) {
        List<String> notes = new ArrayList<>();
        if (spell.capture() && "aura".equals(spell.source())) {
            notes.add("Tirar o ar do mundo deixa um vácuo onde ele estava: sem ar não se respira e o fogo apaga.");
        }
        if (spell.condensed()) {
            notes.add("Condensar é trabalho do espírito: parte da energia se perde nele, e juntar leva tempo antes "
                    + "da soltura.");
        }
        if (spell.condensed() && "vis".equals(spell.element())) {
            notes.add("A vis condensada mostra os quatro aspectos de uma vez: calor, água, ar e massa no mesmo ponto.");
        }
        if (!spell.turns().isEmpty() && !"vis".equals(spell.source())) {
            notes.add("Converter um elemento em outro custa ao espírito 5% da energia por qualidade mudada.");
        }
        return notes;
    }

    // ------------------------------------------------------------------ the name

    private static String name(TreeMap<Integer, List<Spell>> byInstant, List<Spell> spells) {
        for (List<Spell> together : byInstant.values()) {
            if (together.size() < 2) {
                continue;
            }
            Set<String> elements = new LinkedHashSet<>();
            together.forEach(spell -> elements.add(spell.element()));
            if (elements.contains("aqua") && elements.contains("aura")) {
                return "Tempestade";
            }
            if (elements.contains("igni") && elements.contains("aqua")) {
                return "Explosão de vapor";
            }
            if (elements.contains("igni") && elements.contains("aura")) {
                return "Redemoinho de fogo";
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (Spell spell : spells) {
            names.add(name(spell));
        }
        List<String> distinct = new ArrayList<>(names);
        return distinct.size() == 1 ? distinct.get(0) : distinct.get(0) + " e " + lower(distinct.get(1));
    }

    private static String name(Spell spell) {
        String element = spell.element();
        boolean dense = spell.condensed();
        if (spell.function() == null) {
            return "Feitiço incompleto";
        }
        return switch (spell.function()) {
            case "iactare" -> switch (element == null ? "" : element) {
                case "igni" -> dense ? "Bola de fogo" : "Lança de fogo";
                case "aqua" -> dense ? "Projétil de gelo" : "Jato d'água";
                case "aura" -> dense ? "Bomba de ar" : "Rajada";
                case "firmo" -> dense ? "Meteoro" : "Pedra arremessada";
                case "vis" -> dense ? "Esfera de mana" : "Raio de mana";
                default -> spell.mark() != null ? "Arremesso" : "Lançamento";
            };
            case "vocant" -> switch (element == null ? "" : element) {
                case "igni" -> dense ? "Coração de fogo" : "Chama";
                case "aqua" -> dense ? "Gelo condensado" : "Fonte";
                case "aura" -> dense ? "Explosão de ar" : "Brisa";
                case "firmo" -> dense ? "Rocha condensada" : "Pedra invocada";
                case "vis" -> dense ? "Erupção de vis" : "Luz de mana";
                default -> spell.mark() != null ? "Chamado" : "Invocação";
            };
            case "impediunt" -> "Barreira de " + elementName(element);
            case "exsugat" -> "Absorção de " + elementName(element);
            case "surgit" -> "Revelação";
            case "ligabis" -> "Vínculo";
            case "reframe" -> "Nomeação";
            default -> "Feitiço";
        };
    }

    /** An element's name in the text; a spell with no element works on "energia". */
    private static String elementName(String element) {
        return element == null ? "energia" : ELEMENT.getOrDefault(element, element);
    }

    private static String seconds(int column) {
        return String.format(Locale.ROOT, "%.2f", column * 0.25D).replace('.', ',');
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String lower(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}

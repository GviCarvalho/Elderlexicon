package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a line of a page the way the spirit will and says, for each word, what it is and what it does in that spell:
 * the grimoire shows it when the mage rests the pointer on a rune. Only the reading; nothing is cast.
 */
public final class SpellReading {

    /** What a word is ({@code title}) and what it does in this spell ({@code role}). */
    public record Word(String rune, String title, String role) {
    }

    private enum Kind { EMPTY, SOURCE, FUNCTION, FILTER, NUMBER, MARK, NAMED }

    private static final Map<String, String> ELEMENT = Map.of(
            "igni", "fogo", "aqua", "água", "aura", "ar", "firmo", "terra", "vis", "vis");

    private static final Map<String, String> LORE = Map.ofEntries(
            Map.entry("igni", "Fonte: o fogo, a energia como calor."),
            Map.entry("aqua", "Fonte: a água, a energia como coesão."),
            Map.entry("aura", "Fonte: o ar, a energia como expansão (pressão)."),
            Map.entry("firmo", "Fonte: a terra, a energia como massa (densidade)."),
            Map.entry("vis", "Fonte: a vis, os quatro aspectos em equilíbrio; no corpo, a experiência."),
            Map.entry("exsugat", "Função: absorve uma fonte do mundo."),
            Map.entry("vertere", "Função: converte um elemento em outro."),
            Map.entry("iactare", "Função: lança na direção mirada."),
            Map.entry("vocant", "Função: faz surgir no ponto mirado."),
            Map.entry("impediunt", "Função: afasta um elemento de uma zona."),
            Map.entry("ligabis", "Função: liga coisas marcadas."),
            Map.entry("surgit", "Função: faz o espírito ler e revelar."),
            Map.entry("reframe", "Função: reformula o que foi escrito."),
            Map.entry("transvocatio", "Função: transfere o que foi marcado."),
            Map.entry("quantum", "Filtro: quanto (extensão ou quantidade)."),
            Map.entry("chronos", "Filtro: quando e por quanto tempo."),
            Map.entry("ubis", "Filtro: onde."));

    private final ParserDictionary dictionary;
    /** Runes the mage named with reframe in this grimoire, each with the spell it holds. */
    private final Map<String, List<String>> named;

    public SpellReading(ParserDictionary dictionary) {
        this(dictionary, Map.of());
    }

    public SpellReading(ParserDictionary dictionary, Map<String, List<String>> named) {
        this.dictionary = dictionary;
        this.named = named == null ? Map.of() : Map.copyOf(named);
    }

    /** The same reading, knowing the runes this grimoire named with reframe. */
    public SpellReading withNamed(Map<String, List<String>> runes) {
        return new SpellReading(dictionary, runes);
    }

    /** What a rune is, in general: its name and a line about it. */
    public static String lore(String rune) {
        return LORE.getOrDefault(rune, "Runa de fusão.");
    }

    /** The title of a word: the rune's name and what it translates to, a number, a mark. */
    public String title(String rune) {
        Kind kind = kindOf(rune);
        return switch (kind) {
            case EMPTY -> "Vazio";
            case NUMBER -> "Número " + rune;
            case MARK -> "Marca “" + rune + "”";
            case NAMED -> "Runa nomeada “" + rune + "”";
            default -> ELEMENT.containsKey(rune) ? rune + " · " + ELEMENT.get(rune) : rune;
        };
    }

    /** Each word of a line (normalized ids, {@code ""} for an empty cell) with what it does in that spell. */
    public List<Word> read(List<String> ids) {
        List<Word> words = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String rune = ids.get(i) == null ? "" : ids.get(i);
            words.add(new Word(rune, title(rune), role(ids, i)));
        }
        return words;
    }

    private String role(List<String> ids, int at) {
        String rune = ids.get(at);
        return switch (kindOf(rune)) {
            case EMPTY -> "Um passo de tempo em que nada é lido (0,25 s): atrasa o que vem depois.";
            case NUMBER -> numberRole(ids, at);
            case MARK -> markRole(ids, at);
            case NAMED -> "Lê, neste ponto, o feitiço que ela guarda: " + String.join(" ", named.get(rune)) + ".";
            case SOURCE -> sourceRole(ids, at);
            case FILTER -> filterRole(ids, at);
            case FUNCTION -> functionRole(ids, at);
        };
    }

    private String numberRole(List<String> ids, int at) {
        String number = ids.get(at);
        String before = previousWord(ids, at).map(ids::get).orElse("");
        Optional<Integer> next = nextWord(ids, at);
        if ("quantum".equals(before)) {
            return "O quanto do quantum: " + number + " unidades.";
        }
        if ("chronos".equals(before)) {
            return "0".equals(number) ? "Chronos 0: tudo num único instante."
                    : "O tempo do chronos: " + number + " segundos.";
        }
        if (next.isPresent() && "ubis".equals(ids.get(next.get()))) {
            return "O lugar do ubis: " + number + " blocos na direção mirada.";
        }
        return "Um número sem runa que o use aqui.";
    }

    private String markRole(List<String> ids, int at) {
        String mark = ids.get(at);
        Optional<Integer> next = nextWord(ids, at);
        if (next.isPresent() && "ubis".equals(ids.get(next.get()))) {
            return "O lugar do ubis: onde está o que tem a marca “" + mark + "”.";
        }
        if (ids.contains("ligabis")) {
            return "O que tem a marca “" + mark + "”, no vínculo do ligabis.";
        }
        Optional<String> function = nextFunction(ids, at);
        return function.map(f -> "O objeto do " + f + ": o que tem a marca “" + mark + "”.")
                .orElse("O que tem a marca “" + mark + "”.");
    }

    private String sourceRole(List<String> ids, int at) {
        String element = ELEMENT.getOrDefault(ids.get(at), ids.get(at));
        Optional<Integer> previous = previousWord(ids, at);
        if (previous.isPresent() && "vertere".equals(ids.get(previous.get()))) {
            return "O alvo do vertere: a energia vira " + element + ".";
        }
        Optional<Integer> next = nextWord(ids, at);
        if (next.isPresent() && "exsugat".equals(ids.get(next.get()))) {
            return "A fonte que o exsugat vai buscar no mundo: " + element + ".";
        }
        String from = "vis".equals(ids.get(at)) ? "a mana do corpo (a experiência)" : element + " do corpo";
        return nextFunction(ids, at).map(f -> "A fonte do " + f + ": " + from + ".")
                .orElse("Uma fonte sem função que a use: " + from + ".");
    }

    private String filterRole(List<String> ids, int at) {
        String filter = ids.get(at);
        Optional<Integer> next = nextWord(ids, at);
        String number = next.filter(i -> kindOf(ids.get(i)) == Kind.NUMBER).map(ids::get).orElse(null);
        boolean captured = ids.subList(0, at).contains("exsugat");
        return switch (filter) {
            case "quantum" -> number != null ? "Quanto: " + number + " unidades (" + number + " UMU)."
                    : "Sem número: toda a energia disponível da fonte, "
                    + (captured ? "do mundo ao redor." : "do corpo.");
            case "chronos" -> number == null ? "Chronos sem número."
                    : "0".equals(number) ? "Solta tudo num único instante: condensa, e a intensidade vira a energia toda."
                    : "Dura " + number + " segundos, gastando o tempo todo.";
            case "ubis" -> previousWord(ids, at).map(i -> switch (kindOf(ids.get(i))) {
                case NUMBER -> "Onde: " + ids.get(i) + " blocos na direção mirada.";
                case MARK -> "Onde: no que tem a marca “" + ids.get(i) + "”.";
                default -> "Onde: o que vem antes dele.";
            }).orElse("Onde: falta o que vem antes dele (um número ou uma marca).");
            default -> lore(filter);
        };
    }

    private String functionRole(List<String> ids, int at) {
        String function = ids.get(at);
        String what = subjectBefore(ids, at);
        boolean condensed = condensedBefore(ids, at);
        return switch (function) {
            case "iactare" -> "Lança " + what + " na direção mirada"
                    + (condensed ? ", condensado numa esfera que se junta antes (diante da mão)." : ".");
            case "vocant" -> "Faz " + what + " surgir no ponto mirado"
                    + (condensed ? ", condensado: junta ali antes de surgir." : ".");
            case "exsugat" -> nextFunction(ids, at)
                    .map(f -> "Captura " + what + " do mundo, ao redor da mão, e entrega ao " + f + ".")
                    .orElse("Absorve " + what + " do mundo para o corpo.");
            case "vertere" -> vertereRole(ids, at);
            case "impediunt" -> "Afasta " + what + " de uma zona (um cilindro) ao redor do ponto.";
            case "surgit" -> "Faz o espírito ler e revelar " + what + ".";
            case "ligabis" -> "Liga o que foi marcado.";
            default -> lore(function);
        };
    }

    private String vertereRole(List<String> ids, int at) {
        VitaElement from = elementBefore(ids, at);
        Optional<Integer> next = nextWord(ids, at);
        VitaElement to = next.map(ids::get).map(SpellReading::elementOf).orElse(null);
        if (to == null) {
            return "Converte, mas falta o elemento em que converter.";
        }
        String target = ELEMENT.getOrDefault(ids.get(next.get()), ids.get(next.get()));
        if (from == null) {
            return "Converte em " + target + ".";
        }
        int qualities = Conversion.qualities(from, to);
        String source = ELEMENT.getOrDefault(from.name().toLowerCase(java.util.Locale.ROOT), from.name());
        if (from == VitaElement.BALANCED) {
            source = "vis";
        }
        String cost = qualities == 0 ? "sem custo" : "muda " + qualities + (qualities == 1 ? " qualidade" : " qualidades")
                + ", custa " + (int) Math.round(qualities * Conversion.WORK_PER_QUALITY * 100.0D) + "% da energia";
        boolean inPlace = nextFunction(ids, next.get()).isEmpty();
        return "Converte " + source + " em " + target + " (" + cost + ")"
                + (inPlace ? "; sem função depois, converte no lugar." : ".");
    }

    // ------------------------------------------------------------------ reading around a word

    private Kind kindOf(String rune) {
        if (rune == null || rune.isEmpty()) {
            return Kind.EMPTY;
        }
        if (rune.matches("-?\\d+")) {
            return Kind.NUMBER;
        }
        if (named.containsKey(rune)) {
            return Kind.NAMED;
        }
        return dictionary.lookup(rune).map(definition -> switch (definition.type()) {
            case SOURCE -> Kind.SOURCE;
            case FILTER -> Kind.FILTER;
            default -> Kind.FUNCTION;
        }).orElse(Kind.MARK);
    }

    private static Optional<Integer> previousWord(List<String> ids, int at) {
        for (int i = at - 1; i >= 0; i--) {
            if (ids.get(i) != null && !ids.get(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> nextWord(List<String> ids, int at) {
        for (int i = at + 1; i < ids.size(); i++) {
            if (ids.get(i) != null && !ids.get(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** The next function after {@code at} that uses what comes before it (a vertere converts, it does not use). */
    private Optional<String> nextFunction(List<String> ids, int at) {
        for (int i = at + 1; i < ids.size(); i++) {
            String rune = ids.get(i);
            if (kindOf(rune) == Kind.FUNCTION && !"vertere".equals(rune) && !"exsugat".equals(rune)) {
                return Optional.of(rune);
            }
        }
        return Optional.empty();
    }

    private static final Map<String, String> WITH_ARTICLE = Map.of(
            "igni", "o fogo", "aqua", "a água", "aura", "o ar", "firmo", "a terra", "vis", "a vis");

    /** What a function acts on: the last element before it (after any conversion), or a mark that is not a place. */
    private String subjectBefore(List<String> ids, int at) {
        for (int i = at - 1; i >= 0; i--) {
            String rune = ids.get(i);
            Kind kind = kindOf(rune);
            if (kind == Kind.SOURCE) {
                return WITH_ARTICLE.getOrDefault(rune, rune);
            }
            boolean place = nextWord(ids, i).map(ids::get).filter("ubis"::equals).isPresent();
            if (kind == Kind.MARK && !place) {
                return "o que tem a marca “" + rune + "”";
            }
        }
        return "a energia";
    }

    private static VitaElement elementBefore(List<String> ids, int at) {
        for (int i = at - 1; i >= 0; i--) {
            VitaElement element = elementOf(ids.get(i));
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private static VitaElement elementOf(String rune) {
        if (rune == null) {
            return null;
        }
        return switch (rune) {
            case "igni" -> VitaElement.IGNI;
            case "aqua" -> VitaElement.AQUA;
            case "aura" -> VitaElement.AURA;
            case "firmo" -> VitaElement.FIRMO;
            case "vis" -> VitaElement.BALANCED;
            default -> null;
        };
    }

    private static boolean condensedBefore(List<String> ids, int at) {
        for (int i = 0; i + 1 < at; i++) {
            if ("chronos".equals(ids.get(i)) && "0".equals(nextWord(ids, i).map(ids::get).orElse(""))) {
                return true;
            }
        }
        return false;
    }
}

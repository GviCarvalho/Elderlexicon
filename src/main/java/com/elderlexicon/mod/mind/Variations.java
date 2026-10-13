package com.elderlexicon.mod.mind;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a caster does with what worked (docs/feiticeiro-design.md, 3.5): it varies it. A line is varied into a sentence it
 * can read that does the same thing in the same way, with another energy or another number (the same shape); a page
 * is varied by varying its first line that serves an intent, everywhere that line is written (a volley stays a volley).
 */
public final class Variations {

    private Variations() {
    }

    /** Up to {@code most} variations of {@code option}, out of the sentences in {@code readable}. */
    public static List<Option> of(Option option, List<Phrase> readable, RandomSource random, int most) {
        Optional<Option.Line> varied = option.lines().stream()
                .filter(line -> PhraseCatalog.imagine(line.words()).map(phrase -> phrase.acts().stream()
                        .anyMatch(act -> Mind.serves(act) != null)).orElse(false))
                .findFirst();
        if (varied.isEmpty()) {
            return List.of();
        }
        List<String> words = varied.get().words();
        Optional<Phrase> original = PhraseCatalog.imagine(words);
        if (original.isEmpty()) {
            return List.of();
        }
        String shape = shape(original.get());
        List<Phrase> alike = readable.stream()
                .filter(phrase -> !phrase.words().equals(words) && shape.equals(shape(phrase)))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Option> variations = new ArrayList<>();
        while (!alike.isEmpty() && variations.size() < most) {
            Phrase other = alike.remove(random.nextInt(alike.size()));
            List<Option.Line> lines = option.lines().stream()
                    .map(line -> line.words().equals(words) ? new Option.Line(other.words(), line.delaySteps()) : line)
                    .toList();
            String name = option.lines().size() == 1 ? other.text() : option.name() + " ~ " + other.text();
            Option.page(name, lines).map(page -> new Option(page.name(), page.lines(), page.acts(), page.cost(), true))
                    .ifPresent(variations::add);
        }
        return variations;
    }

    /** What a sentence does, but for its energies and numbers: two sentences of one shape do the same, otherwise. */
    static String shape(Phrase phrase) {
        return phrase.acts().stream()
                .map(act -> act.operation() + ">" + act.toward() + (act.reversed() ? " rev" : "")
                        + (act.fromWorld() ? " world" : "") + (act.atOnce() ? " once" : "")
                        + act.visibility().stream().mapToObj(value -> " v" + value).collect(Collectors.joining()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" | "));
    }
}

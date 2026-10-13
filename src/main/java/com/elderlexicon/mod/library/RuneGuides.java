package com.elderlexicon.mod.library;

import com.elderlexicon.mod.magic.lexicon.Glyphs;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.WordClass;
import com.elderlexicon.mod.mind.Act;
import com.elderlexicon.mod.mind.Aim;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Phrase;
import com.elderlexicon.mod.mind.PhraseCatalog;
import com.elderlexicon.mod.nahva.Understanding;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The guides a librarian keeps (docs/mago-design.md, 2 and 5d): written books made from the lexicon alone, so a rune an
 * addon brings has its guide too.
 * <ul>
 *   <li><b>A rune's guide:</b> its glyph and name, what family it is of, what it is, how it is written in a sentence
 *       (with sentences that read well, and what each does), and how it is lived to be understood.</li>
 *   <li><b>A family's guide</b> (the grammar): what the Fonts, the Functions, the Filters or the Facts are, and each of
 *       their runes in a line.</li>
 * </ul>
 * Reading a guide teaches no rune (docs/aprendizado-runas-design.md): a rune is lived, then dreamt. A guide says what to
 * live.
 */
public final class RuneGuides {

    /** What a guide says it is by, on its cover. */
    public static final String AUTHOR = "A Biblioteca";
    /** About what fits a page of a written book. */
    private static final int PAGE_CHARS = 210;
    private static final ResourceLocation GLYPHS = new ResourceLocation("elderlexicon", "sga");
    private static final String GUIDE_TAG = "ElderGuide";
    /** How many sentences a rune's guide shows. */
    private static final int EXAMPLES = 3;

    /** The four families of the language (docs: Fontes, Funções, Filtros, Fatos). */
    public enum Family {
        FONTS("fontes", "Fontes"),
        FUNCTIONS("funcoes", "Funções"),
        FILTERS("filtros", "Filtros"),
        FACTS("fatos", "Fatos");

        public final String id;
        public final String title;

        Family(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public static Optional<Family> byId(String id) {
            for (Family family : values()) {
                if (family.id.equals(id)) {
                    return Optional.of(family);
                }
            }
            return Optional.empty();
        }

        /** The family a rune is of. */
        public static Family of(Rune rune) {
            return switch (rune.wordClass()) {
                case SOURCE -> FONTS;
                case VERB -> FUNCTIONS;
                case FILTER -> FILTERS;
                case CONDITION, REFERENT -> FACTS;
            };
        }

        /** What the family is, as the guide opens it. */
        String about() {
            return switch (this) {
                case FONTS -> "As fontes dizem com que energia se fala. Cada uma é um aspecto dela: o fogo é calor, a "
                        + "água é coesão, o ar é expansão, a terra é massa; a vis é os quatro em equilíbrio. Uma frase "
                        + "sem fonte lança só luz: não fere ninguém.";
                case FUNCTIONS -> "As funções são o que se faz com a energia: lançar, fazer surgir, afastar, revelar, "
                        + "converter, ligar. Com uma quantidade negativa, quase todas se viram ao contrário: o que "
                        + "lança passa a puxar, o que leva passa a trazer.";
                case FILTERS -> "Os filtros dizem como a função age: quanto, por quanto tempo, onde e de onde. Vêm "
                        + "antes da função que mudam, cada um com o seu número ou a sua marca.";
                case FACTS -> "Os fatos falam do que acontece a quem conjura e de quem está em cena. Uma linha com um "
                        + "fato só vale quando ele acontece; em fluxo, é o acontecimento que a desperta. Ego é quem "
                        + "conjura; ille, o outro do acontecimento.";
            };
        }
    }

    private RuneGuides() {
    }

    /** The runes a librarian keeps guides of: the ones that can be learned, of {@code family}. */
    public static List<String> runesOf(Family family) {
        return Lexicons.get().runes().stream()
                .filter(rune -> Family.of(rune) == family)
                .filter(rune -> rune.verb().map(verb -> !verb.names()).orElse(true))
                .map(Rune::id)
                .toList();
    }

    /** The guide of {@code runeId}, if the lexicon has it. */
    public static Optional<ItemStack> runeGuide(String runeId) {
        Lexicon lexicon = Lexicons.get();
        Optional<Rune> found = lexicon.rune(runeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Rune rune = found.get();
        Family family = Family.of(rune);
        List<Component> pages = new ArrayList<>();
        pages.add(cover(rune, family));
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("O que é. " + rune.lore());
        usage(rune).ifPresent(paragraphs::add);
        List<String> examples = examples(lexicon, rune);
        if (!examples.isEmpty()) {
            paragraphs.add("Frases que se leem bem:");
            paragraphs.addAll(examples);
        }
        pages.addAll(paginate(paragraphs));
        pages.addAll(living(rune));
        return Optional.of(book("Guia de " + rune.id(), pages, runeId));
    }

    /** The guide of a family: the grammar of its runes. */
    public static ItemStack familyGuide(Family family) {
        Lexicon lexicon = Lexicons.get();
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(family.about());
        for (String id : runesOf(family)) {
            lexicon.rune(id).ifPresent(rune -> paragraphs.add(rune.id() + " (" + rune.name() + "): "
                    + withoutFamily(rune.lore())));
        }
        List<Component> pages = new ArrayList<>();
        pages.add(Component.literal("Guia das\n" + family.title + "\n\n").withStyle(ChatFormatting.BOLD)
                .append(Component.literal("Gramática da Língua Antiga, para quem começa.")
                        .withStyle(style -> style.withBold(false).withItalic(true))));
        pages.addAll(paginate(paragraphs));
        return book("Guia das " + family.title, pages, family.id);
    }

    /** Whether {@code stack} is a guide, and of what (a rune's id or a family's). */
    public static Optional<String> subject(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(GUIDE_TAG) ? Optional.of(tag.getString(GUIDE_TAG)) : Optional.empty();
    }

    // ------------------------------------------------------------------ pages

    private static Component cover(Rune rune, Family family) {
        MutableComponent cover = Component.literal("\n");
        Glyphs.glyphForRune(rune.id()).ifPresent(glyph -> cover.append(Component.literal("   " + glyph + "\n")
                .withStyle(style -> style.withFont(GLYPHS).withColor(ChatFormatting.DARK_PURPLE))));
        cover.append(Component.literal("\n" + rune.id()).withStyle(ChatFormatting.BOLD));
        cover.append(Component.literal(" (" + rune.name() + ")\n\n"));
        cover.append(Component.literal("Das " + family.title + ".").withStyle(ChatFormatting.ITALIC));
        return cover;
    }

    /** How it is written in a sentence, out of what the lexicon says of its role. */
    private static Optional<String> usage(Rune rune) {
        Optional<String> role = rune.text("role").or(() -> rune.text("role.number"));
        if (role.isEmpty()) {
            return Optional.empty();
        }
        String text = role.get()
                .replace("{what}", "a fonte")
                .replace("{n}", "N")
                .replace("{mark}", "m1")
                .replace("{element}", "a fonte")
                .replace("{filter}", rune.id())
                .replace("{Filter}", rune.id());
        if (text.contains("{")) {
            return Optional.empty();
        }
        String reversed = rune.text("role.reversed").map(turned -> " Ao contrário: " + turned
                .replace("{what}", "a fonte") + ".").orElse("");
        return Optional.of("Na frase. " + text + (text.endsWith(".") ? "" : ".") + reversed);
    }

    /**
     * A few short sentences with the rune that read well and do something, cheapest first, and what each does.
     * Facts are not in the catalog (they wait for a happening): their guide has none.
     */
    private static List<String> examples(Lexicon lexicon, Rune rune) {
        Optional<PhraseCatalog> catalog = PhraseCatalog.ready();
        if (catalog.isEmpty()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        catalog.get().phrases().stream()
                .filter(phrase -> phrase.runes().contains(rune.id()) && phrase.words().size() <= 5)
                .filter(phrase -> describe(lexicon, phrase).isPresent())
                .sorted(Comparator.comparingInt((Phrase phrase) -> phrase.words().size())
                        .thenComparingDouble(Phrase::cost))
                .limit(EXAMPLES)
                .forEach(phrase -> found.add("  " + phrase.text() + ": " + describe(lexicon, phrase).get()));
        return found;
    }

    /** What a sentence does, in a few words: by the title the lexicon gives it, or by what its verb does. */
    public static Optional<String> describe(Lexicon lexicon, Phrase phrase) {
        for (Act act : phrase.acts()) {
            for (Aim aim : List.of(Aim.TARGET, Aim.SKY, Aim.AWAY, Aim.FEET)) {
                if (Mind.serves(act, aim) == null) {
                    continue;
                }
                Optional<String> titled = sourceOf(lexicon, act.element()).flatMap(source -> source.text(
                        "title." + act.operation() + (act.atOnce() ? ".condensed" : "")));
                if (titled.isPresent() && !act.reversed() && act.toward() != Act.Toward.SELF) {
                    return titled.map(text -> text.toLowerCase(Locale.ROOT));
                }
                return Optional.of(switch (Mind.serves(act, aim)) {
                    case STRIKE -> "fere onde se mira";
                    case DRAW -> "tira " + noun(lexicon, act.element()) + " do corpo mirado";
                    case HEAL -> "traz " + noun(lexicon, act.element()) + " do mundo para o corpo, e cura";
                    case VEIL -> "some da vista";
                    case CALL -> "traz o que tem a marca";
                    case WARD -> "afasta quem chega perto";
                    case FLEE -> "lança quem conjura para onde ele mira";
                    case DOUSE -> "traz água aos pés, e apaga o fogo";
                });
            }
        }
        return Optional.empty();
    }

    private static Optional<Rune> sourceOf(Lexicon lexicon, VitaElement element) {
        return lexicon.runes().stream().filter(rune -> rune.source().map(spec -> spec.element() == element)
                .orElse(false)).findFirst();
    }

    private static String noun(Lexicon lexicon, VitaElement element) {
        return sourceOf(lexicon, element).map(Rune::noun).filter(noun -> !noun.isBlank()).orElse("a energia");
    }

    /** How it is lived, to be understood: what the lesson of the rune asks, if it is written. */
    private static List<Component> living(Rune rune) {
        MutableComponent page = Component.literal("Como vivê-la\n\n").withStyle(ChatFormatting.BOLD);
        Optional<Understanding.RuneLesson> lesson = Understanding.lesson(rune.id());
        if (lesson.isPresent()) {
            page.append(Component.literal("Para entendê-la, é preciso viver no corpo:\n")
                    .withStyle(style -> style.withBold(false)));
            for (String experience : lesson.get().experiences()) {
                page.append(Component.literal("- ").withStyle(style -> style.withBold(false)))
                        .append(Component.translatable("guide.elderlexicon.experience." + experience)
                                .withStyle(style -> style.withBold(false)))
                        .append(Component.literal("\n"));
            }
        } else {
            page.append(Component.translatable("guide.elderlexicon.unwritten")
                    .withStyle(style -> style.withBold(false)));
        }
        Component closing = Component.literal("Ler não ensina a runa: ensina o que ela é. Ela vem num sonho, depois de "
                + "vivida.").withStyle(ChatFormatting.ITALIC);
        return List.of(page, closing);
    }

    /** Paragraphs laid out on pages, as many as each page holds. */
    private static List<Component> paginate(List<String> paragraphs) {
        List<Component> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String paragraph : paragraphs) {
            for (String word : (paragraph + "\n\n").split(" ")) {
                if (page.length() + word.length() + 1 > PAGE_CHARS && page.length() > 0) {
                    pages.add(Component.literal(page.toString().strip()));
                    page.setLength(0);
                }
                page.append(word).append(word.endsWith("\n") ? "" : " ");
            }
        }
        if (!page.toString().isBlank()) {
            pages.add(Component.literal(page.toString().strip()));
        }
        return pages;
    }

    private static String withoutFamily(String lore) {
        int colon = lore.indexOf(':');
        return colon > 0 && colon < 10 ? lore.substring(colon + 1).strip() : lore;
    }

    /** A written book of {@code pages}, by the library. */
    private static ItemStack book(String title, List<Component> pages, String subject) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", title.length() > 32 ? title.substring(0, 32) : title);
        tag.putString("author", AUTHOR);
        ListTag list = new ListTag();
        pages.forEach(page -> list.add(StringTag.valueOf(Component.Serializer.toJson(page))));
        tag.put("pages", list);
        tag.putBoolean("resolved", true);
        tag.putString(GUIDE_TAG, subject);
        return book;
    }
}

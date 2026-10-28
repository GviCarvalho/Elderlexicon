package com.elderlexicon.mod.university;

import com.elderlexicon.mod.dialogue.Dialogue;
import com.elderlexicon.mod.dialogue.Dialogue.Choice;
import com.elderlexicon.mod.dialogue.Dialogue.Line;
import com.elderlexicon.mod.spelling.item.SpellScrollItem;
import com.elderlexicon.mod.wizard.Wizard;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The library of spells of the research tower (docs/torre-pesquisa-design.md, 3): the spells the house keeps ready,
 * out of the book, which its researcher sells as loose pages or lets one copy onto paper of their own. A page is
 * bought whole for its price; copied, it costs half and a sheet of paper. A student of the house copies for the paper
 * alone, and buys at half.
 */
public final class SpellLibrary {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String RESOURCE = "/data/elderlexicon/grimoires/research.json";

    /** A page the house keeps. */
    public record Page(String section, String name, List<String> lines, String about, int price) {

        /** The page's text, as a grimoire keeps it. */
        public String text() {
            return String.join("\n", lines);
        }
    }

    private static List<Page> loaded;

    private SpellLibrary() {
    }

    public static List<Page> pages() {
        List<Page> found = loaded;
        if (found == null) {
            found = read();
            loaded = found;
        }
        return found;
    }

    /** {@code player} speaks to the researcher of the library: which shelf? */
    public static void talk(ServerPlayer player, Wizard librarian) {
        Dialogue.open(player, librarian, shelves(player, librarian, student(player)));
    }

    private static boolean student(ServerPlayer player) {
        return Enrollments.of(player.serverLevel()).find(player.getUUID())
                .flatMap(Enrollments.Student::rank).isPresent();
    }

    private static Line shelves(ServerPlayer player, Wizard librarian, boolean student) {
        Map<String, List<Page>> sections = new LinkedHashMap<>();
        for (Page page : pages()) {
            sections.computeIfAbsent(page.section(), key -> new ArrayList<>()).add(page);
        }
        List<Choice> choices = new ArrayList<>();
        sections.forEach((section, pages) -> choices.add(new Choice(Component.literal(section),
                p -> shelf(p, librarian, student, section, pages))));
        choices.add(new Choice(Component.literal("Só olhando, obrigado."), p -> null));
        return new Line(Component.literal("A casa guarda os feitiços que valem ser guardados, escritos e testados. "
                + "Vendo a página pronta, ou deixo copiar no seu papel"
                + (student ? ": para quem estuda aqui, copiar custa só o papel." : ", pela metade.")
                + " De que estante?"), choices);
    }

    private static Line shelf(ServerPlayer player, Wizard librarian, boolean student, String section,
                              List<Page> pages) {
        List<Choice> choices = new ArrayList<>();
        for (Page page : pages) {
            choices.add(new Choice(Component.literal(page.name()), p -> page(p, librarian, student, page)));
        }
        choices.add(new Choice(Component.literal("Outra estante."), p -> shelves(p, librarian, student)));
        return new Line(Component.literal(section + ". Qual deles?"), choices);
    }

    private static Line page(ServerPlayer player, Wizard librarian, boolean student, Page page) {
        int whole = student ? Math.max(1, page.price() / 2) : page.price();
        int copy = student ? 0 : Math.max(1, page.price() / 2);
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(Component.literal("Comprar a página (" + emeralds(whole) + ")"),
                p -> sell(p, librarian, student, page, whole, false)));
        choices.add(new Choice(Component.literal("Copiar no meu papel (" + (copy == 0 ? "" : emeralds(copy) + " e ")
                + "uma folha de papel)"), p -> sell(p, librarian, student, page, copy, true)));
        choices.add(new Choice(Component.literal("Outro."), p -> shelves(p, librarian, student)));
        return new Line(Component.literal(page.name() + ":\n" + page.text().replace("\n", " / ") + "\n\n"
                + page.about()), choices);
    }

    private static String emeralds(int count) {
        return count + " esmeralda" + (count == 1 ? "" : "s");
    }

    /** Takes the price (and the paper, for a copy), and gives the page. */
    private static Line sell(ServerPlayer player, Wizard librarian, boolean student, Page page, int price,
                             boolean copy) {
        Choice more = new Choice(Component.literal("Ver outro."), p -> shelves(p, librarian, student));
        Choice thanks = new Choice(Component.literal("Obrigado."), p -> null);
        if (player.getInventory().countItem(Items.EMERALD) < price) {
            return new Line(Component.literal("Faltam esmeraldas: são " + emeralds(price) + "."), more, thanks);
        }
        if (copy && player.getInventory().countItem(Items.PAPER) < 1) {
            return new Line(Component.literal("Para copiar, traga uma folha de papel."), more, thanks);
        }
        take(player, Items.EMERALD, price);
        if (copy) {
            take(player, Items.PAPER, 1);
        }
        ItemStack sheet = SpellScrollItem.of(page.text());
        sheet.setHoverName(Component.literal(page.name()));
        ItemHandlerHelper.giveItemToPlayer(player, sheet);
        return new Line(Component.literal(copy ? "Copiado, linha por linha. Confira antes de ler."
                : "Aqui está. Leia antes de conjurar: o livro diz por quê."), more, thanks);
    }

    private static void take(ServerPlayer player, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        player.getInventory().setChanged();
    }

    private static List<Page> read() {
        try (InputStream stream = SpellLibrary.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.error("Missing {}", RESOURCE);
                return List.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<Page> pages = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("pages")) {
                JsonObject page = element.getAsJsonObject();
                List<String> lines = new ArrayList<>();
                page.getAsJsonArray("lines").forEach(line -> lines.add(line.getAsString()));
                pages.add(new Page(page.get("section").getAsString(), page.get("name").getAsString(), lines,
                        page.get("about").getAsString(), page.get("price").getAsInt()));
            }
            return List.copyOf(pages);
        } catch (Exception exception) {
            LOGGER.error("Unable to read {}", RESOURCE, exception);
            return List.of();
        }
    }
}

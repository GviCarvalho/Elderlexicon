package com.elderlexicon.mod.university;

import com.elderlexicon.mod.dialogue.Dialogue;
import com.elderlexicon.mod.dialogue.Dialogue.Choice;
import com.elderlexicon.mod.dialogue.Dialogue.Line;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The keeper of the dormitory tower (docs/universidade-arquitetura-design.md, 6.2). The tower has no stair to its
 * floors: each is a flat, and a student goes up by reading, at the foot, the scroll of their floor ({@code pN_code
 * surgit}, whose text is {@code ego aN_code ubis vocant}). One who has not the energy for it asks the keeper, who reads
 * the call for them in a trance, the way an Emissary brings a guest: it marks them for the while, and reads {@code
 * aN_code ubis <their mark> vocant}, the house paying from the gem it sets in its wand.
 */
public final class Porter {

    /** The floors of the tower above the ground, the last the dream's. */
    public static final int FLOORS = 7;

    /** How long the mark it lays on a student lasts, in ticks: the reading done, theirs is given back. */
    private static final int MARKED_FOR = 200;

    private Porter() {
    }

    /** The mark of where one arrives on {@code floor} (0 is the hall at the foot) of the house of {@code code}. */
    public static String arrival(String code, int floor) {
        return "a" + floor + "_" + code;
    }

    /** The mark of the scroll, at the foot, that sends one to {@code floor}. */
    public static String scroll(String code, int floor) {
        return "p" + floor + "_" + code;
    }

    /** {@code player} speaks to the keeper: to which floor? */
    public static void talk(ServerPlayer player, Wizard keeper, String code) {
        List<Choice> choices = new ArrayList<>();
        for (int n = 1; n <= FLOORS; n++) {
            int floor = n;
            choices.add(new Choice(Component.literal(n + "º andar" + (n == FLOORS ? ", o do sonho" : "")),
                    p -> send(p, keeper, code, floor)));
        }
        choices.add(new Choice(Component.literal("Nenhum, obrigado."), p -> null));
        Dialogue.open(player, keeper, new Line(Component.literal("Esta torre não tem escada para os andares: quem "
                + "mora aqui lê, na parede, o pergaminho do seu andar, e vai. Sem energia para ler, eu leio por você. "
                + "Para qual andar?"), choices));
    }

    /** It marks them, sets what the call costs in its gem, and reads the call that sends them up. */
    private static Line send(ServerPlayer player, Wizard keeper, String code, int floor) {
        String mark = "porta_" + player.getUUID().toString().replace("-", "").substring(0, 8);
        String former = MarkHelper.markForEntity(player).orElse("");
        MarkHelper.applyMark(player, mark);
        // Up the tower: a floor is five blocks, and some way across the hall.
        double cost = MarkCost.movement(player.getMaxHealth(), floor * 5.0D + 12.0D);
        Emissaries.seal(keeper, cost * 2.0D);
        Option call = new Option("chamado do andar",
                List.of(new Option.Line(List.of(arrival(code, floor), "ubis", mark, "vocant"), 0)), List.of(), cost,
                false);
        boolean reading = keeper.casterMind().readToward(call, player.getEyePosition());
        SpellTicks.schedule(player.getServer(), MARKED_FOR, () -> {
            if (MarkHelper.markForEntity(player).map(mark::equals).orElse(false)) {
                MarkHelper.applyMark(player, former);
            }
        });
        return new Line(Component.literal(reading
                ? "Fique onde está. A leitura leva um instante; quando acabar, você estará no " + floor + "º andar."
                : "Agora não posso ler: espere um pouco e me peça de novo."),
                new Choice(Component.literal("Obrigado."), p -> null));
    }
}

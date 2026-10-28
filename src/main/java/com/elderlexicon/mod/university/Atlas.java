package com.elderlexicon.mod.university;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.dialogue.Dialogue;
import com.elderlexicon.mod.dialogue.Dialogue.Choice;
import com.elderlexicon.mod.dialogue.Dialogue.Line;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The Atlas of the research tower (docs/torre-pesquisa-design.md): the researcher at its counter makes a map of what
 * the tower sees round it, the nearest wizard's tower, sorcerer's tower, woodland mansion or ruined portal, marked where it
 * stands, and sells it for emeralds; a student of the house pays half. What the tower does not see (farther than it
 * looks) it says so, and takes nothing.
 */
public final class Atlas {

    /** How far the tower looks, in chunks. */
    private static final int SIGHT = 100;

    private record Kind(String name, TagKey<Structure> structures, MapDecoration.Type mark, int price) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("uma torre de mago", tag("wizard_tower"), MapDecoration.Type.TARGET_X, 4),
            new Kind("uma torre de feiticeiro", tag("sorcerer_tower"), MapDecoration.Type.RED_X, 6),
            // The illagers of the woodland mansion read too, and badly (the evokers).
            new Kind("uma mansão da floresta", StructureTags.ON_WOODLAND_EXPLORER_MAPS, MapDecoration.Type.MANSION, 8),
            new Kind("um portal em ruínas", StructureTags.RUINED_PORTAL, MapDecoration.Type.RED_MARKER, 3));

    private Atlas() {
    }

    private static TagKey<Structure> tag(String name) {
        return TagKey.create(Registries.STRUCTURE, new ResourceLocation(ElderLexicon.MODID, name));
    }

    /** {@code player} speaks to the researcher of the Atlas: a map of what? */
    public static void talk(ServerPlayer player, Wizard researcher) {
        boolean student = Enrollments.of(player.serverLevel()).find(player.getUUID())
                .flatMap(Enrollments.Student::rank).isPresent();
        List<Choice> choices = new ArrayList<>();
        for (Kind kind : KINDS) {
            int price = price(kind, student);
            choices.add(new Choice(Component.literal("Até " + kind.name() + " (" + price + " esmeralda"
                    + (price == 1 ? "" : "s") + ")"), p -> map(p, researcher, kind, price)));
        }
        choices.add(new Choice(Component.literal("Nenhum, obrigado."), p -> null));
        Dialogue.open(player, researcher, new Line(Component.literal("Do alto desta torre se vê longe, e o que se "
                + "vê fica nos mapas. Faço um para você do que estiver mais perto daqui"
                + (student ? "; quem estuda na casa paga a metade." : ".") + " Até onde?"), choices));
    }

    private static int price(Kind kind, boolean student) {
        return student ? Math.max(1, kind.price() / 2) : kind.price();
    }

    /** Looks for the nearest of {@code kind}, and if it sees one and is paid, gives the map of the way to it. */
    private static Line map(ServerPlayer player, Wizard researcher, Kind kind, int price) {
        Choice thanks = new Choice(Component.literal("Obrigado."), p -> null);
        if (player.getInventory().countItem(Items.EMERALD) < price) {
            return new Line(Component.literal("O mapa custa " + price + " esmeraldas, e o papel e a tinta não se "
                    + "pagam sozinhos."), thanks);
        }
        ServerLevel level = player.serverLevel();
        BlockPos found = level.findNearestMapStructure(kind.structures(), researcher.blockPosition(), SIGHT, false);
        if (found == null) {
            return new Line(Component.literal("Daqui não se vê " + kind.name() + ". Não vou cobrar por um mapa em "
                    + "branco."), thanks);
        }
        take(player, price);
        ItemStack map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, found, "+", kind.mark());
        map.setHoverName(Component.literal("Mapa do Atlas: " + kind.name()));
        ItemHandlerHelper.giveItemToPlayer(player, map);
        int far = (int) Math.round(Math.sqrt(found.distSqr(researcher.blockPosition())));
        return new Line(Component.literal("Aqui está: " + kind.name() + ", a uns " + far + " blocos daqui. A marca "
                + "no mapa é onde fica."), thanks);
    }

    private static void take(ServerPlayer player, int count) {
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
    }
}

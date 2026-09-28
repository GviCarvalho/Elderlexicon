package com.elderlexicon.mod.spelling.server;

import com.elderlexicon.mod.spell.circle.CircleShape;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.inscription.Inscription;
import com.elderlexicon.mod.spelling.inscription.Inscriptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pieces of a magic circle in the world (docs/circulos-design.md): scrolls laid on blocks and runes written on
 * block faces, on one plane and facing the same way (on the ground, on a wall, on a ceiling). The pieces near each
 * other are one circle, of any shape; {@link CircleShape} finds its rings.
 */
final class CirclePieces {

    /** How far around a piece the rest of its circle is looked for, in blocks. */
    private static final int SEARCH = 32;

    /** One piece: the block it lies on, the face it lies on, and its page. */
    record Piece(BlockPos support, Direction face, String text) {
        long key() {
            return Inscription.key(support, face);
        }
    }

    /** A circle: its pieces, their rings from the heart out (indices into the pieces), and its middle. */
    record Circle(List<Piece> pieces, List<List<Integer>> rings, Vec3 center) {
        /** A key the same for every piece of the circle, to read each circle once. */
        long key() {
            return pieces.stream().mapToLong(Piece::key).min().orElse(0L);
        }
    }

    private CirclePieces() {
    }

    /** The circle the piece on {@code support}'s {@code face} belongs to, when it has at least two pieces. */
    static Optional<Circle> around(ServerLevel level, BlockPos support, Direction face) {
        List<Piece> pieces = piecesNear(level, support, face);
        List<CircleShape.Spot> spots = new ArrayList<>();
        int anchor = -1;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            spots.add(spot(piece.support(), face));
            if (piece.support().equals(support)) {
                anchor = i;
            }
        }
        if (anchor < 0) {
            return Optional.empty();
        }
        for (List<Integer> group : CircleShape.groups(spots)) {
            if (!group.contains(anchor) || group.size() < 2) {
                continue;
            }
            List<Piece> members = new ArrayList<>();
            List<CircleShape.Spot> memberSpots = new ArrayList<>();
            Vec3 sum = Vec3.ZERO;
            for (int index : group) {
                members.add(pieces.get(index));
                memberSpots.add(spots.get(index));
                sum = sum.add(Vec3.atCenterOf(pieces.get(index).support()));
            }
            return Optional.of(new Circle(members, CircleShape.rings(memberSpots), sum.scale(1.0D / members.size())));
        }
        return Optional.empty();
    }

    /** Scrolls and inscriptions near {@code support}, on the same plane and facing the same way. */
    private static List<Piece> piecesNear(ServerLevel level, BlockPos support, Direction face) {
        List<Piece> pieces = new ArrayList<>();
        AABB box = new AABB(support).inflate(SEARCH);
        for (PlacedScrollEntity scroll : level.getEntitiesOfClass(PlacedScrollEntity.class, box, PlacedScrollEntity::isAlive)) {
            if (scroll.getFace() != face || plane(scroll.supportPos(), face) != plane(support, face)) {
                continue;
            }
            String text = pageOf(scroll.getScroll());
            if (!text.isBlank()) {
                pieces.add(new Piece(scroll.supportPos(), face, text));
            }
        }
        for (Inscription inscription : Inscriptions.of(level).all()) {
            if (inscription.face() == face && plane(inscription.pos(), face) == plane(support, face)
                    && box.contains(Vec3.atCenterOf(inscription.pos())) && !level.getBlockState(inscription.pos()).isAir()) {
                pieces.add(new Piece(inscription.pos(), face, inscription.text()));
            }
        }
        return pieces;
    }

    private static String pageOf(ItemStack scroll) {
        CompoundTag tag = scroll.getTag();
        return tag == null ? "" : tag.getString("DetachedPageText");
    }

    /** Where on its plane a piece is: the two coordinates across the face. */
    private static CircleShape.Spot spot(BlockPos pos, Direction face) {
        return switch (face.getAxis()) {
            case Y -> new CircleShape.Spot(pos.getX(), pos.getZ());
            case Z -> new CircleShape.Spot(pos.getX(), pos.getY());
            case X -> new CircleShape.Spot(pos.getZ(), pos.getY());
        };
    }

    /** Which plane a piece is on: the coordinate along the face's axis. */
    private static int plane(BlockPos pos, Direction face) {
        return switch (face.getAxis()) {
            case Y -> pos.getY();
            case Z -> pos.getZ();
            case X -> pos.getX();
        };
    }
}

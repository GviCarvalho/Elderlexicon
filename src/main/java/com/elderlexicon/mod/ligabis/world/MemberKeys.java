package com.elderlexicon.mod.ligabis.world;

import com.elderlexicon.mod.ligabis.MemberId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Builds and reads the keys the engine uses to name an entity or a block. */
public final class MemberKeys {

    private static final String ENTITY_PREFIX = "e:";
    private static final String BLOCK_PREFIX = "b:";

    private MemberKeys() {
    }

    public static MemberId entity(UUID id) {
        return new MemberId(ENTITY_PREFIX + id);
    }

    public static MemberId block(ResourceKey<Level> dimension, BlockPos pos) {
        return new MemberId(BLOCK_PREFIX + dimension.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    public static boolean isEntity(MemberId member) {
        return member.key().startsWith(ENTITY_PREFIX);
    }

    public static boolean isBlock(MemberId member) {
        return member.key().startsWith(BLOCK_PREFIX);
    }

    public static UUID entityId(MemberId member) {
        return UUID.fromString(member.key().substring(ENTITY_PREFIX.length()));
    }

    public static ResourceKey<Level> blockDimension(MemberId member) {
        String body = member.key().substring(BLOCK_PREFIX.length());
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(body.substring(0, body.indexOf('|'))));
    }

    public static BlockPos blockPos(MemberId member) {
        String body = member.key().substring(member.key().indexOf('|') + 1);
        String[] parts = body.split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}

package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.UUID;

/**
 * Nahvá, the sleeping world ("Nahl", idea; "vá", dimension: book 2.1). One dimension, made of realms, each the idea of a
 * rune. A realm is a world of its own, ruled by the kern dreaming it: each mage dreams alone, in an instance of the realm
 * of their own, so far from every other that none can be seen from another.
 */
public final class Nahva {

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ElderLexicon.MODID, "nahva"));
    /** The realms, in the order they are laid out along z ({@link RealmLayout}). */
    public static final List<String> REALMS = RealmLayout.REALMS;
    public static final int SPACING = RealmLayout.SPACING;

    private Nahva() {
    }

    public static boolean isNahva(Level level) {
        return level != null && level.dimension() == LEVEL;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    /** Where {@code mage}'s own instance of {@code realm} is laid out. */
    public static BlockPos origin(MinecraftServer server, UUID mage, String realm) {
        int slot = Instances.get(server).slotOf(mage);
        // A fever dream is dreamed on the kern itself.
        int index = Math.max(0, REALMS.indexOf(FeverDream.REALM.equals(realm) ? Realms.KERN : realm));
        return new BlockPos(slot * SPACING, 0, index * SPACING);
    }

    /** The realm a position of Nahvá is in. */
    public static String realmAt(double z) {
        return RealmLayout.realmAt(z);
    }

    /** Which instance each mage dreams in: a slot each, kept with the world. */
    static final class Instances extends SavedData {

        private static final String NAME = ElderLexicon.MODID + "_nahva";
        private final CompoundTag slots = new CompoundTag();
        private int next;

        static Instances get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Instances::load, Instances::new, NAME);
        }

        int slotOf(UUID mage) {
            String key = mage.toString();
            if (!slots.contains(key)) {
                slots.putInt(key, next++);
                setDirty();
            }
            return slots.getInt(key);
        }

        static Instances load(CompoundTag tag) {
            Instances instances = new Instances();
            instances.slots.merge(tag.getCompound("slots"));
            instances.next = tag.getInt("next");
            return instances;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.put("slots", slots.copy());
            tag.putInt("next", next);
            return tag;
        }
    }
}

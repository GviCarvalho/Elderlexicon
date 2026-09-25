package com.elderlexicon.mod.ligabis.world;

import com.elderlexicon.mod.ligabis.Aspect;
import com.elderlexicon.mod.ligabis.Link;
import com.elderlexicon.mod.ligabis.MemberId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What has to survive a restart: the links, and the marks and wear of blocks (a plain block cannot
 * carry a mark of its own, so it is kept here).
 */
public final class LigabisData extends SavedData {

    private static final String NAME = "elderlexicon_ligabis";

    /** A marked block: the block it was when marked, its mark and the damage it has absorbed. */
    public record StoredBlock(String block, String mark, double wear) { }

    /**
     * A marked entity whose chunk was unloaded: where it was last seen, so a spell can load it back
     * ({@code docs/marcas-como-runas-design.md}, section 7). Mass is its maximum health, for the cost.
     */
    public record StoredEntity(String mark, String dimension, double x, double y, double z, double mass) { }

    private final List<Link> links = new ArrayList<>();
    private final Map<MemberId, StoredBlock> blocks = new LinkedHashMap<>();
    private final Map<UUID, StoredEntity> entities = new LinkedHashMap<>();
    /** Marked scrolls (in frames or placed), wherever they are, so a bound ritual can load and read them. */
    private final Map<UUID, StoredEntity> scrolls = new LinkedHashMap<>();

    public static LigabisData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LigabisData::load, LigabisData::new, NAME);
    }

    public List<Link> links() {
        return new ArrayList<>(links);
    }

    public void addLink(Link link) {
        if (!links.contains(link)) {
            links.add(link);
            setDirty();
        }
    }

    public void removeLink(Link link) {
        if (links.remove(link)) {
            setDirty();
        }
    }

    public Map<MemberId, StoredBlock> blocks() {
        return new LinkedHashMap<>(blocks);
    }

    public StoredBlock block(MemberId id) {
        return blocks.get(id);
    }

    public void putBlock(MemberId id, StoredBlock block) {
        blocks.put(id, block);
        setDirty();
    }

    public void removeBlock(MemberId id) {
        if (blocks.remove(id) != null) {
            setDirty();
        }
    }

    public void setWear(MemberId id, double wear) {
        StoredBlock current = blocks.get(id);
        if (current != null) {
            blocks.put(id, new StoredBlock(current.block(), current.mark(), wear));
            setDirty();
        }
    }

    public Map<UUID, StoredEntity> entities() {
        return new LinkedHashMap<>(entities);
    }

    public void putEntity(UUID id, StoredEntity entity) {
        entities.put(id, entity);
        setDirty();
    }

    public void removeEntity(UUID id) {
        if (entities.remove(id) != null) {
            setDirty();
        }
    }

    public Map<UUID, StoredEntity> scrolls() {
        return new LinkedHashMap<>(scrolls);
    }

    public void putScroll(UUID id, StoredEntity scroll) {
        if (!scroll.equals(scrolls.put(id, scroll))) {
            setDirty();
        }
    }

    public void removeScroll(UUID id) {
        if (scrolls.remove(id) != null) {
            setDirty();
        }
    }

    static LigabisData load(CompoundTag tag) {
        LigabisData data = new LigabisData();
        for (Tag raw : tag.getList("links", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                data.links.add(new Link(entry.getUUID("owner"), Aspect.valueOf(entry.getString("aspect")),
                        entry.getString("first"), entry.contains("second") ? entry.getString("second") : null));
            } catch (IllegalArgumentException ignored) {
                // A link that no longer makes sense is dropped rather than breaking the world.
            }
        }
        for (Tag raw : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            data.blocks.put(new MemberId(entry.getString("key")),
                    new StoredBlock(entry.getString("block"), entry.getString("mark"), entry.getDouble("wear")));
        }
        readEntities(tag.getList("entities", Tag.TAG_COMPOUND), data.entities);
        readEntities(tag.getList("scrolls", Tag.TAG_COMPOUND), data.scrolls);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag linkList = new ListTag();
        for (Link link : links) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", link.owner());
            entry.putString("aspect", link.aspect().name());
            entry.putString("first", link.first());
            if (link.second() != null) {
                entry.putString("second", link.second());
            }
            linkList.add(entry);
        }
        tag.put("links", linkList);

        ListTag blockList = new ListTag();
        for (Map.Entry<MemberId, StoredBlock> block : blocks.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", block.getKey().key());
            entry.putString("block", block.getValue().block());
            entry.putString("mark", block.getValue().mark());
            entry.putDouble("wear", block.getValue().wear());
            blockList.add(entry);
        }
        tag.put("blocks", blockList);

        tag.put("entities", writeEntities(entities));
        tag.put("scrolls", writeEntities(scrolls));
        return tag;
    }

    private static void readEntities(ListTag list, Map<UUID, StoredEntity> into) {
        for (Tag raw : list) {
            CompoundTag entry = (CompoundTag) raw;
            into.put(entry.getUUID("id"), new StoredEntity(entry.getString("mark"), entry.getString("dimension"),
                    entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"), entry.getDouble("mass")));
        }
    }

    private static ListTag writeEntities(Map<UUID, StoredEntity> from) {
        ListTag entityList = new ListTag();
        for (Map.Entry<UUID, StoredEntity> stored : from.entrySet()) {
            CompoundTag entry = new CompoundTag();
            StoredEntity entity = stored.getValue();
            entry.putUUID("id", stored.getKey());
            entry.putString("mark", entity.mark());
            entry.putString("dimension", entity.dimension());
            entry.putDouble("x", entity.x());
            entry.putDouble("y", entity.y());
            entry.putDouble("z", entity.z());
            entry.putDouble("mass", entity.mass());
            entityList.add(entry);
        }
        return entityList;
    }
}

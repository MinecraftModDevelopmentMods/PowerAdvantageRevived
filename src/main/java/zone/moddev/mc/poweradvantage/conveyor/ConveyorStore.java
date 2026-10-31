package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import zone.moddev.mc.poweradvantage.network.transport.TransportNode;

/**
 * Dimension SavedData for authoritative conveyor ownership, separate from derived routing topology. Records are
 * indexed by segment, block position and chunk; waiting records preserve ownership when a segment is already
 * occupied.
 */
public final class ConveyorStore extends SavedData {
    /**
     * Creates an empty authoritative cargo store and its spatial indexes.
     */
    public ConveyorStore() {}

    /**
     * Mutable server-owned cargo record. Movement changes its position, entry and progress while preserving its
     * UUID and item components.
     */
    static final class Travel {
        /**
         * Stable identity of the authoritative cargo record.
         */
        final UUID id;
        /**
         * Physical channel currently owning the cargo.
         */
        TransportNode node;
        /**
         * Owned item contents, including all data components.
         */
        ItemStack stack;
        /**
         * Face from which the cargo entered its current segment.
         */
        Direction entry;
        /**
         * Processed movement ticks within the current segment.
         */
        int progress;
        /**
         * Whether ownership is retained without occupying the already-full segment.
         */
        boolean waiting;
        /**
         * Copies extracted/admitted stack contents into a stable cargo identity and initial physical travel state.
         *
         * @param id Stable identity of the authoritative cargo record
         * @param node Physical channel currently owning the cargo
         * @param stack Owned item contents, including all data components
         * @param entry Face from which the cargo entered its current segment
         * @param progress Processed movement ticks within the current segment
         */
        Travel(UUID id,TransportNode node,ItemStack stack,Direction entry,int progress) {
            this.id=id;this.node=node;this.stack=stack.copy();this.entry=entry;this.progress=progress;
        }
    }
    /**
     * Authoritative cargo records keyed by stable UUID.
     */
    final Map<UUID,Travel> records=new LinkedHashMap<>();
    /**
     * Nonwaiting cargo UUID by occupied segment/channel.
     */
    private final Map<TransportNode,UUID> occupied=new HashMap<>();
    /**
     * Cargo UUIDs indexed by owning block position.
     */
    private final Map<BlockPos,Set<UUID>> positions=new HashMap<>();
    /**
     * Cargo UUIDs indexed by owning packed chunk coordinate.
     */
    private final Map<Long,Set<UUID>> chunks=new HashMap<>();
    /**
     * Chunks requiring updated client cargo snapshots.
     */
    final Set<Long> dirtyChunks=new LinkedHashSet<>();
    /**
     * Creates the SavedData factory for owned conveyor records.
     *
     * @return cargo saved-data constructor/loader
     */
    public static Factory<ConveyorStore> factory(){return new Factory<>(ConveyorStore::new,ConveyorStore::load);}
    /**
     * Looks up cargo indexed at a segment or block position.
     *
     * @param node physical position/channel identity
     * @return owned record or records; null for an empty segment
     */
    Travel at(TransportNode node){return records.get(occupied.get(node));}
    /**
     * Looks up cargo indexed at a segment or block position.
     *
     * @param pos block position being queried or changed
     * @return owned record or records; null for an empty segment
     */
    List<Travel> at(BlockPos pos){return positions.getOrDefault(pos,Set.of()).stream().map(records::get).toList();}
    /**
     * Snapshots records indexed within a packed chunk coordinate.
     *
     * @param chunk packed chunk coordinate
     * @return records in that chunk
     */
    List<Travel> inChunk(long chunk){return chunks.getOrDefault(chunk,Set.of()).stream().map(records::get).toList();}
    /**
     * Indexes a record by occupancy, block position and chunk; waiting records do not occupy a segment.
     *
     * @param item authoritative cargo record
     */
    private void index(Travel item) {
        if(!item.waiting)occupied.put(item.node,item.id);
        positions.computeIfAbsent(item.node.position(),p->new LinkedHashSet<>()).add(item.id);
        chunks.computeIfAbsent(ChunkPos.asLong(item.node.position()),p->new LinkedHashSet<>()).add(item.id);
    }
    /**
     * Removes one UUID from an index bucket and prunes the bucket if empty.
     *
     * @param <K> stable lookup or fairness-key type
     * @param map spatial index whose UUID bucket is updated
     * @param key spatial index or watcher-job key
     * @param id stable cargo UUID
     */
    private <K> void removeIndex(Map<K,Set<UUID>> map,K key,UUID id) {
        var values=map.get(key);if(values!=null){values.remove(id);if(values.isEmpty())map.remove(key);}
    }
    /**
     * Removes a record from occupancy and spatial indexes without discarding its ownership.
     *
     * @param item authoritative cargo record
     */
    private void unindex(Travel item) {
        occupied.remove(item.node,item.id);removeIndex(positions,item.node.position(),item.id);
        removeIndex(chunks,ChunkPos.asLong(item.node.position()),item.id);
    }
    /**
     * Stores a unique cargo UUID, marking it waiting if its segment is already occupied.
     *
     * @param item authoritative cargo record
     * @throws IllegalArgumentException if the cargo UUID is already owned
     */
    void add(Travel item) {
        if(records.containsKey(item.id))throw new IllegalArgumentException("Duplicate conveyor record");
        item.waiting|=occupied.containsKey(item.node);
        records.put(item.id,item);index(item);changed(item);
    }
    /**
     * Moves a record to a free segment, resets travel progress and dirties both owning chunks.
     *
     * @param item authoritative cargo record
     * @param target directed destination vertex or free next segment
     * @param entry face from which the record entered its segment
     * @throws IllegalStateException if the destination channel is occupied
     */
    void move(Travel item,TransportNode target,Direction entry) {
        if(occupied.containsKey(target))throw new IllegalStateException("Occupied conveyor segment");
        unindex(item);changed(item);item.node=target;item.entry=entry;item.progress=0;item.waiting=false;index(item);changed(item);
    }
    /**
     * Makes a waiting record occupy its now-free segment.
     *
     * @param item authoritative cargo record
     * @throws IllegalStateException if the channel is still occupied
     */
    void promote(Travel item){if(at(item.node)!=null)throw new IllegalStateException("Occupied segment");item.waiting=false;occupied.put(item.node,item.id);changed(item);}
    /**
     * Discards an empty or fully transferred record and removes its indexes.
     *
     * @param item authoritative cargo record
     */
    void remove(Travel item){unindex(item);records.remove(item.id);changed(item);}
    /**
     * Marks persistent ownership and the owning client chunk dirty.
     *
     * @param item authoritative cargo record
     */
    void changed(Travel item){setDirty();dirtyChunks.add(ChunkPos.asLong(item.node.position()));}
    /**
     * Restores prototypes, counts, UUIDs, channels and travel state; duplicate occupancy becomes waiting ownership.
     *
     * @param tag compound tag read or populated by this operation
     * @param registries registry lookup provider used by item/fluid NBT codecs
     * @return restored cargo store
     */
    public static ConveyorStore load(CompoundTag tag,HolderLookup.Provider registries) {
        var store=new ConveyorStore();
        for(var element:tag.getList("Items",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)element;
            var stack=ItemStack.parseOptional(registries,entry.getCompound("Stack"));
            int count=entry.getInt("Count");if(stack.isEmpty()||count<=0)continue;
            stack.setCount(count);
            var node=new TransportNode(BlockPos.of(entry.getLong("Position")),entry.getString("Channel"));
            var item=new Travel(entry.getUUID("Id"),node,stack,Direction.from3DDataValue(entry.getInt("Entry")),Math.max(0,entry.getInt("Progress")));
            item.waiting=entry.getBoolean("Waiting");store.add(item);
        }
        store.dirtyChunks.clear();return store;
    }
    /**
     * Serializes record identity and item components, keeping quantity separate from the one-item prototype.
     *
     * @param tag compound tag read or populated by this operation
     * @param registries registry lookup provider used by item/fluid NBT codecs
     * @return tag containing authoritative cargo records
     */
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag();
        for(var item:records.values()) {
            var entry=new CompoundTag();entry.putUUID("Id",item.id);entry.putLong("Position",item.node.position().asLong());
            entry.putString("Channel",item.node.channel());entry.putInt("Entry",item.entry.ordinal());entry.putInt("Progress",item.progress);
            entry.putBoolean("Waiting",item.waiting);entry.putInt("Count",item.stack.getCount());
            entry.put("Stack",item.stack.copyWithCount(1).save(registries));list.add(entry);
        }
        tag.putInt("Version",1);tag.put("Items",list);return tag;
    }
}

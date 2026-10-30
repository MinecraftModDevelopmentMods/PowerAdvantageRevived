package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import zone.moddev.mc.poweradvantage.network.transport.TransportNode;

/** Authoritative stack ownership, persisted independently of loaded topology and block entities. */
public final class ConveyorStore extends SavedData {
    static final class Travel {
        final UUID id;TransportNode node;ItemStack stack;Direction entry;int progress;boolean waiting;
        Travel(UUID id,TransportNode node,ItemStack stack,Direction entry,int progress) {
            this.id=id;this.node=node;this.stack=stack.copy();this.entry=entry;this.progress=progress;
        }
    }
    final Map<UUID,Travel> records=new LinkedHashMap<>();
    private final Map<TransportNode,UUID> occupied=new HashMap<>();
    private final Map<BlockPos,Set<UUID>> positions=new HashMap<>();
    private final Map<Long,Set<UUID>> chunks=new HashMap<>();
    final Set<Long> dirtyChunks=new LinkedHashSet<>();
    public static Factory<ConveyorStore> factory(){return new Factory<>(ConveyorStore::new,ConveyorStore::load);}
    Travel at(TransportNode node){return records.get(occupied.get(node));}
    List<Travel> at(BlockPos pos){return positions.getOrDefault(pos,Set.of()).stream().map(records::get).toList();}
    List<Travel> inChunk(long chunk){return chunks.getOrDefault(chunk,Set.of()).stream().map(records::get).toList();}
    private void index(Travel item) {
        if(!item.waiting)occupied.put(item.node,item.id);
        positions.computeIfAbsent(item.node.position(),p->new LinkedHashSet<>()).add(item.id);
        chunks.computeIfAbsent(ChunkPos.asLong(item.node.position()),p->new LinkedHashSet<>()).add(item.id);
    }
    private <K> void removeIndex(Map<K,Set<UUID>> map,K key,UUID id) {
        var values=map.get(key);if(values!=null){values.remove(id);if(values.isEmpty())map.remove(key);}
    }
    private void unindex(Travel item) {
        occupied.remove(item.node,item.id);removeIndex(positions,item.node.position(),item.id);
        removeIndex(chunks,ChunkPos.asLong(item.node.position()),item.id);
    }
    void add(Travel item) {
        if(records.containsKey(item.id))throw new IllegalArgumentException("Duplicate conveyor record");
        item.waiting|=occupied.containsKey(item.node);
        records.put(item.id,item);index(item);changed(item);
    }
    void move(Travel item,TransportNode target,Direction entry) {
        if(occupied.containsKey(target))throw new IllegalStateException("Occupied conveyor segment");
        unindex(item);changed(item);item.node=target;item.entry=entry;item.progress=0;item.waiting=false;index(item);changed(item);
    }
    void promote(Travel item){if(at(item.node)!=null)throw new IllegalStateException("Occupied segment");item.waiting=false;occupied.put(item.node,item.id);changed(item);}
    void remove(Travel item){unindex(item);records.remove(item.id);changed(item);}
    void changed(Travel item){setDirty();dirtyChunks.add(ChunkPos.asLong(item.node.position()));}
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

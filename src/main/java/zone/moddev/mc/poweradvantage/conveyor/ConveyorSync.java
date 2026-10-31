package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;

/**
 * Server-side, chunk-scoped snapshot scheduler. Sends only to current watchers and paces fragments through the
 * configured packet budget.
 */
final class ConveyorSync {
    /**
     * Identity of one player/chunk synchronization job.
     *
     * @param player UUID of the player watching this chunk
     * @param chunk Packed watched chunk coordinate
     */
    private record Key(UUID player,long chunk) {}
    /**
     * Stable snapshot being transmitted incrementally; changes during transmission request another complete
     * snapshot afterward.
     */
    private static final class Job {
        /**
         * Generation shared by all fragments of this snapshot.
         */
        final long version;
        /**
         * Immutable rendering-record snapshot being transmitted.
         */
        final List<ConveyorSnapshot.View> items;
        /**
         * Index of the next fragment to transmit.
         */
        int part;
        /**
         * Whether changes during transmission require a fresh snapshot afterward.
         */
        boolean again;
        /**
         * Captures a stable rendering-record list for paced transmission.
         *
         * @param version Generation shared by all fragments of this snapshot
         * @param items Immutable rendering-record snapshot being transmitted
         */
        Job(long version,List<ConveyorSnapshot.View> items){this.version=version;this.items=items;}
    }
    /**
     * Server dimension whose watchers receive snapshots.
     */
    private final ServerLevel level;
    /**
     * Authoritative conveyor engine supplying owned cargo.
     */
    private final ConveyorNetworks manager;
    /**
     * Player UUID subscriptions by packed watched chunk.
     */
    private final Map<Long,Set<UUID>> watchers=new HashMap<>();
    /**
     * Stable in-flight snapshot jobs by player/chunk identity.
     */
    private final Map<Key,Job> pending=new HashMap<>();
    /**
     * Rotating synchronization-job keys awaiting another packet fragment.
     */
    private final ArrayDeque<Key> queue=new ArrayDeque<>();
    /**
     * Monotonic snapshot-job generation for this manager lifetime.
     */
    private long version;
    /**
     * Positive maximum fragments transmitted in one dimension tick.
     */
    private final int packetsPerTick;
    /**
     * Creates the dimension's chunk-watcher scheduler with a positive packet work budget.
     *
     * @param level Server dimension whose watchers receive snapshots
     * @param manager Authoritative conveyor engine supplying owned cargo
     * @param packetsPerTick Positive maximum fragments transmitted in one dimension tick
     */
    ConveyorSync(ServerLevel level,ConveyorNetworks manager,int packetsPerTick){this.level=level;this.manager=manager;this.packetsPerTick=packetsPerTick;}
    /**
     * Adds a current chunk watcher and requests an initial complete cargo snapshot.
     *
     * @param player interacting or watching player
     * @param chunk chunk coordinate being watched or withdrawn
     */
    void watch(ServerPlayer player,ChunkPos chunk) {
        watchers.computeIfAbsent(chunk.toLong(),p->new HashSet<>()).add(player.getUUID());request(new Key(player.getUUID(),chunk.toLong()));
    }
    /**
     * Removes a chunk watcher and its pending snapshot job.
     *
     * @param player interacting or watching player
     * @param chunk chunk coordinate being watched or withdrawn
     */
    void unwatch(ServerPlayer player,ChunkPos chunk) {
        var players=watchers.get(chunk.toLong());if(players!=null){players.remove(player.getUUID());if(players.isEmpty())watchers.remove(chunk.toLong());}
        pending.remove(new Key(player.getUUID(),chunk.toLong()));
    }
    /**
     * Queues a stable chunk snapshot or marks an in-flight job for another snapshot after completion.
     *
     * @param key spatial index or watcher-job key
     */
    private void request(Key key) {
        var existing=pending.get(key);if(existing!=null){existing.again=true;return;}
        var views=new ArrayList<ConveyorSnapshot.View>();
        for(var item:manager.store.inChunk(key.chunk))if(!item.waiting&&level.isLoaded(item.node.position())) {
            int duration=level.getBlockState(item.node.position()).getBlock() instanceof ConveyorBlock belt?belt.travelTicks():1;
            views.add(new ConveyorSnapshot.View(item.id,item.node.position(),item.stack,item.stack.getCount(),item.entry,item.progress,duration));
        }
        pending.put(key,new Job(++version,List.copyOf(views)));queue.addLast(key);
    }
    /**
     * Queues changed chunks on the five-tick cadence and sends bounded batches only to current same-dimension
     * watchers.
     */
    void tick() {
        if(level.getGameTime()%5==0) {
            var iterator=manager.store.dirtyChunks.iterator();int count=0;
            while(iterator.hasNext()&&count++<packetsPerTick) {
                long chunk=iterator.next();iterator.remove();
                for(var player:watchers.getOrDefault(chunk,Set.of()))request(new Key(player,chunk));
            }
        }
        for(int sent=0;sent<packetsPerTick&&!queue.isEmpty();sent++) {
            var key=queue.removeFirst();var job=pending.get(key);if(job==null)continue;
            var player=level.getServer().getPlayerList().getPlayer(key.player);
            if(player==null||player.serverLevel()!=level||!watchers.getOrDefault(key.chunk,Set.of()).contains(key.player)){pending.remove(key);continue;}
            int parts=Math.max(1,(job.items.size()+127)/128),from=job.part*128,to=Math.min(job.items.size(),from+128);
            PacketDistributor.sendToPlayer(player,new ConveyorSnapshot(level.dimension().location(),key.chunk,job.version,job.part,parts,job.items.subList(from,to)));
            if(++job.part<parts)queue.addLast(key);
            else {pending.remove(key);if(job.again)request(key);}
        }
    }
}

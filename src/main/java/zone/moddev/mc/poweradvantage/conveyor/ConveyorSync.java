package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;

/** Sends only watched chunks, at most 32 packets of 128 records per dimension tick. */
final class ConveyorSync {
    private record Key(UUID player,long chunk) {}
    private static final class Job {
        final long version;final List<ConveyorSnapshot.View> items;int part;boolean again;
        Job(long version,List<ConveyorSnapshot.View> items){this.version=version;this.items=items;}
    }
    private final ServerLevel level;private final ConveyorNetworks manager;
    private final Map<Long,Set<UUID>> watchers=new HashMap<>();
    private final Map<Key,Job> pending=new HashMap<>();
    private final ArrayDeque<Key> queue=new ArrayDeque<>();
    private long version;
    private final int packetsPerTick;
    ConveyorSync(ServerLevel level,ConveyorNetworks manager,int packetsPerTick){this.level=level;this.manager=manager;this.packetsPerTick=packetsPerTick;}
    void watch(ServerPlayer player,ChunkPos chunk) {
        watchers.computeIfAbsent(chunk.toLong(),p->new HashSet<>()).add(player.getUUID());request(new Key(player.getUUID(),chunk.toLong()));
    }
    void unwatch(ServerPlayer player,ChunkPos chunk) {
        var players=watchers.get(chunk.toLong());if(players!=null){players.remove(player.getUUID());if(players.isEmpty())watchers.remove(chunk.toLong());}
        pending.remove(new Key(player.getUUID(),chunk.toLong()));
    }
    private void request(Key key) {
        var existing=pending.get(key);if(existing!=null){existing.again=true;return;}
        var views=new ArrayList<ConveyorSnapshot.View>();
        for(var item:manager.store.inChunk(key.chunk))if(!item.waiting&&level.isLoaded(item.node.position())) {
            int duration=level.getBlockState(item.node.position()).getBlock() instanceof ConveyorBlock belt?belt.travelTicks():1;
            views.add(new ConveyorSnapshot.View(item.id,item.node.position(),item.stack,item.stack.getCount(),item.entry,item.progress,duration));
        }
        pending.put(key,new Job(++version,List.copyOf(views)));queue.addLast(key);
    }
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

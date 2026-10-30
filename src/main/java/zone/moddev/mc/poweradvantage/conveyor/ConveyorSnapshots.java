package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;

/** Client-independent snapshot assembler; incomplete or stale batches never replace visible state. */
public final class ConveyorSnapshots {
    public record Snapshot(long chunk,long version,List<ConveyorSnapshot.View> items) {}
    private static final class Pending {
        final long version;final List<List<ConveyorSnapshot.View>> parts;
        Pending(long version,int count){this.version=version;parts=new ArrayList<>(Collections.nCopies(count,null));}
    }
    private final Map<Long,Long> versions=new HashMap<>();
    private final Map<Long,Pending> pending=new HashMap<>();
    public Optional<Snapshot> accept(ConveyorSnapshot packet) {
        if(packet.parts()<=0||packet.parts()>65536||packet.part()<0||packet.part()>=packet.parts())throw new IllegalArgumentException("Invalid snapshot batch");
        if(versions.getOrDefault(packet.chunk(),Long.MIN_VALUE)>=packet.version())return Optional.empty();
        var job=pending.get(packet.chunk());
        if(job==null||job.version<packet.version()){job=new Pending(packet.version(),packet.parts());pending.put(packet.chunk(),job);}
        if(job.version!=packet.version()||job.parts.size()!=packet.parts())return Optional.empty();
        job.parts.set(packet.part(),List.copyOf(packet.items()));
        if(job.parts.stream().anyMatch(Objects::isNull))return Optional.empty();
        var all=new ArrayList<ConveyorSnapshot.View>();for(var part:job.parts)all.addAll(part);
        versions.put(packet.chunk(),packet.version());pending.remove(packet.chunk());
        return Optional.of(new Snapshot(packet.chunk(),packet.version(),List.copyOf(all)));
    }
    public void forget(long chunk){versions.remove(chunk);pending.remove(chunk);}
    public void clear(){versions.clear();pending.clear();}
}

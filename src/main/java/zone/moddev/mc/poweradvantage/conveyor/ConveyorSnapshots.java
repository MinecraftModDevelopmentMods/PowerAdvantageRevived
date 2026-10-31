package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;

/**
 * Resource-independent snapshot assembler. Only complete newer snapshots replace visible state; forgetting a chunk
 * permits a fresh server epoch on rewatch.
 */
public final class ConveyorSnapshots {
    /**
     * Creates an empty chunk-version and fragment assembler.
     */
    public ConveyorSnapshots() {}

    /**
     * Completed immutable sequence of rendering records for one chunk and server snapshot version.
     *
     * @param chunk Packed chunk coordinate replaced by the completed snapshot
     * @param version Completed server snapshot generation
     * @param items Immutable rendering records assembled in fragment order
     */
    public record Snapshot(long chunk,long version,List<ConveyorSnapshot.View> items) {}
    /**
     * Partially received snapshot whose fragments are retained in wire batch order.
     */
    private static final class Pending {
        /**
         * Generation being assembled.
         */
        final long version;
        /**
         * Wire-order fragment slots; null denotes a missing fragment.
         */
        final List<List<ConveyorSnapshot.View>> parts;
        /**
         * Allocates ordered empty fragment slots for the snapshot version.
         *
         * @param version Generation being assembled
         * @param count number of candidates or entries
         */
        Pending(long version,int count){this.version=version;parts=new ArrayList<>(Collections.nCopies(count,null));}
    }
    /**
     * Newest completed snapshot generation by chunk.
     */
    private final Map<Long,Long> versions=new HashMap<>();
    /**
     * Partial newer snapshots by chunk.
     */
    private final Map<Long,Pending> pending=new HashMap<>();
    /**
     * Accepts a newer fragment and publishes state only after every part of that version has arrived.
     *
     * @param packet chunk snapshot fragment
     * @return completed newer snapshot, or empty for stale/incomplete batches
     * @throws IllegalArgumentException if fragment index/count bounds are invalid
     */
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
    /**
     * Forgets one chunk's version and pending assembly so a fresh watch epoch may begin.
     *
     * @param chunk packed chunk coordinate
     */
    public void forget(long chunk){versions.remove(chunk);pending.remove(chunk);}
    /**
     * Clears all assembled-version and pending-fragment state on world/session reset.
     */
    public void clear(){versions.clear();pending.clear();}
}

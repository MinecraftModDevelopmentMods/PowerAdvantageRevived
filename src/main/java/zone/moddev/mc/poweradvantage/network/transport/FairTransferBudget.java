package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;

/** Shared per-tick volume allowance and rotating ingress/destination service; units are adapter-defined. */
public final class FairTransferBudget<K> {
    private final Map<K,Long> contenders=new LinkedHashMap<>();
    private final int capacity;
    private long tick=Long.MIN_VALUE;
    private int remaining,sourceCursor,destinationCursor;
    private K granted;
    public FairTransferBudget(int capacity) {
        if(capacity<=0)throw new IllegalArgumentException("Capacity must be positive");
        this.capacity=capacity;
    }
    /** Register demand without consuming volume. A newly observed ingress may wait for its turn. */
    public int allowance(long now,K contender,int requested) {
        if(requested<=0)return 0;
        contenders.put(contender,now);
        if(tick!=now) {
            tick=now;remaining=capacity;
            contenders.entrySet().removeIf(entry->now-entry.getValue()>40);
            var active=new ArrayList<>(contenders.keySet());
            sourceCursor=Math.floorMod(sourceCursor,active.size());
            granted=active.get(sourceCursor++);
        }
        return Objects.equals(contender,granted)?Math.min(requested,remaining):0;
    }
    public int remaining(long now) { return tick==now?remaining:capacity; }
    public void consume(int amount) {
        if(amount<0 || amount>remaining)throw new IllegalArgumentException("Consumption exceeds allowance");
        remaining-=amount;
    }
    public int destinationIndex(int offset,int count) {
        if(count<=0)throw new IllegalArgumentException("No destinations");
        return Math.floorMod((long)destinationCursor+offset,count);
    }
    public void servedDestination(int index,int count) { destinationCursor=Math.floorMod((long)index+1,count); }
}

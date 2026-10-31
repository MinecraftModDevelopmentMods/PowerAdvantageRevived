package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;

/**
 * Shared per-tick quantity allowance with rotating ingress turns and destination cursors. Units are chosen by the
 * transport engine; simulations register demand without consuming quantity.
 *
 * @param <K> stable lookup or fairness-key type
 */
public final class FairTransferBudget<K> {
    /**
     * Recently active ingress identities and their last demand ticks.
     */
    private final Map<K,Long> contenders=new LinkedHashMap<>();
    /**
     * Positive component quantity allowance per server tick.
     */
    private final int capacity;
    /**
     * Tick owning the current grant and quantity accounting.
     */
    private long tick=Long.MIN_VALUE;
    /**
     * Unconsumed quantity for the current tick.
     * Next rotating ingress-turn index.
     * First destination index for the next transfer pass.
     */
    private int remaining,sourceCursor,destinationCursor;
    /**
     * Ingress identity granted the current tick's service turn.
     */
    private K granted;
    /**
     * Creates positive per-tick quantity accounting with initially unselected ingress service.
     *
     * @param capacity Positive component quantity allowance per server tick
     * @throws IllegalArgumentException if capacity is not positive
     */
    public FairTransferBudget(int capacity) {
        if(capacity<=0)throw new IllegalArgumentException("Capacity must be positive");
        this.capacity=capacity;
    }
    /**
     * Register demand without consuming volume. A newly observed ingress may wait for its turn.
     *
     * @param now current world game tick
     * @param contender stable identity of the ingress requesting a service turn
     * @param requested requested resource quantity
     * @return permitted quantity, or zero if exhausted or another ingress owns the turn
     */
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
    /**
     * Reads the remaining quantity, treating a new tick as a fresh allowance without selecting a turn.
     *
     * @param now current world game tick
     * @return remaining quantity for the requested tick
     */
    public int remaining(long now) { return tick==now?remaining:capacity; }
    /**
     * Charges an actually executed transfer against the selected tick allowance.
     *
     * @param amount requested or accepted resource quantity in the owning engine's units
     * @throws IllegalArgumentException if the charged amount is negative or exceeds the current allowance
     */
    public void consume(int amount) {
        if(amount<0 || amount>remaining)throw new IllegalArgumentException("Consumption exceeds allowance");
        remaining-=amount;
    }
    /**
     * Resolves an offset from the rotating destination cursor.
     *
     * @param offset offset from the rotating destination cursor
     * @param count number of candidates or entries
     * @return wrapped destination index
     * @throws IllegalArgumentException if there are no destination candidates
     */
    public int destinationIndex(int offset,int count) {
        if(count<=0)throw new IllegalArgumentException("No destinations");
        return Math.floorMod((long)destinationCursor+offset,count);
    }
    /**
     * Moves the destination cursor past the endpoint that actually accepted a transfer.
     *
     * @param index entry or slot index
     * @param count number of candidates or entries
     */
    public void servedDestination(int index,int count) { destinationCursor=Math.floorMod((long)index+1,count); }
}

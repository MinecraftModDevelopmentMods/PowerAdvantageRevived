package zone.moddev.mc.poweradvantage.network.transport;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Rotating participant service order under a caller-supplied work budget. Callers supply a stable list for each
 * pass.
 */
public final class FairScheduler {
    /**
     * Creates a scheduler whose first pass starts at participant zero.
     */
    public FairScheduler() {}

    /**
     * First participant to visit during the next service pass.
     */
    private int cursor;
    /**
     * Visits a stable participant snapshot in rotating order until the supplied work predicate stops the pass.
     *
     * @param <T> participant type serviced by the scheduler
     * @param participants stable participant snapshot for this service pass
     * @param hasBudget predicate checked before admitting each participant
     * @param action operation applied to each admitted participant
     */
    public <T> void run(List<T> participants,BooleanSupplier hasBudget,Consumer<T> action) {
        if(participants.isEmpty())return;
        cursor=Math.floorMod(cursor,participants.size());
        for(int i=0;i<participants.size() && hasBudget.getAsBoolean();i++)action.accept(participants.get((cursor+i)%participants.size()));
        cursor=(cursor+1)%participants.size();
    }
}

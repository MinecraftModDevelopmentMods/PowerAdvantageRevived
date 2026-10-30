package zone.moddev.mc.poweradvantage.network.transport;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Rotates the first serviced participant across ticks, within the caller's work budget. */
public final class FairScheduler {
    private int cursor;
    public <T> void run(List<T> participants,BooleanSupplier hasBudget,Consumer<T> action) {
        if(participants.isEmpty())return;
        cursor=Math.floorMod(cursor,participants.size());
        for(int i=0;i<participants.size() && hasBudget.getAsBoolean();i++)action.accept(participants.get((cursor+i)%participants.size()));
        cursor=(cursor+1)%participants.size();
    }
}

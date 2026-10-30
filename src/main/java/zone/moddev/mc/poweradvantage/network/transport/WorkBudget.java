package zone.moddev.mc.poweradvantage.network.transport;

/** Per-world-tick operation counts, separate from fluid volume/item quantities. */
public final class WorkBudget {
    private final int limit;
    private long tick=Long.MIN_VALUE;
    private int executions,simulations;
    public WorkBudget(int limit) {
        if(limit<=0)throw new IllegalArgumentException("Work limit must be positive");
        this.limit=limit;
    }
    public void begin(long now) { if(now!=tick){tick=now;executions=simulations=0;} }
    public boolean canExecute() { return executions<limit; }
    public boolean canSimulate() { return simulations<limit; }
    public boolean tryExecution() { if(!canExecute())return false;executions++;return true; }
    public boolean trySimulation() { if(!canSimulate())return false;simulations++;return true; }
}

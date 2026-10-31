package zone.moddev.mc.poweradvantage.network.transport;

/**
 * Per-world-tick operation counters, separate from transported quantities. Simulation and execution each receive
 * the configured maximum number of operations.
 */
public final class WorkBudget {
    /**
     * Independent maximum for simulation and execution operations per tick.
     */
    private final int limit;
    /**
     * World tick owning the current counters.
     */
    private long tick=Long.MIN_VALUE;
    /**
     * Executed/source operations admitted during this tick.
     * Simulated operations admitted during this tick.
     */
    private int executions,simulations;
    /**
     * Creates independent positive simulation/execution operation ceilings.
     *
     * @param limit Independent maximum for simulation and execution operations per tick
     * @throws IllegalArgumentException if the operation limit is not positive
     */
    public WorkBudget(int limit) {
        if(limit<=0)throw new IllegalArgumentException("Work limit must be positive");
        this.limit=limit;
    }
    /**
     * Resets simulation and execution counters only when the world tick changes.
     *
     * @param now current world game tick
     */
    public void begin(long now) { if(now!=tick){tick=now;executions=simulations=0;} }
    /**
     * Checks whether an executed/source operation remains available.
     *
     * @return true if an execution slot remains
     */
    public boolean canExecute() { return executions<limit; }
    /**
     * Checks whether a simulated operation remains available.
     *
     * @return true if a simulation slot remains
     */
    public boolean canSimulate() { return simulations<limit; }
    /**
     * Consumes one execution slot if available.
     *
     * @return true if the operation was admitted
     */
    public boolean tryExecution() { if(!canExecute())return false;executions++;return true; }
    /**
     * Consumes one simulation slot if available.
     *
     * @return true if the simulation was admitted
     */
    public boolean trySimulation() { if(!canSimulate())return false;simulations++;return true; }
}

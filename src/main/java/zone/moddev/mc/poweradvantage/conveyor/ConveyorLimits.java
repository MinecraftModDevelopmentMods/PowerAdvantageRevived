package zone.moddev.mc.poweradvantage.conveyor;

/**
 * Positive per-dimension work budgets, independent of individual belt speed and item-count ratings. Configure them
 * before world managers are created.
 *
 * @param topologySteps maximum discovery/compression steps per dimension tick
 * @param routeSteps maximum shortest-path steps per dimension tick
 * @param recordsPerTick maximum active cargo records processed per tick
 * @param endpointsPerTick maximum extraction endpoints visited per tick
 * @param operationsPerTick maximum simulation and execution operations per tick, independently
 * @param snapshotPacketsPerTick maximum client snapshot fragments transmitted per tick
 */
public record ConveyorLimits(int topologySteps,int routeSteps,int recordsPerTick,int endpointsPerTick,
        int operationsPerTick,int snapshotPacketsPerTick) {
    /**
     * Default validated work-budget settings.
     *
     */
    public static final ConveyorLimits DEFAULT=new ConveyorLimits(512,512,512,128,512,32);
    /**
     * Validates positive per-dimension work budgets.
     *
     * @param topologySteps maximum discovery/compression steps per dimension tick
     * @param routeSteps maximum shortest-path steps per dimension tick
     * @param recordsPerTick maximum active cargo records processed per tick
     * @param endpointsPerTick maximum extraction endpoints visited per tick
     * @param operationsPerTick maximum simulation and execution operations per tick, independently
     * @param snapshotPacketsPerTick maximum client snapshot fragments transmitted per tick
     * @throws IllegalArgumentException if any work budget is not positive
     */
    public ConveyorLimits {
        if(topologySteps<=0||routeSteps<=0||recordsPerTick<=0||endpointsPerTick<=0||operationsPerTick<=0||snapshotPacketsPerTick<=0)
            throw new IllegalArgumentException("Conveyor work budgets must be positive");
    }
}

package zone.moddev.mc.poweradvantage.conveyor;

/** Per-dimension server work limits, independent of each block's speed and item-count rating. */
public record ConveyorLimits(int topologySteps,int routeSteps,int recordsPerTick,int endpointsPerTick,
        int operationsPerTick,int snapshotPacketsPerTick) {
    public static final ConveyorLimits DEFAULT=new ConveyorLimits(512,512,512,128,512,32);
    public ConveyorLimits {
        if(topologySteps<=0||routeSteps<=0||recordsPerTick<=0||endpointsPerTick<=0||operationsPerTick<=0||snapshotPacketsPerTick<=0)
            throw new IllegalArgumentException("Conveyor work budgets must be positive");
    }
}

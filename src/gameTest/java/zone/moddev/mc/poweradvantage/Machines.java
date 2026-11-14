package zone.moddev.mc.poweradvantage;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.block.entity.BlockEntity;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * Test access to a Power Advantage fluid machine's storage. Every such machine is an MMDLib XML definition
 * ({@link DefinedMachineEntity}) keeping its tanks in an MMDLib {@link FluidStorage}.
 */
public final class Machines {
    private Machines() {
    }

    /**
     * A machine's block entity and fluid storage, in public fields so tests read {@code machine.fluids}.
     */
    public static final class View {
        /** The machine's block entity. */
        public final BlockEntity entity;
        /** Its fluid storage. */
        public final FluidStorage fluids;

        View(BlockEntity entity, FluidStorage fluids) {
            this.entity = entity;
            this.fluids = fluids;
        }
    }

    /**
     * Views a Power Advantage machine, failing the test if {@code entity} isn't one.
     *
     * @param entity block entity at the machine's position
     * @return the machine's view
     */
    public static View of(BlockEntity entity) {
        if (entity instanceof DefinedMachineEntity machine && machine.fluids != null) return new View(machine, machine.fluids);
        throw new GameTestAssertException("Not a Power Advantage fluid machine: " + entity);
    }
}

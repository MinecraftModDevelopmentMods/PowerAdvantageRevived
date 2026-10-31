package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

/**
 * Distillery machine block; adds a lit state while a recipe is running.
 */
public final class DistillerBlock extends MachineBlock {
    /**
     * True while the Distillery is converting fluid.
     */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /**
     * Creates the Distillery block.
     *
     * @param properties fresh Minecraft block properties
     * @param binding prepared MMDLib block-entity creation/type binding
     */
    public DistillerBlock(Properties properties, BlockEntityBinding<MachineBlockEntity> binding) {
        super(properties, binding);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    /**
     * Declares facing and active properties.
     *
     * @param builder block-state definition builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }
}

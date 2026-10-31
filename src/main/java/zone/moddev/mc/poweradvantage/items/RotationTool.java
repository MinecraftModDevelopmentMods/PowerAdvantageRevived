package zone.moddev.mc.poweradvantage.items;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Rotation;

/** Reusable tool that turns blocks counterclockwise around their vertical axis. */
public final class RotationTool extends Item {
    /** Creates a nonstacking, nondamageable rotation tool. */
    public RotationTool() { super(new Item.Properties().stacksTo(1)); }

    /**
     * Rotates through the block's world-aware rotation hook, preserving its other properties.
     *
     * @param context clicked block and player
     * @return success when rotation changes the state, otherwise pass
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (context.getPlayer() == null || !context.getPlayer().mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        var state = level.getBlockState(pos);
        var rotated = state.rotate(level, pos, Rotation.COUNTERCLOCKWISE_90);
        if (rotated == state) return InteractionResult.PASS;
        if (!level.isClientSide) level.setBlock(pos, rotated, 3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

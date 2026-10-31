package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** Placeable oil that refreshes its PA2 contact effect on living entities on the server. */
public final class OilBlock extends LiquidBlock {
    /** Effect refreshed by contact with either a source or flowing block. */
    private final Holder<MobEffect> effect;
    /** Zero-based effect strength. */
    private final int amplifier;

    /** Creates an oil block with a ten-second contact effect.
     * @param fluid registered source fluid
     * @param properties liquid block properties
     * @param effect effect applied on contact
     * @param amplifier zero-based effect strength
     */
    public OilBlock(FlowingFluid fluid, Properties properties, Holder<MobEffect> effect, int amplifier) {
        super(fluid, properties);
        this.effect = effect;
        this.amplifier = amplifier;
    }

    /** Refreshes the contact effect while preserving the liquid block's own collision behavior.
     * @param state source or flowing liquid state
     * @param level owning world
     * @param pos liquid block position
     * @param entity entity touching the liquid
     */
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(effect, 200, amplifier));
        }
    }
}

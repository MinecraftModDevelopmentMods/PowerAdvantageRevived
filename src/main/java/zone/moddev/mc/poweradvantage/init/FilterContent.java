package zone.moddev.mc.poweradvantage.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.*;
import zone.moddev.mc.poweradvantage.filter.*;

/** Registers the common implementations behind the eight standalone filter plans. */
public final class FilterContent {
    /** Prevents construction of this registration utility. */
    private FilterContent() {}

    /** Installs block and entity factories before MMDLib prepares standalone plans. */
    public static void registerFactories() {
        MaterialExtensions.GLOBAL.registerBlockImplementation(ModContent.id("item_filter"), new BlockImplementation<FilterBlock>() {
            public Class<FilterBlock> blockClass() { return FilterBlock.class; }
            public FilterBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Filter requires context"); }
            public FilterBlock create(BlockContext context) {
                return new FilterBlock(context.properties(), context.blockEntity().orElseThrow().as(FilterBlockEntity.class));
            }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(ModContent.id("item_filter"), new BlockEntityFactory<FilterBlockEntity, FilterSettings>() {
            public Class<FilterBlockEntity> blockEntityClass() { return FilterBlockEntity.class; }
            public Codec<FilterSettings> settingsCodec() { return FilterSettings.CODEC; }
            public FilterBlockEntity create(BlockEntityType<FilterBlockEntity> type, BlockPos pos, BlockState state, MMDBlockEntity definition, FilterSettings settings) {
                return new FilterBlockEntity(type, pos, state, settings);
            }
            public BlockEntityTicker<FilterBlockEntity> ticker(Level level, MMDBlockEntity definition, FilterSettings settings) {
                return level.isClientSide ? null : FilterBlockEntity::serverTick;
            }
        });
    }

    /** Exposes only the transit slot with live per-face routing restrictions.
     * @param event owning mod capability registration event
     * @param type prepared filter entity type
     */
    @SuppressWarnings("unchecked")
    public static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, (BlockEntityType<FilterBlockEntity>) type, (be, side) -> be.automation(side));
    }
}

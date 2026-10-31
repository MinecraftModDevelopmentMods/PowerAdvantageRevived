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
import zone.moddev.mc.poweradvantage.power.*;

/** Registers the common implementations behind the standalone FE/electricity converter plan. */
public final class ElectricityConverterContent {
    /** Prevents construction of this registration utility. */
    private ElectricityConverterContent() {}

    /** Installs block and entity factories before MMDLib prepares standalone plans. */
    public static void registerFactories() {
        MaterialExtensions.GLOBAL.registerBlockImplementation(ModContent.id("electricity_converter"), new BlockImplementation<ElectricityConverterBlock>() {
            public Class<ElectricityConverterBlock> blockClass() { return ElectricityConverterBlock.class; }
            public ElectricityConverterBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Converter requires context"); }
            public ElectricityConverterBlock create(BlockContext context) {
                return new ElectricityConverterBlock(context.properties(), context.blockEntity().orElseThrow().as(ElectricityConverterBlockEntity.class));
            }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(ModContent.id("electricity_converter"), new BlockEntityFactory<ElectricityConverterBlockEntity, ConverterSettings>() {
            public Class<ElectricityConverterBlockEntity> blockEntityClass() { return ElectricityConverterBlockEntity.class; }
            public Codec<ConverterSettings> settingsCodec() { return ConverterSettings.CODEC; }
            public ElectricityConverterBlockEntity create(BlockEntityType<ElectricityConverterBlockEntity> type, BlockPos pos, BlockState state, MMDBlockEntity definition, ConverterSettings settings) {
                return new ElectricityConverterBlockEntity(type, pos, state, settings);
            }
            public BlockEntityTicker<ElectricityConverterBlockEntity> ticker(Level level, MMDBlockEntity definition, ConverterSettings settings) {
                return level.isClientSide ? null : ElectricityConverterBlockEntity::serverTick;
            }
        });
    }

    /** Exposes FE/RF and PA electricity as distinct sided capability types.
     * @param event owning mod capability event
     * @param type prepared converter type
     */
    @SuppressWarnings("unchecked")
    public static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        var converterType = (BlockEntityType<ElectricityConverterBlockEntity>) type;
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, converterType, (be, side) -> be.fePort());
        event.registerBlockEntity(PowerCapabilities.ELECTRICITY, converterType, (be, side) -> be.electricityPort());
    }
}

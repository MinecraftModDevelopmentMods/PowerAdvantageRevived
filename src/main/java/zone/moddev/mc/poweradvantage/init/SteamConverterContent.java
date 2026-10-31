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

/** Registers the common implementations behind the standalone FE/steam converter plan. */
public final class SteamConverterContent {
    /** Prevents construction of this registration utility. */
    private SteamConverterContent() {}

    /** Installs block and entity factories before MMDLib prepares standalone plans. */
    public static void registerFactories() {
        MaterialExtensions.GLOBAL.registerBlockImplementation(ModContent.id("steam_converter"), new BlockImplementation<SteamConverterBlock>() {
            public Class<SteamConverterBlock> blockClass() { return SteamConverterBlock.class; }
            public SteamConverterBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Converter requires context"); }
            public SteamConverterBlock create(BlockContext context) {
                return new SteamConverterBlock(context.properties(), context.blockEntity().orElseThrow().as(SteamConverterBlockEntity.class));
            }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(ModContent.id("steam_converter"), new BlockEntityFactory<SteamConverterBlockEntity, SteamConverterSettings>() {
            public Class<SteamConverterBlockEntity> blockEntityClass() { return SteamConverterBlockEntity.class; }
            public Codec<SteamConverterSettings> settingsCodec() { return SteamConverterSettings.CODEC; }
            public SteamConverterBlockEntity create(BlockEntityType<SteamConverterBlockEntity> type, BlockPos pos, BlockState state, MMDBlockEntity definition, SteamConverterSettings settings) {
                return new SteamConverterBlockEntity(type, pos, state, settings);
            }
            public BlockEntityTicker<SteamConverterBlockEntity> ticker(Level level, MMDBlockEntity definition, SteamConverterSettings settings) {
                return level.isClientSide ? null : SteamConverterBlockEntity::serverTick;
            }
        });
    }

    /** Exposes FE/RF and a steam-only fluid handler.
     * @param event owning mod capability event
     * @param type prepared converter type
     */
    @SuppressWarnings("unchecked")
    public static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        var converterType = (BlockEntityType<SteamConverterBlockEntity>) type;
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, converterType, (be, side) -> be.fePort());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, converterType, (be, side) -> be.steamPort());
    }
}

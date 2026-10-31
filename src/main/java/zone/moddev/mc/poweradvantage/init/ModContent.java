package zone.moddev.mc.poweradvantage.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;
import zone.moddev.mc.lib.common.types.*;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.blocks.MachineBlock;
import zone.moddev.mc.poweradvantage.tiles.*;

/**
 * Registers Power Advantage factories, queues its MMDLib standalone plans and installs capability,
 * creative-tab and packet hooks.
 */
public final class ModContent {
    /**
     * Owning mod registry for block-entity types prepared by MMDLib.
     */
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PowerAdvantage.MODID);
    /**
     * MMDLib registrar applying this mod's standalone content plans.
     */
    private final RuntimeContentRegistrar runtime;

    /**
     * Creates a namespaced Power Advantage resource identifier.
     *
     * @param path resource path within the mod namespace
     * @return identifier under the poweradvantage namespace
     */
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PowerAdvantage.MODID, path); }

    /**
     * Registers implementation factories and attaches deferred content, capability and packet hooks before
     * registry events.
     *
     * @param bus owning mod event bus
     */
    public ModContent(IEventBus bus) {
        bus.addListener(OptionalPacks::discover);
        BLOCK_ENTITY_TYPES.register(bus);
        Fluids.register(bus);
        ModItems.register(bus);
        Recipes.register(bus);
        FilterContent.registerFactories();
        ElectricityConverterContent.registerFactories();
        SteamConverterContent.registerFactories();
        var api = MaterialExtensions.GLOBAL;
        api.registerBlockImplementation(id("machine"), new BlockImplementation<MachineBlock>() {
            /**
             * Identifies the exact block implementation created by this factory.
             *
             * @return implementation class token
             */
            public Class<MachineBlock> blockClass() { return MachineBlock.class; }
            /**
             * Rejects legacy factory calls that omit the prepared registration context.
             *
             * @param material bound MMDLib material or stats-free standalone host
             * @param definition planned block or block-entity definition
             * @return never returns normally
             * @throws IllegalStateException always; this factory requires a complete context
             */
            public MachineBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("Machine requires a block-entity binding");
            }
            /**
             * Creates the block from its complete MMDLib properties and optional entity binding.
             *
             * @param context complete MMDLib block registration context
             * @return new block or block entity for the prepared context
             */
            public MachineBlock create(BlockContext context) {
                return new MachineBlock(context.properties(), context.blockEntity().orElseThrow().as(MachineBlockEntity.class));
            }
        });
        api.registerBlockImplementation(id("still"), new BlockImplementation<zone.moddev.mc.poweradvantage.blocks.DistillerBlock>() {
            public Class<zone.moddev.mc.poweradvantage.blocks.DistillerBlock> blockClass() { return zone.moddev.mc.poweradvantage.blocks.DistillerBlock.class; }
            public zone.moddev.mc.poweradvantage.blocks.DistillerBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("Distiller requires a block-entity binding");
            }
            public zone.moddev.mc.poweradvantage.blocks.DistillerBlock create(BlockContext context) {
                return new zone.moddev.mc.poweradvantage.blocks.DistillerBlock(context.properties(), context.blockEntity().orElseThrow().as(MachineBlockEntity.class));
            }
        });
        api.registerBlockImplementation(id("fluid_switch"), new BlockImplementation<zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock>() {
            public Class<zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock> blockClass() {
                return zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock.class;
            }
            public zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("Fluid switch requires context");
            }
            public zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock create(BlockContext context) {
                return new zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock(context.properties());
            }
        });
        api.registerBlockImplementation(id("frame"), new BlockImplementation<Block>() {
            /**
             * Identifies the exact block implementation created by this factory.
             *
             * @return implementation class token
             */
            public Class<Block> blockClass() { return Block.class; }
            /**
             * Rejects legacy factory calls that omit the prepared registration context.
             *
             * @param material bound MMDLib material or stats-free standalone host
             * @param definition planned block or block-entity definition
             * @return never returns normally
             * @throws IllegalStateException always; this factory requires a complete context
             */
            public Block create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Requires context"); }
            /**
             * Creates the block from its complete MMDLib properties and optional entity binding.
             *
             * @param context complete MMDLib block registration context
             * @return new block or block entity for the prepared context
             */
            public Block create(BlockContext context) { return new Block(context.properties().noOcclusion()); }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id("machine"), new BlockEntityFactory<MachineBlockEntity, MachineSettings>() {
            /**
             * Identifies the exact machine entity class created by this factory.
             *
             * @return machine entity class token
             */
            public Class<MachineBlockEntity> blockEntityClass() { return MachineBlockEntity.class; }
            /**
             * Provides validation/decoding of machine behavior and capacity settings.
             *
             * @return machine-settings codec
             */
            public Codec<MachineSettings> settingsCodec() { return MachineSettings.CODEC; }
            /**
             * Creates the machine entity with the supplied registered type and decoded settings.
             *
             * @param type registered entity type passed unchanged to the machine constructor
             * @param pos block position being queried or changed
             * @param state current block state
             * @param definition planned block or block-entity definition
             * @param settings decoded machine behavior and capacity settings
             * @return new block or block entity for the prepared context
             */
            public MachineBlockEntity create(BlockEntityType<MachineBlockEntity> type, BlockPos pos, BlockState state,
                    MMDBlockEntity definition, MachineSettings settings) {
                return new MachineBlockEntity(type, pos, state, settings);
            }
            /**
             * Supplies server-only ticking for the prepared machine definition.
             *
             * @param level owning world; server-only engines require its server thread
             * @param definition planned block or block-entity definition
             * @param settings decoded machine behavior and capacity settings
             * @return server ticker, or null on the client
             */
            public BlockEntityTicker<MachineBlockEntity> ticker(Level level, MMDBlockEntity definition, MachineSettings settings) {
                return level.isClientSide ? null : MachineBlockEntity::serverTick;
            }
        });
        for (boolean terminal : new boolean[]{false,true}) {
            api.registerBlockImplementation(id(terminal ? "fluid_terminal" : "fluid_pipe"), new BlockImplementation<zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock>() {
                /**
                 * Identifies the exact block implementation created by this factory.
                 *
                 * @return implementation class token
                 */
                public Class<zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock> blockClass() { return zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.class; }
                /**
                 * Rejects legacy factory calls that omit the prepared registration context.
                 *
                 * @param material bound MMDLib material or stats-free standalone host
                 * @param definition planned block or block-entity definition
                 * @return never returns normally
                 * @throws IllegalStateException always; this factory requires a complete context
                 */
                public zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Requires context"); }
                /**
                 * Creates the block from its complete MMDLib properties and optional entity binding.
                 *
                 * @param context complete MMDLib block registration context
                 * @return new block or block entity for the prepared context
                 */
                public zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock create(BlockContext context) {
                    return new zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock(context.properties(),terminal);
                }
            });
        }
        api.registerBlockImplementation(id("item_conveyor"), new BlockImplementation<zone.moddev.mc.poweradvantage.blocks.ConveyorBlock>() {
            /**
             * Identifies the exact block implementation created by this factory.
             *
             * @return implementation class token
             */
            public Class<zone.moddev.mc.poweradvantage.blocks.ConveyorBlock> blockClass() { return zone.moddev.mc.poweradvantage.blocks.ConveyorBlock.class; }
            /**
             * Rejects legacy factory calls that omit the prepared registration context.
             *
             * @param material bound MMDLib material or stats-free standalone host
             * @param definition planned block or block-entity definition
             * @return never returns normally
             * @throws IllegalStateException always; this factory requires a complete context
             */
            public zone.moddev.mc.poweradvantage.blocks.ConveyorBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Requires context"); }
            /**
             * Creates the block from its complete MMDLib properties and optional entity binding.
             *
             * @param context complete MMDLib block registration context
             * @return new block or block entity for the prepared context
             */
            public zone.moddev.mc.poweradvantage.blocks.ConveyorBlock create(BlockContext context) {
                return new zone.moddev.mc.poweradvantage.blocks.ConveyorBlock(context.properties());
            }
        });
        bus.addListener(zone.moddev.mc.poweradvantage.conveyor.ConveyorPackets::register);
        runtime = new RuntimeContentRegistrar(PowerAdvantage.MODID, api, bus);
        // MMDLib's AFTER dependency ordering loads definitions before this queued work.
        bus.addListener((FMLConstructModEvent event) -> event.enqueueWork(() -> {
            MMDLib.standaloneBlocks().forEach((id, plan) -> {
                if (id.getNamespace().equals(PowerAdvantage.MODID)) runtime.queue(plan);
            });
            runtime.blockEntityHandles().values().forEach(handle -> handle.register(BLOCK_ENTITY_TYPES));
        }));
        bus.addListener(this::capabilities);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().location().equals(id("materials"))) {
                event.accept(ModItems.STARCH.get());
                event.accept(ModItems.BIOPLASTIC_INGOT.get());
                event.accept(ModItems.SPROCKET.get());
                event.accept(ModItems.ROTATOR_TOOL.get());
                event.accept(Fluids.STEAM_BUCKET.get());
                event.accept(Fluids.CRUDE_OIL_BUCKET.get());
                event.accept(Fluids.REFINED_OIL_BUCKET.get());
            }
        });
    }

    /**
     * Registers pipe/conveyor transport interfaces and per-machine fluid/energy storage capabilities.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SuppressWarnings("unchecked")
    private void capabilities(RegisterCapabilitiesEvent event) {
        for (String name : new String[]{"fluid_pipe","fluid_pipe_terminal","fluid_switch"}) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id(name));
            zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.registerCapabilities(event,
                (zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock)block);
        }
        zone.moddev.mc.poweradvantage.blocks.ConveyorBlock.registerCapabilities(event,
            (zone.moddev.mc.poweradvantage.blocks.ConveyorBlock)net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("item_conveyor")));
        runtime.blockEntityTypes().forEach((id, holder) -> {
            if (id.getPath().equals("converter_rf_steam")) {
                SteamConverterContent.registerCapabilities(event, holder.get());
                return;
            }
            if (id.getPath().equals("converter_rf_electricity")) {
                ElectricityConverterContent.registerCapabilities(event, holder.get());
                return;
            }
            if (id.getPath().startsWith("item_filter_")) {
                FilterContent.registerCapabilities(event, holder.get());
                return;
            }
            var type = (BlockEntityType<MachineBlockEntity>) holder.get();
            if (id.getPath().equals("infinite_power")) MachineCapabilities.energy(event, type, be -> be.energy);
            else MachineCapabilities.fluids(event, type, be -> be.fluids);
        });
    }
}

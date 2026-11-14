package zone.moddev.mc.poweradvantage.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;
import zone.moddev.mc.lib.common.types.*;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

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
        FilterContent.registerFactories();
        var api = MaterialExtensions.GLOBAL;
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
                event.accept(ModItems.FLUID_INTAKE_PUMP.get());
                event.accept(ModItems.FLUID_OUTPUT_PUMP.get());
                event.accept(Fluids.STEAM_BUCKET.get());
                event.accept(Fluids.CRUDE_OIL_BUCKET.get());
                event.accept(Fluids.REFINED_OIL_BUCKET.get());
                // XML machines (mmdlib/machine/*.xml) are registered by MMDLib, which lists them in its own
                // Data-defined Machines tab; list Power Advantage's here as well.
                zone.moddev.mc.lib.machine.neoforge.MachineContent.definitions().keySet().stream()
                    .filter(machine -> machine.getNamespace().equals(PowerAdvantage.MODID))
                    .sorted()
                    .flatMap(machine -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(machine).stream())
                    .forEach(event::accept);
            }
        });
    }

    /**
     * Registers pipe/conveyor transport interfaces and per-machine fluid/energy storage capabilities.
     *
     * @param event NeoForge registration or lifecycle event
     */
    private void capabilities(RegisterCapabilitiesEvent event) {
        zone.moddev.mc.poweradvantage.machine.FluidPumps.registerCapabilities(event);
        for (String name : new String[]{"fluid_pipe","fluid_pipe_terminal","fluid_switch"}) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id(name));
            zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.registerCapabilities(event,
                (zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock)block);
        }
        zone.moddev.mc.poweradvantage.blocks.ConveyorBlock.registerCapabilities(event,
            (zone.moddev.mc.poweradvantage.blocks.ConveyorBlock)net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("item_conveyor")));
        runtime.blockEntityTypes().forEach((id, holder) -> {
            if (id.getPath().startsWith("item_filter_")) FilterContent.registerCapabilities(event, holder.get());
        });
        // MMDLib registers the XML converter's FE capability; the electricity side is Power Advantage's own.
        event.registerBlockEntity(zone.moddev.mc.poweradvantage.power.PowerCapabilities.ELECTRICITY,
            zone.moddev.mc.lib.machine.neoforge.MachineContent.entityType(id("converter_rf_electricity")),
            (machine, side) -> machine.features.findProcessor("exchange", zone.moddev.mc.poweradvantage.machine.ElectricityExchange.class)
                .map(zone.moddev.mc.poweradvantage.machine.ElectricityExchange::port).orElse(null));
    }
}

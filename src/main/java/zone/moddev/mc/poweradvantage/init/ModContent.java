package zone.moddev.mc.poweradvantage.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;
import zone.moddev.mc.lib.common.types.*;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.blocks.MachineBlock;
import zone.moddev.mc.poweradvantage.menu.MachineMenu;
import zone.moddev.mc.poweradvantage.tiles.*;

public final class ModContent {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PowerAdvantage.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine",
        () -> IMenuTypeExtension.create((id, inventory, data) -> new MachineMenu(id, inventory, data.readBlockPos())));
    private final RuntimeContentRegistrar runtime;

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PowerAdvantage.MODID, path); }

    public ModContent(IEventBus bus) {
        MENUS.register(bus);
        Fluids.register(bus);
        var api = MaterialExtensions.GLOBAL;
        api.registerBlockImplementation(id("machine"), new BlockImplementation<MachineBlock>() {
            public Class<MachineBlock> blockClass() { return MachineBlock.class; }
            public MachineBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("Machine requires a block-entity binding");
            }
            public MachineBlock create(BlockContext context) {
                return new MachineBlock(context.properties(), context.blockEntity().orElseThrow().as(MachineBlockEntity.class));
            }
        });
        api.registerBlockImplementation(id("frame"), new BlockImplementation<Block>() {
            public Class<Block> blockClass() { return Block.class; }
            public Block create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Requires context"); }
            public Block create(BlockContext context) { return new Block(context.properties().noOcclusion()); }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id("machine"), new BlockEntityFactory<MachineBlockEntity, MachineSettings>() {
            public Class<MachineBlockEntity> blockEntityClass() { return MachineBlockEntity.class; }
            public Codec<MachineSettings> settingsCodec() { return MachineSettings.CODEC; }
            public MachineBlockEntity create(BlockEntityType<MachineBlockEntity> type, BlockPos pos, BlockState state,
                    MMDBlockEntity definition, MachineSettings settings) {
                return new MachineBlockEntity(type, pos, state, settings);
            }
            public BlockEntityTicker<MachineBlockEntity> ticker(Level level, MMDBlockEntity definition, MachineSettings settings) {
                return level.isClientSide ? null : MachineBlockEntity::serverTick;
            }
        });
        for (boolean terminal : new boolean[]{false,true}) {
            api.registerBlockImplementation(id(terminal ? "fluid_terminal" : "fluid_pipe"), new BlockImplementation<zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock>() {
                public Class<zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock> blockClass() { return zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.class; }
                public zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Requires context"); }
                public zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock create(BlockContext context) {
                    return new zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock(context.properties(),terminal);
                }
            });
        }
        api.registerBlockImplementation(id("item_conveyor"), new BlockImplementation<zone.moddev.mc.poweradvantage.blocks.ConveyorBlock>() {
            public Class<zone.moddev.mc.poweradvantage.blocks.ConveyorBlock> blockClass() { return zone.moddev.mc.poweradvantage.blocks.ConveyorBlock.class; }
            public zone.moddev.mc.poweradvantage.blocks.ConveyorBlock create(MMDMaterial material, MMDBlock definition) { throw new IllegalStateException("Requires context"); }
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
        }));
        bus.addListener(this::capabilities);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().location().equals(id("materials"))) event.accept(Fluids.STEAM_BUCKET.get());
        });
    }

    @SuppressWarnings("unchecked")
    private void capabilities(RegisterCapabilitiesEvent event) {
        for (String name : new String[]{"fluid_pipe","fluid_pipe_terminal"}) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id(name));
            zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.registerCapabilities(event,
                (zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock)block);
        }
        zone.moddev.mc.poweradvantage.blocks.ConveyorBlock.registerCapabilities(event,
            (zone.moddev.mc.poweradvantage.blocks.ConveyorBlock)net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id("item_conveyor")));
        runtime.blockEntityTypes().forEach((id, holder) -> {
            var type = (BlockEntityType<MachineBlockEntity>) holder.get();
            if (id.getPath().equals("infinite_power")) MachineCapabilities.energy(event, type, be -> be.energy);
            else MachineCapabilities.fluids(event, type, be -> be.fluids);
        });
    }
}

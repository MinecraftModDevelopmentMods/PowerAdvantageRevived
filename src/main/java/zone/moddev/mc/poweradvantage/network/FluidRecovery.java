package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;

/** Exceptional remainders survive save/restart even if a nontransactional handler changes its mind. */
final class FluidRecovery extends SavedData {
    record Entry(BlockPos pipe,BlockPos source,Direction face,FluidStack fluid) {}
    final List<Entry> entries=new ArrayList<>();
    static Factory<FluidRecovery> factory(){return new Factory<>(FluidRecovery::new,FluidRecovery::load);}
    void add(BlockPos pipe,BlockPos source,Direction face,FluidStack fluid) {
        entries.add(new Entry(pipe,source,face,fluid.copy()));setDirty();
    }
    static FluidRecovery load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new FluidRecovery();
        for(Tag element:tag.getList("Entries",Tag.TAG_COMPOUND)) {
            CompoundTag entry=(CompoundTag)element;
            FluidStack fluid=FluidStack.parseOptional(registries,entry.getCompound("Fluid"));
            if(!fluid.isEmpty())data.entries.add(new Entry(BlockPos.of(entry.getLong("Pipe")),BlockPos.of(entry.getLong("Source")),Direction.from3DDataValue(entry.getInt("Face")),fluid));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag();
        for(var entry:entries) {
            var item=new CompoundTag();item.putLong("Pipe",entry.pipe().asLong());item.putLong("Source",entry.source().asLong());
            item.putInt("Face",entry.face().ordinal());item.put("Fluid",entry.fluid().save(registries));list.add(item);
        }
        tag.put("Entries",list);return tag;
    }
}

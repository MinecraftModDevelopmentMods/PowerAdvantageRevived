package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Saved exceptional terminal-transfer remainders. Ownership survives unavailable routes, removed sources and server
 * restart until delivery or refund succeeds.
 */
final class FluidRecovery extends SavedData {
    /**
     * Creates an empty exceptional-fluid ownership store.
     */
    FluidRecovery() {}

    /**
     * Owned fluid awaiting retry through its original pipe ingress or return to its original source face.
     *
     * @param pipe Original virtual pipe ingress position
     * @param source Original extraction block position
     * @param face Original sided source capability face
     * @param fluid Fluid contents still durably owned by this entry
     */
    record Entry(BlockPos pipe,BlockPos source,Direction face,FluidStack fluid) {}
    /**
     * Saved, nonempty transfer remainders awaiting delivery or refund.
     */
    final List<Entry> entries=new ArrayList<>();
    /**
     * Creates the dimension SavedData factory for exceptional remainders.
     *
     * @return saved-data constructor/loader
     */
    static Factory<FluidRecovery> factory(){return new Factory<>(FluidRecovery::new,FluidRecovery::load);}
    /**
     * Copies an unexpected remainder into durable ownership and marks the data dirty.
     *
     * @param pipe original fluid ingress position
     * @param source injecting/source block position, or null when unknown
     * @param face sided capability face, or null for unsided access where supported
     * @param fluid nonempty fluid contents owned by recovery
     */
    void add(BlockPos pipe,BlockPos source,Direction face,FluidStack fluid) {
        entries.add(new Entry(pipe,source,face,fluid.copy()));setDirty();
    }
    /**
     * Restores nonempty fluid remainders and their original endpoint positions/faces.
     *
     * @param tag compound tag read or populated by this operation
     * @param registries registry lookup provider used by item/fluid NBT codecs
     * @return loaded recovery data
     */
    static FluidRecovery load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new FluidRecovery();
        for(Tag element:tag.getList("Entries",Tag.TAG_COMPOUND)) {
            CompoundTag entry=(CompoundTag)element;
            FluidStack fluid=FluidStack.parseOptional(registries,entry.getCompound("Fluid"));
            if(!fluid.isEmpty())data.entries.add(new Entry(BlockPos.of(entry.getLong("Pipe")),BlockPos.of(entry.getLong("Source")),Direction.from3DDataValue(entry.getInt("Face")),fluid));
        }
        return data;
    }
    /**
     * Writes registry-aware fluid data and routing/refund positions.
     *
     * @param tag compound tag read or populated by this operation
     * @param registries registry lookup provider used by item/fluid NBT codecs
     * @return tag containing the recovery entries
     */
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag();
        for(var entry:entries) {
            var item=new CompoundTag();item.putLong("Pipe",entry.pipe().asLong());item.putLong("Source",entry.source().asLong());
            item.putInt("Face",entry.face().ordinal());item.put("Fluid",entry.fluid().save(registries));list.add(item);
        }
        tag.put("Entries",list);return tag;
    }
}

package zone.moddev.mc.poweradvantage.init;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Optional world-specific data packs for compatibility choices that only change tags or recipes. */
public final class OptionalPacks {
    /** Stable data-pack ID used by world selection and the datapack command. */
    public static final String RUBBER_ID = "mod/poweradvantage:optional_packs/bioplastic_as_rubber";

    /** Prevents construction of this registration utility. */
    private OptionalPacks() {}

    /** Offers rubber equivalence without forcing it into existing or new worlds.
     * @param event resource-pack or data-pack repository discovery event
     */
    public static void discover(AddPackFindersEvent event) {
        event.addPackFinders(ModContent.id("optional_packs/bioplastic_as_rubber"), PackType.SERVER_DATA,
            Component.literal("Power Advantage: Bioplastic as Rubber"), PackSource.FEATURE, false, Pack.Position.TOP);
    }
}

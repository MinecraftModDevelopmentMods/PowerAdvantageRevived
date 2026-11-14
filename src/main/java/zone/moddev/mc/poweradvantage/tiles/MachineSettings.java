package zone.moddev.mc.poweradvantage.tiles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Codec-backed machine behavior and capacity settings supplied by MMDLib standalone definitions.
 *
 * @param kind Behavior selected by the standalone machine definition
 * @param capacity Validated storage capacity in millibuckets or FE
 */
public record MachineSettings(Kind kind, int capacity) {
    /**
     * Implemented server behavior selected by a machine definition.
     */
    public enum Kind {
    /**
     * Passive fluid storage with proportional redstone output.
     */
    TANK,
    /**
     * World-source collection and adjacent fluid export.
     */
    DRAIN,
    /**
     * Adjacent fluid import and world placement below the machine.
     */
    DISCHARGE,
    /**
     * Replenishing steam source with adjacent fluid export.
     */
    STEAM,
    /**
     * Replenishing Forge Energy source with adjacent energy export.
     */
    ENERGY }
    /**
     * Codec validating behavior kind and capacity from 1000 through 1000000 units.
     *
     */
    public static final Codec<MachineSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(MachineSettings::kind),
        Codec.intRange(1000, 1_000_000).fieldOf("capacity").forGetter(MachineSettings::capacity)
    ).apply(instance, MachineSettings::new));
}

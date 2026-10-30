package zone.moddev.mc.poweradvantage.tiles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Shared implementation settings; standalone definitions select behavior and capacity. */
public record MachineSettings(Kind kind, int capacity) {
    public enum Kind { TANK, DRAIN, DISCHARGE, STEAM, ENERGY }
    public static final Codec<MachineSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(MachineSettings::kind),
        Codec.intRange(1000, 1_000_000).fieldOf("capacity").forGetter(MachineSettings::capacity)
    ).apply(instance, MachineSettings::new));
}

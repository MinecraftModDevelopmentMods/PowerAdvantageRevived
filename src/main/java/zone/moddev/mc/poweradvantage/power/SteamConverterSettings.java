package zone.moddev.mc.poweradvantage.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Steam/FE storage, exact exchange batches and per-tick work settings.
 * @param feCapacity FE buffer capacity
 * @param steamCapacity steam capacity in mB
 * @param feQuantum FE per batch
 * @param steamQuantum steam mB per batch
 * @param fePerTick FE-equivalent work limit
 */
public record SteamConverterSettings(int feCapacity, int steamCapacity, int feQuantum, int steamQuantum, int fePerTick) {
    /** Factory codec; one mB of steam exchanges for eight FE/RF by default. */
    public static final Codec<SteamConverterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fe_capacity", 1000).forGetter(SteamConverterSettings::feCapacity),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("steam_capacity", 1000).forGetter(SteamConverterSettings::steamCapacity),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fe_quantum", 8).forGetter(SteamConverterSettings::feQuantum),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("steam_quantum", 1).forGetter(SteamConverterSettings::steamQuantum),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fe_per_tick", 1000).forGetter(SteamConverterSettings::fePerTick)
    ).apply(i, SteamConverterSettings::new));
    /** Validates code-created settings and decoded definitions.
     * @param feCapacity FE capacity
     * @param steamCapacity steam capacity
     * @param feQuantum FE per batch
     * @param steamQuantum steam per batch
     * @param fePerTick FE work allowance
     */
    public SteamConverterSettings {
        if (feQuantum <= 0 || steamQuantum <= 0 || feCapacity < feQuantum || steamCapacity < steamQuantum || fePerTick < feQuantum)
            throw new IllegalArgumentException("Steam converter requires positive complete batches within capacity and rate");
    }
}

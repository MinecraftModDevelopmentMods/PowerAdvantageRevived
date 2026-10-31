package zone.moddev.mc.poweradvantage.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Data-defined electricity/FE buffer sizes, exact exchange ratio and work limit.
 * @param feCapacity FE buffer capacity
 * @param electricityCapacity PA electricity buffer capacity
 * @param feQuantum FE per complete conversion batch
 * @param electricityQuantum electricity units per complete conversion batch
 * @param fePerTick FE-equivalent transfer/conversion ceiling per operation tick
 */
public record ConverterSettings(int feCapacity, int electricityCapacity, int feQuantum, int electricityQuantum, int fePerTick) {
    /** Validated positive factory settings; defaults preserve PA2's electricity converter. */
    public static final Codec<ConverterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fe_capacity", 1000).forGetter(ConverterSettings::feCapacity),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("electricity_capacity", 8000).forGetter(ConverterSettings::electricityCapacity),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fe_quantum", 1).forGetter(ConverterSettings::feQuantum),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("electricity_quantum", 4).forGetter(ConverterSettings::electricityQuantum),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fe_per_tick", 1000).forGetter(ConverterSettings::fePerTick)
    ).apply(i, ConverterSettings::new));

    /** Validates settings supplied by code as well as data.
     * @param feCapacity FE buffer capacity
     * @param electricityCapacity electricity buffer capacity
     * @param feQuantum FE per batch
     * @param electricityQuantum electricity per batch
     * @param fePerTick work limit
     */
    public ConverterSettings {
        if (feQuantum <= 0 || electricityQuantum <= 0 || fePerTick < feQuantum
                || feCapacity < feQuantum || electricityCapacity < electricityQuantum) {
            throw new IllegalArgumentException("Converter capacities and rate must accommodate a positive exchange batch");
        }
    }
}

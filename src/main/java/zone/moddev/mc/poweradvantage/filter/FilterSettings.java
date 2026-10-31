package zone.moddev.mc.poweradvantage.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Data-defined filter behavior and work rate.
 * @param kind predicate used to select the lower output
 * @param interval ticks between transfer attempts
 * @param batch maximum items moved per attempt
 */
public record FilterSettings(Kind kind, int interval, int batch) {
    /** Available PA2 sorting predicates. */
    public enum Kind {
        /** Placeable block items. */ BLOCK,
        /** Items with a food component. */ FOOD,
        /** Items with a positive furnace burn time. */ FUEL,
        /** Items matching one of nine real samples. */ INVENTORY,
        /** Items tagged as ores, ingots, dusts or nuggets. */ ORE,
        /** Live lower-output preference with forward fallback. */ OVERFLOW,
        /** Plant and growable block items. */ PLANT,
        /** Inputs accepted by a current smelting recipe. */ SMELT
    }
    /** Validated standalone block-entity settings. */
    public static final Codec<FilterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(FilterSettings::kind),
        Codec.intRange(1, 1200).optionalFieldOf("interval", 8).forGetter(FilterSettings::interval),
        Codec.intRange(1, 64).optionalFieldOf("batch", 16).forGetter(FilterSettings::batch)
    ).apply(i, FilterSettings::new));

    /** Validates code-created settings as well as decoded data.
     * @param kind sorting predicate
     * @param interval ticks between attempts
     * @param batch maximum items per attempt
     * @throws IllegalArgumentException if a setting is outside its supported range
     */
    public FilterSettings {
        java.util.Objects.requireNonNull(kind);
        if (interval < 1 || interval > 1200 || batch < 1 || batch > 64) throw new IllegalArgumentException("Invalid filter work rate");
    }
}

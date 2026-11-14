package zone.moddev.mc.poweradvantage.power;

/** Steam/FE storage, exact exchange batches and per-tick work settings.
 * @param feCapacity FE buffer capacity
 * @param steamCapacity steam capacity in mB
 * @param feQuantum FE per batch
 * @param steamQuantum steam mB per batch
 * @param fePerTick FE-equivalent work limit
 */
public record SteamConverterSettings(int feCapacity, int steamCapacity, int feQuantum, int steamQuantum, int fePerTick) {
    /** The converter's settings, unchanged from 3.1. */
    public static final SteamConverterSettings DEFAULT = new SteamConverterSettings(1000, 1000, 8, 1, 1000);
    /** Validates the settings.
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

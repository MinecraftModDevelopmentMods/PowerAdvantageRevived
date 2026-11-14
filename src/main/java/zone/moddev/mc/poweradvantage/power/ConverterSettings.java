package zone.moddev.mc.poweradvantage.power;

/** Electricity/FE buffer sizes, exact exchange ratio and work limit.
 * @param feCapacity FE buffer capacity
 * @param electricityCapacity PA electricity buffer capacity
 * @param feQuantum FE per complete conversion batch
 * @param electricityQuantum electricity units per complete conversion batch
 * @param fePerTick FE-equivalent transfer/conversion ceiling per operation tick
 */
public record ConverterSettings(int feCapacity, int electricityCapacity, int feQuantum, int electricityQuantum, int fePerTick) {
    /** The converter's settings, unchanged from 3.1. */
    public static final ConverterSettings DEFAULT = new ConverterSettings(1000, 8000, 1, 4, 1000);

    /** Validates the settings.
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

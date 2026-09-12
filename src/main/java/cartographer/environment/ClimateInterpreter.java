package cartographer.environment;

public class ClimateInterpreter {
    /*
     * Vintage Story IMapRegion documentation describes ClimateMap packing as:
     * bits 16-23 = temperature, bits 8-15 = rain, bits 0-7 = unused.
     * The values are exposed as neutral indexes here, not real-world units.
     */
    private static final int TEMPERATURE_SHIFT =
            16;

    private static final int RAINFALL_SHIFT =
            8;

    private static final int BYTE_MASK =
            0xFF;

    public ClimateSample interpret(
            int rawValue
    ) {
        return new ClimateSample(
                rawValue,
                (rawValue >>> TEMPERATURE_SHIFT)
                        & BYTE_MASK,
                (rawValue >>> RAINFALL_SHIFT)
                        & BYTE_MASK,
                rawValue
                        & BYTE_MASK
        );
    }
}

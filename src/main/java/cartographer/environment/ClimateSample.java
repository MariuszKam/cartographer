package cartographer.environment;

public record ClimateSample(
        int rawValue,
        int temperatureIndex,
        int rainfallIndex,
        int unusedIndex
) {
}

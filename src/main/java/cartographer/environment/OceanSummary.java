package cartographer.environment;

public record OceanSummary(
        int samples,
        int rawMin,
        int rawMax,
        double averageRawValue
) {
}

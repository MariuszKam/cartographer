package cartographer.environment;

public record ForestSummary(
        int samples,
        int rawMin,
        int rawMax,
        double averageNormalizedDensity,
        ForestDensityClass averageDensityClass
) {
}

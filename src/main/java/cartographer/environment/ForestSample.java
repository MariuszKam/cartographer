package cartographer.environment;

public record ForestSample(
        int rawValue,
        double normalizedDensity,
        ForestDensityClass densityClass
) {
}

package cartographer.environment;

public class ForestInterpreter {
    private static final double MAX_DENSITY_VALUE =
            255.0;

    public ForestSample interpret(
            int rawValue
    ) {
        double normalized = Math.clamp(rawValue / MAX_DENSITY_VALUE, 0.0, 1.0);

        return new ForestSample(
                rawValue,
                normalized,
                densityClass(
                        normalized
                )
        );
    }

    private ForestDensityClass densityClass(
            double normalized
    ) {
        if (normalized <= 0.0) {
            return ForestDensityClass.NONE;
        }

        if (normalized < 0.20) {
            return ForestDensityClass.SPARSE;
        }

        if (normalized < 0.40) {
            return ForestDensityClass.LIGHT;
        }

        if (normalized < 0.60) {
            return ForestDensityClass.MODERATE;
        }

        if (normalized < 0.80) {
            return ForestDensityClass.DENSE;
        }

        return ForestDensityClass.VERY_DENSE;
    }

}

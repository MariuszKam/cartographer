package cartographer.environment;

import java.util.List;

public record ClimateSummary(
        int samples,
        double averageTemperatureIndex,
        double averageRainfallIndex,
        List<Integer> rawSample
) {
}

package cartographer.environment;

import java.util.List;

public record IdMapSummary(
        int samples,
        int distinctCount,
        List<Integer> dominantIds
) {
}

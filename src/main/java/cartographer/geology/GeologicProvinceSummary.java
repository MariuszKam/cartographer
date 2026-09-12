package cartographer.geology;

import cartographer.model.MapRegionCoordinate;

import java.util.List;

public record GeologicProvinceSummary(
        MapRegionCoordinate coordinate,
        int samples,
        int distinctCount,
        List<Integer> dominantIds
) {
    public GeologicProvinceSummary {
        if (coordinate == null) {
            throw new IllegalArgumentException(
                    "Geologic province coordinate is required"
            );
        }

        dominantIds =
                dominantIds == null
                        ? List.of()
                        : List.copyOf(
                                dominantIds
                        );
    }
}

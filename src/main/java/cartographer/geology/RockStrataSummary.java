package cartographer.geology;

import cartographer.model.MapRegionCoordinate;

import java.util.List;

public record RockStrataSummary(
        MapRegionCoordinate coordinate,
        List<RockStratumSummary> strata
) {
    public RockStrataSummary {
        if (coordinate == null) {
            throw new IllegalArgumentException(
                    "RockStrataSummary coordinate is required"
            );
        }

        strata =
                strata == null
                        ? List.of()
                        : List.copyOf(
                                strata
                        );
    }
}

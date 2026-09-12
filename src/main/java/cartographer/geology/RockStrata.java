package cartographer.geology;

import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;

import java.util.List;

public record RockStrata(
        MapRegionCoordinate regionCoordinate,
        List<IntDataMap2D> strataMaps
) {
    public RockStrata {
        if (regionCoordinate == null) {
            throw new IllegalArgumentException(
                    "RockStrata region coordinate is required"
            );
        }

        strataMaps =
                strataMaps == null
                        ? List.of()
                        : List.copyOf(
                                strataMaps
                        );
    }
}

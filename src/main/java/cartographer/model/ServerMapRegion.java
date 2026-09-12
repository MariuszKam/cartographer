package cartographer.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ServerMapRegion(
        MapRegionCoordinate coordinate,
        Optional<IntDataMap2D> climateMap,
        Optional<IntDataMap2D> forestMap,
        Optional<IntDataMap2D> landformMap,
        Optional<IntDataMap2D> geologicProvinceMap,
        Optional<IntDataMap2D> oceanMap,
        Map<String, IntDataMap2D> oreMaps,
        List<IntDataMap2D> rockStrata
) {
    public ServerMapRegion {
        if (coordinate == null) {
            throw new IllegalArgumentException(
                    "ServerMapRegion coordinate is required"
            );
        }

        oreMaps =
                oreMaps == null
                        ? Map.of()
                        : Map.copyOf(
                                oreMaps
                        );

        rockStrata =
                rockStrata == null
                        ? List.of()
                        : List.copyOf(
                                rockStrata
                        );
    }

    public ServerMapRegion(
            MapRegionCoordinate coordinate,
            Optional<IntDataMap2D> climateMap,
            Optional<IntDataMap2D> forestMap,
            Optional<IntDataMap2D> landformMap,
            Optional<IntDataMap2D> geologicProvinceMap,
            Optional<IntDataMap2D> oceanMap
    ) {
        this(
                coordinate,
                climateMap,
                forestMap,
                landformMap,
                geologicProvinceMap,
                oceanMap,
                Map.of(),
                List.of()
        );
    }
}

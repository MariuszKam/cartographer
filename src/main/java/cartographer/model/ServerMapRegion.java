package cartographer.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
        Objects.requireNonNull(
                coordinate,
                "ServerMapRegion coordinate is required"
        );

        Objects.requireNonNull(
                climateMap,
                "ServerMapRegion climateMap is required"
        );

        Objects.requireNonNull(
                forestMap,
                "ServerMapRegion forestMap is required"
        );

        Objects.requireNonNull(
                landformMap,
                "ServerMapRegion landformMap is required"
        );

        Objects.requireNonNull(
                geologicProvinceMap,
                "ServerMapRegion geologicProvinceMap is required"
        );

        Objects.requireNonNull(
                oceanMap,
                "ServerMapRegion oceanMap is required"
        );

        oreMaps =
                Map.copyOf(
                        Objects.requireNonNullElseGet(
                                oreMaps,
                                Map::of
                        )
                );

        rockStrata =
                List.copyOf(
                        Objects.requireNonNullElseGet(
                                rockStrata,
                                List::of
                        )
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
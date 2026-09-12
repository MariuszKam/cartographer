package cartographer.model;

import java.util.Optional;

public record ServerMapRegion(
        MapRegionCoordinate coordinate,
        Optional<IntDataMap2D> climateMap,
        Optional<IntDataMap2D> forestMap,
        Optional<IntDataMap2D> landformMap,
        Optional<IntDataMap2D> geologicProvinceMap,
        Optional<IntDataMap2D> oceanMap
) {
    public ServerMapRegion {
        if (coordinate == null) {
            throw new IllegalArgumentException(
                    "ServerMapRegion coordinate is required"
            );
        }

        climateMap =
                climateMap == null
                        ? Optional.empty()
                        : climateMap;

        forestMap =
                forestMap == null
                        ? Optional.empty()
                        : forestMap;

        landformMap =
                landformMap == null
                        ? Optional.empty()
                        : landformMap;

        geologicProvinceMap =
                geologicProvinceMap == null
                        ? Optional.empty()
                        : geologicProvinceMap;

        oceanMap =
                oceanMap == null
                        ? Optional.empty()
                        : oceanMap;
    }
}

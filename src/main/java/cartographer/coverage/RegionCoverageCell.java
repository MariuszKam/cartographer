package cartographer.coverage;

import cartographer.model.MapRegionCoordinate;

public record RegionCoverageCell(
        MapRegionCoordinate coordinate,
        boolean present,
        int worldMinX,
        int worldMinZ,
        int worldMaxXExclusive,
        int worldMaxZExclusive,
        double displayMinX,
        double displayMinZ,
        double displayMaxXExclusive,
        double displayMaxZExclusive
) {
    public RegionCoverageCell {
        if (coordinate == null) {
            throw new IllegalArgumentException(
                    "coverage coordinate is required"
            );
        }

        if (worldMaxXExclusive <= worldMinX
                || worldMaxZExclusive <= worldMinZ) {

            throw new IllegalArgumentException(
                    "coverage cell world bounds must be positive"
            );
        }
    }
}

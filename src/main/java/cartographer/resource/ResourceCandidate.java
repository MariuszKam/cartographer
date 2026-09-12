package cartographer.resource;

import cartographer.model.MapRegionCoordinate;

public record ResourceCandidate(
        String resourceKey,
        MapRegionCoordinate regionCoordinate,
        int localX,
        int localZ,
        int rawValue,
        double relativeIntensity,
        int approximateWorldX,
        int approximateWorldZ
) {
    public ResourceCandidate {
        if (resourceKey == null
                || resourceKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Resource candidate key is required"
            );
        }

        if (regionCoordinate == null) {
            throw new IllegalArgumentException(
                    "Resource candidate region coordinate is required"
            );
        }
    }
}

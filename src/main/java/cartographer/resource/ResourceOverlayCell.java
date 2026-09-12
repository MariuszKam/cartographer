package cartographer.resource;

import cartographer.model.MapRegionCoordinate;

public record ResourceOverlayCell(
        String resourceKey,
        MapRegionCoordinate regionCoordinate,
        int localX,
        int localZ,
        int rawValue,
        double relativeIntensity,
        double worldMinX,
        double worldMinZ,
        double worldMaxX,
        double worldMaxZ
) {
    public ResourceOverlayCell {
        if (resourceKey == null
                || resourceKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Resource overlay cell key is required"
            );
        }

        if (regionCoordinate == null) {
            throw new IllegalArgumentException(
                    "Resource overlay cell region coordinate is required"
            );
        }

        if (relativeIntensity < 0.0
                || relativeIntensity > 1.0) {

            throw new IllegalArgumentException(
                    "Resource overlay relative intensity must be between 0 and 1"
            );
        }

        if (worldMaxX <= worldMinX
                || worldMaxZ <= worldMinZ) {

            throw new IllegalArgumentException(
                    "Resource overlay cell must have positive world-space size"
            );
        }
    }
}
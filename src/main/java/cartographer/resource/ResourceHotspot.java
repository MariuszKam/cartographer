package cartographer.resource;

import cartographer.model.MapRegionCoordinate;

public record ResourceHotspot(
        String resourceKey,
        MapRegionCoordinate peakRegion,
        int peakLocalX,
        int peakLocalZ,
        int peakRawValue,
        double relativeIntensity,
        int approximateWorldX,
        int approximateWorldZ
) {
    public ResourceHotspot {
        if (resourceKey == null
                || resourceKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Resource hotspot key is required"
            );
        }

        if (peakRegion == null) {
            throw new IllegalArgumentException(
                    "Resource hotspot region coordinate is required"
            );
        }

        if (relativeIntensity < 0.0
                || relativeIntensity > 1.0) {

            throw new IllegalArgumentException(
                    "Resource hotspot relative intensity must be between 0 and 1"
            );
        }
    }
}
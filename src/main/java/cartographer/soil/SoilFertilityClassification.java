package cartographer.soil;

import java.util.Objects;

public record SoilFertilityClassification(
        String originalCode,
        String normalizedPath,
        SoilFertilityTier tier,
        SoilFertilitySource source
) {
    public SoilFertilityClassification {
        Objects.requireNonNull(originalCode, "Original block code is required");
        Objects.requireNonNull(normalizedPath, "Normalized block path is required");
        Objects.requireNonNull(tier, "Soil fertility tier is required");
        Objects.requireNonNull(source, "Soil fertility source is required");

        if (originalCode.isBlank() || normalizedPath.isBlank()) {
            throw new IllegalArgumentException("Soil fertility code values must not be blank");
        }
    }
}

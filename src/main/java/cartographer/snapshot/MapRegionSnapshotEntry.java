package cartographer.snapshot;

import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Compact interpreted/static mapregion state for one observed mapregion. */
public record MapRegionSnapshotEntry(
        MapRegionCoordinate coordinate,
        EnvironmentProfile environmentProfile,
        Optional<GeologicProvinceSummary> geologySummary,
        Map<String, IntDataMap2D> oreMaps
) {
    public MapRegionSnapshotEntry {
        Objects.requireNonNull(
                coordinate,
                "coordinate is required"
        );
        Objects.requireNonNull(
                environmentProfile,
                "environmentProfile is required"
        );
        Objects.requireNonNull(
                geologySummary,
                "geologySummary is required"
        );
        oreMaps = Map.copyOf(
                Objects.requireNonNull(oreMaps, "oreMaps are required")
        );
        for (Map.Entry<String, IntDataMap2D> entry : oreMaps.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException(
                        "oreMaps cannot contain blank keys"
                );
            }
            Objects.requireNonNull(
                    entry.getValue(),
                    "oreMaps cannot contain null maps"
            );
        }
        if (!coordinate.equals(environmentProfile.coordinate())) {
            throw new IllegalArgumentException(
                    "environment profile coordinate must match entry coordinate"
            );
        }
        if (geologySummary.isPresent()
                && !coordinate.equals(
                geologySummary.orElseThrow().coordinate()
        )) {
            throw new IllegalArgumentException(
                    "geology summary coordinate must match entry coordinate"
            );
        }
    }
    public MapRegionSnapshotEntry(
            MapRegionCoordinate coordinate,
            EnvironmentProfile environmentProfile,
            Optional<GeologicProvinceSummary> geologySummary
    ) {
        this(
                coordinate,
                environmentProfile,
                geologySummary,
                Map.of()
        );
    }

}

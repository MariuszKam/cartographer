package cartographer.perf;

import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.MapRegionCoordinate;

import java.util.Objects;
import java.util.Optional;

/** Compact interpreted/static mapregion state for one observed mapregion. */
public record MapRegionSnapshotEntry(
        MapRegionCoordinate coordinate,
        EnvironmentProfile environmentProfile,
        Optional<GeologicProvinceSummary> geologySummary
) {
    public MapRegionSnapshotEntry {
        coordinate = Objects.requireNonNull(
                coordinate,
                "coordinate is required"
        );
        environmentProfile = Objects.requireNonNull(
                environmentProfile,
                "environmentProfile is required"
        );
        geologySummary = Objects.requireNonNull(
                geologySummary,
                "geologySummary is required"
        );
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
}

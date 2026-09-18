package cartographer.application;

import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceSummary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Interpreted map-region overlays retained for local Workstation recomposition.
 *
 * <p>Absence means that overlay was not requested/prepared by the source
 * operation. An empty present list means it was prepared and had no entries.</p>
 */
public record MapRegionOverlayState(
        Optional<List<EnvironmentProfile>> environmentProfiles,
        Optional<List<GeologicProvinceSummary>> geologySummaries
) {
    public MapRegionOverlayState {
        environmentProfiles = copy(environmentProfiles, "environmentProfiles");
        geologySummaries = copy(geologySummaries, "geologySummaries");
    }

    public boolean environmentPrepared() {
        return environmentProfiles.isPresent();
    }

    public boolean geologyPrepared() {
        return geologySummaries.isPresent();
    }

    private static <T> Optional<List<T>> copy(
            Optional<List<T>> value,
            String name
    ) {
        Objects.requireNonNull(value, name + " is required");
        return value.map(List::copyOf);
    }
}

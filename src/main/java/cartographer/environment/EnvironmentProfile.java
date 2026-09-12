package cartographer.environment;

import cartographer.model.MapRegionCoordinate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public record EnvironmentProfile(
        MapRegionCoordinate coordinate,
        Optional<ClimateSummary> climate,
        Optional<ForestSummary> forest,
        Optional<OceanSummary> ocean,
        Optional<IdMapSummary> landform,
        Optional<IdMapSummary> geologicProvince,
        Set<EnvironmentLabel> labels
) {
    public EnvironmentProfile {
        climate =
                climate.isEmpty()
                        ? Optional.empty()
                        : climate;

        forest =
                forest.isEmpty()
                        ? Optional.empty()
                        : forest;

        ocean =
                ocean.isEmpty()
                        ? Optional.empty()
                        : ocean;

        landform =
                landform.isEmpty()
                        ? Optional.empty()
                        : landform;

        geologicProvince =
                geologicProvince.isEmpty()
                        ? Optional.empty()
                        : geologicProvince;

        labels =
                labels == null
                        || labels.isEmpty()
                        ? Set.of()
                        : Collections.unmodifiableSet(
                                EnumSet.copyOf(
                                        labels
                                )
                        );
    }
}

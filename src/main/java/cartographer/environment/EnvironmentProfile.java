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

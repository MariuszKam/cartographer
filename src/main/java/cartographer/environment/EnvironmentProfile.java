package cartographer.environment;

import cartographer.model.MapRegionCoordinate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
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
        Objects.requireNonNull(
                coordinate,
                "EnvironmentProfile coordinate is required"
        );

        Objects.requireNonNull(
                climate,
                "EnvironmentProfile climate is required"
        );

        Objects.requireNonNull(
                forest,
                "EnvironmentProfile forest is required"
        );

        Objects.requireNonNull(
                ocean,
                "EnvironmentProfile ocean is required"
        );

        Objects.requireNonNull(
                landform,
                "EnvironmentProfile landform is required"
        );

        Objects.requireNonNull(
                geologicProvince,
                "EnvironmentProfile geologicProvince is required"
        );

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
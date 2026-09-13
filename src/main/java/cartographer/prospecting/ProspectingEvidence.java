package cartographer.prospecting;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

public record ProspectingEvidence(
        OptionalDouble worldgenSignal,
        RockColumnState geologyState,
        List<RockIdentity> observedHostRocks,
        ActualOreObservation actualOreObservation,
        int observedGeologyColumns,
        int noRockGeologyColumns,
        int unavailableGeologyColumns
) {
    public ProspectingEvidence(
            OptionalDouble worldgenSignal,
            RockColumnState geologyState,
            List<RockIdentity> observedHostRocks,
            boolean actualOreObserved
    ) {
        this(
                worldgenSignal,
                geologyState,
                observedHostRocks,
                actualOreObserved
                        ? ActualOreObservation.OBSERVED
                        : ActualOreObservation.NOT_OBSERVED,
                geologyState == RockColumnState.OBSERVED ? 1 : 0,
                geologyState == RockColumnState.NO_ROCK ? 1 : 0,
                geologyState == RockColumnState.UNAVAILABLE ? 1 : 0
        );
    }

    public ProspectingEvidence {
        Objects.requireNonNull(worldgenSignal, "worldgen signal is required");
        Objects.requireNonNull(geologyState, "geology state is required");
        Objects.requireNonNull(actualOreObservation, "actual ore observation is required");
        observedHostRocks = List.copyOf(
                Objects.requireNonNull(observedHostRocks, "observed rocks are required")
        );
        if (geologyState == RockColumnState.OBSERVED
                && observedHostRocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "observed geology requires at least one host rock"
            );
        }
        if (geologyState != RockColumnState.OBSERVED
                && !observedHostRocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "only observed geology may contain host rocks"
            );
        }
        if (worldgenSignal.isPresent()) {
            double signal = worldgenSignal.getAsDouble();
            if (!Double.isFinite(signal) || signal < 0.0 || signal > 1.0) {
                throw new IllegalArgumentException(
                        "worldgen signal must be finite and between 0 and 1"
                );
            }
        }
        if (observedGeologyColumns < 0
                || noRockGeologyColumns < 0
                || unavailableGeologyColumns < 0) {
            throw new IllegalArgumentException("geology coverage counts must not be negative");
        }
        if (unavailableGeologyColumns > 0 && geologyState == RockColumnState.NO_ROCK) {
            throw new IllegalArgumentException("partial geology cannot be classified as NO_ROCK");
        }
    }

    public boolean hasPositiveWorldgenSignal() {
        return worldgenSignal.isPresent() && worldgenSignal.getAsDouble() > 0.0;
    }

    public boolean actualOreObserved() {
        return actualOreObservation == ActualOreObservation.OBSERVED;
    }

    public boolean partialGeologyCoverage() {
        return unavailableGeologyColumns > 0
                && observedGeologyColumns + noRockGeologyColumns > 0;
    }
}

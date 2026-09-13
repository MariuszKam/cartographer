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
        boolean actualOreObserved
) {
    public ProspectingEvidence {
        Objects.requireNonNull(worldgenSignal, "worldgen signal is required");
        Objects.requireNonNull(geologyState, "geology state is required");
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
    }

    public boolean hasPositiveWorldgenSignal() {
        return worldgenSignal.isPresent() && worldgenSignal.getAsDouble() > 0.0;
    }
}

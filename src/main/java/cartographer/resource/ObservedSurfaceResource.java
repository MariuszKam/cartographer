package cartographer.resource;

import java.util.List;
import java.util.Objects;

/** One logical surface-object resource with its physical observations. */
public record ObservedSurfaceResource(
        SurfaceObjectCandidate candidate,
        List<SurfaceObjectObservation> observations
) {
    public ObservedSurfaceResource {
        Objects.requireNonNull(candidate, "candidate is required");
        observations = List.copyOf(Objects.requireNonNull(
                observations,
                "observations are required"
        ));
        if (observations.isEmpty()) {
            throw new IllegalArgumentException(
                    "observed resource requires observations"
            );
        }
        if (observations.stream().anyMatch(
                observation -> !candidate.equals(observation.candidate())
        )) {
            throw new IllegalArgumentException(
                    "observations must belong to the resource candidate"
            );
        }
    }

    public int observedCount() {
        return observations.size();
    }
}

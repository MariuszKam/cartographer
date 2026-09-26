package cartographer.prospecting;

import cartographer.resource.ActualOreObservation;
import cartographer.geology.rock.RockMap;

import java.util.Map;
import java.util.Objects;

public record FusedProspectingResult(
        RockMap rockMap,
        Map<String, ActualOreObservation> observations
) {
    public FusedProspectingResult {
        Objects.requireNonNull(rockMap, "rock map is required");
        observations = Map.copyOf(Objects.requireNonNull(observations, "observations are required"));
    }

    public ActualOreObservation observation(String resourceKey) {
        return observations.getOrDefault(resourceKey, ActualOreObservation.NOT_OBSERVED);
    }
}

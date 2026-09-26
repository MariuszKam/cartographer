package cartographer.application;

import cartographer.geology.rock.RockMap;
import cartographer.model.WorldPosition;
import cartographer.prospecting.ProspectingAssessment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProspectingAreaResult(
        WorldPosition center,
        int radius,
        List<ProspectingAssessment> assessments,
        Optional<RockMap> rockMap
) {
    public ProspectingAreaResult {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(assessments, "assessments are required");
        assessments = List.copyOf(assessments);
        Objects.requireNonNull(
                rockMap,
                "rockMap is required; use Optional.empty() when unavailable"
        );
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}

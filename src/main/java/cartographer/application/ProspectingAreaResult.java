package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.prospecting.ProspectingAssessment;

import java.util.List;
import java.util.Objects;

public record ProspectingAreaResult(
        WorldPosition center,
        int radius,
        List<ProspectingAssessment> assessments
) {
    public ProspectingAreaResult {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(assessments, "assessments are required");
        assessments = List.copyOf(assessments);
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}

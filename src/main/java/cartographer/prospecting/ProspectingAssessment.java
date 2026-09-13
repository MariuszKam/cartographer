package cartographer.prospecting;

import java.util.List;
import java.util.Objects;

public record ProspectingAssessment(
        ProspectingCandidate candidate,
        OreRockCompatibility compatibility,
        ProspectingRank rank,
        List<String> reasons
) {
    public ProspectingAssessment {
        Objects.requireNonNull(candidate, "prospecting candidate is required");
        Objects.requireNonNull(compatibility, "compatibility is required");
        Objects.requireNonNull(rank, "prospecting rank is required");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons are required"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("prospecting assessment requires reasons");
        }
    }
}

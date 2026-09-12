package cartographer.resource;

import java.util.List;

public record ResourceSummary(
        String resourceKey,
        int regions,
        int cells,
        int rawMin,
        int rawMax,
        double averageRawValue,
        int distinctValues,
        List<ResourceCandidate> strongestCandidates
) {
    public ResourceSummary {
        if (resourceKey == null
                || resourceKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Resource summary key is required"
            );
        }

        strongestCandidates =
                strongestCandidates == null
                        ? List.of()
                        : List.copyOf(
                                strongestCandidates
                        );
    }
}

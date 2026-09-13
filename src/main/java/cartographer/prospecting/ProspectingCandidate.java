package cartographer.prospecting;

import java.util.Objects;

public record ProspectingCandidate(
        String resourceKey,
        ProspectingEvidence evidence
) {
    public ProspectingCandidate {
        Objects.requireNonNull(resourceKey, "resource key is required");
        Objects.requireNonNull(evidence, "prospecting evidence is required");
        if (resourceKey.isBlank()) {
            throw new IllegalArgumentException("resource key must not be blank");
        }
    }
}

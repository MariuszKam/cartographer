package cartographer.perf.macro;

import java.util.Objects;
import java.util.Optional;

/** One operation result with correctness and cache-work facts kept separate. */
public record Pf18IterationEvidence(
        Optional<String> semanticFingerprint,
        Optional<String> imageFingerprint,
        boolean cacheHit,
        String sourceWork
) {
    public Pf18IterationEvidence {
        semanticFingerprint = Objects.requireNonNull(semanticFingerprint,
                "semantic fingerprint is required");
        imageFingerprint = Objects.requireNonNull(imageFingerprint,
                "image fingerprint is required");
        sourceWork = Objects.requireNonNull(sourceWork, "source work is required");
    }

    public String compositeFingerprint() {
        return semanticFingerprint.orElse("UNAVAILABLE") + "\n"
                + imageFingerprint.orElse("UNAVAILABLE");
    }
}

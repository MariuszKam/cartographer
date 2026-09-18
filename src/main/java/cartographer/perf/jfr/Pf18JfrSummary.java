package cartographer.perf.jfr;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable separate JFR diagnostic summary; it is not a timing baseline. */
public record Pf18JfrSummary(
        Path recording,
        Path summary,
        boolean diagnosticOnly,
        List<String> lines
) {
    public Pf18JfrSummary {
        recording = Objects.requireNonNull(recording).toAbsolutePath().normalize();
        summary = Objects.requireNonNull(summary).toAbsolutePath().normalize();
        if (!diagnosticOnly) {
            throw new IllegalArgumentException("PF-1.8 JFR evidence is diagnostic only");
        }
        lines = List.copyOf(Objects.requireNonNull(lines));
    }
}

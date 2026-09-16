package cartographer.perf.safety;

import java.util.List;
import java.util.Objects;

/** Immutable result of comparing protected save-state snapshots. */
public record SaveSafetyResult(
        SaveSafetyStatus status,
        List<SaveSafetyViolation> violations
) {
    public SaveSafetyResult {
        Objects.requireNonNull(status, "status is required");
        violations = List.copyOf(Objects.requireNonNull(violations, "violations is required"));
        if (status == SaveSafetyStatus.PASS && !violations.isEmpty()) {
            throw new IllegalArgumentException("PASS cannot contain violations");
        }
        if (status == SaveSafetyStatus.FAIL && violations.isEmpty()) {
            throw new IllegalArgumentException("FAIL requires at least one violation");
        }
    }
}

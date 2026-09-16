package cartographer.perf.benchmark;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.instrumentation.PerformanceInstrumentationSnapshot;

import java.util.Objects;
import java.util.Optional;

/** Operation output supplied to the runner without timing or benchmark policy. */
public record BenchmarkOperationResult(
        Optional<ResultFingerprint> fingerprint,
        Optional<PerformanceInstrumentationSnapshot> instrumentation,
        Optional<BenchmarkFailure> failure
) {
    public BenchmarkOperationResult {
        Objects.requireNonNull(fingerprint, "fingerprint is required");
        Objects.requireNonNull(instrumentation, "instrumentation is required");
        Objects.requireNonNull(failure, "failure is required");
        if (fingerprint.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException(
                    "exactly one of fingerprint or failure must be present"
            );
        }
    }

    public static BenchmarkOperationResult success(
            ResultFingerprint fingerprint,
            Optional<PerformanceInstrumentationSnapshot> instrumentation
    ) {
        return new BenchmarkOperationResult(
                Optional.of(Objects.requireNonNull(fingerprint, "fingerprint is required")),
                Objects.requireNonNull(instrumentation, "instrumentation is required"),
                Optional.empty()
        );
    }

    public static BenchmarkOperationResult success(ResultFingerprint fingerprint) {
        return success(fingerprint, Optional.empty());
    }

    public static BenchmarkOperationResult failure(
            BenchmarkFailure failure,
            Optional<PerformanceInstrumentationSnapshot> instrumentation
    ) {
        return new BenchmarkOperationResult(
                Optional.empty(),
                Objects.requireNonNull(instrumentation, "instrumentation is required"),
                Optional.of(Objects.requireNonNull(failure, "failure is required"))
        );
    }
}

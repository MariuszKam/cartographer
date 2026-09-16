package cartographer.perf.comparison;

/** Deterministic nearest-rank wall-clock summary, with values in nanoseconds. */
public record CandidateMeasurementSummary(
        long minWallClockNanoseconds,
        long p50WallClockNanoseconds,
        long p95WallClockNanoseconds,
        long maxWallClockNanoseconds
) {
    public CandidateMeasurementSummary {
        if (minWallClockNanoseconds < 0
                || p50WallClockNanoseconds < 0
                || p95WallClockNanoseconds < 0
                || maxWallClockNanoseconds < 0) {
            throw new IllegalArgumentException("summary durations must not be negative");
        }
    }
}

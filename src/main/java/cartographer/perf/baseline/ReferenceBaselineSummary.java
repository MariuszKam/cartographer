package cartographer.perf.baseline;

/** Deterministic nearest-rank wall-clock summary, with all values in nanoseconds. */
public record ReferenceBaselineSummary(
        long minWallClockNanoseconds,
        long p50WallClockNanoseconds,
        long p95WallClockNanoseconds,
        long maxWallClockNanoseconds
) {
    public ReferenceBaselineSummary {
        if (minWallClockNanoseconds < 0
                || p50WallClockNanoseconds < 0
                || p95WallClockNanoseconds < 0
                || maxWallClockNanoseconds < 0) {
            throw new IllegalArgumentException("summary durations must not be negative");
        }
    }
}

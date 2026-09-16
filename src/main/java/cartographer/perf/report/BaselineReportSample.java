package cartographer.perf.report;

/** Immutable raw wall-clock sample included in a baseline report. */
public record BaselineReportSample(int iterationIndex, long wallClockNanoseconds) {
    public BaselineReportSample {
        if (iterationIndex < 0) {
            throw new IllegalArgumentException("iterationIndex must not be negative");
        }
        if (wallClockNanoseconds < 0) {
            throw new IllegalArgumentException(
                    "wallClockNanoseconds must not be negative"
            );
        }
    }
}

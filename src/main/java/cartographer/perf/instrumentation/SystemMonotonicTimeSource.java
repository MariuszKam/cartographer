package cartographer.perf.instrumentation;

/** Production monotonic time source. Wall-clock date/time is not involved. */
public final class SystemMonotonicTimeSource implements MonotonicTimeSource {
    public static final SystemMonotonicTimeSource INSTANCE =
            new SystemMonotonicTimeSource();

    private SystemMonotonicTimeSource() {
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}

package cartographer.perf.instrumentation;

/** Supplies monotonic nanoseconds for coarse-grained stage timing. */
@FunctionalInterface
public interface MonotonicTimeSource {
    long nanoTime();
}

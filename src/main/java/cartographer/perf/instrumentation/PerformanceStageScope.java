package cartographer.perf.instrumentation;

/** Coarse stage timing scope; close it with try-with-resources. */
@FunctionalInterface
public interface PerformanceStageScope extends AutoCloseable {
    @Override
    void close();
}

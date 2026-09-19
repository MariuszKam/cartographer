package cartographer.save;

@FunctionalInterface
public interface ChunkReadMetricsProbe {
    ChunkReadMetricsProbe NONE = metrics -> { };

    void record(ChunkReadMetrics metrics);
}

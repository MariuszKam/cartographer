package cartographer.save;

public enum ChunkReadStrategy {
    EXACT_POSITION_BATCHES,
    RANGE_RUN_BATCHES,
    TABLE_STREAM
}

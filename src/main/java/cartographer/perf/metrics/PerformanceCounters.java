package cartographer.perf.metrics;

/** Immutable counts and byte totals collected by future performance stages. */
public record PerformanceCounters(
        long sqliteRowsVisited,
        long sqliteRowsAccepted,
        long sqliteBlobBytesRead,
        long chunksRequested,
        long chunksFound,
        long chunksMissing,
        long chunksPaletteRejected,
        long chunksDecoded,
        long chunkDecodeFailures,
        long mapchunksRead,
        long mapregionsRead,
        long zstdInputBytes,
        long zstdOutputBytes
) {
    public PerformanceCounters {
        requireNonNegative(sqliteRowsVisited, "sqliteRowsVisited");
        requireNonNegative(sqliteRowsAccepted, "sqliteRowsAccepted");
        requireNonNegative(sqliteBlobBytesRead, "sqliteBlobBytesRead");
        requireNonNegative(chunksRequested, "chunksRequested");
        requireNonNegative(chunksFound, "chunksFound");
        requireNonNegative(chunksMissing, "chunksMissing");
        requireNonNegative(chunksPaletteRejected, "chunksPaletteRejected");
        requireNonNegative(chunksDecoded, "chunksDecoded");
        requireNonNegative(chunkDecodeFailures, "chunkDecodeFailures");
        requireNonNegative(mapchunksRead, "mapchunksRead");
        requireNonNegative(mapregionsRead, "mapregionsRead");
        requireNonNegative(zstdInputBytes, "zstdInputBytes");
        requireNonNegative(zstdOutputBytes, "zstdOutputBytes");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}

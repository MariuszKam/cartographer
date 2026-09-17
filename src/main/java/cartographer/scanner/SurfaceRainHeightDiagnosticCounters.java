package cartographer.scanner;

/** Final legacy-compatible diagnostic counters for one compact Surface run. */
public record SurfaceRainHeightDiagnosticCounters(
        int columnsScanned,
        int emptyColumns,
        int liquidUnavailableColumns
) {
    public SurfaceRainHeightDiagnosticCounters {
        if (columnsScanned < 0 || emptyColumns < 0 || liquidUnavailableColumns < 0) {
            throw new IllegalArgumentException("surface diagnostic counters cannot be negative");
        }
    }
}

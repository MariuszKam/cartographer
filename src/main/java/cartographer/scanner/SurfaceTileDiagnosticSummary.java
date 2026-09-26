package cartographer.scanner;

/** Final Surface fallback diagnostics for one complete mapchunk domain. */
public record SurfaceTileDiagnosticSummary(
        int columnsScanned,
        int emptyColumns,
        int liquidUnavailableColumns
) {
    public SurfaceTileDiagnosticSummary {
        if (emptyColumns < 0 || liquidUnavailableColumns < 0
                || emptyColumns > columnsScanned
                || liquidUnavailableColumns > columnsScanned) {
            throw new IllegalArgumentException("invalid Surface fallback diagnostics");
        }
    }
}

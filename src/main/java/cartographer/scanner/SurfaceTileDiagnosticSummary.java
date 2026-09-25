package cartographer.scanner;

/** Final Surface fallback diagnostics for one complete mapchunk domain. */
public record SurfaceTileDiagnosticSummary(
        int columnsScanned,
        int emptyColumns,
        int liquidUnavailableColumns
) {
    public SurfaceTileDiagnosticSummary {
        if (columnsScanned < 0 || emptyColumns < 0 || liquidUnavailableColumns < 0
                || emptyColumns > columnsScanned
                || liquidUnavailableColumns > columnsScanned) {
            throw new IllegalArgumentException("invalid Surface fallback diagnostics");
        }
    }
}

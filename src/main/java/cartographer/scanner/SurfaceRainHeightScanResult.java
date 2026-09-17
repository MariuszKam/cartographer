package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Objects;

/** Compact fast-path result and diagnostics for the C/D transition. */
public record SurfaceRainHeightScanResult(
        SurfaceMap surface,
        List<MapChunkCoordinate> fallbackMapChunks,
        int resolvedColumns,
        int unresolvedColumns,
        int liquidUnavailableColumns
) {
    public SurfaceRainHeightScanResult {
        Objects.requireNonNull(surface, "surface is required");
        fallbackMapChunks = List.copyOf(Objects.requireNonNull(
                fallbackMapChunks, "fallback mapchunks are required"));
        if (resolvedColumns < 0 || unresolvedColumns < 0 || liquidUnavailableColumns < 0) {
            throw new IllegalArgumentException("scan counters cannot be negative");
        }
    }
}

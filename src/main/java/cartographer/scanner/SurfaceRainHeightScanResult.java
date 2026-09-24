package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compact fast-path result and diagnostics for the C/D transition. */
public record SurfaceRainHeightScanResult(
        SurfaceMap surface,
        List<MapChunkCoordinate> fallbackMapChunks,
        SurfaceRainHeightDiagnosticCounters diagnostics,
        Map<MapChunkCoordinate, SurfaceTileDiagnosticSummary> fallbackDiagnosticsByMapChunk,
        int sourceMapChunksLoaded
) {
    public SurfaceRainHeightScanResult {
        Objects.requireNonNull(surface, "surface is required");
        fallbackMapChunks = List.copyOf(Objects.requireNonNull(
                fallbackMapChunks, "fallback mapchunks are required"));
        Objects.requireNonNull(diagnostics, "diagnostics are required");
        fallbackDiagnosticsByMapChunk = Map.copyOf(Objects.requireNonNull(
                fallbackDiagnosticsByMapChunk, "fallback diagnostics are required"));
        if (sourceMapChunksLoaded < 0) {
            throw new IllegalArgumentException("source mapchunk count cannot be negative");
        }
    }
}

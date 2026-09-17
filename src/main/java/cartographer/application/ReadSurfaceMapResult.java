package cartographer.application;

import cartographer.save.ReadDiagnostics;
import cartographer.scanner.SurfaceMapScanResult;

import java.util.Objects;

public record ReadSurfaceMapResult(
        SurfaceMapScanResult surface,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics
) {
    public ReadSurfaceMapResult {
        Objects.requireNonNull(surface, "surface is required");
        Objects.requireNonNull(mapChunkDiagnostics, "mapchunk diagnostics are required");
        Objects.requireNonNull(chunkDiagnostics, "chunk diagnostics are required");
    }
}

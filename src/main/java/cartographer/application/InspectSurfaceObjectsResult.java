package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.WorldPosition;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.scanner.SurfaceObjectCompactScanResult;

import java.util.List;

public record InspectSurfaceObjectsResult(
        WorldPosition center,
        List<BlockInfo> registryMatches,
        int plannedTargetCount,
        int requestedChunkPositionCount,
        SelectiveChunkStreamStats chunkStats,
        SurfaceObjectCompactScanResult scan,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics
) {
    public InspectSurfaceObjectsResult {
        registryMatches = List.copyOf(registryMatches);
        if (plannedTargetCount < 0 || requestedChunkPositionCount < 0) {
            throw new IllegalArgumentException("inspection counts cannot be negative");
        }
    }
}

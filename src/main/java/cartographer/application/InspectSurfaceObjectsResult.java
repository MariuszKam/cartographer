package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.WorldPosition;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.scanner.SurfaceObjectPlan;
import cartographer.scanner.SurfaceObjectScanResult;

import java.util.List;

public record InspectSurfaceObjectsResult(
        WorldPosition center,
        List<BlockInfo> registryMatches,
        SurfaceObjectPlan plan,
        SelectiveChunkStreamStats chunkStats,
        SurfaceObjectScanResult scan,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics
) {
    public InspectSurfaceObjectsResult {
        registryMatches = List.copyOf(registryMatches);
    }
}

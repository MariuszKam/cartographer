package cartographer.application;

import cartographer.model.WorldMetadata;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;

import java.util.Map;

final class SurfaceMapScanResultCompatibility {
    private SurfaceMapScanResultCompatibility() {
    }

    static SurfaceMapScanResult fromLegacy(SurfaceScanResult legacy) {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                0, 0, 1, new WorldMetadata(1, 1, 1));
        return new SurfaceMapScanResult(
                new SurfaceTileAccumulator(layout).finish(),
                Map.of(),
                legacy.chunksScanned(),
                legacy.columnsScanned(),
                legacy.emptyColumns(),
                legacy.liquidUnavailableColumns()
        );
    }
}

package cartographer.scanner;

import cartographer.model.SurfaceBlock;

import java.util.List;

public record SurfaceScanResult(List<SurfaceBlock> blocks, int chunksScanned, int columnsScanned, int emptyColumns) {
}

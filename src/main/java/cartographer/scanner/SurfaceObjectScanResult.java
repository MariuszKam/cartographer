package cartographer.scanner;

import cartographer.model.SurfaceBlock;

import java.util.List;

public record SurfaceObjectScanResult(
        List<SurfaceBlock> blocks,
        int positionsInspected,
        int unavailablePositions
) {
    public SurfaceObjectScanResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        if (positionsInspected < 0 || unavailablePositions < 0
                || unavailablePositions > positionsInspected) {
            throw new IllegalArgumentException("invalid surface object scan counts");
        }
    }

    public int observedObjects() {
        return blocks.size();
    }
}

package cartographer.scanner;

import cartographer.model.SurfaceBlock;

import java.util.List;

public record SurfaceObjectScanResult(
        List<SurfaceBlock> blocks,
        int positionsInspected,
        int unavailablePositions,
        int observedTargets,
        int notObservedTargets
) {
    public SurfaceObjectScanResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        if (positionsInspected < 0 || unavailablePositions < 0
                || unavailablePositions > positionsInspected
                || observedTargets < 0
                || notObservedTargets < 0
                || observedTargets + notObservedTargets + unavailablePositions
                != positionsInspected) {
            throw new IllegalArgumentException("invalid surface object scan counts");
        }
    }

    public int observedObjects() {
        return blocks.size();
    }

    public SurfaceObjectScanResult(
            List<SurfaceBlock> blocks,
            int positionsInspected,
            int unavailablePositions
    ) {
        this(
                blocks,
                positionsInspected,
                unavailablePositions,
                blocks == null ? 0 : blocks.size(),
                Math.max(0, positionsInspected - unavailablePositions
                        - (blocks == null ? 0 : blocks.size()))
        );
    }
}

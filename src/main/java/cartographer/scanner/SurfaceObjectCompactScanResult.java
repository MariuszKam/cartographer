package cartographer.scanner;

import java.util.Objects;

/** Immutable primitive observations and scalar status counts for F. */
public final class SurfaceObjectCompactScanResult {
    private final int[] worldX;
    private final int[] worldY;
    private final int[] worldZ;
    private final int[] blockIds;
    private final int positionsInspected;
    private final int unavailablePositions;
    private final int observedTargets;
    private final int notObservedTargets;

    SurfaceObjectCompactScanResult(
            int[] worldX,
            int[] worldY,
            int[] worldZ,
            int[] blockIds,
            int positionsInspected,
            int unavailablePositions,
            int observedTargets,
            int notObservedTargets
    ) {
        this.worldX = worldX.clone();
        this.worldY = worldY.clone();
        this.worldZ = worldZ.clone();
        this.blockIds = blockIds.clone();
        this.positionsInspected = positionsInspected;
        this.unavailablePositions = unavailablePositions;
        this.observedTargets = observedTargets;
        this.notObservedTargets = notObservedTargets;
        if (this.worldX.length != this.worldY.length
                || this.worldX.length != this.worldZ.length
                || this.worldX.length != this.blockIds.length
                || positionsInspected < 0
                || unavailablePositions < 0
                || observedTargets < 0
                || notObservedTargets < 0
                || unavailablePositions > positionsInspected
                || observedTargets + notObservedTargets + unavailablePositions
                != positionsInspected) {
            throw new IllegalArgumentException("invalid compact object scan result");
        }
    }

    public int positionsInspected() { return positionsInspected; }
    public int unavailablePositions() { return unavailablePositions; }
    public int observedTargets() { return observedTargets; }
    public int notObservedTargets() { return notObservedTargets; }
    public int observedObjects() { return worldX.length; }

    public static SurfaceObjectCompactScanResult empty() {
        return new SurfaceObjectCompactScanResult(
                new int[0], new int[0], new int[0], new int[0], 0, 0, 0, 0);
    }

    public void forEachObservation(ObservationConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer is required");
        for (int index = 0; index < worldX.length; index++) {
            consumer.accept(worldX[index], worldY[index], worldZ[index], blockIds[index]);
        }
    }

    @FunctionalInterface
    public interface ObservationConsumer {
        void accept(int worldX, int worldY, int worldZ, int blockId);
    }
}

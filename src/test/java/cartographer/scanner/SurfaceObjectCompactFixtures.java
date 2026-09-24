package cartographer.scanner;

/** Test-only factory for small deterministic compact Surface-object scans. */
public final class SurfaceObjectCompactFixtures {
    private SurfaceObjectCompactFixtures() {
    }

    public static Observation observation(
            int worldX,
            int worldY,
            int worldZ,
            int blockId
    ) {
        return new Observation(worldX, worldY, worldZ, blockId);
    }

    public static SurfaceObjectCompactScanResult scan(
            Observation... observations
    ) {
        int count = observations.length;
        int[] worldX = new int[count];
        int[] worldY = new int[count];
        int[] worldZ = new int[count];
        int[] blockIds = new int[count];
        for (int index = 0; index < count; index++) {
            Observation observation = observations[index];
            worldX[index] = observation.worldX();
            worldY[index] = observation.worldY();
            worldZ[index] = observation.worldZ();
            blockIds[index] = observation.blockId();
        }
        return new SurfaceObjectCompactScanResult(
                worldX,
                worldY,
                worldZ,
                blockIds,
                0,
                count,
                0
        );
    }

    public record Observation(
            int worldX,
            int worldY,
            int worldZ,
            int blockId
    ) {
    }
}

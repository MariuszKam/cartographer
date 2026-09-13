package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceFallbackMapChunksTest {

    private final SurfaceFallbackMapChunks collector = new SurfaceFallbackMapChunks();
    private final RainHeightSurfacePlan healthyPlan = new RainHeightSurfacePlan(
            List.of(), List.of(), List.of()
    );
    private final RainHeightSurfaceScanResult healthyScan = new RainHeightSurfaceScanResult(
            List.of(), List.of()
    );

    @Test
    void missingDeliveredMapChunkRequiresFallback() {
        MapChunkCoordinate planned = new MapChunkCoordinate(1, 2);

        assertEquals(List.of(planned), collect(List.of(planned), List.of()));
    }

    @Test
    void rainPlanFallbackRequiresFallback() {
        MapChunkCoordinate fallback = new MapChunkCoordinate(1, 2);
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(), List.of(), List.of(fallback)
        );

        assertEquals(List.of(fallback), collect(List.of(), List.of(), plan, healthyScan));
    }

    @Test
    void unresolvedTargetRequiresFallback() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(33, 45, 65);
        RainHeightSurfaceScanResult scan = new RainHeightSurfaceScanResult(
                List.of(), List.of(target)
        );

        assertEquals(
                List.of(new MapChunkCoordinate(1, 2)),
                collect(List.of(), List.of(), healthyPlan, scan)
        );
    }

    @Test
    void healthyDeliveredResolvedMapChunkDoesNotFallback() {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);

        assertEquals(
                List.of(),
                collect(List.of(coordinate), List.of(coordinate))
        );
    }

    @Test
    void unionIsDeduplicated() {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(), List.of(), List.of(coordinate)
        );
        RainHeightSurfaceScanResult scan = new RainHeightSurfaceScanResult(
                List.of(), List.of(new RainHeightSurfaceTarget(33, 45, 65))
        );

        assertEquals(
                List.of(coordinate),
                collect(List.of(coordinate), List.of(), plan, scan)
        );
    }

    @Test
    void outputIsDeterministic() {
        List<MapChunkCoordinate> result = collect(
                List.of(
                        new MapChunkCoordinate(2, 1),
                        new MapChunkCoordinate(1, 0)
                ),
                List.of()
        );

        assertEquals(
                List.of(
                        new MapChunkCoordinate(1, 0),
                        new MapChunkCoordinate(2, 1)
                ),
                result
        );
    }

    private List<MapChunkCoordinate> collect(
            List<MapChunkCoordinate> planned,
            List<MapChunkCoordinate> delivered
    ) {
        return collect(planned, delivered, healthyPlan, healthyScan);
    }

    private List<MapChunkCoordinate> collect(
            List<MapChunkCoordinate> planned,
            List<MapChunkCoordinate> delivered,
            RainHeightSurfacePlan plan,
            RainHeightSurfaceScanResult scan
    ) {
        return collector.collect(planned, delivered, plan, scan);
    }
}
